package com.stockpilot.transfer.api;

import com.stockpilot.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum StockTransferErrorCode implements ErrorCode {
    NOT_FOUND("TRANSFER_404", "调拨单不存在", HttpStatus.NOT_FOUND),
    DUPLICATE_NO("TRANSFER_409_DUPLICATE", "调拨单号已存在", HttpStatus.CONFLICT),
    SAME_WAREHOUSE("TRANSFER_400_SAME_WAREHOUSE", "源仓库和目标仓库不能相同", HttpStatus.BAD_REQUEST),
    INVALID_LINE("TRANSFER_400_LINE", "调拨明细不合法", HttpStatus.BAD_REQUEST),
    INVALID_STATE("TRANSFER_409_STATE", "调拨单状态不允许当前操作", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("TRANSFER_409_CONCURRENT", "调拨单已被其他请求修改", HttpStatus.CONFLICT),
    ALREADY_OUTBOUND("TRANSFER_409_OUTBOUND", "调拨单已经调出", HttpStatus.CONFLICT),
    ALREADY_COMPLETED("TRANSFER_409_COMPLETED", "调拨单已经完成", HttpStatus.CONFLICT),
    ALREADY_CANCELLED("TRANSFER_409_CANCELLED", "调拨单已经取消", HttpStatus.CONFLICT),
    TRANSIT_CONFLICT("TRANSFER_409_TRANSIT", "在途记录状态冲突", HttpStatus.CONFLICT),
    PERSISTENCE_FAILURE("TRANSFER_500_PERSISTENCE", "调拨数据保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
    private final String code;
    private final String message;
    private final HttpStatus status;

    StockTransferErrorCode(String c, String m, HttpStatus s) {
        code = c;
        message = m;
        status = s;
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
