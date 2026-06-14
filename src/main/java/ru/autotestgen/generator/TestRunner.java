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
 * Запускает сгенерированный проект тестов через Maven Surefire
 * и разбирает XML-отчёты в TestRunResult.
 */
public class TestRunner {

    private String lastMavenOutput = "";

    /**
     * Запускает 'mvn test' в каталоге сгенерированного проекта
     * и разбирает XML-отчёты Surefire.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, null);
    }

    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl, Consumer<String> lineConsumer) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, lineConsumer, null);
    }

    /**
     * Запускает сгенерированные тесты. Если {@code testFilter} не пуст, он передаётся в Surefire
     * как {@code -Dtest=<filter>}, и выполняются только выбранные классы / методы тестов.
     * Синтаксис фильтра (Surefire): {@code Class1Test,Class2Test#testCreate*+testUpdate*}.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl,
                             Consumer<String> lineConsumer, String testFilter) throws IOException {
        return run(projectDir, xmlFileName, baseUrl, lineConsumer, testFilter, false);
    }

    /**
     * Запускает сгенерированные тесты. {@code fastMode=true} добавляет -Dheadless=true (без видимого
     * окна браузера, чуть быстрее). forkCount остаётся 1 — несколько Chrome параллельно не запускаем:
     * на одном стендовом логине (AIS_GSK) параллельные сессии конфликтуют (правки одной сущности
     * пересекаются, теряется фокус у клавиатурных Actions), из-за чего create/update не сохраняются.
     */
    public TestRunResult run(Path projectDir, String xmlFileName, String baseUrl,
                             Consumer<String> lineConsumer, String testFilter, boolean fastMode) throws IOException {
        long startTime = System.currentTimeMillis();

        // Проверяем наличие pom.xml
        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IOException("pom.xml не найден в " + projectDir + ". Сначала сгенерируйте тесты.");
        }

        // Запуск mvn test (рабочий каталог уже равен каталогу проекта).
        // На Windows исполняемый файл Maven — mvn.cmd; ProcessBuilder для команды без расширения
        // ищет только mvn.exe и падает с "Cannot run program mvn". Поэтому выбираем имя по ОС.
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> cmd = new ArrayList<>();
        cmd.add(isWindows ? "mvn.cmd" : "mvn");
        cmd.add("test");
        if (testFilter != null && !testFilter.isBlank()) {
            cmd.add("-Dtest=" + testFilter);
            // Не валим сборку, если в отфильтрованном классе нет подходящих методов.
            cmd.add("-DfailIfNoTests=false");
        }
        if (fastMode) {
            // forkCount=1: один браузер за раз. Несколько параллельных Chrome на одном логине ломали
            // сохранение (гонки правок одной сущности и потеря фокуса у Actions-клавиатуры).
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

        // Разбор XML-отчётов Surefire
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

        // Привязываем к тест-кейсам скриншоты из target/screenshots/ (имена позволяют сопоставить
        // по <Entity>_<testMethod>_…). Делаем после разбора Surefire, когда результаты уже есть.
        linkScreenshots(projectDir.resolve("target/screenshots"), result);

        // Пишем автономный HTML-отчёт о прогоне — это единый артефакт, который открывает пользователь:
        // скриншоты по тестам, параметры поиска, тайминги шагов, сообщения об ошибках.
        try {
            new RunReportWriter().write(projectDir.resolve("target/run-report.html"), result);
            new RunReportWriter().writeCsv(projectDir.resolve("target/run-report.csv"), result);
        } catch (Exception e) {
            System.err.println("Failed to write run report: " + e.getMessage());
        }

        return result;
    }

    /** Сопоставляет PNG из target/screenshots/ с тест-кейсами по соглашению об именах файлов. */
    private void linkScreenshots(Path shotDir, TestRunResult result) {
        if (!Files.exists(shotDir)) return;
        try (var stream = Files.list(shotDir)) {
            stream.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
                String name = p.getFileName().toString();
                // Ожидаемый формат: <EntityClass>_<testMethod>_NN_<step>_<status>.png
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

                                // Обходим дочерние элементы до </testcase>
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

        // Считаем по фактическим результатам testcase (надёжнее, чем атрибуты testsuite)
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
     * Нормализует атрибут message у элемента Surefire {@code <skipped>}.
     * JUnit Assumptions обычно приходят как
     * "org.opentest4j.TestAbortedException: Assumption failed: <причина>",
     * а {@code @Disabled("reason")} — просто как "reason". Убираем служебный префикс,
     * чтобы в интерфейсе осталась только понятная причина.
     */
    private String cleanSkipMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "SKIPPED";
        String msg = raw.trim();
        msg = msg.replaceFirst("^org\\.opentest4j\\.TestAbortedException:\\s*", "");
        msg = msg.replaceFirst("^Assumption failed:\\s*", "");
        return msg.isEmpty() ? "SKIPPED" : msg;
    }

    /**
     * Извлекает из stdout теста блоки "=== Search params for 'X' ===". Каждая строка внутри
     * имеет вид "  KEY = 'VALUE'" (печатается в BaseTest.logSearchParams). Возвращает объединённую карту.
     */
    private java.util.Map<String, String> extractSearchParams(String stdout) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (stdout == null || stdout.isEmpty()) return result;
        boolean inBlock = false;
        for (String line : stdout.split("\\r?\\n")) {
            if (line.startsWith("=== Search params")) { inBlock = true; continue; }
            if (line.startsWith("===") && inBlock) { inBlock = false; continue; }
            if (!inBlock) continue;
            // Ожидаемый вид:   KEY = 'VALUE'
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

    /** Переносит строки "[step] name: NNNms" в таймлайн тест-кейса. */
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
