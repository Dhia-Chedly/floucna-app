package com.floucna.api;

import com.floucna.service.*;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class LoanController {
    private static final LoanService svc = new LoanService();

    public static void register(Javalin app) {
        app.post("/api/loans", LoanController::create);
        app.get("/api/loans", LoanController::marketplace);
        app.get("/api/loans/my", LoanController::myLoans);
        app.get("/api/loans/{id}", LoanController::detail);
        app.post("/api/loans/{id}/repay", LoanController::repay);
    }

    private static void create(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "BORROWER");
            String userId = principal.userId();
            LoanService.LoanRequest req = ctx.bodyAsClass(LoanService.LoanRequest.class);
            Map<String, Object> loan = svc.createLoan(userId, req);
            AuditLogger.log(userId, "LOAN_CREATE", loan.get("id").toString(), ctx.ip());
            ctx.status(201).json(loan);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void marketplace(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "BORROWER", "LENDER", "ADMIN");
            ctx.json(svc.listMarketplace());
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void myLoans(Context ctx) {
        try {
            String userId = AuthGuard.requireRole(ctx, "BORROWER").userId();
            ctx.json(svc.listMyLoans(userId));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void detail(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
            ctx.json(svc.getLoanDetailForViewer(ctx.pathParam("id"), principal.userId(), principal.role()));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void repay(Context ctx) {
        try {
            String userId = AuthGuard.requireRole(ctx, "BORROWER").userId();
            String loanId = ctx.pathParam("id");
            double amount = Double.parseDouble(ctx.queryParam("amount"));
            svc.repay(loanId, userId, amount);
            AuditLogger.log(userId, "LOAN_REPAY", loanId, ctx.ip());
            ctx.json(Map.of("message", "Repayment recorded"));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }
}
