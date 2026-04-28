package com.floucna.service;

import com.floucna.db.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ComplianceService {

    public Map<String, Object> verifyContract(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE loan_id=?")) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new Exception("Contract not found for loan " + loanId);
            }

            String pdfPath = rs.getString("pdf_path");
            String storedSig = rs.getString("signature_data");
            String tsaToken = rs.getString("tsa_token");

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("loanId", loanId);
            report.put("verifiedAt", Instant.now().toString());

            boolean pdfExists = Files.exists(Path.of(pdfPath));
            report.put("documentFound", pdfExists);

            boolean signaturePresent = storedSig != null && !storedSig.isBlank();
            String currentHash = "";
            String storedHash = extractStoredHash(storedSig);
            boolean hashMatch = false;

            if (pdfExists) {
                byte[] bytes = Files.readAllBytes(Path.of(pdfPath));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                currentHash = HexFormat.of().formatHex(digest.digest(bytes));
                hashMatch = storedHash != null && storedHash.equalsIgnoreCase(currentHash);
            }

            report.put("signaturePresent", signaturePresent);
            report.put("storedHash", storedHash);
            report.put("documentHash", currentHash);
            report.put("hashMatch", hashMatch);

            boolean timestampValid = tsaToken != null && tsaToken.startsWith("TSA:");
            report.put("timestampValid", timestampValid);
            report.put("tsaToken", tsaToken);

            boolean overallValid = pdfExists && signaturePresent && hashMatch && timestampValid;
            report.put("overallValid", overallValid);
            report.put("verdict", overallValid
                ? "VALID - Document integrity confirmed"
                : "INVALID - Verification failed");

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE contracts SET is_verified=?, verify_report=? WHERE loan_id=?"
            );
            upd.setInt(1, overallValid ? 1 : 0);
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

    private String extractStoredHash(String storedSig) {
        if (storedSig == null || storedSig.isBlank()) {
            return null;
        }
        String prefix = "SHA256:";
        int hashStart = storedSig.indexOf(prefix);
        int hashEnd = storedSig.indexOf(";SIG:");
        if (hashStart != 0 || hashEnd <= prefix.length()) {
            return null;
        }
        return storedSig.substring(prefix.length(), hashEnd);
    }
}
