package com.wastelens.web.dto;

import java.util.List;

public record ExportResponse(
    int schemaVersion,
    String exportedAt,
    String timezone,
    List<EntryResponse> entries
) {}
