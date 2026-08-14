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
            """)
    int updateStateIfVersionMatches(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("state") InventoryBalanceState state);
}
