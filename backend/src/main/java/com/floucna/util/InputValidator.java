package com.floucna.util;

import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private InputValidator() {}

    public static String requireEmail(String email, String fieldName) {
        if (email == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (!EMAIL_RE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return normalized;
    }

    public static String requirePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!(hasUpper && hasLower && hasDigit)) {
            throw new IllegalArgumentException("Password must include upper-case, lower-case, and a number");
        }
        return password;
    }

    public static String requireName(String name, String fieldName, int maxLength) {
        if (name == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalized;
    }

    public static String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Field exceeds max length");
        }
        return normalized;
    }

    public static double requirePositiveAmount(double value, String fieldName, double maxAllowed) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive number");
        }
        if (value > maxAllowed) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum allowed limit");
        }
        return value;
    }

    public static int requireIntRange(int value, String fieldName, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        return value;
    }
}
