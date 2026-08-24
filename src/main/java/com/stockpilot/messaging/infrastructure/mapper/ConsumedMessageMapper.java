package com.stockpilot.messaging.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConsumedMessageMapper {
    @Insert("""
            INSERT IGNORE INTO async_consumed_message
              (message_id,consumer_name,event_name,business_no)
            VALUES (#{messageId},#{consumerName},#{eventName},#{businessNo})
            """)
    int claim(@Param("messageId") String messageId, @Param("consumerName") String consumerName,
              @Param("eventName") String eventName, @Param("businessNo") String businessNo);
}
