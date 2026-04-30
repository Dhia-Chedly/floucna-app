package com.floucna.service;

import com.floucna.db.Database;
import com.floucna.util.ApiException;
import com.floucna.util.InputValidator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class LoanService {

    public record LoanRequest(
        double amount,
        String currency,
        int durationDays,
        String purpose,
        Double declaredIncome,
        Double declaredExistingDebt
    ) {}

    public Map<String, Object> createLoan(String borrowerId, LoanRequest req) throws Exception {
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        double amount = InputValidator.requirePositiveAmount(req.amount(), "Amount", 100_000.0);
        String currency = normalizeCurrency(req.currency());
        int durationDays = InputValidator.requireIntRange(req.durationDays(), "Duration days", 7, 365);
        String purpose = InputValidator.requireName(req.purpose(), "Purpose", 500);

        Double declaredIncome = sanitizeNullableMoney(req.declaredIncome(), "Declared income");
        Double declaredExistingDebt = sanitizeNullableMoney(req.declaredExistingDebt(), "Declared existing debt");

        try (Connection conn = Database.connect()) {
            String kycStatus = getKycStatus(conn, borrowerId);
            if (!"VERIFIED".equals(kycStatus)) {
                throw new ApiException(400, "KYC must be VERIFIED before submitting a loan request");
            }

            ScoringEngine.ScoreResult score = ScoringEngine.calculateDemoScore(
                kycStatus,
                amount,
                durationDays,
                purpose,
                declaredIncome,
                declaredExistingDebt
            );

            String loanId = UUID.randomUUID().toString();
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO loan_requests (id, borrower_id, amount, currency, duration_days, purpose, declared_income, declared_existing_debt, " +
                "demo_score, score_label, status, created_at) VALUES (?,?,?,?,?,?,?,?,?,?, 'SUBMITTED', ?)"
            );
            ins.setString(1, loanId);
            ins.setString(2, borrowerId);
            ins.setDouble(3, amount);
            ins.setString(4, currency);
            ins.setInt(5, durationDays);
            ins.setString(6, purpose);
            if (declaredIncome == null) ins.setNull(7, java.sql.Types.REAL); else ins.setDouble(7, declaredIncome);
            if (declaredExistingDebt == null) ins.setNull(8, java.sql.Types.REAL); else ins.setDouble(8, declaredExistingDebt);
            ins.setInt(9, score.score());
            ins.setString(10, score.label());
            ins.setString(11, Instant.now().toString());
            ins.executeUpdate();

            return getLoanByIdForAdmin(conn, loanId);
        }
    }

    public List<Map<String, Object>> listMyLoans(String borrowerId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT lr.*, c.status AS contract_status FROM loan_requests lr " +
                "LEFT JOIN contracts c ON c.loan_id = lr.id WHERE lr.borrower_id=? ORDER BY lr.created_at DESC"
             )) {
            ps.setString(1, borrowerId);
            return mapResultSet(ps.executeQuery());
        }
    }

    public List<Map<String, Object>> listAllLoansForAdmin() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT lr.*, u.full_name AS borrower_name, u.email AS borrower_email, c.status AS contract_status " +
                "FROM loan_requests lr JOIN users u ON u.id = lr.borrower_id " +
                "LEFT JOIN contracts c ON c.loan_id = lr.id ORDER BY lr.created_at DESC"
             )) {
            return mapResultSet(ps.executeQuery());
        }
    }

    public Map<String, Object> approve(String loanId) throws Exception {
        return updateStatus(loanId, "APPROVED");
    }

    public Map<String, Object> reject(String loanId) throws Exception {
        return updateStatus(loanId, "REJECTED");
    }

    public Map<String, Object> getLoanForBorrowerOrAdmin(String loanId, String viewerId, String viewerRole) throws Exception {
        try (Connection conn = Database.connect()) {
            Map<String, Object> loan = getLoanByIdForAdmin(conn, loanId);
            String borrowerId = String.valueOf(loan.get("borrower_id"));
            boolean owner = borrowerId.equals(viewerId);
            boolean admin = "ADMIN".equals(viewerRole);
            if (!(owner || admin)) {
                throw new ApiException(403, "Forbidden");
            }
            return loan;
        }
    }

    private Map<String, Object> updateStatus(String loanId, String nextStatus) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement check = conn.prepareStatement("SELECT status FROM loan_requests WHERE id=?");
            check.setString(1, loanId);
            ResultSet rs = check.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "Loan request not found");
            }

            String currentStatus = rs.getString("status");
            if (!("SUBMITTED".equals(currentStatus) || "UNDER_REVIEW".equals(currentStatus))) {
                throw new ApiException(400, "Loan cannot be reviewed from current status: " + currentStatus);
            }

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE loan_requests SET status=?, approved_at=? WHERE id=?"
            );
            upd.setString(1, nextStatus);
            upd.setString(2, Instant.now().toString());
            upd.setString(3, loanId);
            upd.executeUpdate();

            return getLoanByIdForAdmin(conn, loanId);
        }
    }

    private String getKycStatus(Connection conn, String userId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT status FROM kyc_records WHERE user_id=?");
        ps.setString(1, userId);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            return "NOT_STARTED";
        }
        String status = rs.getString("status");
        return status == null ? "NOT_STARTED" : status;
    }

    private Map<String, Object> getLoanByIdForAdmin(Connection conn, String loanId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT lr.*, u.full_name AS borrower_name, u.email AS borrower_email, c.status AS contract_status " +
            "FROM loan_requests lr JOIN users u ON u.id = lr.borrower_id " +
            "LEFT JOIN contracts c ON c.loan_id = lr.id WHERE lr.id=?"
        );
        ps.setString(1, loanId);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            throw new ApiException(404, "Loan request not found");
        }
        return rowToMap(rs);
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(rowToMap(rs));
        }
        return rows;
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnName(i), rs.getObject(i));
        }
        return row;
    }

    private String normalizeCurrency(String currency) {
        String normalized = (currency == null || currency.isBlank()) ? "TND" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"TND".equals(normalized)) {
            throw new IllegalArgumentException("Only TND currency is supported");
        }
        return normalized;
    }

    private Double sanitizeNullableMoney(Double value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative number");
        }
        if (value > 1_000_000) {
            throw new IllegalArgumentException(fieldName + " exceeds allowed limit");
        }
        return value;
    }
}
