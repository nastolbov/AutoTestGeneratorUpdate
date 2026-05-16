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
        long startTime = System.currentTimeMillis();

        // Verify pom.xml exists
        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IOException("pom.xml не найден в " + projectDir + ". Сначала сгенерируйте тесты.");
        }

        // Run mvn test (working dir is already the project dir, so just "pom.xml")
        ProcessBuilder pb = new ProcessBuilder("mvn", "test");
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

        return result;
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
                                            tcr.setFailureMessage("SKIPPED");
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
}
