package com.wastelens.web;

import com.wastelens.security.SecurityUtils;
import com.wastelens.service.ReportService;
import com.wastelens.web.dto.CompareResponse;
import com.wastelens.web.exception.InvalidInputException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CompareController {

    private final ReportService reportService;

    public CompareController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/compare")
    public ResponseEntity<CompareResponse> compareWeeks(
            @RequestParam(required = false) String weekA,
            @RequestParam(required = false) String weekB) {
        UUID ownerId = SecurityUtils.getCurrentOwnerId();

        if (weekA == null || weekA.isBlank() || weekB == null || weekB.isBlank()) {
            throw new InvalidInputException("Both weekA and weekB parameters are required");
        }

        LocalDate dateA = parseAndValidateMonday(weekA, "weekA");
        LocalDate dateB = parseAndValidateMonday(weekB, "weekB");

        if (dateA.equals(dateB)) {
            throw new InvalidInputException("weekA and weekB must be different weeks");
        }

        CompareResponse response = reportService.compareWeeks(ownerId, dateA, dateB);
        return ResponseEntity.ok(response);
    }

    private LocalDate parseAndValidateMonday(String paramValue, String paramName) {
        LocalDate date;
        try {
            date = LocalDate.parse(paramValue);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Invalid " + paramName + " date format: " + paramValue);
        }

        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InvalidInputException(paramName + " must be a Monday: " + paramValue);
        }

        if (date.isBefore(ReportService.EARLIEST_WEEK_START)) {
            throw new InvalidInputException(paramName + " cannot be earlier than " + ReportService.EARLIEST_WEEK_START);
        }

        LocalDate currentWeekStart = reportService.getCurrentWeekStart();
        if (date.isAfter(currentWeekStart)) {
            throw new InvalidInputException(paramName + " cannot be in the future (current week start: " + currentWeekStart + ")");
        }

        return date;
    }
}
