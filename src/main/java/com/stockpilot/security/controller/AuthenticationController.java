package com.stockpilot.security.controller;

import com.stockpilot.security.request.SecurityRequests;
import com.stockpilot.security.service.AuthenticationApplicationService;
import com.stockpilot.security.vo.SecurityVO;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.auth.AuthenticatedActor;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationApplicationService authenticationService;

    public AuthenticationController(AuthenticationApplicationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ApiResponse<SecurityVO.Token> login(@Valid @RequestBody SecurityRequests.Login r) {
        return ApiResponse.success(authenticationService.login(r));
    }

    @GetMapping("/me")
    public ApiResponse<SecurityVO.CurrentUser> current(
            @AuthenticationPrincipal AuthenticatedActor principal) {
        return ApiResponse.success(authenticationService.current(principal));
    }
}
