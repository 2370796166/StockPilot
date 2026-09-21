package com.stockpilot.purchase.mapper;

import com.stockpilot.purchase.domain.PurchaseReceiptLineEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PurchaseReceiptLineMapper {
    @Insert(
            """
            <script>
            INSERT INTO purchase_receipt_line(
                receipt_id, warehouse_id, line_no, location_id, sku_id, quantity)
            VALUES
            <foreach collection="lines" item="line" separator=",">
                (#{line.receiptId}, #{line.warehouseId}, #{line.lineNo},
                 #{line.locationId}, #{line.skuId}, #{line.quantity})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<PurchaseReceiptLineEntity> lines);

    @Delete("DELETE FROM purchase_receipt_line WHERE receipt_id = #{receiptId}")
    int deleteByReceiptId(long receiptId);

    @Select(
            """
            SELECT id, receipt_id, warehouse_id, line_no, location_id, sku_id,
                   quantity, created_at, updated_at
            FROM purchase_receipt_line
            WHERE receipt_id = #{receiptId}
            ORDER BY line_no
            """)
    List<PurchaseReceiptLineEntity> selectByReceiptId(long receiptId);

    @Select("SELECT COUNT(*) FROM purchase_receipt_line WHERE receipt_id = #{receiptId}")
    long countByReceiptId(long receiptId);
}
