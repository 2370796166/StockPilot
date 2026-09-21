package com.stockpilot.masterdata.api;

import com.stockpilot.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MasterDataErrorCode implements ErrorCode {
    NOT_FOUND("MASTER_DATA_404", "基础资料不存在", HttpStatus.NOT_FOUND),
    DUPLICATE_CODE("MASTER_DATA_409", "编码已存在", HttpStatus.CONFLICT),
    WAREHOUSE_NOT_FOUND("WAREHOUSE_404", "仓库不存在", HttpStatus.NOT_FOUND),
    WAREHOUSE_DISABLED("WAREHOUSE_409", "仓库已停用，不能创建或修改库位", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND("CATEGORY_404", "商品分类不存在", HttpStatus.NOT_FOUND),
    CATEGORY_DISABLED("CATEGORY_409", "商品分类已停用，不能关联新的SKU", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION("MASTER_DATA_409_CONCURRENT", "数据已被其他请求修改，请刷新后重试", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    MasterDataErrorCode(String code, String message, HttpStatus status) {
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
