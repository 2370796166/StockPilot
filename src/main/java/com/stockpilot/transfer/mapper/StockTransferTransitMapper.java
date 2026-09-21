package com.stockpilot.transfer.mapper;

import com.stockpilot.transfer.domain.StockTransferTransitEntity;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface StockTransferTransitMapper {
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
