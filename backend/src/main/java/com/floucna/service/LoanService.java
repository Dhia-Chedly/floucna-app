package com.floucna.service;

import com.floucna.db.Database;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class LoanService {

    public record LoanRequest(double amount, String purpose, int durationDays, double interestRate) {}

    public Map<String, Object> createLoan(String borrowerId, LoanRequest req) throws Exception {
        // Check verification and score
        try (Connection conn = Database.connect()) {
            PreparedStatement check = conn.prepareStatement(
                "SELECT u.is_verified, fs.risk_ceiling FROM users u LEFT JOIN floucna_scores fs ON fs.user_id=u.id WHERE u.id=?"
            );
            check.setString(1, borrowerId);
            ResultSet rs = check.executeQuery();
            if (!rs.next() || rs.getInt("is_verified") == 0)
                throw new Exception("Account not verified. Complete KYC first.");
            double ceiling = rs.getDouble("risk_ceiling");
            if (req.amount() > ceiling)
                throw new Exception("Amount exceeds your Risk Ceiling of " + ceiling);

            String id = UUID.randomUUID().toString();
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO loans (id, borrower_id, amount, purpose, duration_days, interest_rate, status, funding_pct, created_at) VALUES (?,?,?,?,?,?,'PENDING',0.0,?)"
            );
            ins.setString(1, id);
            ins.setString(2, borrowerId);
            ins.setDouble(3, req.amount());
            ins.setString(4, req.purpose());
            ins.setInt(5, req.durationDays());
            ins.setDouble(6, req.interestRate());
            ins.setString(7, Instant.now().toString());
            ins.executeUpdate();

            return getLoanById(id, conn);
        }
    }

    public List<Map<String, Object>> listMarketplace() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT l.*, u.full_name as borrower_name, fs.score FROM loans l " +
                "JOIN users u ON u.id=l.borrower_id " +
                "LEFT JOIN floucna_scores fs ON fs.user_id=l.borrower_id " +
                "WHERE l.status='PENDING' ORDER BY l.created_at DESC"
             )) {
            return mapResultSet(ps.executeQuery());
        }
    }

    public List<Map<String, Object>> listMyLoans(String borrowerId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM loans WHERE borrower_id=? ORDER BY created_at DESC"
             )) {
            ps.setString(1, borrowerId);
            return mapResultSet(ps.executeQuery());
        }
    }

    public Map<String, Object> getLoanDetail(String loanId) throws Exception {
        try (Connection conn = Database.connect()) {
            return getLoanById(loanId, conn);
        }
    }

    private Map<String, Object> getLoanById(String id, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT l.*, u.full_name as borrower_name, fs.score FROM loans l " +
            "JOIN users u ON u.id=l.borrower_id " +
            "LEFT JOIN floucna_scores fs ON fs.user_id=l.borrower_id WHERE l.id=?"
        );
        ps.setString(1, id);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) throw new SQLException("Loan not found");
        return rowToMap(rs);
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) list.add(rowToMap(rs));
        return list;
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            m.put(meta.getColumnName(i), rs.getObject(i));
        }
        return m;
    }

    public void repay(String loanId, String borrowerId, double amount) throws Exception {
        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);
            // Record repayment
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO repayments (id, loan_id, amount, due_date, paid_at, status) VALUES (?,?,?,?,?,'PAID')"
            );
            ins.setString(1, UUID.randomUUID().toString());
            ins.setString(2, loanId);
            ins.setDouble(3, amount);
            ins.setString(4, Instant.now().toString());
            ins.setString(5, Instant.now().toString());
            ins.executeUpdate();

            // Update score
            ScoringEngine.adjustScore(conn, borrowerId, 10);
            conn.commit();
        }
    }
}
