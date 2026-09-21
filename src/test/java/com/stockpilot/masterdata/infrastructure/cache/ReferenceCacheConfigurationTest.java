package com.stockpilot.masterdata.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class ReferenceCacheConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(ReferenceCacheConfiguration.class)
                    .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                    .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void cacheIsDisabledByDefault() {
        context.run(
                value ->
                        assertThat(value.getBean(ReferenceDataCache.class))
                                .isSameAs(NoOpReferenceDataCache.INSTANCE));
    }

    @Test
    void cacheCanBeEnabledWithoutConnectingDuringStartup() {
        context.withPropertyValues("stockpilot.cache.enabled=true")
                .run(
                        value ->
                                assertThat(value.getBean(ReferenceDataCache.class))
                                        .isInstanceOf(RedisReferenceDataCache.class));
    }
}
