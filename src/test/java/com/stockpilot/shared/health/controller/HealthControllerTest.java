package com.stockpilot.shared.health.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.stockpilot.shared.exception.GlobalExceptionHandler;
import com.stockpilot.shared.health.service.HealthApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class HealthControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HealthApplicationService healthApplicationService = new HealthApplicationService(() -> 1);
        mockMvc =
                standaloneSetup(new HealthController(healthApplicationService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void shouldReturnApplicationAndDatabaseHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }
}
