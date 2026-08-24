package com.stockpilot.transfer.infrastructure.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.transfer.domain.StockTransferEntity;
import com.stockpilot.transfer.request.StockTransferRequests;
import org.apache.ibatis.annotations.*;

public interface StockTransferMapper {
    String COLUMNS = """
        id,transfer_no,source_warehouse_id,target_warehouse_id,status,remark,
        created_by,created_by_name,submitted_by,submitted_by_name,submitted_at,
        approved_by,approved_by_name,approved_at,outbound_by,outbound_by_name,outbound_at,
        transit_by,transit_by_name,transit_at,completed_by,completed_by_name,completed_at,
        cancelled_by,cancelled_by_name,cancelled_at,created_at,updated_at,version
        """;
    @Insert("""
        INSERT INTO stock_transfer_order(transfer_no,source_warehouse_id,target_warehouse_id,status,
          remark,created_by,created_by_name,version)
        VALUES(#{transferNo},#{sourceWarehouseId},#{targetWarehouseId},#{status},#{remark},
          #{createdBy},#{createdByName},0)
        """) @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(StockTransferEntity value);
    @Select("SELECT "+COLUMNS+" FROM stock_transfer_order WHERE id=#{id}") StockTransferEntity selectById(long id);
    @Select("SELECT "+COLUMNS+" FROM stock_transfer_order WHERE id=#{id} FOR UPDATE") StockTransferEntity selectByIdForUpdate(long id);
    @Select("""
      <script>SELECT
      """+COLUMNS+"""
      FROM stock_transfer_order <where>
       <if test="q.transferNo != null and q.transferNo != ''">AND transfer_no LIKE CONCAT('%',#{q.transferNo},'%')</if>
       <if test="q.sourceWarehouseId != null">AND source_warehouse_id=#{q.sourceWarehouseId}</if>
       <if test="q.targetWarehouseId != null">AND target_warehouse_id=#{q.targetWarehouseId}</if>
       <if test="q.status != null">AND status=#{q.status}</if>
      </where> ORDER BY id DESC</script>
      """) IPage<StockTransferEntity> selectPage(Page<StockTransferEntity> page,@Param("q") StockTransferRequests.PageQuery q);
    @Update("""
      UPDATE stock_transfer_order SET source_warehouse_id=#{source},target_warehouse_id=#{target},remark=#{remark},version=version+1
      WHERE id=#{id} AND status='DRAFT' AND version=#{version}
      """) int updateDraft(@Param("id") long id,@Param("version") int version,@Param("source") long source,@Param("target") long target,@Param("remark") String remark);
    @Update("""
      UPDATE stock_transfer_order SET status=#{next},version=version+1,
       submitted_by=CASE WHEN #{next}='SUBMITTED' THEN #{actorId} ELSE submitted_by END,
       submitted_by_name=CASE WHEN #{next}='SUBMITTED' THEN #{actorName} ELSE submitted_by_name END,
       submitted_at=CASE WHEN #{next}='SUBMITTED' THEN CURRENT_TIMESTAMP(3) ELSE submitted_at END,
       approved_by=CASE WHEN #{next}='APPROVED' THEN #{actorId} ELSE approved_by END,
       approved_by_name=CASE WHEN #{next}='APPROVED' THEN #{actorName} ELSE approved_by_name END,
       approved_at=CASE WHEN #{next}='APPROVED' THEN CURRENT_TIMESTAMP(3) ELSE approved_at END,
       outbound_by=CASE WHEN #{next}='OUTBOUND_COMPLETED' THEN #{actorId} ELSE outbound_by END,
       outbound_by_name=CASE WHEN #{next}='OUTBOUND_COMPLETED' THEN #{actorName} ELSE outbound_by_name END,
       outbound_at=CASE WHEN #{next}='OUTBOUND_COMPLETED' THEN CURRENT_TIMESTAMP(3) ELSE outbound_at END,
       transit_by=CASE WHEN #{next}='IN_TRANSIT' THEN #{actorId} ELSE transit_by END,
       transit_by_name=CASE WHEN #{next}='IN_TRANSIT' THEN #{actorName} ELSE transit_by_name END,
       transit_at=CASE WHEN #{next}='IN_TRANSIT' THEN CURRENT_TIMESTAMP(3) ELSE transit_at END,
       completed_by=CASE WHEN #{next}='COMPLETED' THEN #{actorId} ELSE completed_by END,
       completed_by_name=CASE WHEN #{next}='COMPLETED' THEN #{actorName} ELSE completed_by_name END,
       completed_at=CASE WHEN #{next}='COMPLETED' THEN CURRENT_TIMESTAMP(3) ELSE completed_at END,
       cancelled_by=CASE WHEN #{next}='CANCELLED' THEN #{actorId} ELSE cancelled_by END,
       cancelled_by_name=CASE WHEN #{next}='CANCELLED' THEN #{actorName} ELSE cancelled_by_name END,
       cancelled_at=CASE WHEN #{next}='CANCELLED' THEN CURRENT_TIMESTAMP(3) ELSE cancelled_at END
      WHERE id=#{id} AND status=#{current} AND version=#{version}
      """) int transition(@Param("id") long id,@Param("version") int version,@Param("current") String current,
       @Param("next") String next,@Param("actorId") long actorId,@Param("actorName") String actorName);
}
