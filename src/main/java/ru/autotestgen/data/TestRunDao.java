package ru.autotestgen.data;

import ru.autotestgen.model.TestRunResult;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO для таблицы {@code test_run}. Добавляет запись о прогоне и возвращает
 * список всех прогонов (без тест-кейсов — их загрузка делегируется
 * {@link TestCaseDao}).
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
     * Вставляет заголовок прогона в {@code test_run} и возвращает сгенерированный id.
     * Использует соединение вызывающего кода, чтобы вся запись шла одной транзакцией.
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
     * Загружает заголовки прогонов (без результатов по тест-кейсам) в порядке id DESC.
     * Заполнение {@code results} через {@link TestCaseDao} — на стороне вызывающего кода.
     * Возвращает прогоны вместе с их id.
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

    /** Пара (id в базе, заполненный заголовок прогона). */
    public static final class RunRow {
        public final long runId;
        public final TestRunResult run;

        public RunRow(long runId, TestRunResult run) {
            this.runId = runId;
            this.run = run;
        }
    }
}
