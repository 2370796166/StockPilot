package com.stockpilot.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockpilot.inventory.controller.InventoryQueryController;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.apache.ibatis.annotations.Update;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
        Set<String> mutationMethods = Arrays.stream(InventoryBalanceMapper.class.getMethods())
                .filter(method -> method.isAnnotationPresent(org.apache.ibatis.annotations.Update.class))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("updateStateIfVersionMatches", "freezeIfAvailable",
                "shipIfFrozen", "releaseIfFrozen", "adjustCountIfSnapshotMatches"), mutationMethods);
    }

    @Test
    void ledgerMapperIsAppendOnlyAndDoesNotExposeGenericCrud() {
        assertFalse(BaseMapper.class.isAssignableFrom(InventoryLedgerMapper.class));
        assertFalse(Arrays.stream(InventoryLedgerMapper.class.getMethods())
                .map(method -> method.getName().toLowerCase())
                .anyMatch(name -> name.startsWith("update") || name.startsWith("delete")));
    }

    @Test
    void outboundMutationSqlContainsPositiveAndNonNegativeGuards() throws Exception {
        String freezeSql = sqlOf("freezeIfAvailable");
        assertTrue(freezeSql.contains("#{quantity} > 0"));
        assertTrue(freezeSql.contains("available_quantity >= #{quantity}"));

        String shipSql = sqlOf("shipIfFrozen");
        assertTrue(shipSql.contains("#{quantity} > 0"));
        assertTrue(shipSql.contains("actual_quantity >= #{quantity}"));
        assertTrue(shipSql.contains("frozen_quantity >= #{quantity}"));

        String releaseSql = sqlOf("releaseIfFrozen");
        assertTrue(releaseSql.contains("#{quantity} > 0"));
        assertTrue(releaseSql.contains("frozen_quantity >= #{quantity}"));

        String countSql = sqlOf("adjustCountIfSnapshotMatches");
        assertTrue(countSql.contains("#{countedQuantity} >= frozen_quantity"));
        assertTrue(countSql.contains("inventory_count_scope_lock"));
    }

    private String sqlOf(String methodName) {
        return Arrays.stream(InventoryBalanceMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Update.class)
                .value()[0];
    }
}
