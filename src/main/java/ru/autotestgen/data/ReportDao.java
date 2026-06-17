package ru.autotestgen.data;

import ru.autotestgen.model.TestRunResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Фасад над базой отчётов SQLite. Объединяет
 * {@link DatabaseConnection}, {@link SchemaInitializer},
 * {@link TestRunDao} и {@link TestCaseDao}, сохраняя для остального
 * приложения привычный интерфейс {@code saveRun} / {@code getAllRuns}.
 */
public class ReportDao {

    private final DatabaseConnection connection;
    private final TestRunDao testRunDao;
    private final TestCaseDao testCaseDao;

    public ReportDao() {
        this(new DatabaseConnection());
    }

    public ReportDao(DatabaseConnection connection) {
        this(connection, new TestRunDao(connection), new TestCaseDao());
    }

    public ReportDao(DatabaseConnection connection, TestRunDao testRunDao, TestCaseDao testCaseDao) {
        this.connection = connection;
        this.testRunDao = testRunDao;
        this.testCaseDao = testCaseDao;
        new SchemaInitializer(connection).initialize();
    }

    public void saveRun(TestRunResult result) {
        try (Connection conn = connection.open()) {
            conn.setAutoCommit(false);
            long runId = testRunDao.insert(conn, result);
            testCaseDao.insertBatch(conn, runId, result.getResults());
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Failed to save test run: " + e.getMessage());
        }
    }

    public List<TestRunResult> getAllRuns() {
        List<TestRunResult> runs = new ArrayList<>();
        try (Connection conn = connection.open()) {
            for (TestRunDao.RunRow row : testRunDao.selectAll(conn)) {
                row.run.setResults(testCaseDao.selectByRunId(conn, row.runId));
                runs.add(row.run);
            }
        } catch (SQLException e) {
            System.err.println("Failed to load runs: " + e.getMessage());
        }
        return runs;
    }
}
