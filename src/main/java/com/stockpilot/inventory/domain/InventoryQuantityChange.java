package com.stockpilot.inventory.domain;

import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;

public record InventoryQuantityChange(
        BigDecimal actualChange, BigDecimal availableChange, BigDecimal frozenChange) {

    public InventoryQuantityChange {
        if (actualChange == null || availableChange == null || frozenChange == null) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存变化量不能为空");
        }
        if (actualChange.scale() > 4 || availableChange.scale() > 4 || frozenChange.scale() > 4) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存变化量最多4位小数");
        }
        actualChange = actualChange.setScale(4);
        availableChange = availableChange.setScale(4);
        frozenChange = frozenChange.setScale(4);
        if (actualChange.compareTo(availableChange.add(frozenChange)) != 0) {
            throw new BusinessException(
                    InventoryErrorCode.INVARIANT_VIOLATION, "库存变化量必须保持实际量等于可用量与冻结量之和");
        }
    }

    public static InventoryQuantityChange receipt(BigDecimal quantity) {
        requirePositive(quantity);
        return new InventoryQuantityChange(quantity, quantity, BigDecimal.ZERO);
    }

    public static InventoryQuantityChange freeze(BigDecimal quantity) {
        requirePositive(quantity);
        return new InventoryQuantityChange(BigDecimal.ZERO, quantity.negate(), quantity);
    }

    public static InventoryQuantityChange release(BigDecimal quantity) {
        requirePositive(quantity);
        return new InventoryQuantityChange(BigDecimal.ZERO, quantity, quantity.negate());
    }

    public static InventoryQuantityChange ship(BigDecimal quantity) {
        requirePositive(quantity);
        return new InventoryQuantityChange(quantity.negate(), BigDecimal.ZERO, quantity.negate());
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存操作数量必须大于0");
        }
    }
}
