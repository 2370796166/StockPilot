package com.stockpilot.inventory.count.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.count.domain.InventoryCountEntity;
import com.stockpilot.inventory.count.request.InventoryCountRequests;
import org.apache.ibatis.annotations.*;

public interface InventoryCountMapper {
    String COLUMNS =
            """
        id,count_no,warehouse_id,status,remark,created_by,created_by_name,
        counting_by,counting_by_name,counting_at,submitted_by,submitted_by_name,submitted_at,
        approved_by,approved_by_name,approved_at,adjusted_by,adjusted_by_name,adjusted_at,
        created_at,updated_at,version
        """;

    @Insert(
            """
        INSERT INTO inventory_count_order(count_no,warehouse_id,status,remark,created_by,created_by_name,version)
        VALUES(#{countNo},#{warehouseId},#{status},#{remark},#{createdBy},#{createdByName},0)
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InventoryCountEntity value);

    @Select("SELECT " + COLUMNS + " FROM inventory_count_order WHERE id=#{id}")
    InventoryCountEntity selectById(long id);

    @Select("SELECT " + COLUMNS + " FROM inventory_count_order WHERE id=#{id} FOR UPDATE")
    InventoryCountEntity selectByIdForUpdate(long id);

    @Select(
            """
      <script>SELECT
      """
                    + COLUMNS
                    + """
      FROM inventory_count_order <where>
       <if test="q.countNo != null and q.countNo != ''">AND count_no LIKE CONCAT('%',#{q.countNo},'%')</if>
       <if test="q.warehouseId != null">AND warehouse_id=#{q.warehouseId}</if>
       <if test="q.status != null">AND status=#{q.status}</if>
      </where> ORDER BY id DESC</script>
      """)
    IPage<InventoryCountEntity> selectPage(
            Page<InventoryCountEntity> page, @Param("q") InventoryCountRequests.PageQuery q);

    @Update(
            """
      UPDATE inventory_count_order SET status=#{next},version=version+1,
       counting_by=CASE WHEN #{next}='COUNTING' THEN #{actorId} ELSE counting_by END,
       counting_by_name=CASE WHEN #{next}='COUNTING' THEN #{actorName} ELSE counting_by_name END,
       counting_at=CASE WHEN #{next}='COUNTING' THEN CURRENT_TIMESTAMP(3) ELSE counting_at END,
       submitted_by=CASE WHEN #{next}='SUBMITTED' THEN #{actorId} ELSE submitted_by END,
       submitted_by_name=CASE WHEN #{next}='SUBMITTED' THEN #{actorName} ELSE submitted_by_name END,
       submitted_at=CASE WHEN #{next}='SUBMITTED' THEN CURRENT_TIMESTAMP(3) ELSE submitted_at END,
       approved_by=CASE WHEN #{next}='APPROVED' THEN #{actorId} ELSE approved_by END,
       approved_by_name=CASE WHEN #{next}='APPROVED' THEN #{actorName} ELSE approved_by_name END,
       approved_at=CASE WHEN #{next}='APPROVED' THEN CURRENT_TIMESTAMP(3) ELSE approved_at END,
       adjusted_by=CASE WHEN #{next}='ADJUSTED' THEN #{actorId} ELSE adjusted_by END,
       adjusted_by_name=CASE WHEN #{next}='ADJUSTED' THEN #{actorName} ELSE adjusted_by_name END,
       adjusted_at=CASE WHEN #{next}='ADJUSTED' THEN CURRENT_TIMESTAMP(3) ELSE adjusted_at END
      WHERE id=#{id} AND status=#{current} AND version=#{version}
      """)
    int transition(
            @Param("id") long id,
            @Param("version") int version,
            @Param("current") String current,
            @Param("next") String next,
            @Param("actorId") long actorId,
            @Param("actorName") String actorName);

    @Update(
            "UPDATE inventory_count_order SET version=version+1 WHERE id=#{id} AND status='COUNTING' AND version=#{version}")
    int touchCounting(@Param("id") long id, @Param("version") int version);
}
