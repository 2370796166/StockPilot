package com.stockpilot.inventory.count.api;

import com.stockpilot.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InventoryCountErrorCode implements ErrorCode {
    NOT_FOUND("COUNT_404", "盘点单不存在", HttpStatus.NOT_FOUND),
    DUPLICATE_NO("COUNT_409_DUPLICATE_NO", "盘点单号已存在", HttpStatus.CONFLICT),
    DUPLICATE_SCOPE("COUNT_409_DUPLICATE_SCOPE", "库存维度已被其他静态盘点占用", HttpStatus.CONFLICT),
    INVALID_LINE("COUNT_400_LINE", "盘点明细不合法", HttpStatus.BAD_REQUEST),
    INVALID_STATE("COUNT_409_STATE", "盘点单状态不允许该操作", HttpStatus.CONFLICT),
    INCOMPLETE_RESULT("COUNT_409_INCOMPLETE", "盘点明细尚未全部录入", HttpStatus.CONFLICT),
    ALREADY_ADJUSTED("COUNT_409_ADJUSTED", "盘点单已经完成库存调整", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("COUNT_409_CONCURRENT", "盘点单已被其他请求修改", HttpStatus.CONFLICT),
    PERSISTENCE_FAILURE("COUNT_500_PERSISTENCE", "盘点数据写入失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    InventoryCountErrorCode(String code, String message, HttpStatus status) {
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
