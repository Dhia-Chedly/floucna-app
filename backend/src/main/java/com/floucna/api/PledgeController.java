package com.floucna.api;

import com.floucna.service.PledgeEngine;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;

public class PledgeController {
    private static final PledgeEngine engine = new PledgeEngine();

    public static void register(Javalin app) {
        app.post("/api/loans/{id}/pledge", PledgeController::pledge);
        app.get("/api/loans/{id}/pledges", PledgeController::getPledges);
    }

    private static void pledge(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "LENDER");
            String userId = principal.userId();
            String loanId = ctx.pathParam("id");
            PledgeEngine.PledgeRequest req = ctx.bodyAsClass(PledgeEngine.PledgeRequest.class);
            Map<String, Object> result = engine.pledge(loanId, userId, req.amount());
            AuditLogger.log(userId, "PLEDGE", loanId, ctx.ip());
            ctx.json(result);
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void getPledges(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "BORROWER", "LENDER", "ADMIN");
            ctx.json(engine.getPledgesForLoan(ctx.pathParam("id")));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }
}
