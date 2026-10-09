package com.stockpilot.sales.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.sales.domain.SalesOutboundEntity;
import com.stockpilot.sales.request.SalesOutboundRequests;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SalesOutboundMapper {
    @Select(
            """
        <script>SELECT o.outbound_no AS business_no, o.status, SUM(l.quantity) AS quantity
        FROM sales_outbound_order o JOIN sales_outbound_line l ON l.outbound_id = o.id
        WHERE l.sku_id = #{query.skuId} AND l.warehouse_id = #{query.warehouseId}
        <if test="query.locationId != null">AND l.location_id = #{query.locationId}</if>
        <choose>
          <when test="status == 'UNFINISHED'">AND o.status IN ('DRAFT','RESERVED','APPROVED')</when>
          <otherwise>AND o.status = #{status}</otherwise>
        </choose>
        GROUP BY o.id, o.outbound_no, o.status ORDER BY o.id DESC</script>
        """)
    IPage<com.stockpilot.sales.vo.SalesInventoryOrderVO> selectInventoryOrders(
            Page<com.stockpilot.sales.vo.SalesInventoryOrderVO> page,
            @Param("query") com.stockpilot.inventory.request.InventoryDimensionQuery query,
            @Param("status") String status);

    String FROZEN_SCOPE =
            """
        FROM sales_outbound_order o JOIN sales_outbound_line l ON l.outbound_id = o.id
        WHERE o.status IN ('RESERVED', 'APPROVED')
          AND l.sku_id = #{query.skuId} AND l.warehouse_id = #{query.warehouseId}
        <if test="query.locationId != null">AND l.location_id = #{query.locationId}</if>
        """;

    @Select("<script>SELECT COALESCE(SUM(l.quantity), 0) " + FROZEN_SCOPE + "</script>")
    java.math.BigDecimal selectFrozenTotal(
            @Param("query") com.stockpilot.inventory.request.InventoryDimensionQuery query);

    @Select(
            """
        <script>SELECT 'SALES' AS document_type, o.outbound_no AS business_no,
          l.warehouse_id, l.location_id, l.sku_id, o.status, l.quantity, o.reserved_at
        """
                    + FROZEN_SCOPE
                    + " ORDER BY o.id DESC, l.id DESC</script>")
    IPage<com.stockpilot.inventory.vo.InventoryFrozenSourceVO> selectFrozenSources(
            Page<com.stockpilot.inventory.vo.InventoryFrozenSourceVO> page,
            @Param("query") com.stockpilot.inventory.request.InventoryDimensionQuery query);

    @Select("SELECT id FROM sales_outbound_order WHERE outbound_no = #{number}")
    Long selectIdByNumber(String number);

    String COLUMNS =
            """
            id, outbound_no, warehouse_id, status, remark,
            created_by, created_by_name,
            reserved_by, reserved_by_name, reserved_at,
            approved_by, approved_by_name, approved_at,
            completed_by, completed_by_name, completed_at,
            cancelled_by, cancelled_by_name, cancelled_at,
            created_at, updated_at, version
            """;

    @Insert(
            """
            INSERT INTO sales_outbound_order(
                outbound_no, warehouse_id, status, remark, created_by, created_by_name, version)
            VALUES (
                #{outboundNo}, #{warehouseId}, #{status}, #{remark},
                #{createdBy}, #{createdByName}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SalesOutboundEntity entity);

    @Select("SELECT " + COLUMNS + " FROM sales_outbound_order WHERE id = #{id}")
    SalesOutboundEntity selectById(long id);

    @Select("SELECT " + COLUMNS + " FROM sales_outbound_order WHERE id = #{id} FOR UPDATE")
    SalesOutboundEntity selectByIdForUpdate(long id);

    @Select(
            """
            <script>
            SELECT
            """
                    + COLUMNS
                    + """
            FROM sales_outbound_order
            <where>
                <if test="query.outboundNo != null and query.outboundNo != ''">
                    AND outbound_no LIKE CONCAT('%', #{query.outboundNo}, '%')
                </if>
                <if test="query.warehouseId != null">
                    AND warehouse_id = #{query.warehouseId}
                </if>
                <if test="query.status != null">
                    AND status = #{query.status}
                </if>
            <if test="query.unfinished">AND status NOT IN ('COMPLETED','CANCELLED')</if>
            <if test="query.startDate != null">
                <choose><when test="query.dateField.name() == 'COMPLETED'">
                    AND completed_at &gt;= #{query.startDate} AND completed_at &lt; #{query.endExclusive}
                </when><otherwise>
                    AND created_at &gt;= #{query.startDate} AND created_at &lt; #{query.endExclusive}
                </otherwise></choose>
            </if>
            </where>
            ORDER BY id DESC
            </script>
            """)
    IPage<SalesOutboundEntity> selectPage(
            Page<SalesOutboundEntity> page, @Param("query") SalesOutboundRequests.PageQuery query);

    @Update(
            """
            UPDATE sales_outbound_order
            SET warehouse_id = #{warehouseId},
                remark = #{remark},
                version = version + 1
            WHERE id = #{id}
              AND status = 'DRAFT'
              AND version = #{expectedVersion}
            """)
    int updateDraft(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("warehouseId") long warehouseId,
            @Param("remark") String remark);

    @Update(
            """
            UPDATE sales_outbound_order
            SET status = 'RESERVED',
                reserved_by = #{operatorId},
                reserved_by_name = #{operatorName},
                reserved_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status = 'DRAFT'
              AND version = #{expectedVersion}
            """)
    int reserve(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);

    @Update(
            """
            UPDATE sales_outbound_order
            SET status = 'APPROVED',
                approved_by = #{operatorId},
                approved_by_name = #{operatorName},
                approved_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status = 'RESERVED'
              AND version = #{expectedVersion}
            """)
    int approve(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);

    @Update(
            """
            UPDATE sales_outbound_order
            SET status = 'COMPLETED',
                completed_by = #{operatorId},
                completed_by_name = #{operatorName},
                completed_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status = 'APPROVED'
              AND version = #{expectedVersion}
            """)
    int complete(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);

    @Update(
            """
            UPDATE sales_outbound_order
            SET status = 'CANCELLED',
                cancelled_by = #{operatorId},
                cancelled_by_name = #{operatorName},
                cancelled_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status IN ('RESERVED', 'APPROVED')
              AND version = #{expectedVersion}
            """)
    int cancel(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);
}
