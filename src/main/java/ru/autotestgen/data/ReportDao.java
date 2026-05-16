package ru.autotestgen.data;

import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite data access for storing and retrieving test run history.
 */
public class ReportDao {

    private static final String DB_URL = "jdbc:sqlite:autotestgen.db";
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ReportDao() {
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
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
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    public void saveRun(TestRunResult result) {
        String sql = "INSERT INTO test_run (run_date, xml_file, base_url, total, passed, failed, skipped, duration_ms) VALUES (?,?,?,?,?,?,?,?)";
        String sqlCase = "INSERT INTO test_case (run_id, class_name, method_name, passed, failure_msg, duration_ms) VALUES (?,?,?,?,?,?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            long runId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, result.getRunTimestamp().format(DT_FORMAT));
                ps.setString(2, result.getXmlFileName());
                ps.setString(3, result.getBaseUrl());
                ps.setInt(4, result.getTotalTests());
                ps.setInt(5, result.getPassed());
                ps.setInt(6, result.getFailed());
                ps.setInt(7, result.getSkipped());
                ps.setLong(8, result.getDurationMs());
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    runId = rs.getLong(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlCase)) {
                for (TestCaseResult tcr : result.getResults()) {
                    ps.setLong(1, runId);
                    ps.setString(2, tcr.getClassName() != null ? tcr.getClassName() : "");
                    ps.setString(3, tcr.getMethodName() != null ? tcr.getMethodName() : "");
                    ps.setInt(4, tcr.isPassed() ? 1 : 0);
                    ps.setString(5, tcr.getFailureMessage());
                    ps.setLong(6, tcr.getDurationMs());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Failed to save test run: " + e.getMessage());
        }
    }

    public List<TestRunResult> getAllRuns() {
        List<TestRunResult> runs = new ArrayList<>();
        String sql = "SELECT * FROM test_run ORDER BY id DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                TestRunResult run = new TestRunResult();
                long runId = rs.getLong("id");
                run.setRunTimestamp(LocalDateTime.parse(rs.getString("run_date"), DT_FORMAT));
                run.setXmlFileName(rs.getString("xml_file"));
                run.setBaseUrl(rs.getString("base_url"));
                run.setTotalTests(rs.getInt("total"));
                run.setPassed(rs.getInt("passed"));
                run.setFailed(rs.getInt("failed"));
                run.setSkipped(rs.getInt("skipped"));
                run.setDurationMs(rs.getLong("duration_ms"));
                run.setResults(getCaseResults(conn, runId));
                runs.add(run);
            }
        } catch (SQLException e) {
            System.err.println("Failed to load runs: " + e.getMessage());
        }
        return runs;
    }

    private List<TestCaseResult> getCaseResults(Connection conn, long runId) throws SQLException {
        List<TestCaseResult> results = new ArrayList<>();
        String sql = "SELECT * FROM test_case WHERE run_id = ? ORDER BY id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TestCaseResult tcr = new TestCaseResult();
                    tcr.setClassName(rs.getString("class_name"));
                    tcr.setMethodName(rs.getString("method_name"));
                    tcr.setPassed(rs.getInt("passed") == 1);
                    tcr.setFailureMessage(rs.getString("failure_msg"));
                    tcr.setDurationMs(rs.getLong("duration_ms"));
                    results.add(tcr);
                }
            }
        }
        return results;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
}
