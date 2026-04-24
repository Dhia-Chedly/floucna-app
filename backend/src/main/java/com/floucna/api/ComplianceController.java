package com.floucna.api;

import com.floucna.service.ComplianceService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class ComplianceController {
    private static final ComplianceService svc = new ComplianceService();

    public static void register(Javalin app) {
        app.post("/api/contracts/{loanId}/verify", ComplianceController::verify);
        app.get("/api/admin/audit-logs", ComplianceController::auditLogs);
    }

    private static void verify(Context ctx) {
        try {
            requireAdmin(ctx);
            String loanId = ctx.pathParam("loanId");
            Map<String, Object> report = svc.verifyContract(loanId);
            String actorId = JwtUtil.extractUserId(AuthController.extractToken(ctx));
            AuditLogger.log(actorId, "VERIFY_CONTRACT", loanId, ctx.ip());
            ctx.json(report);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void auditLogs(Context ctx) {
        try {
            requireAdmin(ctx);
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
            ctx.json(svc.getAuditLogs(limit));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void requireAdmin(Context ctx) {
        String role = JwtUtil.extractRole(AuthController.extractToken(ctx));
        if (!"ADMIN".equals(role)) throw new RuntimeException("Forbidden");
    }
}
