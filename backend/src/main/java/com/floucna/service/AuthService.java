package com.floucna.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.floucna.db.Database;
import com.floucna.util.JwtUtil;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class AuthService {

    public record RegisterRequest(String email, String password, String fullName, String role) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(String token, String userId, String role, String fullName) {}

    public AuthResponse register(RegisterRequest req) throws Exception {
        String id = UUID.randomUUID().toString();
        String hash = BCrypt.withDefaults().hashToString(12, req.password().toCharArray());
        String role = (req.role() != null && req.role().equals("LENDER")) ? "LENDER" : "BORROWER";

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, email, password_hash, full_name, role, is_verified, created_at) VALUES (?,?,?,?,?,0,?)"
             )) {
            ps.setString(1, id);
            ps.setString(2, req.email());
            ps.setString(3, hash);
            ps.setString(4, req.fullName());
            ps.setString(5, role);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) throw new Exception("Email already registered");
            throw e;
        }

        String token = JwtUtil.generate(id, role);
        return new AuthResponse(token, id, role, req.fullName());
    }

    public AuthResponse login(LoginRequest req) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT id, password_hash, role, full_name, is_verified FROM users WHERE email = ?"
             )) {
            ps.setString(1, req.email());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new Exception("Invalid credentials");

            String hash = rs.getString("password_hash");
            BCrypt.Result result = BCrypt.verifyer().verify(req.password().toCharArray(), hash);
            if (!result.verified) throw new Exception("Invalid credentials");

            String id = rs.getString("id");
            String role = rs.getString("role");
            String fullName = rs.getString("full_name");
            String token = JwtUtil.generate(id, role);
            return new AuthResponse(token, id, role, fullName);
        }
    }

    public Map<String, Object> getProfile(String userId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.email, u.full_name, u.role, u.is_verified, u.created_at, " +
                "fs.score, fs.risk_ceiling FROM users u " +
                "LEFT JOIN floucna_scores fs ON fs.user_id = u.id WHERE u.id = ?"
             )) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new Exception("User not found");
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", rs.getString("id"));
            profile.put("email", rs.getString("email"));
            profile.put("fullName", rs.getString("full_name"));
            profile.put("role", rs.getString("role"));
            profile.put("isVerified", rs.getInt("is_verified") == 1);
            profile.put("createdAt", rs.getString("created_at"));
            profile.put("floucnaScore", rs.getObject("score"));
            profile.put("riskCeiling", rs.getObject("risk_ceiling"));
            return profile;
        }
    }
}
