package com.stockpilot.masterdata.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReferenceCacheProperties.class)
public class ReferenceCacheConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "stockpilot.cache", name = "enabled", havingValue = "true")
    ReferenceDataCache redisReferenceDataCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ReferenceCacheProperties properties) {
        return new RedisReferenceDataCache(redis, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ReferenceDataCache.class)
    ReferenceDataCache noOpReferenceDataCache() {
        return NoOpReferenceDataCache.INSTANCE;
    }
}
