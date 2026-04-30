package com.floucna.util;

import com.floucna.service.AuthService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AuthGuard {

    public record Principal(String userId, String role, String email, String fullName, String providerSubject) {}

    private static final AuthService AUTH = new AuthService();

    private AuthGuard() {}

    public static Principal requireAuth(io.javalin.http.Context ctx) {
        String token = extractBearerToken(ctx);
        AuthService.ExternalIdentity identity = AUTH.resolveExternalIdentity(token);
        AuthService.LocalUser localUser = AUTH.syncLocalUser(identity);
        return new Principal(
            localUser.id(),
            normalizeRole(localUser.role()),
            localUser.email(),
            localUser.fullName(),
            identity.providerSubject()
        );
    }

    public static Principal requireRole(io.javalin.http.Context ctx, String... allowedRoles) {
        Principal principal = requireAuth(ctx);
        Set<String> allowed = new LinkedHashSet<>();
        Arrays.stream(allowedRoles).map(AuthGuard::normalizeRole).forEach(allowed::add);
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

    private static String extractBearerToken(io.javalin.http.Context ctx) {
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

    private static String normalizeRole(String role) {
        if (role == null) {
            return "BORROWER";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }
}
