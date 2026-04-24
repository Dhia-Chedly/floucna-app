package com.floucna.api;

import com.floucna.service.ContractService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.*;
import java.util.Map;

public class ContractController {
    private static final ContractService svc = new ContractService();

    public static void register(Javalin app) {
        app.get("/api/contracts/{loanId}", ContractController::getContract);
        app.get("/api/contracts/{loanId}/download", ContractController::downloadPdf);
    }

    private static void getContract(Context ctx) {
        try {
            ctx.json(svc.getContract(ctx.pathParam("loanId")));
        } catch (Exception e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        }
    }

    private static void downloadPdf(Context ctx) {
        try {
            Map<String, Object> contract = svc.getContract(ctx.pathParam("loanId"));
            String pdfPath = contract.get("pdfPath").toString();
            byte[] bytes = Files.readAllBytes(Path.of(pdfPath));
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "attachment; filename=\"loan_agreement_" + ctx.pathParam("loanId") + ".pdf\"");
            ctx.result(bytes);
        } catch (Exception e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        }
    }
}
