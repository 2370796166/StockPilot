package com.stockpilot.messaging.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageTraceMapper {
    @Insert("""
            INSERT INTO async_message_trace
              (message_id,event_name,business_no,stage,consumer_name,attempt_no,detail)
            VALUES (#{messageId},#{eventName},#{businessNo},#{stage},#{consumerName},#{attemptNo},#{detail})
            """)
    int insert(@Param("messageId") String messageId, @Param("eventName") String eventName,
               @Param("businessNo") String businessNo, @Param("stage") String stage,
               @Param("consumerName") String consumerName, @Param("attemptNo") int attemptNo,
               @Param("detail") String detail);
}
