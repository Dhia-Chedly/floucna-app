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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AuthService {

    public record ExternalIdentity(String providerSubject, String email, String fullName, List<String> roles) {}
    public record LocalUser(String id, String role, String email, String fullName) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String MODE = System.getenv().getOrDefault("FLOUCNA_AUTH_MODE", "LOCAL").toUpperCase(Locale.ROOT);

    public ExternalIdentity resolveExternalIdentity(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new ApiException(401, "Missing bearer token");
        }

        try {
            return switch (MODE) {
                case "KEYCLOAK" -> resolveFromKeycloakIntrospection(bearerToken);
                case "LOCAL" -> resolveFromLocalToken(bearerToken);
                default -> throw new ApiException(500, "Unsupported auth mode: " + MODE);
            };
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(401, "Invalid or expired token");
        }
    }

    public LocalUser syncLocalUser(ExternalIdentity identity) {
        try (Connection conn = Database.connect()) {
            PreparedStatement find = conn.prepareStatement(
                "SELECT id, role, email, full_name FROM users WHERE provider_subject = ?"
            );
            find.setString(1, identity.providerSubject());
            ResultSet rs = find.executeQuery();

            String tokenRole = normalizeRole(identity.roles());
            String email = normalizeEmail(identity.email(), identity.providerSubject());
            String fullName = normalizeName(identity.fullName(), email);

            if (rs.next()) {
                String userId = rs.getString("id");
                String currentRole = rs.getString("role");
                String role = "ADMIN".equals(tokenRole) ? "ADMIN" : currentRole;

                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET email=?, full_name=?, role=? WHERE id=?"
                );
                upd.setString(1, email);
                upd.setString(2, fullName);
                upd.setString(3, role);
                upd.setString(4, userId);
                upd.executeUpdate();

                return new LocalUser(userId, role, email, fullName);
            }

            String userId = UUID.randomUUID().toString();
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users (id, provider_subject, email, full_name, role, created_at) VALUES (?,?,?,?,?,?)"
            );
            ins.setString(1, userId);
            ins.setString(2, identity.providerSubject());
            ins.setString(3, email);
            ins.setString(4, fullName);
            ins.setString(5, tokenRole);
            ins.setString(6, Instant.now().toString());
            ins.executeUpdate();

            return new LocalUser(userId, tokenRole, email, fullName);
        } catch (Exception e) {
            throw new ApiException(500, "Failed to sync user profile");
        }
    }

    public Map<String, Object> getProfile(String localUserId) {
        try (Connection conn = Database.connect()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.email, u.full_name, u.role, u.created_at, k.status AS kyc_status " +
                "FROM users u LEFT JOIN kyc_records k ON k.user_id = u.id WHERE u.id = ?"
            );
            ps.setString(1, localUserId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "User not found");
            }

            String kycStatus = rs.getString("kyc_status");
            if (kycStatus == null || kycStatus.isBlank()) {
                kycStatus = "NOT_STARTED";
            }

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", rs.getString("id"));
            profile.put("email", rs.getString("email"));
            profile.put("fullName", rs.getString("full_name"));
            profile.put("role", rs.getString("role"));
            profile.put("createdAt", rs.getString("created_at"));
            profile.put("kycStatus", kycStatus);
            profile.put("isVerified", "VERIFIED".equals(kycStatus));
            return profile;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "Failed to load profile");
        }
    }

    private ExternalIdentity resolveFromKeycloakIntrospection(String token) throws Exception {
        String introspectionUrl = requiredEnv("FLOUCNA_KEYCLOAK_INTROSPECTION_URL");
        String clientId = requiredEnv("FLOUCNA_KEYCLOAK_CLIENT_ID");
        String clientSecret = requiredEnv("FLOUCNA_KEYCLOAK_CLIENT_SECRET");

        String body = "token=" + urlEncode(token) +
            "&client_id=" + urlEncode(clientId) +
            "&client_secret=" + urlEncode(clientSecret);

        HttpRequest req = HttpRequest.newBuilder(URI.create(introspectionUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new ApiException(401, "Token introspection failed");
        }

        Map<String, Object> payload = JSON.readValue(res.body(), new TypeReference<>() {});
        Object active = payload.get("active");
        if (!(active instanceof Boolean) || !((Boolean) active)) {
            throw new ApiException(401, "Token is not active");
        }

        String subject = asString(payload.get("sub"));
        String email = asString(payload.get("email"));
        String name = firstNonBlank(
            asString(payload.get("name")),
            asString(payload.get("preferred_username")),
            email
        );

        List<String> roles = extractRoles(payload);
        if (subject == null || subject.isBlank()) {
            throw new ApiException(401, "Token subject is missing");
        }
        return new ExternalIdentity(subject, email, name, roles);
    }

    private ExternalIdentity resolveFromLocalToken(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new ApiException(401, "Malformed local token");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> payload = JSON.readValue(payloadJson, new TypeReference<>() {});

        String subject = asString(payload.get("sub"));
        if (subject == null || subject.isBlank()) {
            throw new ApiException(401, "Token subject is missing");
        }

        String email = asString(payload.get("email"));
        String name = firstNonBlank(
            asString(payload.get("name")),
            asString(payload.get("preferred_username")),
            email
        );

        List<String> roles = extractRoles(payload);
        return new ExternalIdentity(subject, email, name, roles);
    }

    private String normalizeRole(List<String> roles) {
        if (roles != null) {
            for (String role : roles) {
                if ("ADMIN".equalsIgnoreCase(role)) {
                    return "ADMIN";
                }
            }
        }
        return "BORROWER";
    }

    private String normalizeEmail(String email, String subject) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        return subject + "@local.floucna";
    }

    private String normalizeName(String fullName, String fallbackEmail) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return fallbackEmail;
    }

    private List<String> extractRoles(Map<String, Object> payload) {
        List<String> roles = new ArrayList<>();

        Object role = payload.get("role");
        if (role instanceof String roleString && !roleString.isBlank()) {
            roles.add(roleString);
        }

        Object topRoles = payload.get("roles");
        if (topRoles instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    roles.add(String.valueOf(item));
                }
            }
        }

        Object realmAccess = payload.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            Object realmRoles = realmMap.get("roles");
            if (realmRoles instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        roles.add(String.valueOf(item));
                    }
                }
            }
        }

        return roles;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
