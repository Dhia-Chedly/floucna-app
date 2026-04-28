package com.floucna.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.floucna.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class SeedService {

    public static void seed() {
        if (!isSeedEnabled()) {
            System.out.println("Demo seed disabled by FLOUCNA_ENABLE_DEMO_SEED");
            return;
        }
        try (Connection conn = Database.connect()) {
            createUser(conn, "borrower@floucna.dz", "Demo1234!", "Yacine Boumediene", "BORROWER", true);
            createUser(conn, "lender@floucna.dz", "Demo1234!", "Amira Hadj", "LENDER", true);
            createUser(conn, "admin@floucna.dz", "Admin1234!", "Admin Floucna", "ADMIN", true);
            System.out.println("Demo accounts ready");
        } catch (SQLException e) {
            System.err.println("Seed failed: " + e.getMessage());
        }
    }

    private static void createUser(Connection conn, String email, String password,
                                   String fullName, String role, boolean verified) throws SQLException {
        PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE email=?");
        check.setString(1, email);
        ResultSet existing = check.executeQuery();
        if (existing.next()) {
            return;
        }

        String id = UUID.randomUUID().toString();
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        String now = Instant.now().toString();

        PreparedStatement u = conn.prepareStatement(
            "INSERT INTO users (id, email, password_hash, full_name, role, is_verified, created_at) VALUES (?,?,?,?,?,?,?)"
        );
        u.setString(1, id);
        u.setString(2, email);
        u.setString(3, hash);
        u.setString(4, fullName);
        u.setString(5, role);
        u.setInt(6, verified ? 1 : 0);
        u.setString(7, now);
        u.executeUpdate();

        PreparedStatement k = conn.prepareStatement(
            "INSERT INTO kyc_data (id, user_id, status, verified_at) VALUES (?,?,'VERIFIED',?)"
        );
        k.setString(1, UUID.randomUUID().toString());
        k.setString(2, id);
        k.setString(3, now);
        k.executeUpdate();

        int score = role.equals("BORROWER") ? 45 : role.equals("LENDER") ? 72 : 100;
        double ceiling = role.equals("BORROWER") ? 25000.0 : role.equals("LENDER") ? 50000.0 : 999999.0;

        PreparedStatement s = conn.prepareStatement(
            "INSERT INTO floucna_scores (user_id, score, risk_ceiling, last_updated) VALUES (?,?,?,?)"
        );
        s.setString(1, id);
        s.setInt(2, score);
        s.setDouble(3, ceiling);
        s.setString(4, now);
        s.executeUpdate();

        System.out.println("  -> Created " + role + ": " + email);
    }

    private static boolean isSeedEnabled() {
        String value = System.getenv("FLOUCNA_ENABLE_DEMO_SEED");
        if (value == null || value.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(value);
    }
}
