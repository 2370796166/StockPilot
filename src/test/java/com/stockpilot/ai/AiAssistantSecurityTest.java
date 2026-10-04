package com.stockpilot.ai;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.stockpilot.ai.controller.AiAssistantController;
import com.stockpilot.ai.service.AiAssistantService;
import com.stockpilot.ai.vo.AiAnswerVO;
import com.stockpilot.security.auth.*;
import com.stockpilot.security.config.SecurityConfig;
import com.stockpilot.security.mapper.UserMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiAssistantController.class)
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    DatabaseUserDetailsService.class
})
class AiAssistantSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AiAssistantService assistant;
    @MockBean UserMapper users;

    @Test
    void unauthenticatedAiRequestIsRejected() throws Exception {
        mvc.perform(
                        post("/api/ai/questions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"库存\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(assistant);
    }

    @Test
    @WithMockUser
    void authenticatedUserCanSeeDisabledStateWithoutGainingBusinessPermissions() throws Exception {
        when(assistant.ask(any()))
                .thenReturn(new AiAnswerVO("DISABLED", "尚未启用", List.of(), Instant.now()));
        mvc.perform(
                        post("/api/ai/questions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"库存\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(post("/api/ai/arbitrary")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void invalidRequestsNeverReachModelService() throws Exception {
        for (String body :
                List.of(
                        "{\"question\":\"\"}",
                        "{\"question\":\"" + "a".repeat(1001) + "\"}",
                        "{\"question\":\"库存\",\"selections\":[{\"kind\":\"sql\",\"keyword\":\"A\",\"id\":1}]}",
                        "{\"question\":\"库存\",\"selections\":[{\"kind\":\"sku\",\"keyword\":\"A\",\"id\":0}]}")) {
            mvc.perform(
                            post("/api/ai/questions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(assistant);
    }
}
