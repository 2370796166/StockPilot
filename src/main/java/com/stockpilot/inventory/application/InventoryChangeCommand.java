package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import org.springframework.util.StringUtils;

public record InventoryChangeCommand(
        long warehouseId,
        long locationId,
        long skuId,
        int expectedVersion,
        InventoryBusinessType businessType,
        String businessNo,
        InventoryQuantityChange quantityChange,
        Long operatorId,
        String operatorName) {

    public InventoryChangeCommand {
        if (warehouseId <= 0 || locationId <= 0 || skuId <= 0 || expectedVersion < 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存维度和版本号不合法");
        }
        if (businessType == null || businessType == InventoryBusinessType.INITIALIZE) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存业务类型不合法");
        }
        if (!StringUtils.hasText(businessNo) || businessNo.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "业务单号不能为空且不能超过64个字符");
        }
        if (quantityChange == null) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存变化量不能为空");
        }
        if (!StringUtils.hasText(operatorName) || operatorName.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "操作人名称不能为空且不能超过64个字符");
        }
        businessNo = businessNo.trim();
        operatorName = operatorName.trim();
    }
}
