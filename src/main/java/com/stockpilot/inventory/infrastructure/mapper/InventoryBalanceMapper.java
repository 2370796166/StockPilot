package com.stockpilot.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InventoryBalanceMapper {
    @Insert("""
            INSERT INTO inventory_balance(
                warehouse_id, location_id, sku_id,
                actual_quantity, available_quantity, frozen_quantity, version)
            VALUES (
                #{warehouseId}, #{locationId}, #{skuId},
                #{actualQuantity}, #{availableQuantity}, #{frozenQuantity}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InventoryBalanceEntity entity);

    @Insert("""
            INSERT IGNORE INTO inventory_balance(
                warehouse_id, location_id, sku_id,
                actual_quantity, available_quantity, frozen_quantity, version)
            VALUES (#{warehouseId}, #{locationId}, #{skuId}, 0.0000, 0.0000, 0.0000, 0)
            """)
    int insertZeroIfAbsent(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);

    @Select("""
            SELECT id, warehouse_id, location_id, sku_id,
                   actual_quantity, available_quantity, frozen_quantity,
                   version, created_at, updated_at
            FROM inventory_balance
            WHERE id = #{id}
            """)
    InventoryBalanceEntity selectById(long id);

    @Select("""
            SELECT id, warehouse_id, location_id, sku_id,
                   actual_quantity, available_quantity, frozen_quantity,
                   version, created_at, updated_at
            FROM inventory_balance
            WHERE warehouse_id = #{warehouseId}
              AND location_id = #{locationId}
              AND sku_id = #{skuId}
            """)
    InventoryBalanceEntity selectByDimension(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);

    @Select("""
            SELECT id, warehouse_id, location_id, sku_id,
                   actual_quantity, available_quantity, frozen_quantity,
                   version, created_at, updated_at
            FROM inventory_balance
            WHERE warehouse_id = #{warehouseId}
              AND location_id = #{locationId}
              AND sku_id = #{skuId}
            FOR UPDATE
            """)
    InventoryBalanceEntity selectByDimensionForUpdate(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);

    @Select("""
            <script>
            SELECT id, warehouse_id, location_id, sku_id,
                   actual_quantity, available_quantity, frozen_quantity,
                   version, created_at, updated_at
            FROM inventory_balance
            <where>
                <if test="query.warehouseId != null">
                    AND warehouse_id = #{query.warehouseId}
                </if>
                <if test="query.locationId != null">
                    AND location_id = #{query.locationId}
                </if>
                <if test="query.skuId != null">
                    AND sku_id = #{query.skuId}
                </if>
            </where>
            ORDER BY id DESC
            </script>
            """)
    IPage<InventoryBalanceEntity> selectInventoryPage(
            Page<InventoryBalanceEntity> page,
            @Param("query") InventoryBalancePageQuery query);

    @Update("""
            UPDATE inventory_balance
            SET actual_quantity = #{state.actualQuantity},
                available_quantity = #{state.availableQuantity},
                frozen_quantity = #{state.frozenQuantity},
                version = version + 1
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND #{state.actualQuantity} >= 0
              AND #{state.availableQuantity} >= 0
              AND #{state.frozenQuantity} >= 0
              AND #{state.actualQuantity} = #{state.availableQuantity} + #{state.frozenQuantity}
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_count_scope_lock count_lock
                  WHERE count_lock.warehouse_id = inventory_balance.warehouse_id
                    AND count_lock.location_id = inventory_balance.location_id
                    AND count_lock.sku_id = inventory_balance.sku_id)
            """)
    int updateStateIfVersionMatches(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("state") InventoryBalanceState state);

    @Update("""
            UPDATE inventory_balance
            SET available_quantity = available_quantity - #{quantity},
                frozen_quantity = frozen_quantity + #{quantity},
                version = version + 1
            WHERE warehouse_id = #{warehouseId}
              AND location_id = #{locationId}
              AND sku_id = #{skuId}
              AND #{quantity} > 0
              AND available_quantity >= #{quantity}
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_count_scope_lock count_lock
                  WHERE count_lock.warehouse_id = inventory_balance.warehouse_id
                    AND count_lock.location_id = inventory_balance.location_id
                    AND count_lock.sku_id = inventory_balance.sku_id)
            """)
    int freezeIfAvailable(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId,
            @Param("quantity") java.math.BigDecimal quantity);

    @Update("""
            UPDATE inventory_balance
            SET actual_quantity = actual_quantity - #{quantity},
                frozen_quantity = frozen_quantity - #{quantity},
                version = version + 1
            WHERE warehouse_id = #{warehouseId}
              AND location_id = #{locationId}
              AND sku_id = #{skuId}
              AND #{quantity} > 0
              AND actual_quantity >= #{quantity}
              AND frozen_quantity >= #{quantity}
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_count_scope_lock count_lock
                  WHERE count_lock.warehouse_id = inventory_balance.warehouse_id
                    AND count_lock.location_id = inventory_balance.location_id
                    AND count_lock.sku_id = inventory_balance.sku_id)
            """)
    int shipIfFrozen(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId,
            @Param("quantity") java.math.BigDecimal quantity);

    @Update("""
            UPDATE inventory_balance
            SET available_quantity = available_quantity + #{quantity},
                frozen_quantity = frozen_quantity - #{quantity},
                version = version + 1
            WHERE warehouse_id = #{warehouseId}
              AND location_id = #{locationId}
              AND sku_id = #{skuId}
              AND #{quantity} > 0
              AND frozen_quantity >= #{quantity}
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_count_scope_lock count_lock
                  WHERE count_lock.warehouse_id = inventory_balance.warehouse_id
                    AND count_lock.location_id = inventory_balance.location_id
                    AND count_lock.sku_id = inventory_balance.sku_id)
            """)
    int releaseIfFrozen(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId,
            @Param("quantity") java.math.BigDecimal quantity);

    @Select("""
            SELECT COUNT(*) FROM inventory_count_scope_lock
            WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} AND sku_id=#{skuId}
            """)
    int countActiveCountLocks(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);

    @Update("""
            UPDATE inventory_balance
            SET actual_quantity = #{countedQuantity},
                available_quantity = #{countedQuantity} - frozen_quantity,
                version = version + 1
            WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} AND sku_id=#{skuId}
              AND version=#{snapshotVersion}
              AND actual_quantity=#{snapshotActual}
              AND available_quantity=#{snapshotAvailable}
              AND frozen_quantity=#{snapshotFrozen}
              AND #{countedQuantity} >= frozen_quantity
              AND EXISTS (
                  SELECT 1 FROM inventory_count_scope_lock count_lock
                  WHERE count_lock.count_id=#{countId} AND count_lock.count_line_id=#{countLineId}
                    AND count_lock.warehouse_id=inventory_balance.warehouse_id
                    AND count_lock.location_id=inventory_balance.location_id
                    AND count_lock.sku_id=inventory_balance.sku_id)
            """)
    int adjustCountIfSnapshotMatches(
            @Param("countId") long countId,
            @Param("countLineId") long countLineId,
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId,
            @Param("snapshotVersion") int snapshotVersion,
            @Param("snapshotActual") java.math.BigDecimal snapshotActual,
            @Param("snapshotAvailable") java.math.BigDecimal snapshotAvailable,
            @Param("snapshotFrozen") java.math.BigDecimal snapshotFrozen,
            @Param("countedQuantity") java.math.BigDecimal countedQuantity);
}
