package com.stockpilot.inventory.count.mapper;

import com.stockpilot.inventory.count.domain.InventoryCountLineEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface InventoryCountLineMapper {
    String COLUMNS =
            """
      id,count_id,warehouse_id,line_no,location_id,sku_id,snapshot_actual_quantity,
      snapshot_available_quantity,snapshot_frozen_quantity,snapshot_balance_version,
      counted_quantity,difference_quantity,reason,created_at,updated_at
      """;

    @Insert(
            """
      INSERT INTO inventory_count_line(count_id,warehouse_id,line_no,location_id,sku_id,
       snapshot_actual_quantity,snapshot_available_quantity,snapshot_frozen_quantity,snapshot_balance_version)
      VALUES(#{countId},#{warehouseId},#{lineNo},#{locationId},#{skuId},#{snapshotActualQuantity},
       #{snapshotAvailableQuantity},#{snapshotFrozenQuantity},#{snapshotBalanceVersion})
      """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InventoryCountLineEntity value);

    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM inventory_count_line WHERE count_id=#{countId} ORDER BY line_no")
    List<InventoryCountLineEntity> selectByCountId(long countId);

    @Update(
            """
      UPDATE inventory_count_line SET counted_quantity=#{counted},difference_quantity=#{difference},reason=#{reason}
      WHERE id=#{id} AND count_id=#{countId}
      """)
    int record(
            @Param("id") long id,
            @Param("countId") long countId,
            @Param("counted") BigDecimal counted,
            @Param("difference") BigDecimal difference,
            @Param("reason") String reason);

    @Select(
            "SELECT COUNT(*) FROM inventory_count_line WHERE count_id=#{countId} AND counted_quantity IS NULL")
    int countIncomplete(long countId);
}
