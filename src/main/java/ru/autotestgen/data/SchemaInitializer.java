package ru.autotestgen.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the SQLite schema (CREATE TABLE IF NOT EXISTS) on startup.
 * Separated from {@link ReportDao} so that DDL concerns live in one place.
 */
public class SchemaInitializer {

    private final DatabaseConnection connection;

    public SchemaInitializer(DatabaseConnection connection) {
        this.connection = connection;
    }

    public void initialize() {
        try (Connection conn = connection.open(); Statement stmt = conn.createStatement()) {
            createTestRunTable(stmt);
            createTestCaseTable(stmt);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    private void createTestRunTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS test_run (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                run_date    TEXT NOT NULL,
                xml_file    TEXT NOT NULL,
                base_url    TEXT NOT NULL,
                total       INTEGER NOT NULL DEFAULT 0,
                passed      INTEGER NOT NULL DEFAULT 0,
                failed      INTEGER NOT NULL DEFAULT 0,
                skipped     INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0
            )
        """);
    }

    private void createTestCaseTable(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS test_case (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id      INTEGER NOT NULL REFERENCES test_run(id),
                class_name  TEXT NOT NULL,
                method_name TEXT NOT NULL,
                passed      INTEGER NOT NULL DEFAULT 1,
                failure_msg TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0
            )
        """);
    }
}
