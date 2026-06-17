package ru.autotestgen.data;

import ru.autotestgen.model.TestCaseResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code test_case} table. Batch-inserts cases under a known
 * {@code run_id} and loads cases for a given run.
 */
public class TestCaseDao {

    private static final String INSERT_SQL = """
        INSERT INTO test_case (run_id, class_name, method_name, passed, failure_msg, duration_ms)
        VALUES (?,?,?,?,?,?)
        """;
    private static final String SELECT_BY_RUN_SQL =
        "SELECT * FROM test_case WHERE run_id = ? ORDER BY id";

    public void insertBatch(Connection conn, long runId, List<TestCaseResult> cases) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (TestCaseResult tcr : cases) {
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
    }

    public List<TestCaseResult> selectByRunId(Connection conn, long runId) throws SQLException {
        List<TestCaseResult> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_RUN_SQL)) {
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
}
