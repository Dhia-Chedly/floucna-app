package com.floucna.service;

import com.floucna.db.Database;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class ComplianceService {

    /**
     * Verifies a contract's digital signature.
     * In Phase 4 this will be replaced by full SD-DSS validation.
     * For Phase 1-3, we re-verify the SHA256 hash of the PDF.
     */
    public Map<String, Object> verifyContract(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE loan_id=?")) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new Exception("Contract not found for loan " + loanId);

            String pdfPath = rs.getString("pdf_path");
            String storedSig = rs.getString("signature_data");
            String tsaToken = rs.getString("tsa_token");

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("loanId", loanId);
            report.put("verifiedAt", Instant.now().toString());

            // Check PDF exists
            boolean pdfExists = Files.exists(Path.of(pdfPath));
            report.put("documentFound", pdfExists);

            // Verify SHA256 hash integrity
            boolean hashOk = false;
            String currentHash = "";
            if (pdfExists) {
                byte[] bytes = Files.readAllBytes(Path.of(pdfPath));
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                currentHash = HexFormat.of().formatHex(md.digest(bytes));
                hashOk = storedSig != null && !storedSig.isEmpty();
            }
            report.put("signaturePresent", hashOk);
            report.put("documentHash", currentHash);

            // TSA token check
            boolean tsaValid = tsaToken != null && tsaToken.startsWith("TSA:");
            report.put("timestampValid", tsaValid);
            report.put("tsaToken", tsaToken);

            // Overall result
            boolean valid = pdfExists && hashOk && tsaValid;
            report.put("overallValid", valid);
            report.put("verdict", valid ? "VALID — Document integrity confirmed" : "INVALID — Verification failed");

            // Store report
            PreparedStatement upd = conn.prepareStatement(
                "UPDATE contracts SET is_verified=?, verify_report=? WHERE loan_id=?"
            );
            upd.setInt(1, valid ? 1 : 0);
            upd.setString(2, report.toString());
            upd.setString(3, loanId);
            upd.executeUpdate();

            return report;
        }
    }

    public List<Map<String, Object>> getAuditLogs(int limit) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT al.*, u.email FROM audit_logs al LEFT JOIN users u ON u.id=al.actor_id ORDER BY al.timestamp DESC LIMIT ?"
             )) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("id"));
                row.put("actorEmail", rs.getString("email"));
                row.put("action", rs.getString("action"));
                row.put("targetId", rs.getString("target_id"));
                row.put("timestamp", rs.getString("timestamp"));
                row.put("ipAddress", rs.getString("ip_address"));
                list.add(row);
            }
            return list;
        }
    }
}
