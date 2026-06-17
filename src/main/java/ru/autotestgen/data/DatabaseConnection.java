package ru.autotestgen.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Управляет JDBC-подключением к базе отчётов SQLite.
 * Единственный источник JDBC-URL.
 */
public class DatabaseConnection {

    public static final String DEFAULT_URL = "jdbc:sqlite:autotestgen.db";

    private final String url;

    public DatabaseConnection() {
        this(DEFAULT_URL);
    }

    public DatabaseConnection(String url) {
        this.url = url;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public String getUrl() {
        return url;
    }
}
