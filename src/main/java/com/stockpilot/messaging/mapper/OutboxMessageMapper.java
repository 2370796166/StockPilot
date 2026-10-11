package com.stockpilot.messaging.mapper;

import com.stockpilot.messaging.domain.OutboxMessageEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxMessageMapper {
    @Select("SELECT * FROM async_outbox_message WHERE message_id=#{id} FOR UPDATE")
    OutboxMessageEntity lock(String id);

    @Insert(
            """
            INSERT INTO async_outbox_message
              (message_id,event_name,event_version,business_no,routing_key,payload_json,status,publish_attempts,next_attempt_at)
            VALUES
              (#{messageId},#{eventName},#{eventVersion},#{businessNo},#{routingKey},CAST(#{payloadJson} AS JSON),'PENDING',0,CURRENT_TIMESTAMP(3))
            """)
    int insert(OutboxMessageEntity message);

    @Select(
            """
            SELECT message_id,event_name,event_version,business_no,routing_key,payload_json,status,
                   publish_attempts,next_attempt_at,last_error,created_at,published_at,claim_token
            FROM async_outbox_message
            WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3)
              AND (claim_token IS NULL OR claim_until<=CURRENT_TIMESTAMP(3))
            ORDER BY next_attempt_at,created_at,message_id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    OutboxMessageEntity selectNextDueForUpdate();

    @Update(
            """
            UPDATE async_outbox_message
            SET publish_attempts=publish_attempts+1,last_error=NULL,claim_token=#{claimToken},
                claim_until=TIMESTAMPADD(MICROSECOND,#{leaseMicroseconds},CURRENT_TIMESTAMP(3))
            WHERE message_id=#{messageId} AND status='PENDING'
              AND (claim_token IS NULL OR claim_until<=CURRENT_TIMESTAMP(3))
            """)
    int markAttempt(
            @Param("messageId") String messageId,
            @Param("claimToken") String claimToken,
            @Param("leaseMicroseconds") long leaseMicroseconds);

    @Update(
            """
            UPDATE async_outbox_message
            SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3),last_error=NULL,claim_token=NULL,claim_until=NULL
            WHERE message_id=#{messageId} AND status='PENDING' AND claim_token=#{claimToken}
            """)
    int markPublished(@Param("messageId") String messageId, @Param("claimToken") String claimToken);

    @Update(
            """
            UPDATE async_outbox_message
            SET status=#{status},next_attempt_at=#{nextAttemptAt},last_error=#{lastError},claim_token=NULL,claim_until=NULL
            WHERE message_id=#{messageId} AND status='PENDING' AND claim_token=#{claimToken}
            """)
    int markFailure(
            @Param("messageId") String messageId,
            @Param("status") String status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("claimToken") String claimToken);

    @Update(
            """
            UPDATE async_outbox_message
            SET status='PENDING',publish_attempts=0,next_attempt_at=CURRENT_TIMESTAMP(3),last_error=NULL,published_at=NULL,claim_token=NULL,claim_until=NULL
            WHERE message_id=#{messageId} AND status='FAILED'
            """)
    int resetForManualRetry(@Param("messageId") String messageId);
}
