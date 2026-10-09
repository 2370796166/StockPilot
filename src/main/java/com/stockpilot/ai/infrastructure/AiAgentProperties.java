package com.stockpilot.ai.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stockpilot.ai.agent")
public record AiAgentProperties(
        @DefaultValue("4") int maxRounds,
        @DefaultValue("12") int maxToolCalls,
        @DefaultValue("65536") int maxContextBytes,
        @DefaultValue("100") int maxSessions,
        @DefaultValue("2") int sessionsPerUser,
        @DefaultValue("10") int historyTurns,
        @DefaultValue("30m") Duration sessionTtl,
        @DefaultValue("5m") Duration inputTtl) {
    public AiAgentProperties {
        if (maxRounds < 1
                || maxRounds > 8
                || maxToolCalls < 1
                || maxToolCalls > 24
                || maxContextBytes < 8192
                || maxContextBytes > 262144
                || maxSessions < 1
                || maxSessions > 1000
                || sessionsPerUser < 1
                || sessionsPerUser > 10
                || historyTurns < 1
                || historyTurns > 20
                || sessionTtl == null
                || inputTtl == null
                || sessionTtl.isNegative()
                || sessionTtl.isZero()
                || inputTtl.isNegative()
                || inputTtl.isZero()) throw new IllegalArgumentException("Invalid agent limits");
    }
}
