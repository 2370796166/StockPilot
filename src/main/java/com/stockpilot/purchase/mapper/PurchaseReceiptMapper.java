package com.stockpilot.purchase.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.purchase.domain.PurchaseReceiptEntity;
import com.stockpilot.purchase.request.PurchaseReceiptRequests;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PurchaseReceiptMapper {
    @Update(
            "UPDATE purchase_receipt SET supplier_id=#{supplierId},supplier_name=#{supplierName} WHERE id=#{id} AND status='DRAFT'")
    int updateSupplier(
            @Param("id") long id,
            @Param("supplierId") Long supplierId,
            @Param("supplierName") String supplierName);

    @Insert(
            """
        INSERT INTO purchase_receipt_action(receipt_id,action,from_status,to_status,reason,operator_id,operator_name,document_version,evidence_json)
        SELECT id,#{action},status,#{next},#{reason},#{actorId},#{actorName},version,
          JSON_OBJECT('receiptNo',receipt_no,'warehouseId',warehouse_id,'status',status,
            'supplierId',supplier_id,'supplierName',supplier_name,'remark',remark,
            'submittedBy',submitted_by,'submittedAt',CAST(submitted_at AS CHAR),
            'approvedBy',approved_by,'approvedAt',CAST(approved_at AS CHAR),
            'lines',(SELECT JSON_ARRAYAGG(JSON_OBJECT('lineNo',line_no,'warehouseId',warehouse_id,'locationId',location_id,'skuId',sku_id,'quantity',CAST(quantity AS CHAR))) FROM purchase_receipt_line WHERE receipt_id=#{id}))
        FROM purchase_receipt WHERE id=#{id}
        """)
    int appendAction(
            @Param("id") long id,
            @Param("action") String action,
            @Param("next") String next,
            @Param("reason") String reason,
            @Param("actorId") long actorId,
            @Param("actorName") String actorName);

    @Select(
            "SELECT action,from_status,to_status,reason,operator_id,operator_name,document_version,evidence_json,occurred_at FROM purchase_receipt_action WHERE receipt_id=#{id} ORDER BY id DESC LIMIT 100")
    java.util.List<com.stockpilot.shared.api.BusinessActionVO> actions(long id);

    @Update(
            """
        UPDATE purchase_receipt SET status=#{next},version=version+1,
          submitted_by=IF(#{next}='DRAFT',NULL,submitted_by),submitted_by_name=IF(#{next}='DRAFT',NULL,submitted_by_name),submitted_at=IF(#{next}='DRAFT',NULL,submitted_at)
        WHERE id=#{id} AND status=#{previous} AND version=#{version}
        """)
    int changeState(
            @Param("id") long id,
            @Param("version") int version,
            @Param("previous") String previous,
            @Param("next") String next);

    @Select("SELECT id FROM purchase_receipt WHERE receipt_no = #{number}")
    Long selectIdByNumber(String number);

    String COLUMNS =
            """
            id, receipt_no, warehouse_id, status, remark, supplier_id, supplier_name,
            created_by, created_by_name,
            submitted_by, submitted_by_name, submitted_at,
            approved_by, approved_by_name, approved_at,
            completed_by, completed_by_name, completed_at,
            created_at, updated_at, version
            """;

    @Insert(
            """
            INSERT INTO purchase_receipt(
                receipt_no, warehouse_id, status, remark, created_by, created_by_name, supplier_id,supplier_name,version)
            VALUES (
                #{receiptNo}, #{warehouseId}, #{status}, #{remark},
                #{createdBy}, #{createdByName}, #{supplierId},#{supplierName},0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PurchaseReceiptEntity entity);

    @Select("SELECT " + COLUMNS + " FROM purchase_receipt WHERE id = #{id}")
    PurchaseReceiptEntity selectById(long id);

    @Select("SELECT " + COLUMNS + " FROM purchase_receipt WHERE id = #{id} FOR UPDATE")
    PurchaseReceiptEntity selectByIdForUpdate(long id);

    @Select(
            """
            <script>
            SELECT
            """
                    + COLUMNS
                    + """
            FROM purchase_receipt
            <where>
                <if test="query.receiptNo != null and query.receiptNo != ''">
                    AND receipt_no LIKE CONCAT('%', #{query.receiptNo}, '%')
                </if>
                <if test="query.warehouseId != null">
                    AND warehouse_id = #{query.warehouseId}
                </if>
            <if test="query.status != null">AND status = #{query.status}</if>
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
    IPage<PurchaseReceiptEntity> selectPage(
            Page<PurchaseReceiptEntity> page,
            @Param("query") PurchaseReceiptRequests.PageQuery query);

    @Update(
            """
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

    @Update(
            """
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

    @Update(
            """
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

    @Update(
            """
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
