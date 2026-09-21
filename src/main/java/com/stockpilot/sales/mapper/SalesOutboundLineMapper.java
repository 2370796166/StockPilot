package com.stockpilot.sales.mapper;

import com.stockpilot.sales.domain.SalesOutboundLineEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SalesOutboundLineMapper {
    @Insert(
            """
            <script>
            INSERT INTO sales_outbound_line(
                outbound_id, warehouse_id, line_no, location_id, sku_id, quantity)
            VALUES
            <foreach collection="lines" item="line" separator=",">
                (#{line.outboundId}, #{line.warehouseId}, #{line.lineNo},
                 #{line.locationId}, #{line.skuId}, #{line.quantity})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<SalesOutboundLineEntity> lines);

    @Delete("DELETE FROM sales_outbound_line WHERE outbound_id = #{outboundId}")
    int deleteByOutboundId(long outboundId);

    @Select(
            """
            SELECT id, outbound_id, warehouse_id, line_no, location_id, sku_id,
                   quantity, created_at, updated_at
            FROM sales_outbound_line
            WHERE outbound_id = #{outboundId}
            ORDER BY line_no
            """)
    List<SalesOutboundLineEntity> selectByOutboundId(long outboundId);

    @Select("SELECT COUNT(*) FROM sales_outbound_line WHERE outbound_id = #{outboundId}")
    long countByOutboundId(long outboundId);
}
