package com.wastelens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.domain.WasteEntry;
import com.wastelens.repository.WasteEntryRepository;
import com.wastelens.web.dto.CategoryGramsDto;
import com.wastelens.web.dto.CompareResponse;
import com.wastelens.web.dto.WeekSummaryResponse;
import com.wastelens.web.exception.RateLimitException;
import com.wastelens.web.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private WasteEntryRepository repository;
    private ObjectMapper objectMapper;
    private Clock fixedClock;
    private ReportService reportService;

    private final UUID ownerA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WasteEntryRepository.class);
        objectMapper = new ObjectMapper();
        // Fixed at 2026-11-01 18:32:00 Asia/Kolkata
        // In UTC: 2026-11-01 13:02:00Z
        fixedClock = Clock.fixed(Instant.parse("2026-11-01T13:02:00Z"), ZoneId.of("Asia/Kolkata"));
        reportService = new ReportService(repository, fixedClock, objectMapper);
    }

    @Test
    @DisplayName("T-TIME-01: UTC 18:29:59Z vs 18:30:00Z on 1 Nov 2026 produces Kolkata Nov 1 vs Nov 2")
    void tTime01_kolkataBoundaryTimezone() {
        // 18:29:59 UTC + 5:30 = 23:59:59 Nov 1 (Sunday)
        Clock clockBeforeBoundary = Clock.fixed(Instant.parse("2026-11-01T18:29:59Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate dateBefore = LocalDate.now(clockBeforeBoundary);
        assertEquals(LocalDate.of(2026, 11, 1), dateBefore);

        // 18:30:00 UTC + 5:30 = 00:00:00 Nov 2 (Monday)
        Clock clockAfterBoundary = Clock.fixed(Instant.parse("2026-11-01T18:30:00Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate dateAfter = LocalDate.now(clockAfterBoundary);
        assertEquals(LocalDate.of(2026, 11, 2), dateAfter);
    }

    @Test
    @DisplayName("T-TIME-02: Sun Nov 1 vs Mon Nov 2 are in distinct weeks")
    void tTime02_sundayAndMondayInDistinctWeeks() {
        Clock sundayClock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        ReportService svcSunday = new ReportService(repository, sundayClock, objectMapper);
        assertEquals(LocalDate.of(2026, 10, 26), svcSunday.getCurrentWeekStart());

        Clock mondayClock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        ReportService svcMonday = new ReportService(repository, mondayClock, objectMapper);
        assertEquals(LocalDate.of(2026, 11, 2), svcMonday.getCurrentWeekStart());
    }

    @Test
    @DisplayName("T-CMP-01: Worked example: A 2000g/2 vs B 1000g/2 -> -1000g, -50.0%, B top produce & dairy")
    void tCmp01_workedExampleMatchesPRD() {
        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);

        when(repository.getWeekTotalAndCount(ownerA, weekA))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(2000L, 2L));
        when(repository.getWeekTopCategories(ownerA, weekA))
            .thenReturn(List.of(new CategoryGramsDto("produce", 1200L), new CategoryGramsDto("grains", 800L)));

        when(repository.getWeekTotalAndCount(ownerA, weekB))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(1000L, 2L));
        when(repository.getWeekTopCategories(ownerA, weekB))
            .thenReturn(List.of(new CategoryGramsDto("produce", 600L), new CategoryGramsDto("dairy", 400L)));

        CompareResponse response = reportService.compareWeeks(ownerA, weekA, weekB);

        assertTrue(response.comparable());
        assertEquals(-1000L, response.differenceGrams());
        assertEquals(-50.0, response.percentChange());
        assertEquals(2, response.weekA().topCategories().size());
        assertEquals(2, response.weekB().topCategories().size());
        assertEquals("produce", response.weekB().topCategories().get(0).category());
        assertEquals(600L, response.weekB().topCategories().get(0).grams());
        assertEquals("dairy", response.weekB().topCategories().get(1).category());
        assertEquals(400L, response.weekB().topCategories().get(1).grams());
    }

    @Test
    @DisplayName("T-CMP-02: Empty A, empty B, or both empty -> count 0, diff/percent null, comparable: false")
    void tCmp02_emptyWeeksNotComparable() {
        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);

        // Case 1: week A has 0 entries
        when(repository.getWeekTotalAndCount(ownerA, weekA))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(0L, 0L));
        when(repository.getWeekTopCategories(ownerA, weekA))
            .thenReturn(List.of());
        when(repository.getWeekTotalAndCount(ownerA, weekB))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(500L, 1L));
        when(repository.getWeekTopCategories(ownerA, weekB))
            .thenReturn(List.of(new CategoryGramsDto("produce", 500L)));

        CompareResponse res1 = reportService.compareWeeks(ownerA, weekA, weekB);
        assertFalse(res1.comparable());
        assertNull(res1.differenceGrams());
        assertNull(res1.percentChange());

        // Case 2: week B has 0 entries
        when(repository.getWeekTotalAndCount(ownerA, weekA))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(500L, 1L));
        when(repository.getWeekTotalAndCount(ownerA, weekB))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(0L, 0L));

        CompareResponse res2 = reportService.compareWeeks(ownerA, weekA, weekB);
        assertFalse(res2.comparable());
        assertNull(res2.differenceGrams());
        assertNull(res2.percentChange());
    }

    @Test
    @DisplayName("T-CMP-03: Both non-empty, equal totals -> 0 g / 0.0%")
    void tCmp03_equalTotalsZeroDiffAndPercent() {
        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);

        when(repository.getWeekTotalAndCount(ownerA, weekA))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(1500L, 3L));
        when(repository.getWeekTopCategories(ownerA, weekA)).thenReturn(List.of());

        when(repository.getWeekTotalAndCount(ownerA, weekB))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(1500L, 2L));
        when(repository.getWeekTopCategories(ownerA, weekB)).thenReturn(List.of());

        CompareResponse res = reportService.compareWeeks(ownerA, weekA, weekB);
        assertTrue(res.comparable());
        assertEquals(0L, res.differenceGrams());
        assertEquals(0.0, res.percentChange());
    }

    @Test
    @DisplayName("T-CMP-04: Current week selected reports inProgress: true")
    void tCmp04_currentWeekInProgress() {
        // Clock is at 2026-11-01 (Sunday). Current week is 2026-10-26 .. 2026-11-01.
        LocalDate currentWeek = LocalDate.of(2026, 10, 26);
        LocalDate pastWeek = LocalDate.of(2026, 10, 19);

        when(repository.getWeekTotalAndCount(eq(ownerA), any()))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(100L, 1L));
        when(repository.getWeekTopCategories(eq(ownerA), any())).thenReturn(List.of());

        WeekSummaryResponse currentSummary = reportService.getWeekSummary(ownerA, currentWeek);
        assertTrue(currentSummary.inProgress());

        WeekSummaryResponse pastSummary = reportService.getWeekSummary(ownerA, pastWeek);
        assertFalse(pastSummary.inProgress());
    }

    @Test
    @DisplayName("T-CMP-05: HALF_UP rounding with negative values (-33.35 -> -33.4)")
    void tCmp05_halfUpRoundingForNegativePercentages() {
        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);

        // Base A = 10000, Target B = 6665. Diff = -3335. Percent = -3335 / 100 = -33.35% -> -33.4%
        when(repository.getWeekTotalAndCount(ownerA, weekA))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(10000L, 5L));
        when(repository.getWeekTopCategories(ownerA, weekA)).thenReturn(List.of());

        when(repository.getWeekTotalAndCount(ownerA, weekB))
            .thenReturn(new WasteEntryRepository.WeekTotalAndCount(6665L, 3L));
        when(repository.getWeekTopCategories(ownerA, weekB)).thenReturn(List.of());

        CompareResponse res = reportService.compareWeeks(ownerA, weekA, weekB);
        assertTrue(res.comparable());
        assertEquals(-3335L, res.differenceGrams());
        assertEquals(-33.4, res.percentChange());
    }

    @Test
    @DisplayName("T-EXP-02: Export empty account returns entries: []")
    void tExp02_emptyAccountExport() throws Exception {
        when(repository.findAllByOwnerForExport(ownerA)).thenReturn(List.of());

        byte[] bytes = reportService.exportEntries(ownerA);
        String json = new String(bytes);

        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"entries\":[]"));
        assertTrue(json.contains("\"timezone\":\"Asia/Kolkata\""));
    }

    @Test
    @DisplayName("T-EXP-03: Export payload exceeding 8 MiB throws ServiceUnavailableException (503)")
    void tExp03_exportExceeding8MiBThrows503() {
        // Create huge list of entries that serializes to > 8 MiB
        List<WasteEntry> hugeList = new ArrayList<>();
        UUID testSub = UUID.randomUUID();
        LocalDate testDate = LocalDate.of(2026, 10, 20);
        Instant now = Instant.now(fixedClock);

        // Each entry is ~250 bytes in JSON, 40000 entries ~ 10 MiB
        for (int i = 0; i < 40000; i++) {
            hugeList.add(new WasteEntry(
                UUID.randomUUID(), ownerA, testSub, testDate,
                Category.PRODUCE, 500, Stage.PREPARATION, Reason.TRIMMING, now
            ));
        }
        when(repository.findAllByOwnerForExport(ownerA)).thenReturn(hugeList);

        assertThrows(ServiceUnavailableException.class, () -> reportService.exportEntries(ownerA));
    }

    @Test
    @DisplayName("T-LIM-03: 6th export within 60s window throws RateLimitException (429)")
    void tLim03_exportRateLimitEnforced() {
        when(repository.findAllByOwnerForExport(ownerA)).thenReturn(List.of());

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> reportService.exportEntries(ownerA));
        }

        // 6th export must fail
        RateLimitException ex = assertThrows(RateLimitException.class, () -> reportService.exportEntries(ownerA));
        assertTrue(ex.getMessage().contains("5 requests per minute"));
    }

    @Test
    @DisplayName("T-RES-02: Global export concurrency limited to 2 simultaneous operations")
    void tRes02_exportConcurrencySemaphore() throws Exception {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();

        CountDownLatch releaseHoldLatch = new CountDownLatch(1);
        CountDownLatch acquiredLatch = new CountDownLatch(2);

        when(repository.findAllByOwnerForExport(any())).thenAnswer(inv -> {
            acquiredLatch.countDown();
            releaseHoldLatch.await(); // Hold permit
            return List.of();
        });

        ExecutorService executor = Executors.newFixedThreadPool(3);
        executor.submit(() -> reportService.exportEntries(owner1));
        executor.submit(() -> reportService.exportEntries(owner2));

        acquiredLatch.await(); // Both permits are now acquired

        // 3rd concurrent attempt must be rejected immediately by tryAcquire()
        assertThrows(RateLimitException.class, () -> reportService.exportEntries(owner3));

        releaseHoldLatch.countDown(); // Release running exports
        executor.shutdown();
    }
}
