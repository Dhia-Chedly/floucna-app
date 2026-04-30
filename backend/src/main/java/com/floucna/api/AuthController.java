package com.floucna.api;

import com.floucna.service.AuthService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;

public class AuthController {

    private static final AuthService AUTH = new AuthService();

    public static void register(Javalin app) {
        app.get("/api/me", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
                ctx.json(AUTH.getProfile(principal.userId()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
