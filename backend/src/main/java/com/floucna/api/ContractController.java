package com.floucna.api;

import com.floucna.service.ContractService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuditLogger;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContractController {

    private static final ContractService CONTRACTS = new ContractService();

    public static void register(Javalin app) {
        app.get("/api/contracts/{loanId}", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
                ctx.json(CONTRACTS.getContractForViewer(ctx.pathParam("loanId"), principal.userId(), principal.role()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/contracts/{loanId}/download", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
                String loanId = ctx.pathParam("loanId");
                String pdfPath = CONTRACTS.getDownloadPathForViewer(loanId, principal.userId(), principal.role());
                byte[] bytes = Files.readAllBytes(Path.of(pdfPath));
                ctx.contentType("application/pdf");
                ctx.header("Content-Disposition", "attachment; filename=\"loan_agreement_" + loanId + ".pdf\"");
                ctx.result(bytes);
                AuditLogger.log(principal.userId(), "CONTRACT_DOWNLOADED", "CONTRACT", loanId, "OK");
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/admin/contracts", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(CONTRACTS.listContractsForAdmin());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/contracts/generate/{loanId}", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String loanId = ctx.pathParam("loanId");
                var contract = CONTRACTS.generatePdfContract(loanId);
                AuditLogger.log(admin.userId(), "PDF_GENERATED", "CONTRACT", loanId, "PDF_GENERATED");
                ctx.json(contract);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/contracts/sign/{loanId}", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String loanId = ctx.pathParam("loanId");
                var contract = CONTRACTS.signPdfContract(loanId);
                AuditLogger.log(admin.userId(), "PDF_SIGNED", "CONTRACT", loanId, "SIGNED");
                ctx.json(contract);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/contracts/timestamp/{loanId}", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String loanId = ctx.pathParam("loanId");
                var contract = CONTRACTS.timestampContract(loanId);
                AuditLogger.log(admin.userId(), "PDF_TIMESTAMPED", "CONTRACT", loanId, "TIMESTAMPED");
                ctx.json(contract);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }
}
