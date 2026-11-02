package com.wastelens.service;

import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.domain.WasteEntry;
import com.wastelens.repository.WasteEntryRepository;
import com.wastelens.web.dto.CreateEntryRequest;
import com.wastelens.web.exception.CapacityLimitException;
import com.wastelens.web.exception.IdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EntryServiceTest {

    private WasteEntryRepository repository;
    private Clock clock;
    private EntryService entryService;

    private final UUID ownerA = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();
    private final LocalDate entryDate = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(WasteEntryRepository.class);
        clock = Clock.fixed(Instant.parse("2026-11-01T13:02:00Z"), ZoneId.of("Asia/Kolkata"));
        entryService = new EntryService(repository, clock);
    }

    private CreateEntryRequest createRequest(int grams) {
        return new CreateEntryRequest(
            submissionId,
            entryDate,
            Category.PRODUCE,
            grams,
            Stage.PREPARATION,
            Reason.TRIMMING
        );
    }

    private WasteEntry createWasteEntry(UUID id, UUID owner, UUID sub, int grams) {
        return new WasteEntry(
            id,
            owner,
            sub,
            entryDate,
            Category.PRODUCE,
            grams,
            Stage.PREPARATION,
            Reason.TRIMMING,
            Instant.now(clock)
        );
    }

    @Test
    @DisplayName("T-IDEM-01: First insert returns newly created entry (replayed: false)")
    void tIdem01_newEntryCreatesRow() {
        CreateEntryRequest request = createRequest(500);

        when(repository.findByOwnerAndSubmissionId(ownerA, submissionId))
            .thenReturn(Optional.empty());
        when(repository.countByOwnerId(ownerA))
            .thenReturn(5L);

        WasteEntry savedEntry = createWasteEntry(UUID.randomUUID(), ownerA, submissionId, 500);
        when(repository.insert(any())).thenReturn(Optional.of(savedEntry));

        EntryService.EntryResult result = entryService.createEntry(ownerA, request);

        assertNotNull(result);
        assertFalse(result.replayed());
        assertEquals(500, result.entry().grams());
        assertEquals(submissionId, result.entry().submissionId());

        verify(repository).acquireOwnerAdvisoryLock(ownerA);
        verify(repository).insert(any());
    }

    @Test
    @DisplayName("T-IDEM-01 / T-IDEM-04: Replay with identical payload returns existing entry (replayed: true)")
    void tIdem01_exactReplayReturnsExistingEntry() {
        CreateEntryRequest request = createRequest(500);
        WasteEntry existing = createWasteEntry(UUID.randomUUID(), ownerA, submissionId, 500);

        when(repository.findByOwnerAndSubmissionId(ownerA, submissionId))
            .thenReturn(Optional.of(existing));

        EntryService.EntryResult result = entryService.createEntry(ownerA, request);

        assertNotNull(result);
        assertTrue(result.replayed());
        assertEquals(existing.id(), result.entry().id());
        assertEquals(500, result.entry().grams());

        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("T-IDEM-02: Same submissionId with changed grams throws IdempotencyConflictException (409)")
    void tIdem02_changedPayloadThrowsConflict() {
        CreateEntryRequest request = createRequest(600); // Changed to 600
        WasteEntry existing = createWasteEntry(UUID.randomUUID(), ownerA, submissionId, 500); // Was 500

        when(repository.findByOwnerAndSubmissionId(ownerA, submissionId))
            .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> entryService.createEntry(ownerA, request));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("T-CAP-01: Owner at 10,000 entries throws CapacityLimitException (409 ENTRY_LIMIT_REACHED)")
    void tCap01_capacityLimitEnforced() {
        CreateEntryRequest request = createRequest(500);

        when(repository.findByOwnerAndSubmissionId(ownerA, submissionId))
            .thenReturn(Optional.empty());
        when(repository.countByOwnerId(ownerA))
            .thenReturn(10000L); // Max reached

        assertThrows(CapacityLimitException.class, () -> entryService.createEntry(ownerA, request));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("T-CAP-02: Replay of an existing entry at 10,000 capacity still succeeds (replayed: true)")
    void tCap02_replayAtCapacitySucceeds() {
        CreateEntryRequest request = createRequest(500);
        WasteEntry existing = createWasteEntry(UUID.randomUUID(), ownerA, submissionId, 500);

        // Existing row is found BEFORE count check
        when(repository.findByOwnerAndSubmissionId(ownerA, submissionId))
            .thenReturn(Optional.of(existing));

        EntryService.EntryResult result = entryService.createEntry(ownerA, request);

        assertNotNull(result);
        assertTrue(result.replayed());
        assertEquals(existing.id(), result.entry().id());
        verify(repository, never()).countByOwnerId(any());
    }
}
