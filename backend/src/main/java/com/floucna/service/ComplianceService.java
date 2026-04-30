package com.floucna.service;

import com.floucna.db.Database;
import com.floucna.util.ApiException;
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

    public Map<String, Object> verify(String loanId, byte[] uploadedBytes, String uploadedFileName) throws Exception {
        ContractState contract = loadContractState(loanId);

        byte[] bytes = uploadedBytes;
        if (bytes == null) {
            String sourcePath = pickContractFilePath(contract);
            if (sourcePath == null) {
                throw new ApiException(404, "No contract file available for verification");
            }
            bytes = Files.readAllBytes(Path.of(sourcePath));
        }

        String uploadedHash = hash(bytes);
        String storedHash = contract.pdfHash();

        boolean signaturePresent = contract.signedPdfPath() != null && !contract.signedPdfPath().isBlank();
        boolean signatureValid = signaturePresent && storedHash != null && storedHash.equalsIgnoreCase(uploadedHash);
        boolean integrityUnchanged = signatureValid;
        boolean timestampPresent = contract.timestampedPdfPath() != null && !contract.timestampedPdfPath().isBlank();
        String certificateStatus = "SELF_SIGNED";

        boolean overallPass = signaturePresent && signatureValid && integrityUnchanged;
        String result = overallPass ? "PASS" : "FAIL";

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("loanId", loanId);
        report.put("uploadedFile", uploadedFileName);
        report.put("verifiedAt", Instant.now().toString());
        report.put("signaturePresence", signaturePresent ? "PRESENT" : "MISSING");
        report.put("signatureValidity", signatureValid ? "VALID" : "INVALID");
        report.put("documentIntegrity", integrityUnchanged ? "UNCHANGED" : "TAMPERED");
        report.put("timestamp", timestampPresent ? "PRESENT" : "MISSING");
        report.put("certificate", certificateStatus);
        report.put("storedHash", storedHash);
        report.put("uploadedHash", uploadedHash);
        report.put("overallResult", result);

        persistVerificationResult(loanId, result, report.toString(), overallPass);
        return report;
    }

    public List<Map<String, Object>> listReports() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT c.loan_id, c.verification_result, c.verification_report, c.verified_at, c.status, " +
                "lr.borrower_id, u.full_name AS borrower_name FROM contracts c " +
                "JOIN loan_requests lr ON lr.id = c.loan_id " +
                "JOIN users u ON u.id = lr.borrower_id " +
                "WHERE c.verification_result IS NOT NULL ORDER BY c.verified_at DESC"
             )) {
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> reports = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("loanId", rs.getString("loan_id"));
                row.put("borrowerId", rs.getString("borrower_id"));
                row.put("borrowerName", rs.getString("borrower_name"));
                row.put("verificationResult", rs.getString("verification_result"));
                row.put("verificationReport", rs.getString("verification_report"));
                row.put("verifiedAt", rs.getString("verified_at"));
                row.put("contractStatus", rs.getString("status"));
                reports.add(row);
            }
            return reports;
        }
    }

    public List<Map<String, Object>> getAuditLogs(int limit) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT a.*, u.email AS actor_email FROM audit_logs a " +
                 "LEFT JOIN users u ON u.id = a.actor_id ORDER BY a.created_at DESC LIMIT ?"
             )) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> logs = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("id"));
                row.put("actorEmail", rs.getString("actor_email"));
                row.put("action", rs.getString("action"));
                row.put("targetType", rs.getString("target_type"));
                row.put("targetId", rs.getString("target_id"));
                row.put("result", rs.getString("result"));
                row.put("createdAt", rs.getString("created_at"));
                logs.add(row);
            }
            return logs;
        }
    }

    private void persistVerificationResult(String loanId, String result, String report, boolean pass) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE contracts SET verification_result=?, verification_report=?, verified_at=?, status=? WHERE loan_id=?"
             )) {
            ps.setString(1, result);
            ps.setString(2, report);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, pass ? "VERIFIED" : "TIMESTAMPED");
            ps.setString(5, loanId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new ApiException(404, "Contract not found for verification");
            }
        }
    }

    private ContractState loadContractState(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT loan_id, unsigned_pdf_path, signed_pdf_path, timestamped_pdf_path, pdf_hash, status FROM contracts WHERE loan_id=?"
             )) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "Contract not found");
            }
            return new ContractState(
                rs.getString("loan_id"),
                rs.getString("unsigned_pdf_path"),
                rs.getString("signed_pdf_path"),
                rs.getString("timestamped_pdf_path"),
                rs.getString("pdf_hash"),
                rs.getString("status")
            );
        }
    }

    private String pickContractFilePath(ContractState contract) {
        if (contract.timestampedPdfPath() != null && !contract.timestampedPdfPath().isBlank()) {
            return contract.timestampedPdfPath();
        }
        if (contract.signedPdfPath() != null && !contract.signedPdfPath().isBlank()) {
            return contract.signedPdfPath();
        }
        if (contract.unsignedPdfPath() != null && !contract.unsignedPdfPath().isBlank()) {
            return contract.unsignedPdfPath();
        }
        return null;
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ContractState(
        String loanId,
        String unsignedPdfPath,
        String signedPdfPath,
        String timestampedPdfPath,
        String pdfHash,
        String status
    ) {}
}
