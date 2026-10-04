package com.stockpilot.shared.api;

import org.springframework.http.HttpStatus;

public enum AccessErrorCode implements ErrorCode {
    UNAUTHENTICATED("SECURITY_401", "未登录或访问令牌无效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("SECURITY_403", "无权访问该资源", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    AccessErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
