package com.wastelens.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedUserCanAccessContext() throws Exception {
        mockMvc.perform(get("/api/context").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
            .andExpect(jsonPath("$.today").isNotEmpty())
            .andExpect(jsonPath("$.currentWeekStart").isNotEmpty());
    }
}
