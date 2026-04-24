package com.floucna.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
        "floucna-super-secret-key-minimum-256-bits-long!!".getBytes()
    );
    private static final long EXPIRY_MS = 24 * 60 * 60 * 1000L; // 24h

    public static String generate(String userId, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
            .signWith(KEY)
            .compact();
    }

    public static Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public static String extractUserId(String token) {
        return parse(token).getSubject();
    }

    public static String extractRole(String token) {
        return parse(token).get("role", String.class);
    }
}
