package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.infrastructure.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real local HTTP protocol tests against a fake provider; not a real model integration. */
class AiModelAdapterTest {
    @Test
    void missingConfigurationDoesNotBreakPropertyBinding() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(AiModelAdapter.class)
                .withPropertyValues("stockpilot.ai.enabled=true")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    "CONFIGURATION_ERROR",
                                    context.getBean(AiModelAdapter.class).configurationStatus());
                        });
    }

    ObjectMapper json = new ObjectMapper();
    HttpServer server;
    ExecutorService executor;
    volatile String body =
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"回答\"}}]}";
    volatile int status = 200;
    volatile long delay = 0;
    volatile String received;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext(
                "/v1/chat/completions",
                exchange -> {
                    received =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    assertEquals(
                            "Bearer test-only-key",
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    try {
                        Thread.sleep(delay);
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(status, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    AiModelAdapter adapter(Duration timeout) {
        return adapter(timeout, "CUSTOM");
    }

    AiModelAdapter adapter(Duration timeout, String provider) {
        return new AiModelAdapter(
                new AiProperties(
                        true,
                        provider,
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "test-only-key",
                        "fake-model",
                        timeout,
                        2),
                json);
    }

    @Test
    void protocolUsesOnlyConfiguredEndpointAndToolMessages() throws Exception {
        var adapter = adapter(Duration.ofSeconds(2));
        assertEquals("READY", adapter.configurationStatus());
        var messages = json.createArrayNode();
        messages.addObject().put("role", "user").put("content", "库存");
        var result = adapter.complete(messages, json.createArrayNode());
        assertEquals("回答", result.path("content").asText());
        assertEquals("fake-model", json.readTree(received).path("model").asText());
        assertFalse(received.contains("test-only-key"));
        assertEquals("none", json.readTree(received).path("tool_choice").asText());
        assertEquals(
                "json_object",
                json.readTree(received).path("response_format").path("type").asText());
        assertFalse(json.readTree(received).has("tools"));
    }

    @Test
    void usageAndErrorCategoriesAreObservableWithoutCredentialsOrBodies() throws Exception {
        var logger =
                (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(AiModelAdapter.class);
        var appender =
                new ch.qos.logback.core.read.ListAppender<
                        ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            body =
                    "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"回答\"}}],\"usage\":{\"prompt_tokens\":777,\"completion_tokens\":21}}";
            var result =
                    adapter(Duration.ofSeconds(2))
                            .complete(json.createArrayNode(), json.createArrayNode());
            assertEquals(777, result.path("_usage").path("promptTokens").asInt());
            assertEquals(21, result.path("_usage").path("completionTokens").asInt());
            body = "private upstream body test-only-key";
            for (int code : new int[] {401, 402, 429, 503}) {
                status = code;
                assertEquals(
                        "MODEL_ERROR",
                        assertThrows(
                                        AiModelAdapter.ModelFailure.class,
                                        () ->
                                                adapter(Duration.ofSeconds(2))
                                                        .complete(
                                                                json.createArrayNode(),
                                                                json.createArrayNode()))
                                .status());
            }
            String output =
                    appender.list.stream()
                            .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .reduce("", (a, b) -> a + b);
            for (String category :
                    new String[] {"AUTHENTICATION", "BALANCE", "RATE_LIMIT", "UPSTREAM"})
                assertTrue(output.contains(category));
            assertFalse(output.contains("test-only-key"));
            assertFalse(output.contains("private upstream body"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void remainingQuestionBudgetBoundsTheProviderCall() {
        delay = 500;
        var failure =
                assertThrows(
                        AiModelAdapter.ModelFailure.class,
                        () ->
                                adapter(Duration.ofSeconds(2))
                                        .complete(
                                                json.createArrayNode(),
                                                json.createArrayNode(),
                                                Duration.ofMillis(100)));
        assertEquals("QUESTION_TIMEOUT", failure.status());
    }

    @Test
    void slowProviderReturnsTimeout() {
        delay = 500;
        var adapter = adapter(Duration.ofMillis(100));
        assertEquals(
                "MODEL_TIMEOUT",
                assertThrows(
                                AiModelAdapter.ModelFailure.class,
                                () ->
                                        adapter.complete(
                                                json.createArrayNode(), json.createArrayNode()))
                        .status());
    }

    @Test
    void malformedTruncatedAndOversizedResponsesAreRejected() {
        var adapter = adapter(Duration.ofSeconds(2));
        for (String invalid :
                new String[] {
                    "invalid",
                    "{\"choices\":[]}",
                    "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"truncated\"}}]}",
                    "x".repeat(70000)
                }) {
            body = invalid;
            assertThrows(
                    AiModelAdapter.ModelFailure.class,
                    () -> adapter.complete(json.createArrayNode(), json.createArrayNode()));
        }
    }

    @Test
    void upstreamErrorsNeverEchoSecrets() {
        status = 401;
        body = "sensitive provider payload";
        var failure =
                assertThrows(
                        AiModelAdapter.ModelFailure.class,
                        () ->
                                adapter(Duration.ofSeconds(2))
                                        .complete(json.createArrayNode(), json.createArrayNode()));
        assertEquals("MODEL_ERROR", failure.status());
        assertFalse(failure.getMessage().contains("sensitive"));
    }

    @Test
    void disabledOrMissingConfigurationDoesNotPreventConstruction() {
        assertEquals(
                "DISABLED",
                new AiModelAdapter(
                                new AiProperties(
                                        false, "CUSTOM", "", "", "", Duration.ofSeconds(20), 6),
                                json)
                        .configurationStatus());
        assertEquals(
                "CONFIGURATION_ERROR",
                new AiModelAdapter(
                                new AiProperties(
                                        true, "CUSTOM", "", "", "", Duration.ofSeconds(20), 6),
                                json)
                        .configurationStatus());
        assertFalse(
                new AiProperties(true, "CUSTOM", "", "secret-value", "", Duration.ofSeconds(20), 6)
                        .toString()
                        .contains("secret-value"));
    }

    @ParameterizedTest
    @EnumSource(AiProvider.class)
    void providersApplyTheirWireParametersAndKeepTools(AiProvider provider) throws Exception {
        var adapter =
                adapter(Duration.ofSeconds(2), provider.name().toLowerCase(java.util.Locale.ROOT));
        var tools = json.createArrayNode();
        var tool = tools.addObject().put("type", "function").putObject("function");
        tool.put("name", "query_balances");
        tool.putObject("parameters").put("type", "object");
        assertEquals("READY", adapter.configurationStatus());
        adapter.complete(json.createArrayNode(), tools);
        var request = json.readTree(received);
        assertEquals(tools, request.path("tools"));
        assertEquals(
                provider == AiProvider.DEEPSEEK ? "required" : "auto",
                request.path("tool_choice").asText());
        assertEquals(1200, request.path("max_tokens").asInt());
        assertEquals("fake-model", request.path("model").asText());
        switch (provider) {
            case CUSTOM -> {
                assertFalse(request.path("parallel_tool_calls").asBoolean(true));
                assertFalse(request.has("thinking"));
                assertFalse(request.has("enable_thinking"));
            }
            case QWEN -> {
                assertFalse(request.path("enable_thinking").asBoolean(true));
                assertFalse(request.has("thinking"));
                assertFalse(request.has("parallel_tool_calls"));
            }
            default -> {
                assertEquals("disabled", request.path("thinking").path("type").asText());
                assertFalse(request.has("enable_thinking"));
                assertFalse(request.has("parallel_tool_calls"));
            }
        }
        if (provider == AiProvider.MOONSHOT)
            assertEquals(0.6, request.path("temperature").asDouble());
        else assertFalse(request.has("temperature"));
    }

    @Test
    void providerEndpointsCanBeOverriddenButUnknownProvidersAreRejected() {
        assertEquals("https://api.deepseek.com", AiProvider.DEEPSEEK.baseUrl(""));
        assertEquals("https://open.bigmodel.cn/api/paas/v4", AiProvider.GLM.baseUrl(""));
        assertEquals("https://ark.cn-beijing.volces.com/api/v3", AiProvider.DOUBAO.baseUrl(""));
        assertEquals("https://api.moonshot.cn/v1", AiProvider.MOONSHOT.baseUrl(""));
        assertEquals("", AiProvider.QWEN.baseUrl(""));
        for (var provider : AiProvider.values())
            assertEquals(
                    "https://gateway.example/v1", provider.baseUrl(" https://gateway.example/v1 "));
        var invalid = adapter(Duration.ofSeconds(2), "unknown");
        assertEquals("CONFIGURATION_ERROR", invalid.configurationStatus());
        assertEquals(
                "CONFIGURATION_ERROR",
                assertThrows(
                                AiModelAdapter.ModelFailure.class,
                                () ->
                                        invalid.complete(
                                                json.createArrayNode(), json.createArrayNode()))
                        .status());
        assertNull(received);
    }

    @Test
    void invalidProviderDoesNotBreakDisabledApplicationPropertyBinding() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(AiModelAdapter.class)
                .withPropertyValues("stockpilot.ai.enabled=false", "stockpilot.ai.provider=unknown")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    "DISABLED",
                                    context.getBean(AiModelAdapter.class).configurationStatus());
                        });
    }

    @Test
    void enabledProvidersNeedModelAndCredentialsAndQwenNeedsRegionalEndpoint() {
        for (var provider : AiProvider.values()) {
            var properties =
                    new AiProperties(true, provider.name(), "", "", "", Duration.ofSeconds(20), 6);
            assertEquals(
                    "CONFIGURATION_ERROR",
                    new AiModelAdapter(properties, json).configurationStatus());
        }
        for (var provider : AiProvider.values()) {
            var properties =
                    new AiProperties(
                            true,
                            provider.name(),
                            "",
                            "test-only-key",
                            "fake-model",
                            Duration.ofSeconds(20),
                            6);
            assertEquals(
                    provider == AiProvider.CUSTOM || provider == AiProvider.QWEN
                            ? "CONFIGURATION_ERROR"
                            : "READY",
                    new AiModelAdapter(properties, json).configurationStatus());
        }
    }

    @Test
    void reasoningResponseIsRejectedWhenPresetRequestedNonThinkingMode() {
        body =
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"回答\",\"reasoning_content\":\"unexpected reasoning\"}}]}";
        assertEquals(
                "INVALID_MODEL_RESPONSE",
                assertThrows(
                                AiModelAdapter.ModelFailure.class,
                                () ->
                                        adapter(Duration.ofSeconds(2), "DEEPSEEK")
                                                .complete(
                                                        json.createArrayNode(),
                                                        json.createArrayNode()))
                        .status());
    }
}
