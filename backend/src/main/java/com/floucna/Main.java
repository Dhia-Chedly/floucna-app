package com.floucna;

import com.floucna.api.AdminController;
import com.floucna.api.AuthController;
import com.floucna.api.ComplianceController;
import com.floucna.api.ContractController;
import com.floucna.api.KycController;
import com.floucna.api.LoanController;
import com.floucna.api.PledgeController;
import com.floucna.db.Database;
import com.floucna.service.SeedService;
import io.javalin.Javalin;
import java.util.Arrays;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Database.initialize();
        SeedService.seed();

        String[] allowedOrigins = resolveAllowedOrigins();

        Javalin app = Javalin.create(config -> {
            config.http.maxRequestSize = 15_000_000L;
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                for (String origin : allowedOrigins) {
                    rule.allowHost(origin);
                }
            }));
        });

        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Referrer-Policy", "no-referrer");
            ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            ctx.header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none';");
        });

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP")));

        AuthController.register(app);
        KycController.register(app);
        LoanController.register(app);
        PledgeController.register(app);
        ContractController.register(app);
        ComplianceController.register(app);
        AdminController.register(app);

        app.start(8080);
        System.out.println("Floucna backend running on http://localhost:8080");
        System.out.println("CORS allowed origins: " + Arrays.toString(allowedOrigins));
    }

    private static String[] resolveAllowedOrigins() {
        String configuredOrigins = System.getenv("FLOUCNA_ALLOWED_ORIGINS");
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            return new String[]{"http://localhost:3000"};
        }
        String[] origins = Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toArray(String[]::new);
        return origins.length == 0 ? new String[]{"http://localhost:3000"} : origins;
    }
}
