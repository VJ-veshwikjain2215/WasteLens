package com.wastelens.web.dto;

public record ContextResponse(
    String today,
    String timezone,
    String currentWeekStart
) {}
