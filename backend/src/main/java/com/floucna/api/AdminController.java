package com.floucna.api;

import com.floucna.db.Database;
import com.floucna.util.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.sql.*;
import java.util.*;

public class AdminController {

    public static void register(Javalin app) {
        app.get("/api/admin/users", AdminController::listUsers);
        app.get("/api/admin/stats", AdminController::getStats);
    }

    private static void listUsers(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "ADMIN");
            try (Connection conn = Database.connect();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.id, u.email, u.full_name, u.role, u.is_verified, u.created_at, fs.score, fs.risk_ceiling " +
                    "FROM users u LEFT JOIN floucna_scores fs ON fs.user_id=u.id ORDER BY u.created_at DESC"
                 )) {
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("email", rs.getString("email"));
                    row.put("fullName", rs.getString("full_name"));
                    row.put("role", rs.getString("role"));
                    row.put("isVerified", rs.getInt("is_verified") == 1);
                    row.put("createdAt", rs.getString("created_at"));
                    row.put("score", rs.getObject("score"));
                    row.put("riskCeiling", rs.getObject("risk_ceiling"));
                    list.add(row);
                }
                ctx.json(list);
            }
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static void getStats(Context ctx) {
        try {
            AuthGuard.requireRole(ctx, "ADMIN");
            try (Connection conn = Database.connect()) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("totalUsers", queryCount(conn, "SELECT COUNT(*) FROM users"));
                stats.put("verifiedUsers", queryCount(conn, "SELECT COUNT(*) FROM users WHERE is_verified=1"));
                stats.put("pendingKyc", queryCount(conn, "SELECT COUNT(*) FROM kyc_data WHERE status='PENDING'"));
                stats.put("totalLoans", queryCount(conn, "SELECT COUNT(*) FROM loans"));
                stats.put("activeLoans", queryCount(conn, "SELECT COUNT(*) FROM loans WHERE status='ACTIVE'"));
                stats.put("fundedLoans", queryCount(conn, "SELECT COUNT(*) FROM loans WHERE status='FUNDED'"));
                stats.put("totalContracts", queryCount(conn, "SELECT COUNT(*) FROM contracts"));
                ctx.json(stats);
            }
        } catch (Exception e) {
            ApiErrorHandler.handle(ctx, e);
        }
    }

    private static long queryCount(Connection conn, String sql) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery(sql);
        return rs.next() ? rs.getLong(1) : 0;
    }
}
