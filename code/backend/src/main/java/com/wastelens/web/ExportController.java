package com.wastelens.web;

import com.wastelens.security.SecurityUtils;
import com.wastelens.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ExportController {

    private final ReportService reportService;

    public ExportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportEntries() {
        UUID ownerId = SecurityUtils.getCurrentOwnerId();
        byte[] exportBytes = reportService.exportEntries(ownerId);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"waste-lens-export.json\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(exportBytes);
    }
}
