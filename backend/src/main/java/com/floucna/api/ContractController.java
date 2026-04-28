package com.floucna.api;

import com.floucna.service.ContractService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.*;

public class ContractController {
    private static final ContractService svc = new ContractService();

    public static void register(Javalin app) {
        app.get("/api/contracts/{loanId}", ContractController::getContract);
        app.get("/api/contracts/{loanId}/download", ContractController::downloadPdf);
    }

    private static void getContract(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
            ctx.json(svc.getContractForViewer(ctx.pathParam("loanId"), principal.userId(), principal.role()));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void downloadPdf(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireAuth(ctx);
            String pdfPath = svc.getContractFilePathForViewer(ctx.pathParam("loanId"), principal.userId(), principal.role());
            byte[] bytes = Files.readAllBytes(Path.of(pdfPath));
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "attachment; filename=\"loan_agreement_" + ctx.pathParam("loanId") + ".pdf\"");
            ctx.header("Cache-Control", "no-store");
            ctx.result(bytes);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }
}
