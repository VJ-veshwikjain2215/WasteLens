package com.wastelens.web;

import com.wastelens.service.ReportService;
import com.wastelens.web.dto.CompareResponse;
import com.wastelens.web.dto.WeekSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportingEndpointsValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    @DisplayName("T-TIME-04: Non-Monday, pre-2019-12-30, and future week dates return 400 Bad Request")
    void tTime04_invalidWeekParametersRejected() throws Exception {
        String testSub = UUID.randomUUID().toString();
        when(reportService.getCurrentWeekStart()).thenReturn(LocalDate.of(2026, 10, 26));

        // 1. Wednesday date
        mockMvc.perform(get("/api/weeks/2026-10-21").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 2. Pre-2019-12-30 date (2019-12-23 is Monday)
        mockMvc.perform(get("/api/weeks/2019-12-23").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 3. Future date
        mockMvc.perform(get("/api/weeks/2099-01-04").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 4. Malformed date
        mockMvc.perform(get("/api/weeks/not-a-date").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("T-TIME-05: Earliest allowed week start 2019-12-30 returns 200 OK")
    void tTime05_earliestAllowedWeekAccepted() throws Exception {
        String testSub = UUID.randomUUID().toString();
        LocalDate earliest = LocalDate.of(2019, 12, 30);
        when(reportService.getCurrentWeekStart()).thenReturn(LocalDate.of(2026, 10, 26));
        when(reportService.getWeekSummary(any(), eq(earliest)))
            .thenReturn(new WeekSummaryResponse(
                "2019-12-30", "2020-01-05", "Asia/Kolkata", 0L, 0L, false, List.of()
            ));

        mockMvc.perform(get("/api/weeks/2019-12-30").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weekStart").value("2019-12-30"))
            .andExpect(jsonPath("$.weekEnd").value("2020-01-05"));
    }

    @Test
    @DisplayName("T-CMP: Compare endpoint rejects missing, identical, or invalid weeks")
    void tCmp_validationChecks() throws Exception {
        String testSub = UUID.randomUUID().toString();
        when(reportService.getCurrentWeekStart()).thenReturn(LocalDate.of(2026, 10, 26));

        // Missing parameters
        mockMvc.perform(get("/api/compare").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // Identical weeks
        mockMvc.perform(get("/api/compare?weekA=2026-10-19&weekB=2026-10-19").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // Valid distinct weeks
        LocalDate weekA = LocalDate.of(2026, 10, 19);
        LocalDate weekB = LocalDate.of(2026, 10, 26);
        WeekSummaryResponse sumA = new WeekSummaryResponse("2026-10-19", "2026-10-25", "Asia/Kolkata", 2000L, 2L, false, List.of());
        WeekSummaryResponse sumB = new WeekSummaryResponse("2026-10-26", "2026-11-01", "Asia/Kolkata", 1000L, 2L, true, List.of());

        when(reportService.compareWeeks(any(), eq(weekA), eq(weekB)))
            .thenReturn(new CompareResponse(sumA, sumB, -1000L, -50.0, true));

        mockMvc.perform(get("/api/compare?weekA=2026-10-19&weekB=2026-10-26").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comparable").value(true))
            .andExpect(jsonPath("$.differenceGrams").value(-1000))
            .andExpect(jsonPath("$.percentChange").value(-50.0));
    }

    @Test
    @DisplayName("T-EXP: Export endpoint returns application/json with attachment and no-store headers")
    void tExp_headersValidated() throws Exception {
        String testSub = UUID.randomUUID().toString();
        byte[] dummyJson = "{\"schemaVersion\":1,\"entries\":[]}".getBytes();
        when(reportService.exportEntries(any())).thenReturn(dummyJson);

        mockMvc.perform(get("/api/export").with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/json"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"waste-lens-export.json\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(dummyJson));
    }
}
