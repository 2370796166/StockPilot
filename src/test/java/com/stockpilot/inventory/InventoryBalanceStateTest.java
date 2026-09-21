package com.stockpilot.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryBalanceStateTest {
    @Test
    void zeroStateMaintainsInvariant() {
        InventoryBalanceState state = InventoryBalanceState.zero();
        assertEquals(new BigDecimal("0.0000"), state.actualQuantity());
        assertEquals(state.actualQuantity(), state.availableQuantity().add(state.frozenQuantity()));
    }

    @Test
    void negativeQuantityIsRejected() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                new InventoryBalanceState(
                                        new BigDecimal("1.0000"),
                                        new BigDecimal("-1.0000"),
                                        new BigDecimal("2.0000")));
        assertEquals("INVENTORY_400_QUANTITY", exception.getErrorCode().code());
    }

    @Test
    void brokenInvariantIsRejected() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                new InventoryBalanceState(
                                        new BigDecimal("10.0000"),
                                        new BigDecimal("8.0000"),
                                        new BigDecimal("1.0000")));
        assertEquals("INVENTORY_409_INVARIANT", exception.getErrorCode().code());
    }

    @Test
    void applyingChangeThatWouldCreateNegativeQuantityIsRejected() {
        InventoryBalanceState state =
                new InventoryBalanceState(
                        new BigDecimal("2.0000"),
                        new BigDecimal("1.0000"),
                        new BigDecimal("1.0000"));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                state.apply(
                                        InventoryQuantityChange.freeze(new BigDecimal("1.0001"))));

        assertEquals("INVENTORY_400_QUANTITY", exception.getErrorCode().code());
    }

    @Test
    void quantityPrecisionAndScaleMustFitDecimal19_4() {
        BusinessException tooManyDecimals =
                assertThrows(
                        BusinessException.class,
                        () ->
                                new InventoryBalanceState(
                                        new BigDecimal("1.00001"),
                                        new BigDecimal("1.00001"),
                                        BigDecimal.ZERO));
        BusinessException tooManyIntegerDigits =
                assertThrows(
                        BusinessException.class,
                        () ->
                                new InventoryBalanceState(
                                        new BigDecimal("1000000000000000"),
                                        new BigDecimal("1000000000000000"),
                                        BigDecimal.ZERO));

        assertEquals("INVENTORY_400_QUANTITY", tooManyDecimals.getErrorCode().code());
        assertEquals("INVENTORY_400_QUANTITY", tooManyIntegerDigits.getErrorCode().code());
    }

    @Test
    void quantityChangeMustPreserveInvariant() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                new InventoryQuantityChange(
                                        new BigDecimal("1.0000"),
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO));

        assertEquals("INVENTORY_409_INVARIANT", exception.getErrorCode().code());
    }

    @Test
    void futureSemanticChangesPreserveInvariant() {
        InventoryBalanceState received =
                InventoryBalanceState.zero()
                        .apply(InventoryQuantityChange.receipt(new BigDecimal("10.0000")));
        InventoryBalanceState frozen =
                received.apply(InventoryQuantityChange.freeze(new BigDecimal("3.0000")));
        InventoryBalanceState released =
                frozen.apply(InventoryQuantityChange.release(new BigDecimal("1.0000")));
        InventoryBalanceState shipped =
                released.apply(InventoryQuantityChange.ship(new BigDecimal("2.0000")));

        assertEquals(new BigDecimal("8.0000"), shipped.actualQuantity());
        assertEquals(
                new BigDecimal("8.0000"),
                shipped.availableQuantity().add(shipped.frozenQuantity()));
    }
}
