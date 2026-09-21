package com.stockpilot.security;

import com.stockpilot.security.auth.StockPilotPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
class TestProtectedController {
    @GetMapping("/api/auth/me")
    StockPilotPrincipal current(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    StockPilotPrincipal principal) {
        return principal;
    }

    @PreAuthorize("hasAuthority('SECURITY_USER_READ')")
    @GetMapping("/api/security/users/{id}")
    String user(@PathVariable long id) {
        return "ok";
    }

    @PreAuthorize("hasAuthority('SECURITY_GRANT')")
    @PutMapping("/api/security/users/{id}/roles")
    String grant(@PathVariable long id) {
        return "ok";
    }

    @GetMapping("/future-unannotated-endpoint")
    String futureEndpoint() {
        return "should-never-be-public";
    }
}
