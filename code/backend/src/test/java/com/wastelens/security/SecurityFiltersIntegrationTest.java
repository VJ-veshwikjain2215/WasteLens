package com.wastelens.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityFiltersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("T-AUTH-01: Missing / non-Bearer authorization returns 401 with error JSON")
    void tAuth01_missingOrNonBearerAuth() throws Exception {
        // Missing Authorization
        mockMvc.perform(get("/api/context"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.requestId").isNotEmpty());

        // Non-Bearer Authorization
        mockMvc.perform(get("/api/context").header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-ISO-02: Spoofed ownerId in query parameter or header returns 400 Bad Request")
    void tIso02_spoofedOwnerParametersRejected() throws Exception {
        UUID ownerId = UUID.randomUUID();

        // In query parameter
        mockMvc.perform(get("/api/context?ownerId=" + ownerId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.error.requestId").isNotEmpty());

        mockMvc.perform(get("/api/context?owner_id=" + ownerId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        // In header
        mockMvc.perform(get("/api/context").header("X-Owner-Id", ownerId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/context").header("ownerId", ownerId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("T-VAL-05 / T-LIM-01: Body exceeding 4096 bytes returns 413 Payload Too Large")
    void tLim01_bodySizeLimitsEnforced() throws Exception {
        // 4097 bytes -> 413
        String oversized = "{\"data\":\"" + "a".repeat(4090) + "\"}";
        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversized))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));

        // Content-Length header declared > 4096
        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Length", "5000")
                .content("{}"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    @DisplayName("T-VAL-05: Non-JSON Content-Type on POST with body returns 415 Unsupported Media Type")
    void tVal05_nonJsonContentTypeRejected() throws Exception {
        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.TEXT_PLAIN)
                .content("some plain text body"))
            .andExpect(status().is(415))
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));

        mockMvc.perform(post("/api/entries")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("field=value"))
            .andExpect(status().is(415))
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("T-LIM-02: 61st request in a minute for an owner returns 429 Too Many Requests")
    void tLim02_ownerRateLimitEnforced() throws Exception {
        String testSub = UUID.randomUUID().toString();

        // Send 60 requests -> all should pass rate limiter
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/context")
                    .with(jwt().jwt(j -> j.subject(testSub))))
                .andExpect(status().isOk());
        }

        // 61st request -> 429
        mockMvc.perform(get("/api/context")
                .with(jwt().jwt(j -> j.subject(testSub))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        // Another owner is NOT rate limited
        String otherOwner = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/context")
                .with(jwt().jwt(j -> j.subject(otherOwner))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("T-SEC-04: Security headers and Cache-Control: no-store present on API responses")
    void tSec04_securityHeadersVerified() throws Exception {
        mockMvc.perform(get("/api/context").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().exists("Content-Security-Policy"));
    }
}
