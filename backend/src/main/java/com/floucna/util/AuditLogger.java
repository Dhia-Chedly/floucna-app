package com.floucna.util;

import com.floucna.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class AuditLogger {

    public static void log(String actorId, String action, String targetType, String targetId, String result) {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_logs (id, actor_id, action, target_type, target_id, result, created_at) VALUES (?,?,?,?,?,?,?)"
             )) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, actorId);
            ps.setString(3, action);
            ps.setString(4, targetType);
            ps.setString(5, targetId);
            ps.setString(6, result);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-fatal: do not fail primary business action for audit logging failures.
            System.err.println("Audit log failed: " + e.getMessage());
        }
    }
}
