package com.stockpilot.security.service;

import com.stockpilot.shared.api.CommonErrorCode;
import com.stockpilot.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;

final class PasswordValidation {
    private PasswordValidation() {}

    static void requireSupportedNewPassword(String password) {
        if (password == null || password.isBlank())
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "新密码不能为空");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "新密码的UTF-8编码不能超过72字节");
    }
}
