package com.floucna.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floucna.service.AuthService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import java.util.Map;

public class AuthController {

    private static final AuthService AUTH = new AuthService();
    private static final ObjectMapper JSON = new ObjectMapper();

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
                ctx.json(AUTH.loginLocal(body.get("email"), body.get("password")));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
