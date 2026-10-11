package com.stockpilot.security.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Recoverable process-local abuse budget; never stores passwords or changes account state. */
@Component
public class LoginAttemptLimiter {
    private final int perAccount;
    private final int global;
    private final Map<String, Integer> accounts = new LinkedHashMap<>();
    private long window = Long.MIN_VALUE;
    private int attempts;

    public LoginAttemptLimiter(
            @Value("${stockpilot.security.login-attempts-per-minute:50}") int perAccount,
            @Value("${stockpilot.security.login-global-attempts-per-minute:1000}") int global) {
        if (perAccount < 1 || perAccount > 1000 || global < perAccount || global > 50000)
            throw new IllegalArgumentException("Invalid login attempt budget");
        this.perAccount = perAccount;
        this.global = global;
    }

    public synchronized void acquire(String username) {
        long now = System.nanoTime() / TimeUnit.MINUTES.toNanos(1);
        if (now != window) {
            window = now;
            attempts = 0;
            accounts.clear();
        }
        String account = username.trim().toLowerCase(Locale.ROOT);
        int current = accounts.getOrDefault(account, 0);
        if (attempts >= global
                || current >= perAccount
                || !accounts.containsKey(account) && accounts.size() >= 1024)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后重试");
        attempts++;
        accounts.put(account, current + 1);
    }
}
