package ru.autotestgen.generator;

import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the generated test project via Maven Surefire
 * and parses the XML reports to build TestRunResult.
 */
public class TestRunner {

    private String lastMavenOutput = "";

    /**
     * Runs 'mvn test' in the generated project directory
     * and parses Surefire XML reports.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, null);
    }

    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl, Consumer<String> lineConsumer) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, lineConsumer, null);
    }

    /**
     * Runs the generated tests. When {@code testFilter} is non-empty it is passed to Surefire
     * as {@code -Dtest=<filter>}, so only the selected test classes / methods run.
     * Filter syntax (Surefire): {@code Class1Test,Class2Test#testCreate*+testUpdate*}.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl,
                             Consumer<String> lineConsumer, String testFilter) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, lineConsumer, testFilter, false);
    }

    /**
     * Runs the generated tests. {@code fastMode=true} adds -Dheadless=true (без видимого окна
     * браузера, чуть быстрее). ВАЖНО: forkCount остаётся 1 — НЕ запускаем несколько Chrome
     * параллельно. На одном стендовом логине (AIS_GSK) параллельные сессии конфликтуют:
     * правки одной сущности гоняются между сессиями + теряется фокус у клавиатурных Actions,
     * из-за чего create/update не сохраняются. Надёжность важнее скорости для мутирующих тестов.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl,
                             Consumer<String> lineConsumer, String testFilter, boolean fastMode) throws IOException {
        long startTime = System.currentTimeMillis();

        // Verify pom.xml exists
        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IOException("pom.xml не найден в " + projectDir + ". Сначала сгенерируйте тесты.");
        }

        // Run mvn test (working dir is already the project dir, so just "pom.xml")
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("test");
        if (testFilter != null && !testFilter.isBlank()) {
            cmd.add("-Dtest=" + testFilter);
            // Don't fail the build when a filtered class has no matching methods.
            cmd.add("-DfailIfNoTests=false");
        }
        if (fastMode) {
            // forkCount=1: один браузер за раз. 3 параллельных Chrome на одном логине ломали
            // сохранение (гонки правок одной сущности + потеря фокуса у Actions-клавиатуры).
            cmd.add("-DforkCount=1");
            cmd.add("-Dheadless=true");
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(projectDir.toFile());

        Process process = pb.start();
        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                outputBuilder.append(line).append("\n");
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
        }
        lastMavenOutput = outputBuilder.toString();

        int exitCode = -1;
        try {
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (finished) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Parse Surefire XML reports
        TestRunResult result = new TestRunResult();
        result.setRunTimestamp(LocalDateTime.now());
        result.setDurationMs(durationMs);
        result.setXmlFileName(xmlFileName);
        result.setBaseUrl(baseUrl);
        result.setMavenOutput(lastMavenOutput);

        Path reportsDir = projectDir.resolve("target/surefire-reports");
        if (Files.exists(reportsDir)) {
            parseSurefireReports(reportsDir, result);
        } else {
            result.setTotalTests(0);
            result.setPassed(0);
            result.setFailed(0);
            result.setSkipped(0);
        }

        // Enrich each test case with screenshots from target/screenshots/ (named so we can map by
        // <Entity>_<testMethod>_…). Done after Surefire parsing so test results already exist.
        linkScreenshots(projectDir.resolve("target/screenshots"), result);

        // Write the standalone HTML run report. It's the single artifact the user opens to see
        // what happened: per-test screenshots, search params, step timings, failure messages.
        try {
            new RunReportWriter().write(projectDir.resolve("target/run-report.html"), result);
            new RunReportWriter().writeCsv(projectDir.resolve("target/run-report.csv"), result);
        } catch (Exception e) {
            System.err.println("Failed to write run report: " + e.getMessage());
        }

        return result;
    }

    /** Maps PNGs in target/screenshots/ to test cases by file-name convention. */
    private void linkScreenshots(Path shotDir, TestRunResult result) {
        if (!Files.exists(shotDir)) return;
        try (var stream = Files.list(shotDir)) {
            stream.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                String name = p.getFileName().toString();
                // Expected: <EntityClass>_<testMethod>_NN_<step>_<status>.png
                int firstUnderscore = name.indexOf('_');
                if (firstUnderscore < 0) return;
                String entity = name.substring(0, firstUnderscore);
                String rest = name.substring(firstUnderscore + 1);
                int secondUnderscore = rest.indexOf('_');
                String method = secondUnderscore < 0 ? rest : rest.substring(0, secondUnderscore);
                for (TestCaseResult tc : result.getResults()) {
                    String cls = tc.getClassName() == null ? "" : tc.getClassName();
                    if (cls.contains(entity) && method.equals(tc.getMethodName())) {
                        tc.addScreenshot(p.toString());
                        break;
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("linkScreenshots: " + e.getMessage());
        }
    }

    public String getLastMavenOutput() {
        return lastMavenOutput;
    }


    private void parseSurefireReports(Path reportsDir, TestRunResult runResult) throws IOException {
        List<TestCaseResult> allResults = new ArrayList<>();
        int totalTests = 0, passed = 0, failed = 0, skipped = 0;

        try (var stream = Files.list(reportsDir)) {
            List<Path> xmlFiles = stream
                    .filter(p -> p.toString().endsWith(".xml"))
                    .toList();

            XMLInputFactory factory = XMLInputFactory.newInstance();

            for (Path xmlFile : xmlFiles) {
                try (InputStream is = Files.newInputStream(xmlFile)) {
                    XMLStreamReader reader = factory.createXMLStreamReader(is);

                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            String local = reader.getLocalName();

                            if ("testsuite".equals(local)) {
                                totalTests += parseIntAttr(reader, "tests");
                                failed += parseIntAttr(reader, "failures") + parseIntAttr(reader, "errors");
                                skipped += parseIntAttr(reader, "skipped");
                            } else if ("testcase".equals(local)) {
                                TestCaseResult tcr = new TestCaseResult();
                                tcr.setClassName(reader.getAttributeValue(null, "classname"));
                                tcr.setMethodName(reader.getAttributeValue(null, "name"));
                                tcr.setDurationMs((long) (parseDoubleAttr(reader, "time") * 1000));
                                tcr.setPassed(true);

                                // Scan child elements until </testcase>
                                while (reader.hasNext()) {
                                    event = reader.next();
                                    if (event == XMLStreamConstants.END_ELEMENT && "testcase".equals(reader.getLocalName())) {
                                        break;
                                    }
                                    if (event == XMLStreamConstants.START_ELEMENT) {
                                        String childLocal = reader.getLocalName();
                                        if ("failure".equals(childLocal) || "error".equals(childLocal)) {
                                            tcr.setPassed(false);
                                            tcr.setFailureMessage(reader.getAttributeValue(null, "message"));
                                        } else if ("skipped".equals(childLocal)) {
                                            tcr.setPassed(false);
                                            tcr.setSkipped(true);
                                            tcr.setFailureMessage(cleanSkipMessage(reader.getAttributeValue(null, "message")));
                                        } else if ("system-out".equals(childLocal)) {
                                            String text = reader.getElementText();
                                            tcr.setStdOut(text);
                                            tcr.setSearchParams(extractSearchParams(text));
                                            extractStepTimings(text, tcr);
                                        }
                                    }
                                }
                                allResults.add(tcr);
                            }
                        }
                    }
                    reader.close();
                } catch (Exception e) {
                    System.err.println("Error parsing report " + xmlFile + ": " + e.getMessage());
                }
            }
        }

        // Calculate from actual testcase results (more reliable than testsuite attrs)
        int actualPassed = 0, actualFailed = 0, actualSkipped = 0;
        for (TestCaseResult tcr : allResults) {
            if (tcr.isSkipped()) actualSkipped++;
            else if (tcr.isPassed()) actualPassed++;
            else actualFailed++;
        }
        runResult.setTotalTests(allResults.size());
        runResult.setPassed(actualPassed);
        runResult.setFailed(actualFailed);
        runResult.setSkipped(actualSkipped);
        runResult.setResults(allResults);
    }

    private int parseIntAttr(XMLStreamReader reader, String name) {
        String val = reader.getAttributeValue(null, name);
        if (val == null || val.isEmpty()) return 0;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }

    private double parseDoubleAttr(XMLStreamReader reader, String name) {
        String val = reader.getAttributeValue(null, name);
        if (val == null || val.isEmpty()) return 0;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Normalises the message attribute on a Surefire {@code <skipped>} element.
     * JUnit Assumptions usually arrive as
     * "org.opentest4j.TestAbortedException: Assumption failed: <reason>";
     * {@code @Disabled("reason")} arrives as just "reason". Strip the boilerplate so the
     * UI just shows the human-readable cause.
     */
    private String cleanSkipMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "SKIPPED";
        String msg = raw.trim();
        msg = msg.replaceFirst("^org\\.opentest4j\\.TestAbortedException:\\s*", "");
        msg = msg.replaceFirst("^Assumption failed:\\s*", "");
        return msg.isEmpty() ? "SKIPPED" : msg;
    }

    /**
     * Pulls "=== Search params for 'X' ===" blocks out of test stdout. Each contained line
     * matches "  KEY = 'VALUE'" (printed by BaseTest.logSearchParams). Returns the merged map.
     */
    private java.util.Map<String, String> extractSearchParams(String stdout) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (stdout == null || stdout.isEmpty()) return result;
        boolean inBlock = false;
        for (String line : stdout.split("\\r?\\n")) {
            if (line.startsWith("=== Search params")) { inBlock = true; continue; }
            if (line.startsWith("===") && inBlock) { inBlock = false; continue; }
            if (!inBlock) continue;
            // Expected:   KEY = 'VALUE'
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            result.put(key, val);
        }
        return result;
    }

    /** Pulls "[step] name: NNNms" lines into the test case timeline. */
    private void extractStepTimings(String stdout, TestCaseResult tcr) {
        if (stdout == null || stdout.isEmpty()) return;
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile("^\\[step\\] (.+?): (\\d+)ms\\s*$");
        for (String line : stdout.split("\\r?\\n")) {
            java.util.regex.Matcher m = pat.matcher(line);
            if (m.matches()) {
                try { tcr.addStep(m.group(1), Long.parseLong(m.group(2))); }
                catch (NumberFormatException ignored) {}
            }
        }
    }
}
