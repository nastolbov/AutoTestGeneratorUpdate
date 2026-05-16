package ru.autotestgen.ui;

/**
 * CLI entry point без зависимости от JavaFX.
 *
 * Использование: mvn compile exec:java -Dexec.args="--xml model.xml --output ./tests"
 */
public class CliMain {
    public static void main(String[] args) {
        CliRunner.run(args);
    }
}
