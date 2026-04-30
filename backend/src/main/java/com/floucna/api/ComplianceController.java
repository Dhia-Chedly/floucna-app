package com.floucna.api;

import com.floucna.service.ComplianceService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuditLogger;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import java.io.InputStream;
import java.util.Map;

public class ComplianceController {

    private static final ComplianceService COMPLIANCE = new ComplianceService();

    public static void register(Javalin app) {
        app.post("/api/admin/compliance/verify", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String loanId = resolveLoanId(ctx);
                if (loanId == null || loanId.isBlank()) {
                    throw new IllegalArgumentException("loanId is required");
                }

                UploadedFile file = ctx.uploadedFile("file");
                byte[] bytes = null;
                String fileName = null;
                if (file != null) {
                    fileName = file.filename();
                    try (InputStream in = file.content()) {
                        bytes = in.readAllBytes();
                    }
                }

                AuditLogger.log(admin.userId(), "COMPLIANCE_VERIFICATION_RUN", "CONTRACT", loanId, "STARTED");
                Map<String, Object> report = COMPLIANCE.verify(loanId, bytes, fileName);

                String result = String.valueOf(report.get("overallResult"));
                if ("PASS".equals(result)) {
                    AuditLogger.log(admin.userId(), "COMPLIANCE_VERIFICATION_VALID", "CONTRACT", loanId, "PASS");
                } else {
                    AuditLogger.log(admin.userId(), "COMPLIANCE_VERIFICATION_FAILED", "CONTRACT", loanId, "FAIL");
                }

                ctx.json(report);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/admin/compliance/reports", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(COMPLIANCE.listReports());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/admin/audit-logs", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
                ctx.json(COMPLIANCE.getAuditLogs(limit));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }

    private static String resolveLoanId(io.javalin.http.Context ctx) {
        String loanId = ctx.formParam("loanId");
        if (loanId != null && !loanId.isBlank()) {
            return loanId;
        }
        loanId = ctx.queryParam("loanId");
        if (loanId != null && !loanId.isBlank()) {
            return loanId;
        }

        if (ctx.contentType() != null && ctx.contentType().contains("application/json")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = ctx.bodyAsClass(Map.class);
                Object fromBody = body.get("loanId");
                return fromBody == null ? null : String.valueOf(fromBody);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
