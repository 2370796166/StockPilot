package com.stockpilot.ai.controller;

import com.stockpilot.ai.request.AiQuestionRequest;
import com.stockpilot.ai.service.AiAssistantService;
import com.stockpilot.ai.vo.AiAnswerVO;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/questions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiAnswerVO> ask(@Valid @RequestBody AiQuestionRequest request) {
        return ApiResponse.success(aiAssistantService.ask(request));
    }
}
