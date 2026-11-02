package com.wastelens.web;

import com.wastelens.security.SecurityUtils;
import com.wastelens.service.ReportService;
import com.wastelens.web.dto.WeekSummaryResponse;
import com.wastelens.web.exception.InvalidInputException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WeekController {

    private final ReportService reportService;

    public WeekController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/weeks/{monday}")
    public ResponseEntity<WeekSummaryResponse> getWeekSummary(@PathVariable String monday) {
        UUID ownerId = SecurityUtils.getCurrentOwnerId();

        LocalDate mondayDate;
        try {
            mondayDate = LocalDate.parse(monday);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Invalid monday date format: " + monday);
        }

        if (mondayDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidInputException("Week date must be a Monday: " + monday);
        }

        if (mondayDate.isBefore(ReportService.EARLIEST_WEEK_START)) {
            throw new InvalidInputException("Week date cannot be earlier than " + ReportService.EARLIEST_WEEK_START);
        }

        LocalDate currentWeekStart = reportService.getCurrentWeekStart();
        if (mondayDate.isAfter(currentWeekStart)) {
            throw new InvalidInputException("Week date cannot be in the future (current week start: " + currentWeekStart + ")");
        }

        WeekSummaryResponse response = reportService.getWeekSummary(ownerId, mondayDate);
        return ResponseEntity.ok(response);
    }
}
