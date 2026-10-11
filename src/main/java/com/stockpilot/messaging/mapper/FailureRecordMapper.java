package com.stockpilot.messaging.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FailureRecordMapper {
    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM async_failure_record WHERE id=#{id} FOR UPDATE")
    java.util.Map<String, Object> lock(long id);

    @org.apache.ibatis.annotations.Select(
            "SELECT id,message_id,event_name,event_version,business_no,failure_stage,consumer_name,failure_reason,status,occurrence_count,first_failed_at,last_failed_at,resolved_at,resolved_by,resolution_note FROM async_failure_record WHERE id=#{id}")
    com.stockpilot.messaging.vo.MessageManagementVO.Failure find(long id);

    @org.apache.ibatis.annotations.Select(
            """
        <script>SELECT id,message_id,event_name,event_version,business_no,failure_stage,consumer_name,
          failure_reason,status,occurrence_count,first_failed_at,last_failed_at,resolved_at,resolved_by,resolution_note
        FROM async_failure_record <where>
        <if test="status != null">status=#{status}</if>
        <if test="businessNo != null">AND business_no=#{businessNo}</if>
        </where> ORDER BY last_failed_at DESC,id DESC</script>
        """)
    com.baomidou.mybatisplus.core.metadata.IPage<
                    com.stockpilot.messaging.vo.MessageManagementVO.Failure>
            page(
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<
                                    com.stockpilot.messaging.vo.MessageManagementVO.Failure>
                            page,
                    @Param("status") String status,
                    @Param("businessNo") String businessNo);

    @org.apache.ibatis.annotations.Select(
            "SELECT stage,consumer_name,attempt_no,detail,occurred_at FROM async_message_trace WHERE message_id=#{id} ORDER BY id DESC LIMIT 100")
    java.util.List<com.stockpilot.messaging.vo.MessageManagementVO.Trace> traces(String id);

    @org.apache.ibatis.annotations.Select(
            """
        SELECT (SELECT COUNT(*) FROM async_outbox_message WHERE status='PENDING') AS pending_messages,
          (SELECT COUNT(*) FROM async_outbox_message WHERE status='FAILED') AS failed_messages,
          (SELECT COUNT(*) FROM async_failure_record WHERE status IN ('PENDING','RETRY_REQUESTED')) AS pending_failures,
          (SELECT TIMESTAMPDIFF(SECOND,MIN(created_at),CURRENT_TIMESTAMP(3)) FROM async_outbox_message WHERE status='PENDING') AS oldest_pending_age_seconds
        """)
    com.stockpilot.messaging.vo.MessageManagementVO.Backlog backlog();

    @Update(
            """
        UPDATE async_failure_record SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(3)
        WHERE message_id=#{messageId} AND failure_stage='PUBLISH' AND status='RETRY_REQUESTED'
        """)
    int resolvePublisherRetry(String messageId);

    @Insert(
            """
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
    int upsert(
            @Param("messageId") String messageId,
            @Param("eventName") String eventName,
            @Param("eventVersion") int eventVersion,
            @Param("businessNo") String businessNo,
            @Param("failureStage") String failureStage,
            @Param("consumerName") String consumerName,
            @Param("payloadJson") String payloadJson,
            @Param("failureReason") String failureReason);

    @Update(
            """
            UPDATE async_failure_record
            SET status='RETRY_REQUESTED',resolved_by=#{operatorId},resolution_note=#{note}
            WHERE id=#{id} AND status='PENDING'
            """)
    int markRetryRequested(
            @Param("id") long id, @Param("operatorId") long operatorId, @Param("note") String note);

    @Update(
            """
            UPDATE async_failure_record
            SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(3),resolved_by=#{operatorId},resolution_note=#{note}
            WHERE id=#{id} AND status IN ('PENDING','RETRY_REQUESTED')
            """)
    int markResolved(
            @Param("id") long id, @Param("operatorId") long operatorId, @Param("note") String note);
}
