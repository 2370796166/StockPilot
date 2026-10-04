package com.stockpilot.security.auth;

import com.stockpilot.security.api.SecurityErrorCode;
import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.shared.api.AccessErrorCode;
import com.stockpilot.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final SecurityProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(SecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long userId, String username) {
        byte[] key = key();
        Instant now = clock.instant(),
                exp = now.plus(Duration.ofMinutes(properties.accessTokenMinutes()));
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload =
                b64(
                        "{\"sub\":\""
                                + userId
                                + "\",\"usr\":\""
                                + escape(username)
                                + "\",\"iat\":"
                                + now.getEpochSecond()
                                + ",\"exp\":"
                                + exp.getEpochSecond()
                                + "}");
        String unsigned = header + "." + payload;
        return unsigned + "." + sign(unsigned, key);
    }

    public Claims verify(String token) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3
                    || !"{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                            .equals(
                                    new String(
                                            Base64.getUrlDecoder().decode(p[0]),
                                            StandardCharsets.UTF_8))
                    || !MessageDigest.isEqual(
                            p[2].getBytes(StandardCharsets.US_ASCII),
                            sign(p[0] + "." + p[1], key()).getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException();
            String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8);
            long sub = number(json, "sub"), exp = number(json, "exp");
            String usr = text(json, "usr");
            if (clock.instant().getEpochSecond() >= exp) throw new IllegalArgumentException();
            return new Claims(sub, usr, Instant.ofEpochSecond(exp));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AccessErrorCode.UNAUTHENTICATED);
        }
    }

    private byte[] key() {
        String s = properties.jwtSecret();
        if (s == null || s.length() < 32)
            throw new BusinessException(SecurityErrorCode.JWT_NOT_CONFIGURED);
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String value, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static long number(String json, String key) {
        String v = text(json, key);
        return Long.parseLong(v);
    }

    private static String text(String json, String key) {
        var m =
                java.util.regex.Pattern.compile(
                                "\\\""
                                        + key
                                        + "\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"])*)\\\"|(\\d+))")
                        .matcher(json);
        if (!m.find()) throw new IllegalArgumentException();
        return m.group(1) != null
                ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\")
                : m.group(2);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record Claims(long userId, String username, Instant expiresAt) {}
}
