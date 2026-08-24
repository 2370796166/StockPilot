package com.stockpilot.inventorycount.infrastructure.mapper;

import org.apache.ibatis.annotations.*;

public interface InventoryCountScopeMapper {
    @Insert("""
      INSERT INTO inventory_count_scope_lock(count_id,count_line_id,warehouse_id,location_id,sku_id)
      VALUES(#{countId},#{lineId},#{warehouseId},#{locationId},#{skuId})
      """) int insert(@Param("countId")long countId,@Param("lineId")long lineId,
       @Param("warehouseId")long warehouseId,@Param("locationId")long locationId,@Param("skuId")long skuId);
    @Delete("DELETE FROM inventory_count_scope_lock WHERE count_id=#{countId}") int deleteByCountId(long countId);
}
