package com.stockpilot.transfer.mapper;

import com.stockpilot.transfer.domain.StockTransferLineEntity;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface StockTransferLineMapper {
    @Insert(
            """
 <script>INSERT INTO stock_transfer_line(transfer_id,source_warehouse_id,target_warehouse_id,line_no,
 source_location_id,target_location_id,sku_id,quantity) VALUES
 <foreach collection="values" item="v" separator=",">(#{v.transferId},#{v.sourceWarehouseId},#{v.targetWarehouseId},
 #{v.lineNo},#{v.sourceLocationId},#{v.targetLocationId},#{v.skuId},#{v.quantity})</foreach></script>
 """)
    int insertBatch(@Param("values") List<StockTransferLineEntity> values);

    @Delete("DELETE FROM stock_transfer_line WHERE transfer_id=#{id}")
    int deleteByTransferId(long id);

    @Select(
            """
 SELECT id,transfer_id,source_warehouse_id,target_warehouse_id,line_no,source_location_id,
 target_location_id,sku_id,quantity FROM stock_transfer_line WHERE transfer_id=#{id} ORDER BY line_no
 """)
    List<StockTransferLineEntity> selectByTransferId(long id);
}
