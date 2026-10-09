package com.stockpilot.ai.infrastructure;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.*;

/**
 * Spring AI transport with a deadline covering headers AND body, bounded bytes and cancellation.
 */
final class AiBoundedRequestFactory implements ClientHttpRequestFactory {
    private final HttpClient client;
    private final Duration timeout;

    AiBoundedRequestFactory(HttpClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
        return new AbstractClientHttpRequest() {
            final ByteArrayOutputStream body = new ByteArrayOutputStream();

            public URI getURI() {
                return uri;
            }

            public HttpMethod getMethod() {
                return method;
            }

            protected OutputStream getBodyInternal(HttpHeaders headers) {
                return body;
            }

            protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                var request =
                        HttpRequest.newBuilder(uri)
                                .timeout(timeout)
                                .method(
                                        method.name(),
                                        HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
                headers.forEach(
                        (key, values) -> {
                            if (!Set.of("content-length", "host", "connection", "expect", "upgrade")
                                    .contains(key.toLowerCase(Locale.ROOT)))
                                values.forEach(value -> request.header(key, value));
                        });
                var pending = client.sendAsync(request.build(), info -> new LimitedSubscriber());
                try {
                    var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    HttpHeaders safe = new HttpHeaders();
                    response.headers().map().forEach(safe::put);
                    return new ClientHttpResponse() {
                        public HttpStatusCode getStatusCode() {
                            return HttpStatusCode.valueOf(response.statusCode());
                        }

                        public String getStatusText() {
                            return Integer.toString(response.statusCode());
                        }

                        public HttpHeaders getHeaders() {
                            return safe;
                        }

                        public InputStream getBody() {
                            return new ByteArrayInputStream(response.body());
                        }

                        public void close() {}
                    };
                } catch (TimeoutException e) {
                    throw new HttpTimeoutException("Model deadline exceeded");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Model cancelled", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof AiModelAdapter.ModelFailure failure) throw failure;
                    throw new IOException("Model transport failed", e.getCause());
                } finally {
                    if (!pending.isDone()) pending.cancel(true);
                }
            }
        };
    }

    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> body = new CompletableFuture<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Flow.Subscription subscription;

        public CompletionStage<byte[]> getBody() {
            return body;
        }

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 65536) {
                    subscription.cancel();
                    body.completeExceptionally(
                            new AiModelAdapter.ModelFailure("INVALID_MODEL_RESPONSE"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        public void onError(Throwable e) {
            body.completeExceptionally(e);
        }

        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
