package com.stockpilot.ai.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.slf4j.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Spring AI owns model protocol. Application owns bounded, authorized tool execution. */
@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiModelAdapter {
    private static final Logger log = LoggerFactory.getLogger(AiModelAdapter.class);
    private final AiProperties properties;
    private final ObjectMapper json;
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public AiModelAdapter(AiProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public String configurationStatus() {
        if (!properties.enabled()) return "DISABLED";
        try {
            URI uri =
                    URI.create(
                            AiProvider.parse(properties.provider()).baseUrl(properties.baseUrl()));
            boolean loopback =
                    Set.of("localhost", "127.0.0.1").contains(Objects.toString(uri.getHost(), ""));
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equals(uri.getScheme())
                            || loopback && "http".equals(uri.getScheme()))
                    || properties.apiKey().isBlank()
                    || properties.model().isBlank()
                    || properties.timeout().toMillis() < 100
                    || properties.timeout().toMillis() > 60000
                    || properties.maxToolCalls() < 1
                    || properties.maxToolCalls() > 8
                    || properties.totalTimeout().toMillis() < 100
                    || properties.totalTimeout().toMillis() > 300000) return "CONFIGURATION_ERROR";
            return "READY";
        } catch (RuntimeException e) {
            return "CONFIGURATION_ERROR";
        }
    }

    public int maxToolCalls() {
        return properties.maxToolCalls();
    }

    public Duration questionTimeout() {
        return properties.totalTimeout();
    }

    public boolean containsSensitiveInput(String text) {
        return properties.apiKey().length() > 8 && text.contains(properties.apiKey())
                || text.matches(
                        "(?is).*(?:sk-[A-Za-z0-9_-]{12,}|[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{16,}|(?:password|密码|api[_ -]?key|密钥|bearer)\\s*[:=：]\\s*\\S+).*");
    }

    public ObjectNode complete(ArrayNode messages, ArrayNode tools) {
        return complete(messages, tools, properties.timeout());
    }

    public ObjectNode complete(ArrayNode messages, ArrayNode tools, Duration remaining) {
        if (Thread.currentThread().isInterrupted()) throw new ModelFailure("REQUEST_CANCELLED");
        String ready = configurationStatus();
        if (!ready.equals("READY")) throw new ModelFailure(ready);
        try {
            if (containsSensitiveInput(json.writeValueAsString(messages)))
                throw new ModelFailure("SENSITIVE_DATA_REDACTED");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ModelFailure("INVALID_MODEL_RESPONSE");
        }
        if (remaining == null || remaining.toMillis() <= 0)
            throw new ModelFailure("QUESTION_TIMEOUT");
        boolean budgetLimited = remaining.compareTo(properties.timeout()) < 0;
        Duration timeout = budgetLimited ? remaining : properties.timeout();
        long started = System.nanoTime();
        String outcome = "INVALID_MODEL_RESPONSE";
        String[] category = {"PROTOCOL"};
        long[] usage = {0, 0};
        try {
            AiProvider provider = AiProvider.parse(properties.provider());
            AiBoundedRequestFactory factory = new AiBoundedRequestFactory(client, timeout);
            var builder =
                    RestClient.builder()
                            .requestFactory(factory)
                            .requestInterceptor(
                                    (request, body, execution) -> {
                                        ClientHttpResponse response =
                                                execution.execute(request, body);
                                        try {
                                            int status = response.getStatusCode().value();
                                            if (!response.getStatusCode().is2xxSuccessful()) {
                                                category[0] =
                                                        switch (status) {
                                                            case 401, 403 -> "AUTHENTICATION";
                                                            case 402 -> "BALANCE";
                                                            case 429 -> "RATE_LIMIT";
                                                            default ->
                                                                    status >= 500
                                                                            ? "UPSTREAM"
                                                                            : "HTTP_CLIENT";
                                                        };
                                                throw new ModelFailure("MODEL_ERROR");
                                            }
                                            byte[] bytes = response.getBody().readNBytes(65537);
                                            if (bytes.length > 65536)
                                                throw new ModelFailure("INVALID_MODEL_RESPONSE");
                                            JsonNode root = json.readTree(bytes);
                                            var choice = root.path("choices").path(0);
                                            var message = choice.path("message");
                                            if (!message.isObject()
                                                    || !message.path("role")
                                                            .asText()
                                                            .equals("assistant")
                                                    || !Set.of("stop", "tool_calls")
                                                            .contains(
                                                                    choice.path("finish_reason")
                                                                            .asText())
                                                    || provider != AiProvider.CUSTOM
                                                            && !message.path("reasoning_content")
                                                                    .asText("")
                                                                    .isBlank())
                                                throw new ModelFailure("INVALID_MODEL_RESPONSE");
                                            usage[0] =
                                                    root.path("usage")
                                                            .path("prompt_tokens")
                                                            .asLong(0);
                                            usage[1] =
                                                    root.path("usage")
                                                            .path("completion_tokens")
                                                            .asLong(0);
                                            HttpStatusCode code = response.getStatusCode();
                                            HttpHeaders headers = new HttpHeaders();
                                            headers.putAll(response.getHeaders());
                                            headers.setContentType(MediaType.APPLICATION_JSON);
                                            String statusText = response.getStatusText();
                                            return new ClientHttpResponse() {
                                                public HttpStatusCode getStatusCode() {
                                                    return code;
                                                }

                                                public String getStatusText() {
                                                    return statusText;
                                                }

                                                public HttpHeaders getHeaders() {
                                                    return headers;
                                                }

                                                public InputStream getBody() {
                                                    return new ByteArrayInputStream(bytes);
                                                }

                                                public void close() {}
                                            };
                                        } finally {
                                            response.close();
                                        }
                                    });
            var api =
                    OpenAiApi.builder()
                            .baseUrl(provider.baseUrl(properties.baseUrl()).replaceAll("/+$", ""))
                            .apiKey(properties.apiKey())
                            .completionsPath("/chat/completions")
                            .restClientBuilder(builder)
                            .build();
            ObjectNode wire = json.createObjectNode();
            provider.configureRequest(wire);
            Map<String, Object> extra =
                    json.convertValue(
                            wire,
                            new com.fasterxml.jackson.core.type.TypeReference<
                                    Map<String, Object>>() {});
            extra.remove("temperature");
            extra.remove("parallel_tool_calls");
            var options =
                    OpenAiChatOptions.builder()
                            .model(properties.model())
                            .maxTokens(1200)
                            .temperature(provider == AiProvider.MOONSHOT ? 0.6 : null)
                            .internalToolExecutionEnabled(false)
                            .extraBody(extra)
                            .toolChoice(
                                    tools.isEmpty()
                                            ? "none"
                                            : provider == AiProvider.DEEPSEEK
                                                    ? "required"
                                                    : "auto");
            if (provider == AiProvider.CUSTOM) options.parallelToolCalls(false);
            if (tools.isEmpty())
                options.responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null));
            else
                options.tools(
                        json.convertValue(
                                tools,
                                new com.fasterxml.jackson.core.type.TypeReference<
                                        List<OpenAiApi.FunctionTool>>() {}));
            var model =
                    OpenAiChatModel.builder()
                            .openAiApi(api)
                            .defaultOptions(options.build())
                            .retryTemplate(
                                    RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build())
                            .build();
            List<Message> typed = new ArrayList<>();
            Map<String, String> names = new HashMap<>();
            for (JsonNode message : messages) {
                String content = message.path("content").asText("");
                switch (message.path("role").asText()) {
                    case "system" -> typed.add(new SystemMessage(content));
                    case "user" -> typed.add(new UserMessage(content));
                    case "assistant" -> {
                        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                        for (JsonNode call : message.path("tool_calls")) {
                            String id = call.path("id").asText(),
                                    name = call.path("function").path("name").asText();
                            names.put(id, name);
                            calls.add(
                                    new AssistantMessage.ToolCall(
                                            id,
                                            "function",
                                            name,
                                            call.path("function").path("arguments").asText()));
                        }
                        typed.add(
                                AssistantMessage.builder()
                                        .content(content)
                                        .toolCalls(calls)
                                        .build());
                    }
                    case "tool" -> {
                        String id = message.path("tool_call_id").asText();
                        typed.add(
                                ToolResponseMessage.builder()
                                        .responses(
                                                List.of(
                                                        new ToolResponseMessage.ToolResponse(
                                                                id,
                                                                names.getOrDefault(id, ""),
                                                                content)))
                                        .build());
                    }
                    default -> throw new ModelFailure("INVALID_MODEL_RESPONSE");
                }
            }
            var response = model.call(new Prompt(typed));
            var output = response.getResult().getOutput();
            ObjectNode safe =
                    json.createObjectNode()
                            .put("role", "assistant")
                            .put("content", output.getText());
            if (output.hasToolCalls()) {
                ArrayNode calls = safe.putArray("tool_calls");
                for (var call : output.getToolCalls())
                    calls.addObject()
                            .put("id", call.id())
                            .put("type", call.type())
                            .putObject("function")
                            .put("name", call.name())
                            .put("arguments", call.arguments());
            }
            safe.putObject("_usage")
                    .put("promptTokens", usage[0])
                    .put("completionTokens", usage[1]);
            outcome = "OK";
            category[0] = "SUCCESS";
            return safe;
        } catch (Exception e) {
            log.debug(
                    "AI protocol failure class={} location={}",
                    e.getClass().getSimpleName(),
                    e.getStackTrace().length == 0 ? "unknown" : e.getStackTrace()[0]);
            if (Thread.currentThread().isInterrupted()) {
                outcome = "REQUEST_CANCELLED";
                category[0] = "CANCELLED";
            } else if (cause(e, ModelFailure.class) != null)
                outcome = ((ModelFailure) cause(e, ModelFailure.class)).status();
            else if (cause(e, java.net.http.HttpTimeoutException.class) != null
                    || cause(e, java.util.concurrent.TimeoutException.class) != null
                    || cause(e, java.net.SocketTimeoutException.class) != null) {
                outcome = budgetLimited ? "QUESTION_TIMEOUT" : "MODEL_TIMEOUT";
                category[0] = "TIMEOUT";
            } else if (cause(e, IOException.class) != null) {
                outcome = "MODEL_ERROR";
                category[0] = "TRANSPORT";
            }
            throw new ModelFailure(outcome);
        } finally {
            log.info(
                    "AI model requestId={} provider={} status={} category={} elapsedMs={} promptTokens={} completionTokens={}",
                    MDC.get("aiRequestId"),
                    AiProvider.parse(properties.provider()).name(),
                    outcome,
                    category[0],
                    (System.nanoTime() - started) / 1000000,
                    usage[0],
                    usage[1]);
        }
    }

    private static Throwable cause(Throwable e, Class<?> type) {
        for (Throwable t = e; t != null; t = t.getCause()) if (type.isInstance(t)) return t;
        return null;
    }

    public static class ModelFailure extends RuntimeException {
        private final String status;

        public ModelFailure(String status) {
            super(status);
            this.status = status;
        }

        public String status() {
            return status;
        }
    }
}
