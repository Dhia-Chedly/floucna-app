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
            stmt.execute("PRAGMA foreign_keys=ON");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            TEXT PRIMARY KEY,
                    email         TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    full_name     TEXT NOT NULL,
                    role          TEXT NOT NULL DEFAULT 'BORROWER' CHECK (role IN ('BORROWER','LENDER','ADMIN')),
                    is_verified   INTEGER NOT NULL DEFAULT 0,
                    created_at    TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kyc_data (
                    id            TEXT PRIMARY KEY,
                    user_id       TEXT UNIQUE NOT NULL REFERENCES users(id),
                    id_front_path TEXT,
                    id_back_path  TEXT,
                    selfie_path   TEXT,
                    status        TEXT NOT NULL DEFAULT 'PENDING',
                    verified_at   TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS floucna_scores (
                    user_id       TEXT PRIMARY KEY REFERENCES users(id),
                    score         INTEGER NOT NULL DEFAULT 0,
                    risk_ceiling  REAL NOT NULL DEFAULT 0.0,
                    last_updated  TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS loans (
                    id            TEXT PRIMARY KEY,
                    borrower_id   TEXT NOT NULL REFERENCES users(id),
                    amount        REAL NOT NULL,
                    purpose       TEXT,
                    duration_days INTEGER NOT NULL,
                    interest_rate REAL NOT NULL,
                    status        TEXT NOT NULL DEFAULT 'PENDING',
                    funding_pct   REAL NOT NULL DEFAULT 0.0,
                    created_at    TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pledges (
                    id         TEXT PRIMARY KEY,
                    loan_id    TEXT NOT NULL REFERENCES loans(id),
                    lender_id  TEXT NOT NULL REFERENCES users(id),
                    amount     REAL NOT NULL,
                    pledged_at TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS repayments (
                    id        TEXT PRIMARY KEY,
                    loan_id   TEXT NOT NULL REFERENCES loans(id),
                    amount    REAL NOT NULL,
                    due_date  TEXT NOT NULL,
                    paid_at   TEXT,
                    status    TEXT NOT NULL DEFAULT 'PENDING'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS contracts (
                    id             TEXT PRIMARY KEY,
                    loan_id        TEXT UNIQUE NOT NULL REFERENCES loans(id),
                    pdf_path       TEXT NOT NULL,
                    signature_data TEXT,
                    tsa_token      TEXT,
                    signed_at      TEXT,
                    is_verified    INTEGER DEFAULT 0,
                    verify_report  TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id         TEXT PRIMARY KEY,
                    actor_id   TEXT REFERENCES users(id),
                    action     TEXT NOT NULL,
                    target_id  TEXT,
                    timestamp  TEXT NOT NULL,
                    ip_address TEXT
                )
            """);

            System.out.println("Database initialized successfully");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
}
