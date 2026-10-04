package com.stockpilot.inventory.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InventoryLedgerMapper {
    @Select(
            """
        <script>
        SELECT business_type, COUNT(*) AS ledger_count,
               SUM(change_actual_quantity) AS change_actual_quantity,
               SUM(change_available_quantity) AS change_available_quantity,
               SUM(change_frozen_quantity) AS change_frozen_quantity
        FROM inventory_ledger
        WHERE sku_id = #{query.dimension.skuId} AND warehouse_id = #{query.dimension.warehouseId}
          AND occurred_at &gt;= #{query.startInclusive} AND occurred_at &lt; #{query.endExclusive}
        <if test="query.dimension.locationId != null">AND location_id = #{query.dimension.locationId}</if>
        GROUP BY business_type ORDER BY business_type
        </script>
        """)
    java.util.List<com.stockpilot.inventory.vo.InventoryMovementTotalVO> selectPeriodTotals(
            @Param("query") com.stockpilot.inventory.request.InventoryPeriodQuery query);

    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM inventory_ledger WHERE ledger_no = #{ledgerNo}")
    com.stockpilot.inventory.domain.InventoryLedgerEntity selectByLedgerNo(String ledgerNo);

    @Insert(
            """
            INSERT INTO inventory_ledger(
                ledger_no, business_type, business_no,
                warehouse_id, location_id, sku_id,
                before_actual_quantity, change_actual_quantity, after_actual_quantity,
                before_available_quantity, change_available_quantity, after_available_quantity,
                before_frozen_quantity, change_frozen_quantity, after_frozen_quantity,
                balance_version_before, balance_version_after,
                count_book_quantity, counted_quantity, difference_quantity, adjustment_reason,
                operator_id, operator_name)
            VALUES (
                #{ledgerNo}, #{businessType}, #{businessNo},
                #{warehouseId}, #{locationId}, #{skuId},
                #{beforeActualQuantity}, #{changeActualQuantity}, #{afterActualQuantity},
                #{beforeAvailableQuantity}, #{changeAvailableQuantity}, #{afterAvailableQuantity},
                #{beforeFrozenQuantity}, #{changeFrozenQuantity}, #{afterFrozenQuantity},
                #{balanceVersionBefore}, #{balanceVersionAfter},
                #{countBookQuantity}, #{countedQuantity}, #{differenceQuantity}, #{adjustmentReason},
                #{operatorId}, #{operatorName})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InventoryLedgerEntity entity);

    @Select(
            """
            <script>
            SELECT id, ledger_no, business_type, business_no,
                   warehouse_id, location_id, sku_id,
                   before_actual_quantity, change_actual_quantity, after_actual_quantity,
                   before_available_quantity, change_available_quantity, after_available_quantity,
                   before_frozen_quantity, change_frozen_quantity, after_frozen_quantity,
                   balance_version_before, balance_version_after,
                   count_book_quantity, counted_quantity, difference_quantity, adjustment_reason,
                   operator_id, operator_name, occurred_at
            FROM inventory_ledger
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
                <if test="query.businessType != null">
                    AND business_type = #{query.businessType}
                </if>
                <if test="query.businessNo != null and query.businessNo != ''">
                    AND business_no = #{query.businessNo}
                </if>
                <if test="query.startDate != null">
                    AND occurred_at &gt;= #{query.startInclusive} AND occurred_at &lt; #{query.endExclusive}
                </if>
            </where>
            ORDER BY occurred_at DESC, id DESC
            </script>
            """)
    IPage<InventoryLedgerEntity> selectInventoryPage(
            Page<InventoryLedgerEntity> page, @Param("query") InventoryLedgerPageQuery query);
}
