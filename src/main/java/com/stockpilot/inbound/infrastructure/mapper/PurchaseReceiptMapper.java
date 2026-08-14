package com.stockpilot.inbound.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inbound.domain.PurchaseReceiptEntity;
import com.stockpilot.inbound.request.PurchaseReceiptRequests;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PurchaseReceiptMapper {
    String COLUMNS = """
            id, receipt_no, warehouse_id, status, remark,
            created_by, created_by_name,
            submitted_by, submitted_by_name, submitted_at,
            approved_by, approved_by_name, approved_at,
            completed_by, completed_by_name, completed_at,
            created_at, updated_at, version
            """;

    @Insert("""
            INSERT INTO purchase_receipt(
                receipt_no, warehouse_id, status, remark, created_by, created_by_name, version)
            VALUES (
                #{receiptNo}, #{warehouseId}, #{status}, #{remark},
                #{createdBy}, #{createdByName}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseReceiptEntity entity);

    @Select("SELECT " + COLUMNS + " FROM purchase_receipt WHERE id = #{id}")
    PurchaseReceiptEntity selectById(long id);

    @Select("SELECT " + COLUMNS + " FROM purchase_receipt WHERE id = #{id} FOR UPDATE")
    PurchaseReceiptEntity selectByIdForUpdate(long id);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM purchase_receipt
            <where>
                <if test="query.receiptNo != null and query.receiptNo != ''">
                    AND receipt_no LIKE CONCAT('%', #{query.receiptNo}, '%')
                </if>
                <if test="query.warehouseId != null">
                    AND warehouse_id = #{query.warehouseId}
                </if>
            </where>
            ORDER BY id DESC
            </script>
            """)
    IPage<PurchaseReceiptEntity> selectPage(
            Page<PurchaseReceiptEntity> page,
            @Param("query") PurchaseReceiptRequests.PageQuery query);

    @Update("""
            UPDATE purchase_receipt
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

    @Update("""
            UPDATE purchase_receipt
            SET status = 'SUBMITTED',
                submitted_by = #{operatorId},
                submitted_by_name = #{operatorName},
                submitted_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status = 'DRAFT'
              AND version = #{expectedVersion}
            """)
    int submit(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);

    @Update("""
            UPDATE purchase_receipt
            SET status = 'APPROVED',
                approved_by = #{operatorId},
                approved_by_name = #{operatorName},
                approved_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND status = 'SUBMITTED'
              AND version = #{expectedVersion}
            """)
    int approve(
            @Param("id") long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("operatorId") long operatorId,
            @Param("operatorName") String operatorName);

    @Update("""
            UPDATE purchase_receipt
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
}
