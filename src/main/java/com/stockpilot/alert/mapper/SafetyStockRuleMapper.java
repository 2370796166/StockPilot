package com.stockpilot.alert.mapper;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SafetyStockRuleMapper {
    @org.apache.ibatis.annotations.Insert(
            """
        INSERT INTO safety_stock_rule(warehouse_id,location_id,sku_id,threshold_quantity,status)
        VALUES(#{warehouseId},#{locationId},#{skuId},#{thresholdQuantity},'ENABLED')
        """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SafetyStockRuleEntity rule);

    @Select("SELECT * FROM safety_stock_rule WHERE id=#{id} FOR UPDATE")
    SafetyStockRuleEntity lock(long id);

    @org.apache.ibatis.annotations.Update(
            """
        UPDATE safety_stock_rule SET threshold_quantity=#{threshold},status=#{status},version=version+1
        WHERE id=#{id} AND version=#{version}
        """)
    int update(
            @Param("id") long id,
            @Param("version") int version,
            @Param("threshold") java.math.BigDecimal threshold,
            @Param("status") String status);

    @Select(
            """
        <script>SELECT * FROM safety_stock_rule
        <where><if test="warehouseId != null">warehouse_id=#{warehouseId}</if>
        <if test="status != null">AND status=#{status}</if></where> ORDER BY id DESC</script>
        """)
    com.baomidou.mybatisplus.core.metadata.IPage<SafetyStockRuleEntity> page(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SafetyStockRuleEntity> page,
            @Param("warehouseId") Long warehouseId,
            @Param("status") String status);

    @Select(
            """
            SELECT id,warehouse_id,location_id,sku_id,threshold_quantity,status,version
            FROM safety_stock_rule
            WHERE warehouse_id=#{warehouseId} AND location_id=#{locationId} AND sku_id=#{skuId}
              AND status='ENABLED' FOR UPDATE
            """)
    SafetyStockRuleEntity selectEnabledByDimension(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);
}
