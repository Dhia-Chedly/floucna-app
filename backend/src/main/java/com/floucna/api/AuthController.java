package com.floucna.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floucna.service.AuthService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuthGuard;
import com.floucna.util.RateLimiter;
import io.javalin.Javalin;
import java.util.Locale;
import java.util.Map;

public class AuthController {

    private static final AuthService AUTH = new AuthService();
    private static final ObjectMapper JSON = new ObjectMapper();

    // 20 attempts per IP per 15 minutes
    private static final RateLimiter IP_LIMITER = new RateLimiter(20, 15 * 60 * 1000L);
    // 10 attempts per email per 15 minutes — reset on successful login
    private static final RateLimiter EMAIL_LIMITER = new RateLimiter(10, 15 * 60 * 1000L);

    public static void register(Javalin app) {
        app.get("/api/me", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
                ctx.json(AUTH.getProfile(principal.userId()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/auth/register", ctx -> {
            try {
                Map<String, String> body = JSON.readValue(ctx.body(), new TypeReference<>() {});
                Map<String, Object> result = AUTH.registerLocal(
                    body.get("email"),
                    body.get("password"),
                    body.get("fullName")
                );
                ctx.status(201).json(result);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/auth/login", ctx -> {
            try {
                Map<String, String> body = JSON.readValue(ctx.body(), new TypeReference<>() {});
                String email = body.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
                IP_LIMITER.check(ctx.ip());
                EMAIL_LIMITER.check(email);
                Map<String, Object> result = AUTH.loginLocal(body.get("email"), body.get("password"));
                EMAIL_LIMITER.reset(email);
                ctx.json(result);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
