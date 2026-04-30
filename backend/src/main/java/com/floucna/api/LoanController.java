package com.floucna.api;

import com.floucna.service.LoanService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuditLogger;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;

public class LoanController {

    private static final LoanService LOANS = new LoanService();

    public static void register(Javalin app) {
        app.post("/api/loans", ctx -> {
            try {
                AuthGuard.Principal borrower = AuthGuard.requireRole(ctx, "BORROWER");
                LoanService.LoanRequest request = ctx.bodyAsClass(LoanService.LoanRequest.class);
                var loan = LOANS.createLoan(borrower.userId(), request);
                AuditLogger.log(borrower.userId(), "LOAN_SUBMITTED", "LOAN", String.valueOf(loan.get("id")), "SUBMITTED");
                ctx.status(201).json(loan);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/loans/me", ctx -> {
            try {
                AuthGuard.Principal borrower = AuthGuard.requireRole(ctx, "BORROWER");
                ctx.json(LOANS.listMyLoans(borrower.userId()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/loans/{id}", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
                ctx.json(LOANS.getLoanForBorrowerOrAdmin(ctx.pathParam("id"), principal.userId(), principal.role()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
