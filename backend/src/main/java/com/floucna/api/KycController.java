package com.floucna.api;

import com.floucna.service.KycService;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KycController {
    private static final KycService svc = new KycService();
    private static final long MAX_UPLOAD_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public static void register(Javalin app) {
        app.post("/api/kyc/upload", KycController::handleUpload);
        app.get("/api/kyc/status", KycController::handleStatus);
        app.post("/api/kyc/{userId}/approve", KycController::handleApprove);
        app.get("/api/kyc/pending", KycController::handleListPending);
    }

    private static void handleUpload(Context ctx) {
        try {
            AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "BORROWER", "LENDER");
            String userId = principal.userId();
            Path uploadDir = resolveUserUploadDir(userId);
            Files.createDirectories(uploadDir);

            UploadedFile front = ctx.uploadedFile("idFront");
            UploadedFile back = ctx.uploadedFile("idBack");
            UploadedFile selfie = ctx.uploadedFile("selfie");
            if (front == null || back == null || selfie == null) {
                throw new IllegalArgumentException("idFront, idBack and selfie are required");
            }

            String frontPath = saveFile(front, uploadDir, "id_front");
            String backPath = saveFile(back, uploadDir, "id_back");
            String selfiePath = saveFile(selfie, uploadDir, "selfie");

            svc.submitKyc(userId, frontPath, backPath, selfiePath);
            AuditLogger.log(userId, "KYC_SUBMIT", userId, ctx.ip());
            ctx.json(Map.of("message", "KYC submitted successfully"));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static String saveFile(UploadedFile file, Path dir, String name) throws Exception {
        validateFile(file);

        String ext = normalizeExtension(file.extension(), file.contentType());
        Path dest = dir.resolve(name + ext);
        try (InputStream in = file.content()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return dest.toString();
    }

    private static void handleStatus(Context ctx) {
        try {
            String userId = AuthGuard.requireAuth(ctx).userId();
            ctx.json(svc.getStatus(userId));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void handleApprove(Context ctx) {
        try {
            AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
            String targetUserId = ctx.pathParam("userId");
            boolean approve = Boolean.parseBoolean(ctx.queryParam("approve"));
            svc.approveKyc(targetUserId, approve);
            AuditLogger.log(admin.userId(), approve ? "KYC_APPROVE" : "KYC_REJECT", targetUserId, ctx.ip());
            ctx.json(Map.of("message", approve ? "KYC approved" : "KYC rejected"));
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void handleListPending(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "ADMIN");
            ctx.json(svc.listPending());
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static Path resolveUserUploadDir(String userId) {
        Path root = Path.of("uploads", "kyc").toAbsolutePath().normalize();
        Path target = root.resolve(userId).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(400, "Invalid upload path");
        }
        return target;
    }

    private static void validateFile(UploadedFile file) {
        if (file.size() <= 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.size() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File exceeds 5MB limit");
        }
        String contentType = file.contentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported file type");
        }
    }

    private static String normalizeExtension(String extension, String contentType) {
        if (extension != null) {
            String ext = extension.toLowerCase(Locale.ROOT).replace(".", "");
            if (ALLOWED_EXTENSIONS.contains(ext)) {
                return "." + ext;
            }
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported file type");
        };
    }
}
