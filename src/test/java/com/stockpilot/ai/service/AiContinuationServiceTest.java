package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AiContinuationServiceTest {
    ObjectMapper json = new ObjectMapper();
    MutableClock clock = new MutableClock();
    AiContinuationService continuations = new AiContinuationService(json, clock);

    @BeforeEach
    void auth() {
        actor("first-user");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void actor(String name) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(name, "unused", List.of()));
    }

    String token() throws Exception {
        return continuations.issue(
                "A在一号仓的库存",
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        json.readTree(
                                "[{\"id\":\"call-a\",\"type\":\"function\",\"function\":{\"name\":\"query_balances\",\"arguments\":\"{\\\"sku\\\":\\\"A\\\",\\\"warehouse\\\":\\\"一号仓\\\"}\"}}]"));
    }

    @Test
    void planPreservesOriginalToolAndParameters() throws Exception {
        String token = token();
        var calls = continuations.verify(token, "A在一号仓的库存");
        assertEquals("query_balances", calls.get(0).path("function").path("name").asText());
        assertEquals(
                "一号仓",
                json.readTree(calls.get(0).path("function").path("arguments").asText())
                        .path("warehouse")
                        .asText());
    }

    @Test
    void tamperedPlanAndOtherQuestionAreRejected() throws Exception {
        String token = token();
        assertThrows(
                IllegalArgumentException.class,
                () -> continuations.verify("A" + token.substring(1), "A在一号仓的库存"));
        assertThrows(IllegalArgumentException.class, () -> continuations.verify(token, "其他商品库存"));
    }

    @Test
    void otherUserCannotResumePlan() throws Exception {
        String token = token();
        actor("second-user");
        assertThrows(IllegalArgumentException.class, () -> continuations.verify(token, "A在一号仓的库存"));
    }

    @Test
    void expirationAndRestartInvalidatePlan() throws Exception {
        String token = token();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiContinuationService(json, clock).verify(token, "A在一号仓的库存"));
        clock.now = clock.now.plusSeconds(300);
        assertThrows(IllegalArgumentException.class, () -> continuations.verify(token, "A在一号仓的库存"));
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }
}
