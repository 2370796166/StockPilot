package com.stockpilot.alert.infrastructure.mapper;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SafetyStockRuleMapper {
    @Select("""
            SELECT id,warehouse_id,location_id,sku_id,threshold_quantity,status,version
            FROM safety_stock_rule
            WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} AND sku_id=#{skuId}
              AND status='ENABLED'
            """)
    SafetyStockRuleEntity selectEnabledByDimension(
            @Param("warehouseId") long warehouseId, @Param("locationId") long locationId,
            @Param("skuId") long skuId);
}
