package com.wastelens.web;

import com.wastelens.domain.Category;
import com.wastelens.domain.Reason;
import com.wastelens.domain.Stage;
import com.wastelens.domain.WasteEntry;
import com.wastelens.service.EntryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EntryValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntryService entryService;

    private String validPayload(int grams, String date) {
        return """
            {
              "submissionId": "%s",
              "entryDate": "%s",
              "category": "produce",
              "grams": %d,
              "stage": "preparation",
              "reason": "trimming"
            }
            """.formatted(UUID.randomUUID(), date, grams);
    }

    @Test
    @DisplayName("T-VAL-01: Boundary grams 1 and 100000 return 201 Created")
    void tVal01_boundaryGramsAccepted() throws Exception {
        WasteEntry dummyEntry = new WasteEntry(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.of(2026, 11, 1), Category.PRODUCE, 1,
            Stage.PREPARATION, Reason.TRIMMING, Instant.now()
        );
        when(entryService.createEntry(any(), any()))
            .thenReturn(new EntryService.EntryResult(dummyEntry, false));

        // grams = 1
        mockMvc.perform(post("/api/entries")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload(1, "2026-11-01")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.entry.grams").value(1));

        // grams = 100000
        mockMvc.perform(post("/api/entries")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload(100000, "2026-11-01")))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("T-VAL-02: Invalid grams tokens are strictly rejected with 400 Bad Request")
    void tVal02_invalidGramsTokensRejected() throws Exception {
        String testSub = UUID.randomUUID().toString();

        String[] invalidGramsBodies = {
            "\"grams\": 0",
            "\"grams\": -1",
            "\"grams\": 100001",
            "\"grams\": 1.5",
            "\"grams\": 1.0",
            "\"grams\": \"1\"",
            "\"grams\": true",
            "\"grams\": null",
            "\"grams\": 1e2"
        };

        for (String invalidGram : invalidGramsBodies) {
            String json = """
                {
                  "submissionId": "%s",
                  "entryDate": "2026-11-01",
                  "category": "produce",
                  %s,
                  "stage": "preparation",
                  "reason": "trimming"
                }
                """.formatted(UUID.randomUUID(), invalidGram);

            mockMvc.perform(post("/api/entries")
                    .with(jwt().jwt(j -> j.subject(testSub)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }

    @Test
    @DisplayName("T-VAL-03: Invalid enums (case sensitivity), unknown keys, and invalid UUIDs return 400")
    void tVal03_enumsUnknownKeysAndUuidValidated() throws Exception {
        String testSub = UUID.randomUUID().toString();

        // 1. Case-sensitive uppercase category
        String upperCategory = """
            {
              "submissionId": "%s",
              "entryDate": "2026-11-01",
              "category": "PRODUCE",
              "grams": 500,
              "stage": "preparation",
              "reason": "trimming"
            }
            """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/entries").with(jwt().jwt(j -> j.subject(testSub))).contentType(MediaType.APPLICATION_JSON).content(upperCategory))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 2. Unknown key in body (e.g. client attempts to send ownerId or arbitrary field)
        String unknownKey = """
            {
              "submissionId": "%s",
              "entryDate": "2026-11-01",
              "category": "produce",
              "grams": 500,
              "stage": "preparation",
              "reason": "trimming",
              "ownerId": "%s"
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/entries").with(jwt().jwt(j -> j.subject(testSub))).contentType(MediaType.APPLICATION_JSON).content(unknownKey))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // 3. Invalid UUID for submissionId
        String invalidUuid = """
            {
              "submissionId": "not-a-valid-uuid",
              "entryDate": "2026-11-01",
              "category": "produce",
              "grams": 500,
              "stage": "preparation",
              "reason": "trimming"
            }
            """;
        mockMvc.perform(post("/api/entries").with(jwt().jwt(j -> j.subject(testSub))).contentType(MediaType.APPLICATION_JSON).content(invalidUuid))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("T-VAL-04: Invalid calendar dates, pre-2020 dates, and future Kolkata dates return 400")
    void tVal04_dateValidationRulesEnforced() throws Exception {
        String testSub = UUID.randomUUID().toString();

        String[] invalidDates = {
            "2026-02-30", // Invalid calendar day
            "2026-2-3",   // Missing leading zeros
            "2019-12-31", // Earlier than 2020-01-01 bound
            "2099-01-01"  // Future date
        };

        for (String dateStr : invalidDates) {
            String json = """
                {
                  "submissionId": "%s",
                  "entryDate": "%s",
                  "category": "produce",
                  "grams": 500,
                  "stage": "preparation",
                  "reason": "trimming"
                }
                """.formatted(UUID.randomUUID(), dateStr);

            mockMvc.perform(post("/api/entries")
                    .with(jwt().jwt(j -> j.subject(testSub)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }
}
