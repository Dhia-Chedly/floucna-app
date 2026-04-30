package com.floucna.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:" +
        System.getenv().getOrDefault("FLOUCNA_DB_PATH", "floucna.db");

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initialize() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=OFF");

            if (isSchemaResetEnabled()) {
                dropLegacyAndCurrentTables(stmt);
            }

            createSchema(stmt);
            stmt.execute("PRAGMA foreign_keys=ON");

            System.out.println("Database initialized successfully");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private static boolean isSchemaResetEnabled() {
        String value = System.getenv("FLOUCNA_SCHEMA_RESET");
        if (value == null || value.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(value);
    }

    private static void dropLegacyAndCurrentTables(Statement stmt) throws SQLException {
        // New schema tables
        stmt.execute("DROP TABLE IF EXISTS audit_logs");
        stmt.execute("DROP TABLE IF EXISTS contracts");
        stmt.execute("DROP TABLE IF EXISTS loan_requests");
        stmt.execute("DROP TABLE IF EXISTS kyc_records");
        stmt.execute("DROP TABLE IF EXISTS users");

        // Legacy schema tables (hard-cut cleanup)
        stmt.execute("DROP TABLE IF EXISTS repayments");
        stmt.execute("DROP TABLE IF EXISTS pledges");
        stmt.execute("DROP TABLE IF EXISTS loans");
        stmt.execute("DROP TABLE IF EXISTS floucna_scores");
        stmt.execute("DROP TABLE IF EXISTS kyc_data");
    }

    private static void createSchema(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id               TEXT PRIMARY KEY,
                provider_subject TEXT UNIQUE NOT NULL,
                email            TEXT UNIQUE NOT NULL,
                full_name        TEXT NOT NULL,
                role             TEXT NOT NULL CHECK (role IN ('BORROWER', 'ADMIN')),
                created_at       TEXT NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS kyc_records (
                id                  TEXT PRIMARY KEY,
                user_id             TEXT NOT NULL UNIQUE REFERENCES users(id),
                mode                TEXT NOT NULL CHECK (mode IN ('DIDIT', 'LOCAL')),
                provider_session_id TEXT,
                provider_result_id  TEXT,
                id_photo_path       TEXT,
                status              TEXT NOT NULL DEFAULT 'NOT_STARTED',
                reviewed_by         TEXT REFERENCES users(id),
                reviewed_at         TEXT,
                created_at          TEXT NOT NULL,
                updated_at          TEXT NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS loan_requests (
                id                     TEXT PRIMARY KEY,
                borrower_id            TEXT NOT NULL REFERENCES users(id),
                amount                 REAL NOT NULL,
                currency               TEXT NOT NULL DEFAULT 'TND',
                duration_days          INTEGER NOT NULL,
                purpose                TEXT NOT NULL,
                declared_income        REAL,
                declared_existing_debt REAL,
                demo_score             INTEGER,
                score_label            TEXT,
                status                 TEXT NOT NULL DEFAULT 'SUBMITTED',
                created_at             TEXT NOT NULL,
                approved_at            TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS contracts (
                id                  TEXT PRIMARY KEY,
                loan_id             TEXT UNIQUE NOT NULL REFERENCES loan_requests(id),
                unsigned_pdf_path   TEXT,
                signed_pdf_path     TEXT,
                timestamped_pdf_path TEXT,
                pdf_hash            TEXT,
                signature_level     TEXT,
                status              TEXT NOT NULL DEFAULT 'PDF_GENERATED',
                verification_result TEXT,
                verification_report TEXT,
                signed_at           TEXT,
                timestamped_at      TEXT,
                verified_at         TEXT,
                created_at          TEXT NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id          TEXT PRIMARY KEY,
                actor_id    TEXT REFERENCES users(id),
                action      TEXT NOT NULL,
                target_type TEXT,
                target_id   TEXT,
                result      TEXT,
                created_at  TEXT NOT NULL
            )
        """);
    }
}
