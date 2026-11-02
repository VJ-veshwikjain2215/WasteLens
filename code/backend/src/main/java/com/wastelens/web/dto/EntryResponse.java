package com.wastelens.web.dto;

import com.wastelens.domain.WasteEntry;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

public record EntryResponse(
    UUID id,
    UUID submissionId,
    String entryDate,
    String category,
    int grams,
    String stage,
    String reason,
    String createdAt
) {
    public static EntryResponse fromDomain(WasteEntry entry) {
        return new EntryResponse(
            entry.id(),
            entry.submissionId(),
            entry.entryDate().toString(),
            entry.category().getValue(),
            entry.grams(),
            entry.stage().getValue(),
            entry.reason().getValue(),
            DateTimeFormatter.ISO_INSTANT.format(entry.createdAt())
        );
    }
}
