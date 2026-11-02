package com.wastelens.service;

import com.wastelens.domain.WasteEntry;
import com.wastelens.repository.WasteEntryRepository;
import com.wastelens.web.dto.CreateEntryRequest;
import com.wastelens.web.exception.CapacityLimitException;
import com.wastelens.web.exception.IdempotencyConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EntryService {

    public static final long MAX_ENTRIES_PER_OWNER = 10000L;

    private final WasteEntryRepository repository;
    private final Clock clock;

    public record EntryResult(WasteEntry entry, boolean replayed) {}

    public EntryService(WasteEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Creates a waste entry under a transaction-scoped advisory lock in READ COMMITTED isolation.
     * Enforces replay comparison, 10,000-entry capacity, and atomic insert.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EntryResult createEntry(UUID ownerId, CreateEntryRequest request) {
        // Step 1: Acquire per-owner transaction-scoped advisory lock (ADR-005)
        repository.acquireOwnerAdvisoryLock(ownerId);

        // Step 2: Check replay lookup before capacity check
        Optional<WasteEntry> existing = repository.findByOwnerAndSubmissionId(ownerId, request.submissionId());
        if (existing.isPresent()) {
            return checkReplay(existing.get(), request);
        }

        // Step 3: Check capacity limit (10,000 entries)
        long count = repository.countByOwnerId(ownerId);
        if (count >= MAX_ENTRIES_PER_OWNER) {
            throw new CapacityLimitException("Account capacity limit reached (maximum 10,000 entries)");
        }

        // Step 4: Atomic insert with ON CONFLICT DO NOTHING
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(clock);
        WasteEntry newEntry = new WasteEntry(
            id,
            ownerId,
            request.submissionId(),
            request.entryDate(),
            request.category(),
            request.grams(),
            request.stage(),
            request.reason(),
            now
        );

        Optional<WasteEntry> inserted = repository.insert(newEntry);
        if (inserted.isPresent()) {
            return new EntryResult(inserted.get(), false);
        }

        // In the rare event of a race conflict, re-read and apply replay comparison
        Optional<WasteEntry> concurrent = repository.findByOwnerAndSubmissionId(ownerId, request.submissionId());
        if (concurrent.isPresent()) {
            return checkReplay(concurrent.get(), request);
        }

        throw new IllegalStateException("Failed to insert waste entry and no concurrent row found");
    }

    private EntryResult checkReplay(WasteEntry existing, CreateEntryRequest request) {
        boolean matches = existing.entryDate().equals(request.entryDate())
                && existing.category() == request.category()
                && existing.grams() == request.grams()
                && existing.stage() == request.stage()
                && existing.reason() == request.reason();

        if (matches) {
            return new EntryResult(existing, true); // Exact replay
        } else {
            throw new IdempotencyConflictException(
                "Idempotency conflict: submissionId '" + request.submissionId() + "' was already used with a different payload"
            );
        }
    }
}
