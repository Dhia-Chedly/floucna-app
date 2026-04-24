package com.floucna.util;

import com.floucna.db.Database;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public class AuditLogger {

    public static void log(String actorId, String action, String targetId, String ip) {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_logs (id, actor_id, action, target_id, timestamp, ip_address) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, actorId);
            ps.setString(3, action);
            ps.setString(4, targetId);
            ps.setString(5, Instant.now().toString());
            ps.setString(6, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-fatal: log but do not break request
            System.err.println("Audit log failed: " + e.getMessage());
        }
    }
}
