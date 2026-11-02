package com.wastelens.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wastelens.domain.WasteEntry;
import com.wastelens.repository.WasteEntryRepository;
import com.wastelens.web.dto.*;
import com.wastelens.web.exception.RateLimitException;
import com.wastelens.web.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class ReportService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static final LocalDate EARLIEST_WEEK_START = LocalDate.of(2019, 12, 30);
    public static final int MAX_EXPORT_BYTES = 8 * 1024 * 1024; // 8 MiB bound

    private final WasteEntryRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    // Maximum 2 concurrent exports across the application (T-RES-02)
    private final Semaphore exportSemaphore = new Semaphore(2);

    // Export rate limit: 5 requests per minute per owner (T-LIM-03)
    private final ConcurrentHashMap<UUID, Deque<Long>> exportTimestamps = new ConcurrentHashMap<>();

    public ReportService(WasteEntryRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Aggregates weekly summary within a single REPEATABLE_READ snapshot (ADR-007).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WeekSummaryResponse getWeekSummary(UUID ownerId, LocalDate monday) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        return buildWeekSummary(ownerId, monday, today);
    }

    /**
     * Compares two distinct weeks from a single REPEATABLE_READ snapshot (T-CMP-01..06).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompareResponse compareWeeks(UUID ownerId, LocalDate weekA, LocalDate weekB) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        WeekSummaryResponse summaryA = buildWeekSummary(ownerId, weekA, today);
        WeekSummaryResponse summaryB = buildWeekSummary(ownerId, weekB, today);

        if (summaryA.entryCount() == 0 || summaryB.entryCount() == 0) {
            // Nulls and comparable: false if either count is 0 (T-CMP-02)
            return new CompareResponse(summaryA, summaryB, null, null, false);
        }

        long diffGrams = summaryB.totalGrams() - summaryA.totalGrams();
        Double percentChange;

        if (summaryA.totalGrams() == 0) {
            percentChange = 0.0;
        } else {
            BigDecimal bigDiff = BigDecimal.valueOf(diffGrams).multiply(BigDecimal.valueOf(100));
            BigDecimal bigBase = BigDecimal.valueOf(summaryA.totalGrams());
            BigDecimal rounded = bigDiff.divide(bigBase, 1, RoundingMode.HALF_UP);
            percentChange = rounded.doubleValue();
        }

        return new CompareResponse(summaryA, summaryB, diffGrams, percentChange, true);
    }

    /**
     * Exports all entries for the owner in sorted order with 8 MiB limit, rate limit and concurrency semaphore.
     */
    public byte[] exportEntries(UUID ownerId) {
        checkAndRecordExportRateLimit(ownerId);

        if (!exportSemaphore.tryAcquire()) {
            throw new RateLimitException("Export concurrency limit reached (maximum 2 concurrent exports)");
        }

        try {
            return executeExportUnderTransaction(ownerId);
        } finally {
            exportSemaphore.release();
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    protected byte[] executeExportUnderTransaction(UUID ownerId) {
        List<WasteEntry> entries = repository.findAllByOwnerForExport(ownerId);
        List<EntryResponse> dtos = entries.stream()
            .map(EntryResponse::fromDomain)
            .toList();

        ExportResponse exportResponse = new ExportResponse(
            1,
            Instant.now(clock).toString(),
            ZONE.getId(),
            dtos
        );

        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writeValueAsBytes(exportResponse);
        } catch (JsonProcessingException e) {
            throw new ServiceUnavailableException("Failed to serialize export data: " + e.getMessage());
        }

        if (jsonBytes.length > MAX_EXPORT_BYTES) {
            throw new ServiceUnavailableException("Export payload exceeded 8 MiB limit");
        }

        return jsonBytes;
    }

    private WeekSummaryResponse buildWeekSummary(UUID ownerId, LocalDate monday, LocalDate today) {
        LocalDate sunday = monday.plusDays(6);
        boolean inProgress = !today.isBefore(monday) && !today.isAfter(sunday);

        WasteEntryRepository.WeekTotalAndCount totalAndCount = repository.getWeekTotalAndCount(ownerId, monday);
        List<CategoryGramsDto> topCategories = repository.getWeekTopCategories(ownerId, monday);

        return new WeekSummaryResponse(
            monday.toString(),
            sunday.toString(),
            ZONE.getId(),
            totalAndCount.totalGrams(),
            totalAndCount.entryCount(),
            inProgress,
            topCategories
        );
    }

    private void checkAndRecordExportRateLimit(UUID ownerId) {
        long now = clock.millis();
        long windowStart = now - 60_000;

        exportTimestamps.compute(ownerId, (key, queue) -> {
            if (queue == null) {
                queue = new ArrayDeque<>();
            }
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }
            if (queue.size() >= 5) {
                throw new RateLimitException("Export rate limit exceeded (5 requests per minute)");
            }
            queue.addLast(now);
            return queue;
        });
    }

    public LocalDate getCurrentWeekStart() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
