package com.stockpilot.purchase.api;

import com.stockpilot.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PurchaseReceiptErrorCode implements ErrorCode {
    NOT_FOUND("PURCHASE_RECEIPT_404", "采购入库单不存在", HttpStatus.NOT_FOUND),
    DUPLICATE_RECEIPT_NO("PURCHASE_RECEIPT_409_DUPLICATE", "采购入库单号已存在", HttpStatus.CONFLICT),
    INVALID_STATE("PURCHASE_RECEIPT_409_STATE", "采购入库单当前状态不允许执行该操作", HttpStatus.CONFLICT),
    ALREADY_COMPLETED("PURCHASE_RECEIPT_409_COMPLETED", "采购入库单已经完成，不能重复入库", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(
            "PURCHASE_RECEIPT_409_CONCURRENT", "采购入库单已被其他请求修改", HttpStatus.CONFLICT),
    INVALID_LINE("PURCHASE_RECEIPT_400_LINE", "采购入库明细不合法", HttpStatus.BAD_REQUEST),
    EMPTY_LINES("PURCHASE_RECEIPT_400_EMPTY", "采购入库单至少需要一条明细", HttpStatus.BAD_REQUEST),
    PERSISTENCE_FAILURE("PURCHASE_RECEIPT_500", "采购入库单保存失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    PurchaseReceiptErrorCode(String code, String message, HttpStatus status) {
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
