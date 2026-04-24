package com.floucna;

import com.floucna.api.*;
import com.floucna.db.Database;
import io.javalin.Javalin;
import java.util.Map;
import io.javalin.http.staticfiles.Location;

public class Main {

    public static void main(String[] args) {
        // Initialize database
        Database.initialize();

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    rule.allowHost("http://localhost:3000");
                    rule.allowCredentials = true;
                });
            });
            config.useVirtualThreads = true;
        });

        // Health check for Docker
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP")));

        // Register routes
        AuthController.register(app);
        KycController.register(app);
        LoanController.register(app);
        PledgeController.register(app);
        ContractController.register(app);
        ComplianceController.register(app);
        AdminController.register(app);

        app.start(8080);
        System.out.println("✅ Floucna backend running on http://localhost:8080");
    }
}
