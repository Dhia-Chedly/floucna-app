package com.floucna.service;

import com.floucna.db.Database;
import java.sql.*;
import java.time.Instant;

public class ScoringEngine {

    public static void adjustScore(Connection conn, String userId, int delta) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE floucna_scores SET score = MAX(0, score + ?), " +
            "risk_ceiling = MAX(0, MIN(50000, risk_ceiling + ? * 50)), " +
            "last_updated = ? WHERE user_id = ?"
        );
        ps.setInt(1, delta);
        ps.setInt(2, delta);
        ps.setString(3, Instant.now().toString());
        ps.setString(4, userId);
        ps.executeUpdate();
    }

    public static int getScore(String userId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT score FROM floucna_scores WHERE user_id=?"
             )) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return 0;
            return rs.getInt("score");
        }
    }
}
