package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public record OutboundInventoryCommand(
        long warehouseId,
        long locationId,
        long skuId,
        InventoryBusinessType businessType,
        String outboundNo,
        BigDecimal quantity,
        Long operatorId,
        String operatorName) {

    public OutboundInventoryCommand {
        if (warehouseId <= 0 || locationId <= 0 || skuId <= 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存维度ID必须大于0");
        }
        if (businessType != InventoryBusinessType.OUTBOUND_FREEZE
                && businessType != InventoryBusinessType.OUTBOUND_RELEASE
                && businessType != InventoryBusinessType.OUTBOUND_SHIP) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "销售出库业务类型不合法");
        }
        if (!StringUtils.hasText(outboundNo) || outboundNo.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY,
                    "销售出库单号不能为空且不能超过64个字符");
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY,
                    "销售出库数量必须大于0，且最多15位整数和4位小数");
        }
        if (!StringUtils.hasText(operatorName) || operatorName.trim().length() > 64) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY,
                    "操作人名称不能为空且不能超过64个字符");
        }
        outboundNo = outboundNo.trim();
        quantity = quantity.setScale(4);
        operatorName = operatorName.trim();
    }
}
