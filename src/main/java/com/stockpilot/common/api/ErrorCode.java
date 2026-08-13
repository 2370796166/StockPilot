package com.stockpilot.common.api;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String code();

    String message();

    HttpStatus httpStatus();
}
