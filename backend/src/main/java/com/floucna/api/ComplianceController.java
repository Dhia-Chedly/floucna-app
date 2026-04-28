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
            AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "ADMIN");
            String loanId = ctx.pathParam("loanId");
            Map<String, Object> report = svc.verifyContract(loanId);
            AuditLogger.log(principal.userId(), "VERIFY_CONTRACT", loanId, ctx.ip());
            ctx.json(report);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void auditLogs(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "ADMIN");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
            ctx.json(svc.getAuditLogs(limit));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }
}
