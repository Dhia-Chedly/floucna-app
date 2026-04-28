package com.floucna.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Date;

public class JwtUtil {

    private static final SecretKey KEY = resolveSigningKey();
    private static final long EXPIRY_MS = resolveExpiryMs();
    private static final String ISSUER = System.getenv().getOrDefault("FLOUCNA_JWT_ISSUER", "floucna-backend");
    private static final String AUDIENCE = System.getenv().getOrDefault("FLOUCNA_JWT_AUDIENCE", "floucna-frontend");

    public static String generate(String userId, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .claim("iss", ISSUER)
            .claim("aud", AUDIENCE)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
            .signWith(KEY)
            .compact();
    }

    public static Claims parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(KEY)
            .clockSkewSeconds(30)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String issuer = claims.get("iss", String.class);
        if (!ISSUER.equals(issuer)) {
            throw new JwtException("Invalid token issuer");
        }

        Object audienceClaim = claims.get("aud");
        boolean audienceMatches = false;
        if (audienceClaim instanceof String audString) {
            audienceMatches = AUDIENCE.equals(audString);
        } else if (audienceClaim instanceof Collection<?> audCollection) {
            audienceMatches = audCollection.contains(AUDIENCE);
        }
        if (!audienceMatches) {
            throw new JwtException("Invalid token audience");
        }
        return claims;
    }

    public static String extractUserId(String token) {
        return parse(token).getSubject();
    }

    public static String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    private static SecretKey resolveSigningKey() {
        String configuredSecret = System.getenv("FLOUCNA_JWT_SECRET");
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < 32) {
                throw new IllegalStateException("FLOUCNA_JWT_SECRET must be at least 32 bytes");
            }
            return Keys.hmacShaKeyFor(secretBytes);
        }

        byte[] ephemeralSecret = new byte[64];
        new SecureRandom().nextBytes(ephemeralSecret);
        System.err.println("WARNING: FLOUCNA_JWT_SECRET is not set. Using ephemeral key for this process.");
        return Keys.hmacShaKeyFor(ephemeralSecret);
    }

    private static long resolveExpiryMs() {
        String expirySeconds = System.getenv("FLOUCNA_JWT_EXPIRY_SECONDS");
        if (expirySeconds == null || expirySeconds.isBlank()) {
            return 24 * 60 * 60 * 1000L;
        }
        try {
            long seconds = Long.parseLong(expirySeconds);
            if (seconds <= 0) {
                throw new IllegalStateException("FLOUCNA_JWT_EXPIRY_SECONDS must be > 0");
            }
            return seconds * 1000L;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("FLOUCNA_JWT_EXPIRY_SECONDS must be numeric");
        }
    }
}
