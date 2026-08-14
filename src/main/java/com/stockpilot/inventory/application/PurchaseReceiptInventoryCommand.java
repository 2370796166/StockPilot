package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public record PurchaseReceiptInventoryCommand(
        long warehouseId,
        long locationId,
        long skuId,
        String receiptNo,
        BigDecimal quantity,
        Long operatorId,
        String operatorName) {

    public PurchaseReceiptInventoryCommand {
        if (warehouseId <= 0 || locationId <= 0 || skuId <= 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存维度ID必须大于0");
        }
        if (!StringUtils.hasText(receiptNo) || receiptNo.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "采购入库单号不能为空且不能超过64个字符");
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY,
                    "采购入库数量必须大于0，且最多15位整数和4位小数");
        }
        if (!StringUtils.hasText(operatorName) || operatorName.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "操作人名称不能为空且不能超过64个字符");
        }
        receiptNo = receiptNo.trim();
        quantity = quantity.setScale(4);
        operatorName = operatorName.trim();
    }
}
