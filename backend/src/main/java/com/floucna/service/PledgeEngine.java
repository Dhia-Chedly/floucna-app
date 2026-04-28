package com.floucna.service;

import com.floucna.db.Database;
import com.floucna.util.ApiException;
import com.floucna.util.InputValidator;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class PledgeEngine {

    public record PledgeRequest(double amount) {}

    public Map<String, Object> pledge(String loanId, String lenderId, double amount) throws Exception {
        InputValidator.requirePositiveAmount(amount, "Pledge amount", 100_000.0);
        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);
            // BEGIN IMMEDIATE for safe concurrent writes
            conn.createStatement().execute("BEGIN IMMEDIATE");

            PreparedStatement userPs = conn.prepareStatement("SELECT role, is_verified FROM users WHERE id=?");
            userPs.setString(1, lenderId);
            ResultSet userRs = userPs.executeQuery();
            if (!userRs.next()) {
                throw new ApiException(404, "Lender not found");
            }
            if (!"LENDER".equals(userRs.getString("role"))) {
                throw new ApiException(403, "Forbidden");
            }
            if (userRs.getInt("is_verified") == 0) {
                throw new IllegalArgumentException("Lender account is not verified");
            }

            // Fetch loan
            PreparedStatement ls = conn.prepareStatement("SELECT * FROM loans WHERE id=?");
            ls.setString(1, loanId);
            ResultSet lrs = ls.executeQuery();
            if (!lrs.next()) throw new Exception("Loan not found");
            if (!lrs.getString("status").equals("PENDING")) throw new Exception("Loan is not open for funding");
            if (lenderId.equals(lrs.getString("borrower_id"))) {
                throw new IllegalArgumentException("Borrower cannot pledge to own loan");
            }

            double loanAmount = lrs.getDouble("amount");
            double currentFunding = lrs.getDouble("funding_pct") * loanAmount / 100.0;
            double remaining = loanAmount - currentFunding;

            if (amount > remaining) throw new Exception("Pledge exceeds remaining amount of " + remaining);

            // Insert pledge
            PreparedStatement pi = conn.prepareStatement(
                "INSERT INTO pledges (id, loan_id, lender_id, amount, pledged_at) VALUES (?,?,?,?,?)"
            );
            pi.setString(1, UUID.randomUUID().toString());
            pi.setString(2, loanId);
            pi.setString(3, lenderId);
            pi.setDouble(4, amount);
            pi.setString(5, Instant.now().toString());
            pi.executeUpdate();

            double newFunding = currentFunding + amount;
            double newPct = (newFunding / loanAmount) * 100.0;
            boolean fullyFunded = newPct >= 99.99;

            String newStatus = fullyFunded ? "FUNDED" : "PENDING";
            PreparedStatement lu = conn.prepareStatement(
                "UPDATE loans SET funding_pct=?, status=? WHERE id=?"
            );
            lu.setDouble(1, Math.min(newPct, 100.0));
            lu.setString(2, newStatus);
            lu.setString(3, loanId);
            lu.executeUpdate();

            conn.commit();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("loanId", loanId);
            result.put("fundingPct", Math.min(newPct, 100.0));
            result.put("fullyFunded", fullyFunded);
            result.put("status", newStatus);

            // Trigger contract generation asynchronously if funded
            if (fullyFunded) {
                String borrowerId = lrs.getString("borrower_id");
                new Thread(() -> {
                    try {
                        ContractService cs = new ContractService();
                        cs.generateAndSign(loanId, borrowerId);
                    } catch (Exception e) {
                        System.err.println("Contract generation failed: " + e.getMessage());
                    }
                }).start();
            }

            return result;
        }
    }

    public List<Map<String, Object>> getPledgesForLoan(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT p.*, u.full_name as lender_name FROM pledges p JOIN users u ON u.id=p.lender_id WHERE p.loan_id=?"
             )) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getString("id"));
                row.put("lenderName", rs.getString("lender_name"));
                row.put("amount", rs.getDouble("amount"));
                row.put("pledgedAt", rs.getString("pledged_at"));
                list.add(row);
            }
            return list;
        }
    }
}
