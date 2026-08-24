package com.stockpilot.outbound.api;

import com.stockpilot.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SalesOutboundErrorCode implements ErrorCode {
    NOT_FOUND("SALES_OUTBOUND_404", "销售出库单不存在", HttpStatus.NOT_FOUND),
    DUPLICATE_OUTBOUND_NO("SALES_OUTBOUND_409_DUPLICATE", "销售出库单号已存在", HttpStatus.CONFLICT),
    INVALID_STATE("SALES_OUTBOUND_409_STATE", "销售出库单当前状态不允许执行该操作", HttpStatus.CONFLICT),
    ALREADY_RESERVED("SALES_OUTBOUND_409_RESERVED", "销售出库单已经冻结库存", HttpStatus.CONFLICT),
    ALREADY_COMPLETED("SALES_OUTBOUND_409_COMPLETED", "销售出库单已经完成", HttpStatus.CONFLICT),
    ALREADY_CANCELLED("SALES_OUTBOUND_409_CANCELLED", "销售出库单已经取消并释放库存", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("SALES_OUTBOUND_409_CONCURRENT", "销售出库单已被其他请求修改", HttpStatus.CONFLICT),
    INVALID_LINE("SALES_OUTBOUND_400_LINE", "销售出库明细不合法", HttpStatus.BAD_REQUEST),
    EMPTY_LINES("SALES_OUTBOUND_400_EMPTY", "销售出库单至少需要一条明细", HttpStatus.BAD_REQUEST),
    PERSISTENCE_FAILURE("SALES_OUTBOUND_500", "销售出库单保存失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    SalesOutboundErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() { return code; }
    public String message() { return message; }
    public HttpStatus httpStatus() { return status; }
}
