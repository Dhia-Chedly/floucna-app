package com.floucna.api;

import com.floucna.service.LoanService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuditLogger;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import java.util.Map;

public class AdminLoanController {

    private static final LoanService LOANS = new LoanService();

    public static void register(Javalin app) {
        app.get("/api/admin/loans", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(LOANS.listAllLoansForAdmin());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/loans/{id}/approve", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                Map<String, Object> loan = LOANS.approve(ctx.pathParam("id"));
                AuditLogger.log(admin.userId(), "LOAN_APPROVED", "LOAN", String.valueOf(loan.get("id")), "APPROVED");
                ctx.json(loan);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/loans/{id}/reject", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                Map<String, Object> loan = LOANS.reject(ctx.pathParam("id"));
                AuditLogger.log(admin.userId(), "LOAN_REJECTED", "LOAN", String.valueOf(loan.get("id")), "REJECTED");
                ctx.json(loan);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
