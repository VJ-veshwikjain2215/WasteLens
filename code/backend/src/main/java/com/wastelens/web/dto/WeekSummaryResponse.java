package com.wastelens.web.dto;

import java.util.List;

public record WeekSummaryResponse(
    String weekStart,
    String weekEnd,
    String timezone,
    long totalGrams,
    long entryCount,
    boolean inProgress,
    List<CategoryGramsDto> topCategories
) {}
