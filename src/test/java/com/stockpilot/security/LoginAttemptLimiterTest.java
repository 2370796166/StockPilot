package com.stockpilot.security;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.security.service.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LoginAttemptLimiterTest {
    @Test
    void accountLimitNormalizesCaseAndDoesNotBlockAnotherAccount() {
        var limiter = new LoginAttemptLimiter(2, 20);
        limiter.acquire(" Alice ");
        limiter.acquire("alice");
        var error = assertThrows(ResponseStatusException.class, () -> limiter.acquire("ALICE"));
        assertEquals(429, error.getStatusCode().value());
        limiter.acquire("bob");
    }

    @Test
    void globalBudgetBoundsRandomUsernameAttempts() {
        var limiter = new LoginAttemptLimiter(2, 3);
        limiter.acquire("one");
        limiter.acquire("two");
        limiter.acquire("three");
        assertEquals(
                429,
                assertThrows(ResponseStatusException.class, () -> limiter.acquire("four"))
                        .getStatusCode()
                        .value());
    }
}
