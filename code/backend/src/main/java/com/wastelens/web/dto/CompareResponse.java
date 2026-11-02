package com.wastelens.web.dto;

public record CompareResponse(
    WeekSummaryResponse weekA,
    WeekSummaryResponse weekB,
    Long differenceGrams,
    Double percentChange,
    boolean comparable
) {}
