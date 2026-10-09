package com.stockpilot.shared.exception;

import com.stockpilot.shared.api.AccessErrorCode;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.CommonErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(
                        new ApiResponse<>(
                                "HTTP_" + exception.getStatusCode().value(),
                                exception.getReason() == null ? "请求未能完成" : exception.getReason(),
                                null,
                                java.time.Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(this::formatFieldError)
                        .distinct()
                        .collect(Collectors.joining("; "));
        return build(CommonErrorCode.INVALID_PARAMETER, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {
        String message =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        violation.getPropertyPath() + ": " + violation.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
        return build(CommonErrorCode.INVALID_PARAMETER, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception) {
        return build(CommonErrorCode.INVALID_PARAMETER, "请求体格式或字段值不合法");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return build(CommonErrorCode.INVALID_PARAMETER, exception.getName() + ": 参数值不合法");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return build(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return build(AccessErrorCode.FORBIDDEN, AccessErrorCode.FORBIDDEN.message());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return build(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.message());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiResponse<Void>> build(
            com.stockpilot.shared.api.ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode, message));
    }
}
