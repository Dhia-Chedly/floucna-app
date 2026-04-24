package com.floucna.api;

import com.floucna.service.KycService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.nio.file.*;
import java.util.Map;

public class KycController {
    private static final KycService svc = new KycService();

    public static void register(Javalin app) {
        app.post("/api/kyc/upload", KycController::handleUpload);
        app.get("/api/kyc/status", KycController::handleStatus);
        app.post("/api/kyc/{userId}/approve", KycController::handleApprove);
        app.get("/api/kyc/pending", KycController::handleListPending);
    }

    private static void handleUpload(Context ctx) {
        try {
            String token = AuthController.extractToken(ctx);
            String userId = JwtUtil.extractUserId(token);

            Path uploadDir = Path.of("uploads", userId);
            Files.createDirectories(uploadDir);

            UploadedFile front = ctx.uploadedFile("idFront");
            UploadedFile back = ctx.uploadedFile("idBack");
            UploadedFile selfie = ctx.uploadedFile("selfie");

            String frontPath = saveFile(front, uploadDir, "id_front");
            String backPath = saveFile(back, uploadDir, "id_back");
            String selfiePath = saveFile(selfie, uploadDir, "selfie");

            svc.submitKyc(userId, frontPath, backPath, selfiePath);
            AuditLogger.log(userId, "KYC_SUBMIT", userId, ctx.ip());
            ctx.json(Map.of("message", "KYC submitted successfully"));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static String saveFile(UploadedFile file, Path dir, String name) throws Exception {
        if (file == null) return null;
        String ext = file.filename().contains(".") ? file.filename().substring(file.filename().lastIndexOf('.')) : ".bin";
        Path dest = dir.resolve(name + ext);
        Files.copy(file.content(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }

    private static void handleStatus(Context ctx) {
        try {
            String userId = JwtUtil.extractUserId(AuthController.extractToken(ctx));
            ctx.json(svc.getStatus(userId));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void handleApprove(Context ctx) {
        try {
            requireAdmin(ctx);
            String targetUserId = ctx.pathParam("userId");
            boolean approve = Boolean.parseBoolean(ctx.queryParam("approve"));
            svc.approveKyc(targetUserId, approve);
            String actorId = JwtUtil.extractUserId(AuthController.extractToken(ctx));
            AuditLogger.log(actorId, approve ? "KYC_APPROVE" : "KYC_REJECT", targetUserId, ctx.ip());
            ctx.json(Map.of("message", approve ? "KYC approved" : "KYC rejected"));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void handleListPending(Context ctx) {
        try {
            requireAdmin(ctx);
            ctx.json(svc.listPending());
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private static void requireAdmin(Context ctx) {
        String role = JwtUtil.extractRole(AuthController.extractToken(ctx));
        if (!"ADMIN".equals(role)) throw new RuntimeException("Forbidden: Admin only");
    }
}
