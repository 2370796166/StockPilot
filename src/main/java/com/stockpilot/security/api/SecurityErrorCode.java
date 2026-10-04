package com.stockpilot.security.api;

import com.stockpilot.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SecurityErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("AUTH_401", "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    NOT_FOUND("SECURITY_404", "安全资源不存在", HttpStatus.NOT_FOUND),
    DUPLICATE("SECURITY_409", "编码或用户名已存在", HttpStatus.CONFLICT),
    CONCURRENT("SECURITY_409_CONCURRENT", "数据已被其他请求修改，请刷新后重试", HttpStatus.CONFLICT),
    INVALID_RELATION("SECURITY_400_RELATION", "关联的用户、角色或权限不存在或已停用", HttpStatus.BAD_REQUEST),
    JWT_NOT_CONFIGURED("SECURITY_500_JWT", "JWT密钥未配置", HttpStatus.INTERNAL_SERVER_ERROR);
    private final String code;
    private final String message;
    private final HttpStatus status;

    SecurityErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return status;
    }
}
