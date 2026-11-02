package com.wastelens.web.dto;

public record CreateEntryResponse(
    EntryResponse entry,
    boolean replayed
) {}
