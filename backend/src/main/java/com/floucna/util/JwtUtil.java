package com.floucna.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtUtil {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HEADER_B64 = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {}

    public static String sign(Map<String, Object> payload, String secret) {
        try {
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(JSON.writeValueAsBytes(payload));
            String signingInput = HEADER_B64 + "." + payloadB64;
            return signingInput + "." + hmacSHA256(signingInput, secret);
        } catch (Exception e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    public static Map<String, Object> verify(String token, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new ApiException(401, "Invalid token format");
            }
            String signingInput = parts[0] + "." + parts[1];
            String expected = hmacSHA256(signingInput, secret);
            if (!constantTimeEquals(expected, parts[2])) {
                throw new ApiException(401, "Invalid token signature");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = JSON.readValue(payloadJson, new TypeReference<>() {});
            Object exp = payload.get("exp");
            if (exp instanceof Number n && Instant.now().getEpochSecond() > n.longValue()) {
                throw new ApiException(401, "Token has expired");
            }
            return payload;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(401, "Invalid or expired token");
        }
    }

    private static String hmacSHA256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }
}
