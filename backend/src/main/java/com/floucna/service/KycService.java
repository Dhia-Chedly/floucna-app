package com.floucna.service;

import com.floucna.db.Database;
import com.floucna.util.ApiException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KycService {

    public void submitKyc(String userId, String idFrontPath, String idBackPath, String selfiePath) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM kyc_data WHERE user_id = ?");
            check.setString(1, userId);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE kyc_data SET id_front_path=?, id_back_path=?, selfie_path=?, status='PENDING', verified_at=NULL WHERE user_id=?"
                );
                upd.setString(1, idFrontPath);
                upd.setString(2, idBackPath);
                upd.setString(3, selfiePath);
                upd.setString(4, userId);
                upd.executeUpdate();
            } else {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO kyc_data (id, user_id, id_front_path, id_back_path, selfie_path, status) VALUES (?,?,?,?,?,'PENDING')"
                );
                ins.setString(1, UUID.randomUUID().toString());
                ins.setString(2, userId);
                ins.setString(3, idFrontPath);
                ins.setString(4, idBackPath);
                ins.setString(5, selfiePath);
                ins.executeUpdate();
            }
        }
    }

    public Map<String, Object> getStatus(String userId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM kyc_data WHERE user_id = ?")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            Map<String, Object> result = new LinkedHashMap<>();
            if (!rs.next()) {
                result.put("status", "NOT_SUBMITTED");
                return result;
            }
            result.put("status", rs.getString("status"));
            result.put("verifiedAt", rs.getString("verified_at"));
            return result;
        }
    }

    public void approveKyc(String kycUserId, boolean approve) throws Exception {
        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);
            String newStatus = approve ? "VERIFIED" : "REJECTED";
            String verifiedAt = approve ? Instant.now().toString() : null;

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE kyc_data SET status=?, verified_at=? WHERE user_id=?"
            );
            upd.setString(1, newStatus);
            upd.setString(2, verifiedAt);
            upd.setString(3, kycUserId);
            int updatedRows = upd.executeUpdate();
            if (updatedRows == 0) {
                throw new ApiException(404, "KYC record not found");
            }

            PreparedStatement userUpd = conn.prepareStatement(
                "UPDATE users SET is_verified=? WHERE id=?"
            );
            userUpd.setInt(1, approve ? 1 : 0);
            userUpd.setString(2, kycUserId);
            userUpd.executeUpdate();

            if (approve) {
                PreparedStatement scoreCheck = conn.prepareStatement(
                    "SELECT user_id FROM floucna_scores WHERE user_id=?"
                );
                scoreCheck.setString(1, kycUserId);
                if (!scoreCheck.executeQuery().next()) {
                    PreparedStatement scoreIns = conn.prepareStatement(
                        "INSERT INTO floucna_scores (user_id, score, risk_ceiling, last_updated) VALUES (?,0,500.0,?)"
                    );
                    scoreIns.setString(1, kycUserId);
                    scoreIns.setString(2, Instant.now().toString());
                    scoreIns.executeUpdate();
                }
            }
            conn.commit();
        }
    }

    public List<Map<String, Object>> listPending() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT k.*, u.email, u.full_name FROM kyc_data k JOIN users u ON u.id=k.user_id WHERE k.status='PENDING'"
             )) {
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", rs.getString("user_id"));
                row.put("email", rs.getString("email"));
                row.put("fullName", rs.getString("full_name"));
                row.put("status", rs.getString("status"));
                list.add(row);
            }
            return list;
        }
    }
}
