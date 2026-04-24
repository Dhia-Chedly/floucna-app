package com.floucna.api;

import com.floucna.service.AuthService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class AuthController {

    private static final AuthService svc = new AuthService();

    public static void register(Javalin app) {
        app.post("/api/auth/register", AuthController::handleRegister);
        app.post("/api/auth/login", AuthController::handleLogin);
        app.get("/api/auth/me", AuthController::handleMe);
    }

    private static void handleRegister(Context ctx) {
        try {
            AuthService.RegisterRequest req = ctx.bodyAsClass(AuthService.RegisterRequest.class);
            AuthService.AuthResponse res = svc.register(req);
            AuditLogger.log(res.userId(), "REGISTER", res.userId(), ctx.ip());
            ctx.json(res);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void handleLogin(Context ctx) {
        try {
            AuthService.LoginRequest req = ctx.bodyAsClass(AuthService.LoginRequest.class);
            AuthService.AuthResponse res = svc.login(req);
            AuditLogger.log(res.userId(), "LOGIN", res.userId(), ctx.ip());
            ctx.json(res);
        } catch (Exception e) {
            ctx.status(401).json(Map.of("error", e.getMessage()));
        }
    }

    private static void handleMe(Context ctx) {
        try {
            String token = extractToken(ctx);
            String userId = JwtUtil.extractUserId(token);
            ctx.json(svc.getProfile(userId));
        } catch (Exception e) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
        }
    }

    public static String extractToken(Context ctx) {
        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) throw new RuntimeException("Missing token");
        return auth.substring(7);
    }
}
