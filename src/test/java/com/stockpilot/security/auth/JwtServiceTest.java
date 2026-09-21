package com.stockpilot.security.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.shared.exception.BusinessException;
import java.time.*;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void verifiesIssuedTokenAndRejectsTampering() {
        JwtService s =
                new JwtService(
                        new SecurityProperties("01234567890123456789012345678901", 60, "", ""));
        String token = s.issue(7, "alice");
        assertEquals(7, s.verify(token).userId());
        assertThrows(BusinessException.class, () -> s.verify(token + "x"));
    }

    @Test
    void rejectsUnsupportedAlgorithmHeader() {
        JwtService s =
                new JwtService(
                        new SecurityProperties("01234567890123456789012345678901", 60, "", ""));
        String token = s.issue(7, "alice");
        String unsupported =
                java.util.Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        "{\"alg\":\"none\",\"typ\":\"JWT\"}"
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + token.substring(token.indexOf('.'));
        assertThrows(BusinessException.class, () -> s.verify(unsupported));
    }

    @Test
    void rejectsExpiredToken() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
        JwtService issuer =
                new JwtService(
                        new SecurityProperties("01234567890123456789012345678901", 1, "", ""),
                        fixed);
        String token = issuer.issue(7, "alice");
        JwtService verifier =
                new JwtService(
                        new SecurityProperties("01234567890123456789012345678901", 1, "", ""),
                        Clock.offset(fixed, Duration.ofMinutes(2)));
        assertThrows(BusinessException.class, () -> verifier.verify(token));
    }
}
