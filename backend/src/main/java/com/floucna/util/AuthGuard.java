package com.floucna.util;

import io.jsonwebtoken.JwtException;
import io.javalin.http.Context;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthGuard {

    public record Principal(String userId, String role) {}

    private AuthGuard() {}

    public static Principal requireAuth(Context ctx) {
        String token = extractBearerToken(ctx);
        try {
            String userId = JwtUtil.extractUserId(token);
            String role = JwtUtil.extractRole(token);
            if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
                throw new ApiException(401, "Unauthorized");
            }
            return new Principal(userId, role);
        } catch (JwtException e) {
            throw new ApiException(401, "Invalid or expired token");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(401, "Unauthorized");
        }
    }

    public static Principal requireRole(Context ctx, String... allowedRoles) {
        Principal principal = requireAuth(ctx);
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedRoles));
        if (!allowed.contains(principal.role())) {
            throw new ApiException(403, "Forbidden");
        }
        return principal;
    }

    public static void requireOwnerOrAdmin(String ownerUserId, Principal principal) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new ApiException(404, "Resource not found");
        }
        if (!ownerUserId.equals(principal.userId()) && !"ADMIN".equals(principal.role())) {
            throw new ApiException(403, "Forbidden");
        }
    }

    public static boolean isAdmin(Principal principal) {
        return "ADMIN".equals(principal.role());
    }

    private static String extractBearerToken(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing bearer token");
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new ApiException(401, "Missing bearer token");
        }
        return token;
    }
}
