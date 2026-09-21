package com.stockpilot.shared.api;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_PARAMETER("COMMON_400", "请求参数不合法", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("COMMON_404", "请求的资源不存在", HttpStatus.NOT_FOUND),
    BUSINESS_CONFLICT("COMMON_409", "当前业务状态不允许该操作", HttpStatus.CONFLICT),
    INTERNAL_ERROR("COMMON_500", "系统暂时不可用", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String message, HttpStatus httpStatus) {
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
