package ru.autotestgen.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for writing formatted Java source files.
 */
public class JavaFileWriter {

    private final StringBuilder sb = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT = "    ";

    public JavaFileWriter writeLine(String line) {
        for (int i = 0; i < indentLevel; i++) sb.append(INDENT);
        sb.append(line).append("\n");
        return this;
    }

    public JavaFileWriter writeLine() {
        sb.append("\n");
        return this;
    }

    public JavaFileWriter openBlock(String header) {
        writeLine(header + " {");
        indentLevel++;
        return this;
    }

    public JavaFileWriter closeBlock() {
        indentLevel--;
        writeLine("}");
        return this;
    }

    public JavaFileWriter indent() {
        indentLevel++;
        return this;
    }

    public JavaFileWriter unindent() {
        indentLevel--;
        return this;
    }

    public void writeToFile(Path dir, String fileName) throws IOException {
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(filePath, StandardCharsets.UTF_8))) {
            pw.write(sb.toString());
        }
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
