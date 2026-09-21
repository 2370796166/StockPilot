package com.stockpilot.shared.api;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String code();

    String message();

    HttpStatus httpStatus();
}
