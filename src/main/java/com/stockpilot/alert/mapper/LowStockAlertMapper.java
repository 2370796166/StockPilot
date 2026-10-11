package com.stockpilot.alert.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LowStockAlertMapper {
    @org.apache.ibatis.annotations.Select(
            """
        <script>SELECT id,rule_id,warehouse_id,location_id,sku_id,threshold_quantity,available_quantity,status,
          source_message_id,source_event_name,business_no,first_triggered_at,last_evaluated_at,resolved_at FROM low_stock_alert
        <where><if test="warehouseId != null">warehouse_id=#{warehouseId}</if>
        <if test="status != null">AND status=#{status}</if></where> ORDER BY last_evaluated_at DESC,id DESC</script>
        """)
    com.baomidou.mybatisplus.core.metadata.IPage<com.stockpilot.alert.vo.SafetyStockVO.Alert> page(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<
                            com.stockpilot.alert.vo.SafetyStockVO.Alert>
                    page,
            @Param("warehouseId") Long warehouseId,
            @Param("status") String status);

    @Insert(
            """
            INSERT INTO low_stock_alert
              (rule_id,warehouse_id,location_id,sku_id,threshold_quantity,available_quantity,status,
               source_message_id,source_event_name,business_no,first_triggered_at,last_evaluated_at)
            VALUES
              (#{ruleId},#{warehouseId},#{locationId},#{skuId},#{threshold},#{available},'OPEN',
               #{messageId},#{eventName},#{businessNo},CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
              first_triggered_at=IF(status='RESOLVED',CURRENT_TIMESTAMP(3),first_triggered_at),
              threshold_quantity=#{threshold},available_quantity=#{available},status='OPEN',
              source_message_id=#{messageId},source_event_name=#{eventName},business_no=#{businessNo},
              last_evaluated_at=CURRENT_TIMESTAMP(3),resolved_at=NULL,version=version+1
            """)
    int open(
            @Param("ruleId") long ruleId,
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId,
            @Param("threshold") BigDecimal threshold,
            @Param("available") BigDecimal available,
            @Param("messageId") String messageId,
            @Param("eventName") String eventName,
            @Param("businessNo") String businessNo);

    @Update(
            """
            UPDATE low_stock_alert
            SET threshold_quantity=#{threshold},available_quantity=#{available},
                source_message_id=#{messageId},source_event_name=#{eventName},business_no=#{businessNo},
                resolved_at=IF(status='OPEN',CURRENT_TIMESTAMP(3),resolved_at),status='RESOLVED',
                last_evaluated_at=CURRENT_TIMESTAMP(3),version=version+1
            WHERE rule_id=#{ruleId}
            """)
    int resolve(
            @Param("ruleId") long ruleId,
            @Param("threshold") BigDecimal threshold,
            @Param("available") BigDecimal available,
            @Param("messageId") String messageId,
            @Param("eventName") String eventName,
            @Param("businessNo") String businessNo);
}
