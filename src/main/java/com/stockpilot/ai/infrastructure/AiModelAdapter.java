package com.stockpilot.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Only this adapter knows the provider protocol; destinations come exclusively from configuration.
 */
@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiModelAdapter {
    private final AiProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    public AiModelAdapter(AiProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
    }

    public String configurationStatus() {
        if (!properties.enabled()) return "DISABLED";
        try {
            URI uri =
                    URI.create(
                            AiProvider.parse(properties.provider()).baseUrl(properties.baseUrl()));
            boolean loopback =
                    "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equals(uri.getScheme())
                            || (loopback && "http".equals(uri.getScheme())))
                    || properties.apiKey().isBlank()
                    || properties.model().isBlank()
                    || properties.timeout().toMillis() < 100
                    || properties.timeout().toMillis() > 60000
                    || properties.maxToolCalls() < 1
                    || properties.maxToolCalls() > 8) return "CONFIGURATION_ERROR";
            return "READY";
        } catch (RuntimeException e) {
            return "CONFIGURATION_ERROR";
        }
    }

    public int maxToolCalls() {
        return properties.maxToolCalls();
    }

    public ObjectNode complete(ArrayNode messages, ArrayNode tools) {
        String configurationStatus = configurationStatus();
        if (!"READY".equals(configurationStatus)) throw new ModelFailure(configurationStatus);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            AiProvider provider = AiProvider.parse(properties.provider());
            ObjectNode body =
                    json.createObjectNode()
                            .put("model", properties.model())
                            .put("max_tokens", 1200);
            provider.configureRequest(body);
            body.set("messages", messages);
            body.set("tools", tools);
            body.put("tool_choice", "auto");
            String endpoint =
                    provider.baseUrl(properties.baseUrl()).replaceAll("/+$", "")
                            + "/chat/completions";
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(endpoint))
                            .timeout(properties.timeout())
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + properties.apiKey())
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            pending = client.sendAsync(request, ignored -> new LimitedBodySubscriber());
            HttpResponse<byte[]> response =
                    pending.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new ModelFailure("MODEL_ERROR");
            JsonNode root = json.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            if (!message.isObject()
                    || !"assistant".equals(message.path("role").asText())
                    || !("stop".equals(choice.path("finish_reason").asText())
                            || "tool_calls".equals(choice.path("finish_reason").asText())))
                throw new ModelFailure("INVALID_MODEL_RESPONSE");
            if (provider != AiProvider.CUSTOM
                    && !message.path("reasoning_content").asText("").isBlank())
                throw new ModelFailure("INVALID_MODEL_RESPONSE");
            ObjectNode safe = json.createObjectNode().put("role", "assistant");
            if (message.has("tool_calls")) safe.set("tool_calls", message.get("tool_calls"));
            safe.set("content", message.path("content"));
            return safe;
        } catch (TimeoutException e) {
            throw new ModelFailure("MODEL_TIMEOUT");
        } catch (ExecutionException e) {
            throw new ModelFailure(
                    e.getCause() instanceof HttpTimeoutException ? "MODEL_TIMEOUT" : "MODEL_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelFailure("MODEL_ERROR");
        } catch (ModelFailure e) {
            throw e;
        } catch (Exception e) {
            throw new ModelFailure("INVALID_MODEL_RESPONSE");
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
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

    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;

        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

        public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 65536) {
                    subscription.cancel();
                    body.completeExceptionally(new ModelFailure("INVALID_MODEL_RESPONSE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
