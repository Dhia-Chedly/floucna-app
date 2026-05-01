package com.floucna.api;

import com.floucna.service.KycService;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuditLogger;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KycController {

    private static final KycService KYC = new KycService();
    private static final long MAX_UPLOAD_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public static void register(Javalin app) {
        app.post("/api/kyc/session", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "BORROWER");
                Map<String, Object> response = KYC.createSession(principal.userId());
                AuditLogger.log(principal.userId(), "KYC_SESSION_CREATED", "KYC", principal.userId(), "OK");
                ctx.json(response);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/kyc/status", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "BORROWER");
                ctx.json(KYC.getStatus(principal.userId()));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/kyc/local/upload", ctx -> {
            try {
                AuthGuard.Principal principal = AuthGuard.requireRole(ctx, "BORROWER");
                UploadedFile idPhoto = ctx.uploadedFile("idPhoto");
                if (idPhoto == null) {
                    throw new IllegalArgumentException("idPhoto is required");
                }

                validateFile(idPhoto);
                Path uploadDir = resolveUserUploadDir(principal.userId());
                Files.createDirectories(uploadDir);

                String ext = extensionFromContentType(idPhoto.contentType());
                Path dest = uploadDir.resolve("id_photo" + ext);
                try (InputStream in = idPhoto.content()) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }

                KYC.uploadLocalKyc(principal.userId(), dest.toString());
                AuditLogger.log(principal.userId(), "KYC_LOCAL_UPLOAD", "KYC", principal.userId(), "UNDER_REVIEW");
                ctx.status(201).json(Map.of("message", "Local KYC uploaded", "status", "UNDER_REVIEW"));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/kyc/webhook/didit", ctx -> {
            try {
                handleDiditWebhook(ctx);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        // Backward-compatible fallback: if the provider is configured with the bare domain
        // (POST /), process the webhook with the same signature checks.
        app.post("/", ctx -> {
            try {
                handleDiditWebhook(ctx);
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/admin/kyc/pending", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(KYC.listPendingForAdmin());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/kyc/{id}/approve", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String id = ctx.pathParam("id");
                KYC.approve(id, admin.userId());
                AuditLogger.log(admin.userId(), "KYC_APPROVED", "KYC", id, "VERIFIED");
                ctx.json(Map.of("message", "KYC approved"));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.post("/api/admin/kyc/{id}/reject", ctx -> {
            try {
                AuthGuard.Principal admin = AuthGuard.requireRole(ctx, "ADMIN");
                String id = ctx.pathParam("id");
                KYC.reject(id, admin.userId());
                AuditLogger.log(admin.userId(), "KYC_REJECTED", "KYC", id, "REJECTED");
                ctx.json(Map.of("message", "KYC rejected"));
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }

    private static Path resolveUserUploadDir(String userId) {
        Path root = Path.of("storage", "kyc").toAbsolutePath().normalize();
        Path target = root.resolve(userId).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid upload path");
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
        String type = file.contentType();
        if (type == null || !ALLOWED_CONTENT_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported file type");
        }
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void handleDiditWebhook(io.javalin.http.Context ctx) throws Exception {
        String signature = firstNonBlank(
            ctx.header("X-Signature"),
            ctx.header("X-Didit-Signature")
        );
        String signatureV2 = ctx.header("X-Signature-V2");
        String signatureSimple = ctx.header("X-Signature-Simple");
        String timestamp = ctx.header("X-Timestamp");
        Map<String, Object> result = KYC.handleDiditWebhook(
            ctx.body(),
            signature,
            signatureV2,
            signatureSimple,
            timestamp
        );
        AuditLogger.log(null, "KYC_WEBHOOK_RECEIVED", "KYC", String.valueOf(result.get("sessionId")), "OK");
        ctx.json(result);
    }
}
