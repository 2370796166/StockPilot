package com.stockpilot.inventory.api;

import com.stockpilot.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InventoryErrorCode implements ErrorCode {
    BALANCE_NOT_FOUND("INVENTORY_404", "库存余额不存在", HttpStatus.NOT_FOUND),
    BALANCE_ALREADY_EXISTS("INVENTORY_409_DUPLICATE", "该仓库、库位和SKU的库存余额已存在", HttpStatus.CONFLICT),
    DUPLICATE_BUSINESS_ACTION("INVENTORY_409_IDEMPOTENT", "该库存业务动作已经执行", HttpStatus.CONFLICT),
    INVALID_QUANTITY("INVENTORY_400_QUANTITY", "库存数量不合法", HttpStatus.BAD_REQUEST),
    INVARIANT_VIOLATION("INVENTORY_409_INVARIANT", "库存不变量校验失败", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("INVENTORY_409_CONCURRENT", "库存已被其他请求修改，请重试", HttpStatus.CONFLICT),
    LEDGER_WRITE_FAILED("INVENTORY_500_LEDGER", "库存流水写入失败", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    InventoryErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() { return code; }
    public String message() { return message; }
    public HttpStatus httpStatus() { return status; }
}
