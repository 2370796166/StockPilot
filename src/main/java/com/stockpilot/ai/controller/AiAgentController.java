package com.stockpilot.ai.controller;

import com.stockpilot.ai.request.AiAgentRequests;
import com.stockpilot.ai.service.AiAgentService;
import com.stockpilot.ai.vo.AiAgentVO;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("isAuthenticated()")
public class AiAgentController {
    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/sessions")
    public ApiResponse<AiAgentVO.Session> create() {
        return ApiResponse.success(aiAgentService.create());
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<AiAgentVO.Session> session(@PathVariable String id) {
        return ApiResponse.success(aiAgentService.session(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> clear(@PathVariable String id) {
        aiAgentService.clear(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/sessions/{id}/tasks")
    public ApiResponse<AiAgentVO.Task> submit(
            @PathVariable String id, @Valid @RequestBody AiAgentRequests.Question request) {
        return ApiResponse.success(aiAgentService.submit(id, request));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<AiAgentVO.Task> task(@PathVariable String id) {
        return ApiResponse.success(aiAgentService.task(id));
    }

    @PostMapping("/tasks/{id}/input")
    public ApiResponse<AiAgentVO.Task> input(
            @PathVariable String id, @Valid @RequestBody AiAgentRequests.Input input) {
        return ApiResponse.success(aiAgentService.input(id, input));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse<AiAgentVO.Task> cancel(@PathVariable String id) {
        return ApiResponse.success(aiAgentService.cancel(id));
    }

    @PostMapping("/tasks/{id}/retry")
    public ApiResponse<AiAgentVO.Task> retry(
            @PathVariable String id, @Valid @RequestBody AiAgentRequests.Retry request) {
        return ApiResponse.success(aiAgentService.retry(id, request));
    }
}
