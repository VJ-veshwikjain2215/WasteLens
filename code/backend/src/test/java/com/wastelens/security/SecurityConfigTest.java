package com.wastelens.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedApiAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/context"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Authentication required"))
            .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void publicStaticIndexRouteReturns200() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void publicSpaRoutesForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/entries/new"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/compare"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
    }
}
