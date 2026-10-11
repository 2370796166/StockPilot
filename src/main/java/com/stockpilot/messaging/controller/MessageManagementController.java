package com.stockpilot.messaging.controller;

import com.stockpilot.messaging.request.MessageManagementRequests;
import com.stockpilot.messaging.service.MessageManagementService;
import com.stockpilot.messaging.vo.MessageManagementVO;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/messages/failures")
public class MessageManagementController {
    private final MessageManagementService messageManagementService;

    public MessageManagementController(MessageManagementService messageManagementService) {
        this.messageManagementService = messageManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MESSAGE_FAILURE_READ')")
    public ApiResponse<PageResult<MessageManagementVO.Failure>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessNo) {
        return ApiResponse.success(messageManagementService.page(page, size, status, businessNo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MESSAGE_FAILURE_READ')")
    public ApiResponse<MessageManagementVO.Detail> detail(@Positive @PathVariable long id) {
        return ApiResponse.success(messageManagementService.detail(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('MESSAGE_FAILURE_WRITE')")
    public ApiResponse<MessageManagementVO.Detail> retry(
            @Positive @PathVariable long id,
            @Valid @RequestBody MessageManagementRequests.Action request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return ApiResponse.success(messageManagementService.retry(id, request, actor));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('MESSAGE_FAILURE_WRITE')")
    public ApiResponse<MessageManagementVO.Detail> resolve(
            @Positive @PathVariable long id,
            @Valid @RequestBody MessageManagementRequests.Action request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return ApiResponse.success(messageManagementService.resolve(id, request, actor));
    }
}
