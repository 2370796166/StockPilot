package com.stockpilot.inventory.service;

import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import org.springframework.util.StringUtils;

public record TransferInventoryCommand(
        long warehouseId,
        long locationId,
        long skuId,
        InventoryBusinessType businessType,
        String transferNo,
        BigDecimal quantity,
        Long operatorId,
        String operatorName) {

    public TransferInventoryCommand {
        if (warehouseId <= 0 || locationId <= 0 || skuId <= 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存维度ID必须大于0");
        }
        if (businessType != InventoryBusinessType.TRANSFER_FREEZE
                && businessType != InventoryBusinessType.TRANSFER_RELEASE
                && businessType != InventoryBusinessType.TRANSFER_OUT
                && businessType != InventoryBusinessType.TRANSFER_IN) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "调拨业务类型不合法");
        }
        if (!StringUtils.hasText(transferNo) || transferNo.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "调拨单号不能为空且不能超过64个字符");
        }
        if (quantity == null
                || quantity.signum() <= 0
                || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15) {
            throw new BusinessException(
                    InventoryErrorCode.INVALID_QUANTITY, "调拨数量必须大于0，且最多15位整数和4位小数");
        }
        if (!StringUtils.hasText(operatorName) || operatorName.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "操作人名称不能为空且不能超过64个字符");
        }
        transferNo = transferNo.trim();
        quantity = quantity.setScale(4);
        operatorName = operatorName.trim();
    }
}
