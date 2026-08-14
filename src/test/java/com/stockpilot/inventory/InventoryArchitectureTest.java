package com.stockpilot.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockpilot.inventory.controller.InventoryQueryController;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryArchitectureTest {
    @Test
    void controllerOnlyExposesGetQueriesAndNeverReturnsEntities() {
        assertTrue(Arrays.stream(InventoryQueryController.class.getDeclaredMethods())
                .allMatch(method -> method.isAnnotationPresent(GetMapping.class)));
        assertFalse(Arrays.stream(InventoryQueryController.class.getDeclaredMethods())
                .map(method -> method.getGenericReturnType().getTypeName())
                .anyMatch(type -> type.contains(InventoryBalanceEntity.class.getName())
                        || type.contains(InventoryLedgerEntity.class.getName())));
    }

    @Test
    void inventoryControllerDoesNotDependOnMapperOrEntity() {
        assertFalse(Arrays.stream(InventoryQueryController.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type.getSimpleName().endsWith("Mapper")
                        || type.getSimpleName().endsWith("Entity")));
    }

    @Test
    void balanceMapperDoesNotExposeGenericCrud() {
        assertFalse(BaseMapper.class.isAssignableFrom(InventoryBalanceMapper.class));
        assertFalse(Arrays.stream(InventoryBalanceMapper.class.getMethods())
                .map(method -> method.getName().toLowerCase())
                .anyMatch(name -> name.startsWith("delete")));
        assertEquals(1, Arrays.stream(InventoryBalanceMapper.class.getMethods())
                .filter(method -> method.getName().startsWith("update"))
                .count());
        assertTrue(Arrays.stream(InventoryBalanceMapper.class.getMethods())
                .filter(method -> method.getName().startsWith("update"))
                .allMatch(method -> method.getName().contains("VersionMatches")));
    }

    @Test
    void ledgerMapperIsAppendOnlyAndDoesNotExposeGenericCrud() {
        assertFalse(BaseMapper.class.isAssignableFrom(InventoryLedgerMapper.class));
        assertFalse(Arrays.stream(InventoryLedgerMapper.class.getMethods())
                .map(method -> method.getName().toLowerCase())
                .anyMatch(name -> name.startsWith("update") || name.startsWith("delete")));
    }
}
