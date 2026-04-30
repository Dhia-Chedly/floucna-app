package com.floucna.api;

import com.floucna.db.Database;
import com.floucna.util.ApiErrorHandler;
import com.floucna.util.AuthGuard;
import io.javalin.Javalin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdminController {

    public static void register(Javalin app) {
        app.get("/api/admin/stats", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(loadStats());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });

        app.get("/api/admin/users", ctx -> {
            try {
                AuthGuard.requireRole(ctx, "ADMIN");
                ctx.json(loadUsers());
            } catch (Exception e) {
                ApiErrorHandler.handle(ctx, e);
            }
        });
    }

    private static Map<String, Object> loadStats() throws Exception {
        try (Connection conn = Database.connect()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalUsers", count(conn, "SELECT COUNT(*) FROM users"));
            stats.put("borrowers", count(conn, "SELECT COUNT(*) FROM users WHERE role='BORROWER'"));
            stats.put("admins", count(conn, "SELECT COUNT(*) FROM users WHERE role='ADMIN'"));
            stats.put("kycVerified", count(conn, "SELECT COUNT(*) FROM kyc_records WHERE status='VERIFIED'"));
            stats.put("submittedLoans", count(conn, "SELECT COUNT(*) FROM loan_requests WHERE status='SUBMITTED'"));
            stats.put("approvedLoans", count(conn, "SELECT COUNT(*) FROM loan_requests WHERE status='APPROVED'"));
            stats.put("rejectedLoans", count(conn, "SELECT COUNT(*) FROM loan_requests WHERE status='REJECTED'"));
            stats.put("contractsGenerated", count(conn, "SELECT COUNT(*) FROM contracts"));
            stats.put("compliancePass", count(conn, "SELECT COUNT(*) FROM contracts WHERE verification_result='PASS'"));
            return stats;
        }
    }

    private static List<Map<String, Object>> loadUsers() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT u.id, u.provider_subject, u.email, u.full_name, u.role, u.created_at, k.status AS kyc_status " +
                "FROM users u LEFT JOIN kyc_records k ON k.user_id = u.id ORDER BY u.created_at DESC"
             )) {
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> users = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("id"));
                row.put("providerSubject", rs.getString("provider_subject"));
                row.put("email", rs.getString("email"));
                row.put("fullName", rs.getString("full_name"));
                row.put("role", rs.getString("role"));
                row.put("kycStatus", rs.getString("kyc_status") == null ? "NOT_STARTED" : rs.getString("kyc_status"));
                row.put("createdAt", rs.getString("created_at"));
                users.add(row);
            }
            return users;
        }
    }

    private static long count(Connection conn, String sql) throws Exception {
        ResultSet rs = conn.createStatement().executeQuery(sql);
        return rs.next() ? rs.getLong(1) : 0L;
    }
}
