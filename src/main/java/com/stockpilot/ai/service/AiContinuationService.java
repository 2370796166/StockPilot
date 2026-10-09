package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Five-minute, user/question-bound query plans; no session storage or model credentials. */
@Service
public class AiContinuationService {
    private final ObjectMapper json;
    private final Clock clock;
    private final byte[] signingKey = new byte[32];

    @Autowired
    public AiContinuationService(ObjectMapper json) {
        this(json, Clock.systemUTC());
    }

    AiContinuationService(ObjectMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
        new SecureRandom().nextBytes(signingKey);
    }

    public String issue(String question, ArrayNode calls) {
        String actor = actor();
        if (actor == null) return null;
        try {
            var payload =
                    json.createObjectNode()
                            .put("actorHash", hash(actor))
                            .put("questionHash", hash(question))
                            .put("expiresAt", clock.instant().plusSeconds(300).getEpochSecond());
            payload.set("calls", calls);
            String encoded =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(json.writeValueAsBytes(payload));
            String token =
                    encoded
                            + "."
                            + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
            if (token.length() > 32768) throw new IllegalArgumentException("Query plan too large");
            return token;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot create query continuation");
        }
    }

    public ArrayNode verify(String token, String question) {
        try {
            if (token == null || token.length() > 32768) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2
                    || !MessageDigest.isEqual(
                            sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            var payload = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            String actor = actor();
            if (actor == null
                    || !hash(actor).equals(payload.path("actorHash").asText())
                    || !hash(question).equals(payload.path("questionHash").asText())
                    || clock.instant().getEpochSecond() >= payload.path("expiresAt").asLong()
                    || !payload.path("calls").isArray()
                    || payload.path("calls").isEmpty()
                    || payload.path("calls").size() > 8) throw new IllegalArgumentException();
            return ((ArrayNode) payload.path("calls")).deepCopy();
        } catch (Exception e) {
            throw new IllegalArgumentException("Query continuation is invalid or expired");
        }
    }

    private byte[] sign(String encoded) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(encoded.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(String question) throws Exception {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256")
                                .digest(question.getBytes(StandardCharsets.UTF_8)));
    }

    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? null
                : authentication.getName();
    }
}
