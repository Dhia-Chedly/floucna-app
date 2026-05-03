package com.floucna.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floucna.db.Database;
import com.floucna.util.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class KycService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public Map<String, Object> createSession(String userId) throws Exception {
        String mode = configuredKycMode();
        boolean fallbackAllowed = isFallbackAllowed();

        boolean fallbackTriggered = false;
        String fallbackReason = null;

        if ("DIDIT".equals(mode)) {
            try {
                Map<String, Object> didit = createDiditSession(userId);
                upsertKycRecord(userId, "DIDIT", "SESSION_CREATED", (String) didit.get("sessionId"), null, null, null, null, null);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("mode", "DIDIT");
                response.put("status", "SESSION_CREATED");
                response.put("sessionId", didit.get("sessionId"));
                response.put("verificationUrl", didit.get("verificationUrl"));
                return response;
            } catch (Exception e) {
                if (!fallbackAllowed) {
                    throw new ApiException(502, "Failed to create Didit session");
                }
                fallbackTriggered = true;
                fallbackReason = "Didit unreachable, falling back to local KYC";
            }
        }

        upsertKycRecord(userId, "LOCAL", "NOT_STARTED", null, null, null, null, null, null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", "LOCAL");
        response.put("status", "NOT_STARTED");
        response.put("uploadEndpoint", "/api/kyc/local/upload");
        response.put("requiredFields", List.of("idFront", "idBack", "face"));
        if (fallbackTriggered) {
            response.put("fallback", true);
            response.put("reason", fallbackReason);
        }
        return response;
    }

    public Map<String, Object> getStatus(String userId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kyc_records WHERE user_id=?")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Map.of("status", "NOT_STARTED", "mode", "LOCAL");
            }

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("id", rs.getString("id"));
            status.put("mode", rs.getString("mode"));
            status.put("status", rs.getString("status"));
            status.put("reviewedAt", rs.getString("reviewed_at"));
            status.put("updatedAt", rs.getString("updated_at"));
            return status;
        }
    }

    public void uploadLocalKyc(String userId, String idFrontPath, String idBackPath, String facePath) throws Exception {
        upsertKycRecord(userId, "LOCAL", "UNDER_REVIEW", null, null, idFrontPath, idBackPath, facePath, null);
    }

    public Map<String, Object> handleDiditWebhook(
        String rawBody,
        String signatureHeader,
        String signatureV2Header,
        String signatureSimpleHeader,
        String timestampHeader
    ) throws Exception {
        Map<String, Object> payload = JSON.readValue(rawBody, new TypeReference<>() {});
        verifyDiditSignature(rawBody, payload, signatureHeader, signatureV2Header, signatureSimpleHeader, timestampHeader);

        String sessionId = firstNonBlank(
            asString(payload.get("sessionId")),
            asString(payload.get("session_id")),
            nestedString(payload, "data", "sessionId"),
            nestedString(payload, "data", "session_id")
        );
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(400, "Didit webhook missing session id");
        }

        String providerStatus = firstNonBlank(
            asString(payload.get("status")),
            nestedString(payload, "data", "status"),
            "UNKNOWN"
        );

        String mapped = mapDiditStatus(providerStatus);
        String providerResultId = firstNonBlank(
            asString(payload.get("resultId")),
            asString(payload.get("result_id")),
            nestedString(payload, "data", "resultId"),
            nestedString(payload, "data", "result_id")
        );

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            PreparedStatement find = conn.prepareStatement(
                "SELECT id FROM kyc_records WHERE provider_session_id=?"
            );
            find.setString(1, sessionId);
            ResultSet rs = find.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "KYC session not found");
            }

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE kyc_records SET status=?, provider_result_id=?, updated_at=? WHERE provider_session_id=?"
            );
            upd.setString(1, mapped);
            upd.setString(2, providerResultId);
            upd.setString(3, Instant.now().toString());
            upd.setString(4, sessionId);
            upd.executeUpdate();

            conn.commit();
        }

        return Map.of("sessionId", sessionId, "status", mapped);
    }

    public List<Map<String, Object>> listPendingForAdmin() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT k.id, k.user_id, k.mode, k.status, k.created_at, u.email, u.full_name " +
                 "FROM kyc_records k JOIN users u ON u.id = k.user_id WHERE k.status='UNDER_REVIEW' ORDER BY k.created_at ASC"
             )) {
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("id"));
                row.put("userId", rs.getString("user_id"));
                row.put("mode", rs.getString("mode"));
                row.put("status", rs.getString("status"));
                row.put("createdAt", rs.getString("created_at"));
                row.put("email", rs.getString("email"));
                row.put("fullName", rs.getString("full_name"));
                list.add(row);
            }
            return list;
        }
    }

    public void approve(String kycRecordId, String adminUserId) throws Exception {
        review(kycRecordId, adminUserId, "VERIFIED");
    }

    public void reject(String kycRecordId, String adminUserId) throws Exception {
        review(kycRecordId, adminUserId, "REJECTED");
    }

    private void review(String kycRecordId, String adminUserId, String targetStatus) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement upd = conn.prepareStatement(
                "UPDATE kyc_records SET status=?, reviewed_by=?, reviewed_at=?, updated_at=? WHERE id=?"
            );
            upd.setString(1, targetStatus);
            upd.setString(2, adminUserId);
            upd.setString(3, Instant.now().toString());
            upd.setString(4, Instant.now().toString());
            upd.setString(5, kycRecordId);
            int rows = upd.executeUpdate();
            if (rows == 0) {
                throw new ApiException(404, "KYC record not found");
            }
        }
    }

    private Map<String, Object> createDiditSession(String userId) throws Exception {
        String apiBaseUrl = System.getenv().getOrDefault("FLOUCNA_DIDIT_API_BASE_URL", "https://verification.didit.me");
        apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        String sessionUrl = apiBaseUrl + "/v3/session/";
        String apiKey = requiredEnv("FLOUCNA_DIDIT_API_KEY");
        String workflowId = requiredEnv("FLOUCNA_DIDIT_WORKFLOW_ID");
        String callbackUrl = System.getenv().getOrDefault("FLOUCNA_DIDIT_CALLBACK_URL", "http://localhost:3000/kyc");

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("workflow_id", workflowId);
        reqBody.put("vendor_data", userId);
        reqBody.put("callback", callbackUrl);
        reqBody.put("callback_method", "both");

        HttpRequest req = HttpRequest.newBuilder(URI.create(sessionUrl))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(reqBody)))
            .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new ApiException(502, "Didit provider returned " + res.statusCode());
        }

        Map<String, Object> body = JSON.readValue(res.body(), new TypeReference<>() {});
        String sessionId = firstNonBlank(
            asString(body.get("sessionId")),
            asString(body.get("session_id")),
            nestedString(body, "data", "sessionId"),
            nestedString(body, "data", "session_id")
        );
        String verificationUrl = firstNonBlank(
            asString(body.get("verificationUrl")),
            asString(body.get("verification_url")),
            asString(body.get("url")),
            nestedString(body, "data", "verificationUrl"),
            nestedString(body, "data", "verification_url"),
            nestedString(body, "data", "url")
        );

        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(502, "Didit response missing session id");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("verificationUrl", verificationUrl);
        return result;
    }

    private void upsertKycRecord(
        String userId,
        String mode,
        String status,
        String providerSessionId,
        String providerResultId,
        String idFrontPath,
        String idBackPath,
        String facePath,
        String reviewedBy
    ) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement find = conn.prepareStatement("SELECT id FROM kyc_records WHERE user_id=?");
            find.setString(1, userId);
            ResultSet rs = find.executeQuery();

            if (rs.next()) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE kyc_records SET mode=?, status=?, provider_session_id=?, provider_result_id=?, " +
                    "id_front_path=?, id_back_path=?, face_path=?, reviewed_by=?, reviewed_at=NULL, updated_at=? WHERE user_id=?"
                );
                upd.setString(1, mode);
                upd.setString(2, status);
                upd.setString(3, providerSessionId);
                upd.setString(4, providerResultId);
                upd.setString(5, idFrontPath);
                upd.setString(6, idBackPath);
                upd.setString(7, facePath);
                upd.setString(8, reviewedBy);
                upd.setString(9, Instant.now().toString());
                upd.setString(10, userId);
                upd.executeUpdate();
                return;
            }

            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO kyc_records (id, user_id, mode, provider_session_id, provider_result_id, " +
                "id_front_path, id_back_path, face_path, status, reviewed_by, reviewed_at, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            );
            String now = Instant.now().toString();
            ins.setString(1, UUID.randomUUID().toString());
            ins.setString(2, userId);
            ins.setString(3, mode);
            ins.setString(4, providerSessionId);
            ins.setString(5, providerResultId);
            ins.setString(6, idFrontPath);
            ins.setString(7, idBackPath);
            ins.setString(8, facePath);
            ins.setString(9, status);
            ins.setString(10, reviewedBy);
            ins.setString(11, null);
            ins.setString(12, now);
            ins.setString(13, now);
            ins.executeUpdate();
        }
    }

    private void verifyDiditSignature(
        String rawBody,
        Map<String, Object> payload,
        String signatureHeader,
        String signatureV2Header,
        String signatureSimpleHeader,
        String timestampHeader
    ) throws Exception {
        String secret = System.getenv("FLOUCNA_DIDIT_WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new ApiException(500, "Didit webhook secret is not configured");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new ApiException(401, "Missing Didit webhook timestamp");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(401, "Invalid Didit webhook timestamp");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > ChronoUnit.MINUTES.getDuration().getSeconds() * 5) {
            throw new ApiException(401, "Stale Didit webhook timestamp");
        }

        String rawSignature = normalizeSignature(signatureHeader);
        String v2Signature = normalizeSignature(signatureV2Header);
        String simpleSignature = normalizeSignature(signatureSimpleHeader);

        if ((rawSignature == null || rawSignature.isBlank())
            && (v2Signature == null || v2Signature.isBlank())
            && (simpleSignature == null || simpleSignature.isBlank())) {
            throw new ApiException(401, "Missing Didit webhook signature");
        }

        boolean verified = false;
        if (v2Signature != null && !v2Signature.isBlank()) {
            verified = verifyDiditSignatureV2(payload, v2Signature, secret);
        }
        if (!verified && simpleSignature != null && !simpleSignature.isBlank()) {
            verified = verifyDiditSignatureSimple(payload, simpleSignature, secret);
        }
        if (!verified && rawSignature != null && !rawSignature.isBlank()) {
            verified = verifyDiditSignatureRaw(rawBody, rawSignature, secret);
        }

        if (!verified) {
            throw new ApiException(401, "Invalid Didit webhook signature");
        }
    }

    private boolean verifyDiditSignatureRaw(String rawBody, String signatureHeader, String secret) throws Exception {
        String expected = hmacSha256Hex(rawBody, secret);
        return signaturesEqual(expected, signatureHeader);
    }

    private boolean verifyDiditSignatureV2(Map<String, Object> payload, String signatureHeader, String secret) throws Exception {
        Object normalized = normalizeDiditPayload(payload);
        String canonicalJson = JSON.writeValueAsString(normalized);
        String expected = hmacSha256Hex(canonicalJson, secret);
        return signaturesEqual(expected, signatureHeader);
    }

    private boolean verifyDiditSignatureSimple(Map<String, Object> payload, String signatureHeader, String secret) throws Exception {
        String canonicalString = String.join(
            ":",
            stringOrEmpty(payload.get("timestamp")),
            stringOrEmpty(payload.get("session_id")),
            stringOrEmpty(payload.get("status")),
            stringOrEmpty(payload.get("webhook_type"))
        );
        String expected = hmacSha256Hex(canonicalString, secret);
        return signaturesEqual(expected, signatureHeader);
    }

    private String hmacSha256Hex(String input, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private boolean signaturesEqual(String expected, String provided) {
        String normalizedExpected = normalizeSignature(expected);
        String normalizedProvided = normalizeSignature(provided);
        if (normalizedExpected == null || normalizedProvided == null) {
            return false;
        }
        return MessageDigest.isEqual(
            normalizedExpected.getBytes(StandardCharsets.UTF_8),
            normalizedProvided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeSignature(String signature) {
        if (signature == null) {
            return null;
        }
        String normalized = signature.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256=")) {
            normalized = normalized.substring("sha256=".length());
        }
        return normalized;
    }

    private String stringOrEmpty(Object value) {
        String text = asString(value);
        return text == null ? "" : text;
    }

    private Object normalizeDiditPayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                normalized.put(key, normalizeDiditPayload(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizeDiditPayload(item));
            }
            return normalized;
        }
        if (value instanceof Double d && Double.isFinite(d) && d == Math.rint(d)) {
            long cast = (long) d.doubleValue();
            if ((double) cast == d) {
                return cast;
            }
        }
        if (value instanceof Float f && Float.isFinite(f) && f == Math.rint(f)) {
            long cast = (long) f.floatValue();
            if ((float) cast == f) {
                return cast;
            }
        }
        return value;
    }

    private String mapDiditStatus(String providerStatus) {
        String status = providerStatus == null ? "" : providerStatus.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "VERIFIED", "APPROVED", "PASSED", "SUCCESS" -> "VERIFIED";
            case "REJECTED", "DECLINED", "FAILED" -> "REJECTED";
            case "EXPIRED" -> "EXPIRED";
            default -> "UNDER_REVIEW";
        };
    }

    private String configuredKycMode() {
        return System.getenv().getOrDefault("FLOUCNA_KYC_MODE", "LOCAL").trim().toUpperCase(Locale.ROOT);
    }

    private boolean isFallbackAllowed() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("FLOUCNA_KYC_ALLOW_FALLBACK", "true"));
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new ApiException(500, "Missing required environment variable: " + key);
        }
        return value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private String nestedString(Map<String, Object> payload, String parentKey, String childKey) {
        Object parent = payload.get(parentKey);
        if (parent instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(childKey);
            return asString(value);
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
