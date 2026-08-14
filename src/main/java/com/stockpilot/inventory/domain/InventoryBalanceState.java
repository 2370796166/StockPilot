package com.stockpilot.inventory.domain;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;

import java.math.BigDecimal;

public record InventoryBalanceState(
        BigDecimal actualQuantity,
        BigDecimal availableQuantity,
        BigDecimal frozenQuantity) {

    private static final int SCALE = 4;
    private static final int MAX_INTEGER_DIGITS = 15;

    public InventoryBalanceState {
        actualQuantity = normalize(actualQuantity);
        availableQuantity = normalize(availableQuantity);
        frozenQuantity = normalize(frozenQuantity);
        if (actualQuantity.signum() < 0 || availableQuantity.signum() < 0 || frozenQuantity.signum() < 0) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存数量不得小于0");
        }
        if (actualQuantity.compareTo(availableQuantity.add(frozenQuantity)) != 0) {
            throw new BusinessException(InventoryErrorCode.INVARIANT_VIOLATION,
                    "实际库存必须等于可用库存与冻结库存之和");
        }
    }

    public static InventoryBalanceState zero() {
        return new InventoryBalanceState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public InventoryBalanceState apply(InventoryQuantityChange change) {
        return new InventoryBalanceState(
                actualQuantity.add(change.actualChange()),
                availableQuantity.add(change.availableChange()),
                frozenQuantity.add(change.frozenChange()));
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY, "库存数量不能为空");
        }
        if (value.scale() > SCALE || value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            throw new BusinessException(InventoryErrorCode.INVALID_QUANTITY,
                    "库存数量最多15位整数和4位小数");
        }
        return value.setScale(SCALE);
    }
}
