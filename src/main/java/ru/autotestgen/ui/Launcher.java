package ru.autotestgen.ui;

/**
 * Точка входа для упакованного приложения (jpackage).
 * Не наследует Application — иначе запуск из classpath падает с ошибкой
 * «JavaFX runtime components are missing». Просто передаёт управление App.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
