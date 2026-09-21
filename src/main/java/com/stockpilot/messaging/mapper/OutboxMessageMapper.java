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
                   publish_attempts,next_attempt_at,last_error,created_at,published_at
            FROM async_outbox_message
            WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3)
            ORDER BY created_at
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    OutboxMessageEntity selectNextDueForUpdate();

    @Update(
            """
            UPDATE async_outbox_message
            SET publish_attempts=publish_attempts+1,last_error=NULL
            WHERE message_id=#{messageId} AND status='PENDING'
            """)
    int markAttempt(@Param("messageId") String messageId);

    @Update(
            """
            UPDATE async_outbox_message
            SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3),last_error=NULL
            WHERE message_id=#{messageId} AND status='PENDING'
            """)
    int markPublished(@Param("messageId") String messageId);

    @Update(
            """
            UPDATE async_outbox_message
            SET status=#{status},next_attempt_at=#{nextAttemptAt},last_error=#{lastError}
            WHERE message_id=#{messageId} AND status='PENDING'
            """)
    int markFailure(
            @Param("messageId") String messageId,
            @Param("status") String status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError);

    @Update(
            """
            UPDATE async_outbox_message
            SET status='PENDING',publish_attempts=0,next_attempt_at=CURRENT_TIMESTAMP(3),last_error=NULL,published_at=NULL
            WHERE message_id=#{messageId}
            """)
    int resetForManualRetry(@Param("messageId") String messageId);
}
