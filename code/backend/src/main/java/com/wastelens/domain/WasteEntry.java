package com.wastelens.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WasteEntry(
    UUID id,
    UUID ownerId,
    UUID submissionId,
    LocalDate entryDate,
    Category category,
    int grams,
    Stage stage,
    Reason reason,
    Instant createdAt
) {}
