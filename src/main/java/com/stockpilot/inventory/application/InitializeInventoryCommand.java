package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import org.springframework.util.StringUtils;

public record InitializeInventoryCommand(
        long warehouseId,
        long locationId,
        long skuId,
        Long operatorId,
        String operatorName) {

    public InitializeInventoryCommand {
        if (warehouseId <= 0 || locationId <= 0 || skuId <= 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存维度ID必须大于0");
        }
        if (!StringUtils.hasText(operatorName) || operatorName.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "操作人名称不能为空且不能超过64个字符");
        }
        operatorName = operatorName.trim();
    }
}
