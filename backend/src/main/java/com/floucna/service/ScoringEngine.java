package com.floucna.service;

import java.util.Locale;

public class ScoringEngine {

    public record ScoreResult(int score, String label) {}

    public static ScoreResult calculateDemoScore(
        String kycStatus,
        double amount,
        int durationDays,
        String purpose,
        Double declaredIncome,
        Double declaredExistingDebt
    ) {
        int score = 50;

        boolean verified = "VERIFIED".equalsIgnoreCase(kycStatus);
        if (verified) {
            score += 20;
        } else {
            score -= 20;
        }

        if (amount <= 500) {
            score += 10;
        }
        if (durationDays <= 90) {
            score += 10;
        }

        if (isProductiveOrEssentialPurpose(purpose)) {
            score += 5;
        }
        if (declaredIncome != null && declaredIncome > 0) {
            score += 5;
        }

        if (declaredExistingDebt != null && declaredExistingDebt > 0) {
            score -= 10;
        }
        if (amount > 1000) {
            score -= 15;
        }

        score = Math.max(0, Math.min(100, score));
        return new ScoreResult(score, labelFor(score));
    }

    private static boolean isProductiveOrEssentialPurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return false;
        }
        String normalized = purpose.toLowerCase(Locale.ROOT);
        return normalized.contains("medical")
            || normalized.contains("health")
            || normalized.contains("school")
            || normalized.contains("education")
            || normalized.contains("study")
            || normalized.contains("business")
            || normalized.contains("equipment")
            || normalized.contains("rent")
            || normalized.contains("essential");
    }

    private static String labelFor(int score) {
        if (score >= 80) {
            return "LOW_RISK";
        }
        if (score >= 60) {
            return "ACCEPTABLE";
        }
        if (score >= 40) {
            return "MANUAL_REVIEW";
        }
        return "REJECT_RECOMMENDED";
    }
}
