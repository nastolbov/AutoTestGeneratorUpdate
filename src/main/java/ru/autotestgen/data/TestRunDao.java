package ru.autotestgen.data;

import ru.autotestgen.model.TestRunResult;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code test_run} table. Inserts a new run row and lists
 * all existing runs (without their cases — case loading is delegated
 * to {@link TestCaseDao}).
 */
public class TestRunDao {

    static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String INSERT_SQL = """
        INSERT INTO test_run (run_date, xml_file, base_url, total, passed, failed, skipped, duration_ms)
        VALUES (?,?,?,?,?,?,?,?)
        """;
    private static final String SELECT_ALL_SQL =
        "SELECT * FROM test_run ORDER BY id DESC";

    private final DatabaseConnection connection;

    public TestRunDao(DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * Inserts the run header into {@code test_run} and returns the generated id.
     * Uses the caller's connection so the whole save is one transaction.
     */
    public long insert(Connection conn, TestRunResult result) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
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
                return rs.getLong(1);
            }
        }
    }

    /**
     * Loads run headers (without per-case results) ordered by id DESC.
     * Caller is responsible for hydrating {@code results} via {@link TestCaseDao}.
     * Returns the runs together with the loaded id list, paired by index.
     */
    public List<RunRow> selectAll(Connection conn) throws SQLException {
        List<RunRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL_SQL)) {
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
                rows.add(new RunRow(runId, run));
            }
        }
        return rows;
    }

    /** Pair of (database id, hydrated run header). */
    public static final class RunRow {
        public final long runId;
        public final TestRunResult run;

        public RunRow(long runId, TestRunResult run) {
            this.runId = runId;
            this.run = run;
        }
    }
}
