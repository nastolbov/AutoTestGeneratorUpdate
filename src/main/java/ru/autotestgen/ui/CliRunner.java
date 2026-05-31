package ru.autotestgen.ui;

import ru.autotestgen.generator.*;
import ru.autotestgen.model.AppModel;
import ru.autotestgen.model.EntityObject;
import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;
import ru.autotestgen.parser.XmlModelParser;
import ru.autotestgen.data.ReportDao;

import java.io.File;
import java.nio.file.Path;

/**
 * Консольный режим: парсинг XML, генерация тестов, запуск — без GUI.
 * Аргументы: --xml, --url, --login, --password, --output, --site-type, --subsystem,
 *           --test-level, --smoke-all-subsystems true|false (по умолчанию true), --no-smoke
 */
public class CliRunner {

    public static void run(String[] args) {
        String xmlPath = null;
        String url = "https://rrpo.cmirit.ru/E3Core";
        String login = "test";
        String password = "b394e86609";
        String outputDir = null;
        String siteType = "e3core";
        String subsystem = null;
        String testLevel = "basic";
        boolean smokeAllSubsystems = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--xml": xmlPath = args[++i]; break;
                case "--url": url = args[++i]; break;
                case "--login": login = args[++i]; break;
                case "--password": password = args[++i]; break;
                case "--output": outputDir = args[++i]; break;
                case "--site-type": siteType = args[++i]; break;
                case "--subsystem": subsystem = args[++i]; break;
                case "--test-level": testLevel = args[++i]; break;
                case "--smoke-all-subsystems": smokeAllSubsystems = Boolean.parseBoolean(args[++i]); break;
                case "--no-smoke": smokeAllSubsystems = false; break;
                default: break;
            }
        }

        if (xmlPath == null) {
            System.err.println("Использование: --cli --xml <путь> [--url URL] [--login ЛОГИН] [--password ПАРОЛЬ] [--output КАТАЛОГ]");
            System.exit(1);
        }

        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            System.err.println("Файл не найден: " + xmlPath);
            System.exit(1);
        }

        if (outputDir == null) {
            String parent = xmlFile.getParent();
            if (parent == null) parent = ".";
            outputDir = parent + "/generated-tests";
        }

        System.out.println("=== AutoTestGenerator CLI ===");
        System.out.println("XML:    " + xmlPath);
        System.out.println("URL:    " + url);
        System.out.println("Login:  " + login);
        System.out.println("Output: " + outputDir);
        System.out.println();

        try {
            // 1. Parse XML
            System.out.println("[1/3] Парсинг XML-модели...");
            XmlModelParser parser = new XmlModelParser();
            AppModel model = parser.parse(xmlFile);
            System.out.println("  Найдено сущностей: " + model.getEntities().size());
            for (EntityObject entity : model.getEntities()) {
                String info = "    - " + entity.getName();
                if (entity.hasCrudOperations()) info += " [CRUD]";
                System.out.println(info);
            }

            // Default the subsystem to the XML CategoryName when --subsystem wasn't passed
            if (subsystem == null) {
                String xmlSubsystem = model.getSubsystemNameFromCategory();
                subsystem = !xmlSubsystem.isEmpty() ? xmlSubsystem : "Гаражно-строительные кооперативы";
                System.out.println("  Подсистема: " + subsystem);
            }

            // 2. Generate tests
            System.out.println();
            System.out.println("[2/3] Генерация тестов...");
            TestConfig config = new TestConfig();
            config.setBaseUrl(url);
            config.setLogin(login);
            config.setPassword(password);
            config.setOutputDir(Path.of(outputDir));
            config.setSiteType(siteType);
            config.setSubsystemName(subsystem);
            config.setTestLevel(testLevel);
            config.setSmokeAllSubsystems(smokeAllSubsystems);

            TestGenerator generator = new TestGenerator(config);
            generator.generate(model);
            System.out.println("  Тесты сгенерированы в: " + outputDir);

            // 3. Run tests
            System.out.println();
            System.out.println("[3/3] Запуск тестов (параллельно)...");
            System.out.println("  (первый запуск может занять 1-2 мин на загрузку зависимостей)");
            System.out.println();
            TestRunner runner = new TestRunner();
            TestRunResult result = runner.run(Path.of(outputDir), xmlFile.getName(), url, System.out::println);

            // Save to DB
            ReportDao reportDao = new ReportDao();
            reportDao.saveRun(result);

            // Print results
            System.out.println();
            System.out.println("=== РЕЗУЛЬТАТЫ ===");
            System.out.println("Всего:     " + result.getTotalTests());
            System.out.println("Успешно:   " + result.getPassed());
            System.out.println("Ошибки:    " + result.getFailed());
            System.out.println("Пропущено: " + result.getSkipped());
            System.out.println("Время:     " + (result.getDurationMs() / 1000) + " сек");
            System.out.println();

            if (!result.getResults().isEmpty()) {
                System.out.printf("%-40s %-35s %-8s %-10s %s%n",
                        "Класс", "Метод", "Статус", "Время", "Сообщение");
                System.out.println("-".repeat(120));
                for (TestCaseResult tcr : result.getResults()) {
                    System.out.printf("%-40s %-35s %-8s %-10s %s%n",
                            truncate(tcr.getClassName(), 40),
                            truncate(tcr.getMethodName(), 35),
                            tcr.isSkipped() ? "SKIP" : (tcr.isPassed() ? "OK" : "FAIL"),
                            tcr.getDurationMs() + " мс",
                            tcr.getFailureMessage() != null ? truncate(tcr.getFailureMessage(), 50) : "");
                }
            }

            // Maven output was already streamed live above

            System.exit(result.getFailed() > 0 ? 1 : 0);

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
