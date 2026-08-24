package com.stockpilot.messaging.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FailureRecordMapper {
    @Insert("""
            INSERT INTO async_failure_record
              (message_id,event_name,event_version,business_no,failure_stage,consumer_name,
               payload_json,failure_reason,status,occurrence_count)
            VALUES
              (#{messageId},#{eventName},#{eventVersion},#{businessNo},#{failureStage},#{consumerName},
               CAST(#{payloadJson} AS JSON),#{failureReason},'PENDING',1)
            ON DUPLICATE KEY UPDATE
              failure_reason=#{failureReason},payload_json=CAST(#{payloadJson} AS JSON),
              status='PENDING',occurrence_count=occurrence_count+1,last_failed_at=CURRENT_TIMESTAMP(3),
              resolved_at=NULL,resolved_by=NULL,resolution_note=NULL
            """)
    int upsert(@Param("messageId") String messageId, @Param("eventName") String eventName,
               @Param("eventVersion") int eventVersion, @Param("businessNo") String businessNo,
               @Param("failureStage") String failureStage, @Param("consumerName") String consumerName,
               @Param("payloadJson") String payloadJson, @Param("failureReason") String failureReason);

    @Update("""
            UPDATE async_failure_record
            SET status='RETRY_REQUESTED',resolved_by=#{operatorId},resolution_note=#{note}
            WHERE id=#{id} AND status='PENDING'
            """)
    int markRetryRequested(@Param("id") long id, @Param("operatorId") long operatorId,
                           @Param("note") String note);

    @Update("""
            UPDATE async_failure_record
            SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(3),resolved_by=#{operatorId},resolution_note=#{note}
            WHERE id=#{id} AND status IN ('PENDING','RETRY_REQUESTED')
            """)
    int markResolved(@Param("id") long id, @Param("operatorId") long operatorId,
                     @Param("note") String note);
}
