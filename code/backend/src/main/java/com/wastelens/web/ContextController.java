package com.wastelens.web;

import com.wastelens.web.dto.ContextResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@RestController
@RequestMapping("/api")
public class ContextController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private final Clock clock;

    public ContextController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/context")
    public ContextResponse getContext() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new ContextResponse(today.toString(), ZONE.getId(), currentWeekStart.toString());
    }
}
