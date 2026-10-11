package com.stockpilot.transfer.mapper;

import com.stockpilot.transfer.domain.StockTransferTransitEntity;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface StockTransferTransitMapper {
    @Select(
            """
        SELECT o.transfer_no,o.status AS order_status,l.source_warehouse_id,l.source_location_id,
          l.target_warehouse_id,l.target_location_id,l.sku_id,l.quantity,t.outbound_quantity,
          t.in_transit_quantity,t.received_quantity,t.status AS transit_status
        FROM stock_transfer_order o JOIN stock_transfer_line l ON l.transfer_id=o.id
          LEFT JOIN stock_transfer_transit t ON t.transfer_line_id=l.id
        WHERE o.status IN ('OUTBOUND_COMPLETED','IN_TRANSIT','COMPLETED') AND l.sku_id=#{skuId}
          AND ((l.source_warehouse_id=#{warehouseId} AND l.source_location_id=#{locationId})
            OR (l.target_warehouse_id=#{warehouseId} AND l.target_location_id=#{locationId}))
        ORDER BY o.id,l.id LIMIT 201
        """)
    List<com.stockpilot.transfer.vo.StockTransferAuditVO> audit(
            @Param("warehouseId") long warehouseId,
            @Param("locationId") long locationId,
            @Param("skuId") long skuId);

    @Insert(
            """
 INSERT INTO stock_transfer_transit(transfer_id,transfer_line_id,outbound_quantity,in_transit_quantity,
 received_quantity,status,outbound_at,version) VALUES(#{transferId},#{transferLineId},#{outboundQuantity},
 #{inTransitQuantity},#{receivedQuantity},'IN_TRANSIT',CURRENT_TIMESTAMP(3),0)
 """)
    int insert(StockTransferTransitEntity value);

    @Select(
            """
 SELECT id,transfer_id,transfer_line_id,outbound_quantity,in_transit_quantity,received_quantity,status,version
 FROM stock_transfer_transit WHERE transfer_id=#{id} ORDER BY transfer_line_id
 """)
    List<StockTransferTransitEntity> selectByTransferId(long id);

    @Update(
            """
 UPDATE stock_transfer_transit SET in_transit_quantity=0,received_quantity=outbound_quantity,
 status='RECEIVED',received_at=CURRENT_TIMESTAMP(3),version=version+1
 WHERE transfer_line_id=#{lineId} AND status='IN_TRANSIT' AND in_transit_quantity=#{quantity}
 """)
    int receive(@Param("lineId") long lineId, @Param("quantity") java.math.BigDecimal quantity);
}
