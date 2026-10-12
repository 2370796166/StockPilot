package com.stockpilot.security.service;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordValidationTest {
    @Test
    void supportsTheActualBcryptByteBoundaryIncludingMultibyteCharacters() {
        var encoder = new BCryptPasswordEncoder(4);
        for (var password : java.util.List.of("a".repeat(72), "密".repeat(24))) {
            PasswordValidation.requireSupportedNewPassword(password);
            assertTrue(encoder.matches(password, encoder.encode(password)));
        }
    }

    @Test
    void rejectsOversizedOrEmptyNewPasswordsWithAClientError() {
        for (var password :
                java.util.List.of("a".repeat(73), "密".repeat(25), "😀".repeat(19), "", " ")) {
            var error =
                    assertThrows(
                            BusinessException.class,
                            () -> PasswordValidation.requireSupportedNewPassword(password));
            assertEquals(400, error.getErrorCode().httpStatus().value());
        }
        assertThrows(
                BusinessException.class,
                () -> PasswordValidation.requireSupportedNewPassword(null));
    }
}
