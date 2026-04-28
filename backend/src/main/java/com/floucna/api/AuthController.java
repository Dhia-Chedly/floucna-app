package com.floucna.api;

import com.floucna.service.AuthService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;

public class AuthController {

    private static final AuthService svc = new AuthService();

    public static void register(Javalin app) {
        app.post("/api/auth/register", AuthController::handleRegister);
        app.post("/api/auth/login", AuthController::handleLogin);
        app.get("/api/auth/me", AuthController::handleMe);
        app.get("/api/me", AuthController::handleMe);
    }

    private static void handleRegister(Context ctx) {
        try {
            AuthService.RegisterRequest req = ctx.bodyAsClass(AuthService.RegisterRequest.class);
            AuthService.AuthResponse res = svc.register(req);
            AuditLogger.log(res.userId(), "REGISTER", res.userId(), ctx.ip());
            ctx.json(res);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void handleLogin(Context ctx) {
        try {
            AuthService.LoginRequest req = ctx.bodyAsClass(AuthService.LoginRequest.class);
            AuthService.AuthResponse res = svc.login(req);
            AuditLogger.log(res.userId(), "LOGIN", res.userId(), ctx.ip());
            ctx.json(res);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void handleMe(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
            String userId = principal.userId();
            ctx.json(svc.getProfile(userId));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }
}
