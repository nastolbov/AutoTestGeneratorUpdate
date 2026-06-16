package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.model.AppModel;
import ru.autotestgen.model.EntityClassifier;
import ru.autotestgen.model.EntityKind;
import ru.autotestgen.model.EntityObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Создаёт структуру генерируемого тест-проекта и генерирует
 * Page Object'ы и тест-классы для каждой сущности.
 */
public class TestGenerator {

    private final TestConfig config;

    public TestGenerator(TestConfig config) {
        this.config = config;
    }

    public void generate(AppModel model) throws IOException {
        Path baseOutput = config.getOutputDir();
        String basePackage = config.getBasePackage();

        // Каждый XML — в свою подпапку по имени файла, чтобы результаты разных XML не смешивались.
        Path outputDir = resolveProjectDir(baseOutput, config.getSourceXmlName());
        boolean isolated = !outputDir.equals(baseOutput);

        // Повторная генерация того же XML: полностью пересоздаём его подпапку (включая target/ со
        // старыми отчётами и скринами), чтобы не оставалось устаревших тестов и артефактов.
        if (isolated && Files.exists(outputDir)) {
            deleteRecursively(outputDir);
        }

        Path srcDir = outputDir.resolve("src/test/java");
        Files.createDirectories(srcDir);

        // Если имя XML неизвестно (подпапку не сделали) — чистим хотя бы ранее сгенерированный
        // пакет тестов/page object'ов, чтобы при смене вердикта классификатора не оставались
        // устаревшие тесты. SharedDriver/BaseTest/TestData всё равно перезаписываются.
        if (!isolated) {
            Path generatedRoot = srcDir.resolve(basePackage.replace('.', '/'));
            deleteRecursively(generatedRoot);
        }

        // pom.xml тест-проекта
        generatePom(outputDir);

        // junit-platform.properties (последовательный прогон — общий браузер)
        generateJUnitConfig(outputDir);

        // SharedDriver.java (общий экземпляр браузера)
        generateSharedDriver(srcDir, basePackage);

        // BaseTest.java
        generateBaseTest(srcDir, basePackage);

        // TestData.java
        generateTestData(srcDir, basePackage);

        // Классифицируем сущности и генерируем Page Object'ы / тест-классы только для PRIMARY.
        // CHILD-сущности живут как табы-гриды родителя; REFERENCE_DICTIONARY — справочники,
        // достижимые только через FK-поля в других формах. Отдельные тесты для них дают лишь
        // пропуски навигации — потерянное время и бессмысленные отчёты.
        PageObjectWriter pageWriter = new PageObjectWriter(basePackage);
        TestClassWriter testWriter = new TestClassWriter(basePackage, config.getTestLevel());
        StringBuilder csv = new StringBuilder("kind,entity,reason\n");
        int primary = 0, child = 0, ref = 0;
        for (EntityObject entity : model.getEntities()) {
            EntityClassifier.Classification cls = EntityClassifier.classify(entity, model);
            csv.append(cls.kind).append(",")
                    .append(csvEscape(entity.getName())).append(",")
                    .append(csvEscape(cls.reason)).append("\n");
            if (cls.kind == EntityKind.PRIMARY) {
                primary++;
                pageWriter.write(entity, srcDir);
                testWriter.write(entity, model, srcDir, null);
            } else if (cls.kind == EntityKind.CHILD) {
                child++;
                if (cls.parentEntity != null && cls.parentGrid != null) {
                    // дочерняя сущность-таб с гридом (например, История/Документы ГСК)
                    pageWriter.write(entity, srcDir);
                    testWriter.writeChildTest(entity, model, srcDir, cls);
                } else if (cls.parentEntity != null) {
                    pageWriter.write(entity, srcDir);
                    if (entity.hasCrudOperations() && cls.parentEntity.getPropertyGroups().isEmpty()) {
                        // Справочник-в-дереве (новый тип): родитель — пустой контейнер-меню
                        // «Справочник …», сама сущность имеет карточку и I/U/D. CRUD через ПКМ
                        // по узлу-контейнеру → подменю сущности → «Добавить» (модалка с «Готово»),
                        // изменение/удаление — «Редактирование → Сохранить Изменения / Удалить».
                        testWriter.writeTreeDictionaryCrudTest(entity, model, srcDir, cls);
                    } else {
                        // дочерний узел дерева (addFromTree=1, например, Повестка совещания) — открывается
                        // через карточку родителя и раскрытие узла дерева, а не из главного меню.
                        testWriter.writeTreeChildTest(entity, model, srcDir, cls);
                    }
                }
            } else {
                ref++;
            }
        }
        // Сохраняем отчёт о классификации рядом с тест-проектом — сразу видно, какие сущности
        // стали тестами, какие табами, какие справочниками.
        Path csvPath = outputDir.resolve("entity-classification.csv");
        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("Entity classification: " + primary + " PRIMARY, "
                + child + " CHILD (tab-grid), " + ref + " REFERENCE_DICTIONARY (FK target)");
        System.out.println("  Report: " + csvPath);

    }

    /** Имя подпапки для XML-файла: без пути и расширения, безопасное для файловой системы. */
    public static String folderNameForXml(String xmlFileName) {
        if (xmlFileName == null) return "";
        String n = xmlFileName.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        if (n.toLowerCase().endsWith(".xml")) n = n.substring(0, n.length() - 4);
        n = n.replaceAll("[^\\p{L}\\p{N}_.-]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return n;
    }

    /**
     * Папка тест-проекта для данного XML: baseOutput/&lt;имя XML&gt; (или сам baseOutput, если имя
     * не задано). Используется и при генерации, и при прогоне, чтобы оба смотрели в одну папку.
     */
    public static Path resolveProjectDir(Path baseOutput, String xmlFileName) {
        String sub = folderNameForXml(xmlFileName);
        return (sub == null || sub.isEmpty()) ? baseOutput : baseOutput.resolve(sub);
    }

    /** Рекурсивно удаляет каталог со всем содержимым (если существует). */
    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // Логика классификации сущностей — в ru.autotestgen.model.EntityClassifier.

    private void generatePom(Path outputDir) throws IOException {
        JavaFileWriter w = new JavaFileWriter();
        w.writeLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        w.writeLine("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"");
        w.writeLine("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        w.writeLine("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">");
        w.writeLine("    <modelVersion>4.0.0</modelVersion>");
        w.writeLine("    <groupId>generated</groupId>");
        w.writeLine("    <artifactId>generated-autotests</artifactId>");
        w.writeLine("    <version>1.0.0</version>");
        w.writeLine("    <properties>");
        w.writeLine("        <maven.compiler.source>17</maven.compiler.source>");
        w.writeLine("        <maven.compiler.target>17</maven.compiler.target>");
        w.writeLine("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>");
        // Дефолт forkCount=1 — последовательный прогон. Чтобы запустить параллельно:
        // mvn test -DforkCount=3  (или сколько Chrome-инстансов потянет стенд + машина).
        w.writeLine("        <forkCount>1</forkCount>");
        w.writeLine("    </properties>");
        w.writeLine("    <dependencies>");
        w.writeLine("        <dependency>");
        w.writeLine("            <groupId>org.seleniumhq.selenium</groupId>");
        w.writeLine("            <artifactId>selenium-java</artifactId>");
        w.writeLine("            <version>4.15.0</version>");
        w.writeLine("        </dependency>");
        w.writeLine("        <dependency>");
        w.writeLine("            <groupId>org.junit.jupiter</groupId>");
        w.writeLine("            <artifactId>junit-jupiter</artifactId>");
        w.writeLine("            <version>5.10.1</version>");
        w.writeLine("        </dependency>");
        w.writeLine("        <dependency>");
        w.writeLine("            <groupId>io.github.bonigarcia</groupId>");
        w.writeLine("            <artifactId>webdrivermanager</artifactId>");
        w.writeLine("            <version>5.6.2</version>");
        w.writeLine("        </dependency>");
        w.writeLine("    </dependencies>");
        w.writeLine("    <build>");
        w.writeLine("        <plugins>");
        w.writeLine("            <plugin>");
        w.writeLine("                <groupId>org.apache.maven.plugins</groupId>");
        w.writeLine("                <artifactId>maven-surefire-plugin</artifactId>");
        w.writeLine("                <version>3.2.2</version>");
        w.writeLine("                <configuration>");
        // forkCount=${forkCount} с дефолтом 1 — через -DforkCount=3 можно запустить параллельно
        // (каждый форк = свой JVM + своя ChromeDriver-сессия с уникальным user-data-dir).
        w.writeLine("                    <forkCount>${forkCount}</forkCount>");
        w.writeLine("                    <reuseForks>true</reuseForks>");
        // Перенаправляем stdout/stderr каждого тест-класса в target/surefire-reports/<class>-output.txt.
        // Без этого System.out (диагностика шагов) уходит только в общую консоль и теряется при
        // нескольких классах — а так у каждого класса свой полный лог.
        w.writeLine("                    <redirectTestOutputToFile>true</redirectTestOutputToFile>");
        w.writeLine("                    <forkedProcessExitTimeoutInSeconds>60</forkedProcessExitTimeoutInSeconds>");
        w.writeLine("                </configuration>");
        w.writeLine("            </plugin>");
        w.writeLine("        </plugins>");
        w.writeLine("    </build>");
        w.writeLine("    <reporting>");
        w.writeLine("        <plugins>");
        w.writeLine("            <plugin>");
        w.writeLine("                <groupId>org.apache.maven.plugins</groupId>");
        w.writeLine("                <artifactId>maven-surefire-report-plugin</artifactId>");
        w.writeLine("                <version>3.2.2</version>");
        w.writeLine("            </plugin>");
        w.writeLine("        </plugins>");
        w.writeLine("    </reporting>");
        w.writeLine("</project>");
        w.writeToFile(outputDir, "pom.xml");
    }

    private void generateJUnitConfig(Path outputDir) throws IOException {
        Path resourceDir = outputDir.resolve("src/test/resources");
        Files.createDirectories(resourceDir);
        JavaFileWriter w = new JavaFileWriter();
        w.writeLine("junit.jupiter.execution.parallel.enabled=false");
        w.writeToFile(resourceDir, "junit-platform.properties");
    }

    private void generateSharedDriver(Path srcDir, String basePackage) throws IOException {
        Path dir = srcDir.resolve(basePackage.replace('.', '/'));
        JavaFileWriter w = new JavaFileWriter();

        w.writeLine("package " + basePackage + ";");
        w.writeLine();
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.chrome.ChromeDriver;");
        w.writeLine("import org.openqa.selenium.chrome.ChromeOptions;");
        w.writeLine("import org.openqa.selenium.firefox.FirefoxDriver;");
        w.writeLine("import org.openqa.selenium.interactions.Actions;");
        w.writeLine("import org.openqa.selenium.support.ui.ExpectedConditions;");
        w.writeLine("import org.openqa.selenium.support.ui.WebDriverWait;");
        w.writeLine("import io.github.bonigarcia.wdm.WebDriverManager;");
        w.writeLine("import java.time.Duration;");
        w.writeLine("import java.util.ArrayList;");
        w.writeLine("import java.util.Collections;");
        w.writeLine("import java.util.LinkedHashSet;");
        w.writeLine("import java.util.List;");
        w.writeLine();
        w.writeLine("/**");
        w.writeLine(" * Shared browser instance for all test classes.");
        w.writeLine(" * Login, optional smoke of all subsystems, and selection of the configured");
        w.writeLine(" * subsystem all happen once; the driver is reused across all tests.");
        w.writeLine(" */");
        w.openBlock("public class SharedDriver");
        w.writeLine();
        w.writeLine("private static WebDriver driver;");
        w.writeLine("private static WebDriverWait wait;");
        w.writeLine("private static boolean initialized = false;");
        w.writeLine("private static final List<SmokeResult> smokeResults = new ArrayList<>();");
        w.writeLine();

        // Вложенный класс SmokeResult
        w.writeLine("/** Result of opening one subsystem during the smoke phase. */");
        w.openBlock("public static class SmokeResult");
        w.writeLine("public final String name;");
        w.writeLine("public final boolean ok;");
        w.writeLine("public final String error;");
        w.openBlock("public SmokeResult(String name, boolean ok, String error)");
        w.writeLine("this.name = name;");
        w.writeLine("this.ok = ok;");
        w.writeLine("this.error = error;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // getDriver()
        w.openBlock("public static synchronized WebDriver getDriver()");
        w.openBlock("if (!initialized)");
        w.writeLine("initialize();");
        w.closeBlock();
        w.writeLine("return driver;");
        w.closeBlock();
        w.writeLine();

        // getWait()
        w.openBlock("public static synchronized WebDriverWait getWait()");
        w.openBlock("if (!initialized)");
        w.writeLine("initialize();");
        w.closeBlock();
        w.writeLine("return wait;");
        w.closeBlock();
        w.writeLine();

        // getSmokeResults()
        w.writeLine("/** Returns the immutable list of smoke results recorded during initialize(). */");
        w.openBlock("public static synchronized List<SmokeResult> getSmokeResults()");
        w.openBlock("if (!initialized)");
        w.writeLine("initialize();");
        w.closeBlock();
        w.writeLine("return Collections.unmodifiableList(smokeResults);");
        w.closeBlock();
        w.writeLine();

        // initialize()
        w.openBlock("private static void initialize()");
        w.writeLine("setupDriver();");
        w.writeLine("performLogin();");
        w.writeLine();
        w.openBlock("if (TestData.SMOKE_ALL_SUBSYSTEMS)");
        w.writeLine("runSmokeAllSubsystems();");
        w.closeBlock();
        w.writeLine();
        w.openBlock("if (TestData.SUBSYSTEM_NAME != null && !TestData.SUBSYSTEM_NAME.isEmpty())");
        w.writeLine("selectSubsystem(TestData.SUBSYSTEM_NAME);");
        w.closeBlock();
        w.writeLine();
        w.writeLine("initialized = true;");
        w.writeLine("Runtime.getRuntime().addShutdownHook(new Thread(() -> {");
        w.writeLine("    if (driver != null) driver.quit();");
        w.writeLine("}));");
        w.closeBlock(); // end initialize()
        w.writeLine();

        // setupDriver() — вынесен отдельно, чтобы вызвать заново после quit() между подсистемами
        w.writeLine("/** Creates a fresh WebDriver / WebDriverWait, replacing any existing one. */");
        w.openBlock("private static void setupDriver()");
        w.writeLine("String browser = System.getProperty(\"browser\", \"" + config.getBrowserType() + "\");");
        w.openBlock("if (\"firefox\".equalsIgnoreCase(browser))");
        w.writeLine("WebDriverManager.firefoxdriver().setup();");
        w.writeLine("driver = new FirefoxDriver();");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("WebDriverManager.chromedriver().setup();");
        w.writeLine("ChromeOptions options = new ChromeOptions();");
        w.writeLine("options.addArguments(\"--remote-allow-origins=*\");");
        // У стенда rrpo.cmirit.ru самоподписанный сертификат — Chrome по умолчанию
        // показывает «Подключение не защищено / NET::ERR_CERT_AUTHORITY_INVALID» и
        // тест на первом get() висит. Эти флаги говорят Chrome принимать invalid
        // certs без интерстишала.
        w.writeLine("options.setAcceptInsecureCerts(true);");
        w.writeLine("options.addArguments(\"--ignore-certificate-errors\");");
        w.writeLine("options.addArguments(\"--allow-insecure-localhost\");");
        // Headless по флагу -Dheadless=true (быстрее ~20-30%, нет окна для наблюдения).
        // По умолчанию выключен — пользователь видит браузер. --headless=new = новый
        // headless-режим Chrome (старый --headless deprecated).
        w.openBlock("if (Boolean.parseBoolean(System.getProperty(\"headless\", \"false\")))");
        w.writeLine("options.addArguments(\"--headless=new\");");
        w.closeBlock();
        w.writeLine("options.addArguments(\"--no-sandbox\");");
        w.writeLine("options.addArguments(\"--disable-dev-shm-usage\");");
        w.writeLine("options.addArguments(\"--window-size=1920,1080\");");
        // Уникальный user-data-dir на каждый JVM-форк — без этого 2+ параллельных Chrome
        // дерутся за один профиль и падают «user data directory is already in use».
        w.writeLine("try {");
        w.writeLine("    java.nio.file.Path udd = java.nio.file.Files.createTempDirectory(\"chrome-udd-\");");
        w.writeLine("    options.addArguments(\"--user-data-dir=\" + udd.toAbsolutePath());");
        w.writeLine("} catch (Exception ignored) {}");
        w.writeLine("driver = new ChromeDriver(options);");
        w.closeBlock();
        w.writeLine("driver.manage().window().maximize();");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("wait = new WebDriverWait(driver, Duration.ofSeconds(3));");
        w.closeBlock();
        w.writeLine();

        // restartBrowserAndLogin() — закрываем текущий драйвер, создаём заново, логинимся с нуля
        w.writeLine("/** Quits the current driver, recreates it, and logs in. Used to isolate each smoke iteration. */");
        w.openBlock("private static void restartBrowserAndLogin()");
        w.openBlock("try");
        w.openBlock("if (driver != null)");
        w.writeLine("driver.quit();");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("setupDriver();");
        w.writeLine("performLogin();");
        w.closeBlock();
        w.writeLine();

        // performLogin()
        w.writeLine("/** Navigates to BASE_URL and submits the login form if it is visible. Idempotent. */");
        w.openBlock("private static void performLogin()");
        w.writeLine("driver.get(TestData.BASE_URL);");
        w.openBlock("if (TestData.LOGIN.isEmpty())");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.openBlock("try");
        w.writeLine("List<WebElement> loginFields = driver.findElements(By.cssSelector(\"input[name='login'], input[name='username'], input[type='text']\"));");
        w.writeLine("WebElement loginField = loginFields.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);");
        w.openBlock("if (loginField == null)");
        w.writeLine("// Already logged in — no form visible");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("loginField.clear();");
        w.writeLine("loginField.sendKeys(TestData.LOGIN);");
        w.writeLine("WebElement passField = driver.findElement(By.cssSelector(\"input[name='password'], input[type='password']\"));");
        w.writeLine("passField.clear();");
        w.writeLine("passField.sendKeys(TestData.PASSWORD);");
        w.writeLine("WebElement submitBtn = null;");
        w.openBlock("try");
        w.writeLine("submitBtn = driver.findElement(By.cssSelector(\"button[type='submit'], input[type='submit']\"));");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("if (submitBtn == null)");
        w.openBlock("try");
        w.writeLine("submitBtn = driver.findElement(By.xpath(\"//button[contains(text(),'OK')] | //input[@value='OK'] | //button[contains(text(),'\\u0412\\u043e\\u0439\\u0442\\u0438')] | //input[@value='\\u0412\\u043e\\u0439\\u0442\\u0438'] | //button[contains(text(),'Ok')] | //input[@value='Ok'] | //a[contains(text(),'OK')]\"));");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (submitBtn == null)");
        w.openBlock("try");
        w.writeLine("submitBtn = driver.findElement(By.cssSelector(\"button, input[type='button']\"));");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (submitBtn != null)");
        w.writeLine("submitBtn.click();");
        w.closeBlock();
        w.writeLine("Thread.sleep(2000);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Login form not found or login failed: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock(); // end performLogin
        w.writeLine();

        // selectSubsystem(name) — перебирает варианты тегов, в которых лаунчер может рендерить
        // название плитки (b/span/div/a). Широкий локатор плюс увеличенное ожидание, иначе на
        // некоторых сборках выбор подсистемы не срабатывает и все тесты пропускаются.
        w.writeLine("/** Double-clicks the tile of the given subsystem and verifies the launcher was left. */");
        w.openBlock("private static boolean selectSubsystem(String name)");
        w.openBlock("try");
        w.writeLine("WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(12));");
        w.writeLine("WebElement tile = longWait.until(ExpectedConditions.elementToBeClickable(");
        w.writeLine("    By.xpath(\"//b[contains(normalize-space(.), '\" + name + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'tile') or contains(@class,'panel-header')][contains(normalize-space(.), '\" + name + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'tile') or contains(@class,'subsystem')][contains(normalize-space(.), '\" + name + \"')]\"");
        w.writeLine("    + \" | //a[contains(normalize-space(.), '\" + name + \"')]\"");
        w.writeLine("    + \" | //*[self::div or self::span or self::a or self::b][contains(@title, '\" + name + \"')]\")));");
        w.writeLine("new Actions(driver).doubleClick(tile).perform();");
        w.writeLine("Thread.sleep(2000);");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.openBlock("try");
        w.writeLine("List<WebElement> menuBtns = driver.findElements(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + name + \"')]\"));");
        w.openBlock("if (menuBtns.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("System.out.println(\"Subsystem '\" + name + \"' selected (menu button)\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("List<WebElement> tabs = driver.findElements(By.cssSelector(\".x-tab-strip-text, .x-tab-strip-active\"));");
        w.openBlock("if (tabs.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("System.out.println(\"Subsystem '\" + name + \"' selected (tab strip visible)\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("List<WebElement> header = driver.findElements(By.xpath(\"//*[contains(text(), '\\u0414\\u043e\\u0441\\u0442\\u0443\\u043f\\u043d\\u044b\\u0435 \\u043f\\u043e\\u0434\\u0441\\u0438\\u0441\\u0442\\u0435\\u043c\\u044b')]\"));");
        w.openBlock("if (header.stream().noneMatch(WebElement::isDisplayed))");
        w.writeLine("System.out.println(\"Subsystem '\" + name + \"' selected (launcher header gone)\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Subsystem selection failed for '\" + name + \"': \" + e.getMessage().split(\"\\n\")[0]);");
        // Диагностика: какие видимые элементы-плитки вообще нашлись?
        w.openBlock("try");
        w.writeLine("List<WebElement> any = driver.findElements(By.xpath(\"//*[self::b or self::span[contains(@class,'tile') or contains(@class,'panel-header')] or self::div[contains(@class,'tile')] or self::a]\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement el : any)");
        w.openBlock("try");
        w.openBlock("if (!el.isDisplayed() || shown >= 12)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String txt = el.getText() == null ? \"\" : el.getText().trim();");
        w.openBlock("if (txt.isEmpty() || txt.length() > 80)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  visible launcher element: <\" + el.getTagName() + \" class='\" + el.getAttribute(\"class\") + \"'> '\" + txt + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (any.isEmpty())");
        w.writeLine("System.out.println(\"  (no launcher tiles visible at all — login may have failed or page not loaded; try increasing wait or check session)\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock(); // end selectSubsystem
        w.writeLine();

        // discoverSubsystems — тот же широкий xpath, чтобы smoke-режим тоже находил плитки.
        w.writeLine("/** Returns display names of all visible subsystem tiles on the launcher screen. */");
        w.openBlock("private static List<String> discoverSubsystems()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.openBlock("try");
        w.writeLine("List<WebElement> tiles = driver.findElements(By.xpath(");
        w.writeLine("    \"//b\"");
        w.writeLine("    + \" | //span[contains(@class,'tile') or contains(@class,'panel-header')]\"");
        w.writeLine("    + \" | //div[contains(@class,'tile') or contains(@class,'subsystem')]\"));");
        w.writeLine("LinkedHashSet<String> names = new LinkedHashSet<>();");
        w.openBlock("for (WebElement tile : tiles)");
        w.openBlock("try");
        w.openBlock("if (!tile.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String text = tile.getText();");
        w.openBlock("if (text == null)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String trimmed = text.trim();");
        w.openBlock("if (trimmed.length() < 3)");
        w.writeLine("continue;");
        w.closeBlock();
        w.openBlock("if (!trimmed.chars().anyMatch(Character::isLetter))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("names.add(stripTechSuffix(trimmed));");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return new ArrayList<>(names);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Subsystem discovery failed: \" + e.getMessage());");
        w.writeLine("return new ArrayList<>();");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock(); // end discoverSubsystems
        w.writeLine();

        // stripTechSuffix(text)
        w.writeLine("/** Strips a trailing technical code such as ' - AIS_GSK@DEMJKX' from a subsystem tile label. */");
        w.openBlock("private static String stripTechSuffix(String text)");
        w.writeLine("int idx = text.lastIndexOf(\" - \");");
        w.openBlock("if (idx <= 0)");
        w.writeLine("return text;");
        w.closeBlock();
        w.writeLine("String tail = text.substring(idx + 3).trim();");
        w.openBlock("if (tail.contains(\"@\") || tail.matches(\"[A-Z][A-Z0-9_]+\"))");
        w.writeLine("return text.substring(0, idx).trim();");
        w.closeBlock();
        w.writeLine("return text;");
        w.closeBlock();
        w.writeLine();

        // runSmokeAllSubsystems()
        w.writeLine("/**");
        w.writeLine(" * Discovers all subsystems on the launcher, then opens each one in an isolated browser session:");
        w.writeLine(" * the browser is fully restarted between subsystems so leftover state from one cannot mask the next.");
        w.writeLine(" * After smoke, the browser is restarted one final time so entity tests start from the launcher.");
        w.writeLine(" */");
        w.openBlock("private static void runSmokeAllSubsystems()");
        w.writeLine("List<String> names = discoverSubsystems();");
        w.writeLine("System.out.println(\"Discovered \" + names.size() + \" subsystem(s): \" + names);");
        w.openBlock("for (int i = 0; i < names.size(); i++)");
        w.writeLine("String name = names.get(i);");
        w.writeLine("boolean ok = false;");
        w.writeLine("String error = null;");
        w.openBlock("try");
        // Перезапускаем браузер перед каждой итерацией кроме первой — первая использует уже
        // залогиненную сессию из initialize().
        w.openBlock("if (i > 0)");
        w.writeLine("System.out.println(\"Restarting browser for subsystem #\" + (i + 1) + \": \" + name);");
        w.writeLine("restartBrowserAndLogin();");
        w.closeBlock();
        w.writeLine("ok = selectSubsystem(name);");
        w.openBlock("if (!ok)");
        w.writeLine("error = \"Subsystem did not open. URL=\" + safeUrl() + \" screenshot=\" + dumpFailureScreenshot(name);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("error = e.getMessage() + \" (URL=\" + safeUrl() + \" screenshot=\" + dumpFailureScreenshot(name) + \")\";");
        w.closeBlock();
        w.writeLine("smokeResults.add(new SmokeResult(name, ok, error));");
        w.closeBlock();
        // Финальный перезапуск, чтобы тесты сущностей стартовали с чистой сессии в лаунчере
        w.openBlock("if (!names.isEmpty())");
        w.writeLine("System.out.println(\"Smoke phase done — restarting browser for entity tests\");");
        w.writeLine("restartBrowserAndLogin();");
        w.closeBlock();
        w.closeBlock(); // end runSmokeAllSubsystems
        w.writeLine();

        // safeUrl()
        w.openBlock("private static String safeUrl()");
        w.openBlock("try");
        w.writeLine("return driver.getCurrentUrl();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return \"?\";");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // dumpFailureScreenshot()
        w.openBlock("private static String dumpFailureScreenshot(String name)");
        w.openBlock("try");
        w.writeLine("java.io.File src = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.FILE);");
        w.writeLine("java.nio.file.Path dir = java.nio.file.Path.of(\"target/screenshots\");");
        w.writeLine("java.nio.file.Files.createDirectories(dir);");
        w.writeLine("String fname = \"smoke-failed-\" + name.replaceAll(\"[^a-zA-Z0-9\\u0400-\\u04FF]+\", \"_\") + \".png\";");
        w.writeLine("java.nio.file.Path target = dir.resolve(fname);");
        w.writeLine("java.nio.file.Files.copy(src.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);");
        w.writeLine("return \"target/screenshots/\" + fname;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return \"(screenshot failed: \" + e.getMessage() + \")\";");
        w.closeBlock();
        w.closeBlock();

        w.closeBlock(); // end class

        w.writeToFile(dir, "SharedDriver.java");
    }

    private void generateBaseTest(Path srcDir, String basePackage) throws IOException {
        Path dir = srcDir.resolve(basePackage.replace('.', '/'));
        JavaFileWriter w = new JavaFileWriter();

        w.writeLine("package " + basePackage + ";");
        w.writeLine();
        w.writeLine("import org.junit.jupiter.api.Assumptions;");
        w.writeLine("import org.junit.jupiter.api.BeforeAll;");
        w.writeLine("import org.junit.jupiter.api.TestInfo;");
        w.writeLine("import org.junit.jupiter.api.TestInstance;");
        w.writeLine("import org.openqa.selenium.Alert;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import org.openqa.selenium.NoAlertPresentException;");
        w.writeLine("import org.openqa.selenium.OutputType;");
        w.writeLine("import org.openqa.selenium.TakesScreenshot;");
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.interactions.Actions;");
        w.writeLine("import org.openqa.selenium.support.ui.ExpectedConditions;");
        w.writeLine("import org.openqa.selenium.support.ui.WebDriverWait;");
        w.writeLine("import java.io.File;");
        w.writeLine("import java.nio.file.Files;");
        w.writeLine("import java.nio.file.Path;");
        w.writeLine("import java.nio.file.StandardCopyOption;");
        w.writeLine("import java.time.Duration;");
        w.writeLine("import java.util.List;");
        w.writeLine();
        w.writeLine("@TestInstance(TestInstance.Lifecycle.PER_CLASS)");
        w.openBlock("public abstract class BaseTest");
        w.writeLine();
        w.writeLine("protected WebDriver driver;");
        w.writeLine("protected WebDriverWait wait;");
        w.writeLine("protected boolean navigationOk = false;");
        // Кэш: навигация выполняется не более одного раза на тест-класс. После неудачной
        // первой попытки остальные методы не повторяют 10-секундный поиск по меню.
        w.writeLine("protected boolean navigationAttempted = false;");
        w.writeLine("protected boolean cachedNavigationOk = false;");
        // Кэш открытия карточки: после неудачной первой попытки openRecordCard следующие
        // testGrid* в этом классе пропускают перебор из 5 стратегий (по ~30-50с каждая).
        w.writeLine("protected boolean cardOpenAttempted = false;");
        w.writeLine("private boolean cachedCardOpenOk = false;");
        // Кэш диалога добавления: если waitForDialog один раз истёк по таймауту, считаем диалог
        // недоступным. Последующие CRUD-тесты (testCreate, testUpdate, …) сразу возвращают false,
        // не дожидаясь ещё 4с каждый.
        w.writeLine("protected boolean addDialogFailed = false;");
        // Учёт скриншотов: currentTestName берётся в @BeforeEach, stepCounter сбрасывается на каждый тест.
        w.writeLine("protected String currentTestName = \"test\";");
        w.writeLine("protected int stepCounter = 0;");
        w.writeLine();

        // BeforeAll — берём драйвер из SharedDriver
        w.writeLine("@BeforeAll");
        w.openBlock("void initDriver()");
        w.writeLine("driver = SharedDriver.getDriver();");
        w.writeLine("wait = SharedDriver.getWait();");
        w.closeBlock();
        w.writeLine();

        // BeforeEach в BaseTest — запоминаем имя теста для скриншотов и сбрасываем счётчик шагов.
        // Выполняется ДО @BeforeEach подкласса (порядок JUnit 5: родитель первым).
        w.writeLine("@org.junit.jupiter.api.BeforeEach");
        w.openBlock("void initTestContext(TestInfo info)");
        w.writeLine("this.currentTestName = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse(\"test\");");
        w.writeLine("this.stepCounter = 0;");
        w.closeBlock();
        w.writeLine();

        // Хелпер: выполнить действие через меню E3Core (например, Добавить, Найти)
        w.openBlock("protected void menuAction(String entityName, String actionName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"НСИ\", \"Отчёты\", \"Сервис\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("menuBtn.click();");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(\"//span[contains(@class, 'x-menu-item-text')][contains(text(), '\" + entityName + \"')] | //div[contains(@class, 'x-menu')]//*[contains(text(), '\" + entityName + \"')]\"));");
        w.openBlock("if (!items.isEmpty())");
        w.writeLine("new Actions(driver).moveToElement(items.get(0)).perform();");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("List<WebElement> actionBtns = driver.findElements(By.xpath(\"//span[contains(@class, 'x-menu-item-text')][contains(text(), '\" + actionName + \"')] | //a[contains(@class, 'x-menu-item')][contains(text(), '\" + actionName + \"')]\"));");
        w.openBlock("if (!actionBtns.isEmpty())");
        w.writeLine("actionBtns.get(actionBtns.size() - 1).click();");
        w.writeLine("Thread.sleep(1000);");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored)");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored2)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: навигация к сущности. Первый вызов делает реальную работу; последующие в том же
        // экземпляре класса возвращают кэш, чтобы неудачная навигация не стоила 10с на каждый
        // @BeforeEach. Перегрузка с 2 аргументами — для сущностей с формой поиска «Найти».
        w.openBlock("protected void navigateToEntity(String entityName, String featureName)");
        w.writeLine("navigateToEntity(entityName, featureName, true);");
        w.closeBlock();
        w.writeLine();
        // hasSearchForm=false для справочников-списков, открываемых прямым кликом (своих поисков
        // нет). Для них шаг «Дерево поисков → по параметрам → Выполнить поиск» лишний: грид уже
        // открыт самим кликом по пункту меню, а попытка найти несуществующую форму поиска лишь
        // тратит ~9с и рискует оставить окно поиска поверх грида.
        w.openBlock("protected void navigateToEntity(String entityName, String featureName, boolean hasSearchForm)");
        w.openBlock("if (navigationAttempted)");
        w.writeLine("navigationOk = cachedNavigationOk;");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("navigationAttempted = true;");
        w.writeLine("navigationOk = false;");
        w.openBlock("if (\"e3core\".equals(TestData.SITE_TYPE))");
        w.writeLine("navigateE3Core(entityName);");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("navigateGeneric(entityName);");
        w.closeBlock();
        // После «Найти» открывается страница параметров — автоматически запускаем пустой поиск,
        // чтобы наполнить грид результатов. Иначе isFieldDisplayed видит только форму параметров
        // (Тип/Наименование) и сообщает «Fields found: 0 of N».
        w.openBlock("if (navigationOk && hasSearchForm)");
        // После «Найти» E3Core открывает окно «Дерево поисков», но форма пустая, пока не сделать
        // двойной клик по узлу «по параметрам». Без этого шага форма параметров не отрисуется и
        // executeSearchIfPresent не найдёт свою кнопку.
        w.writeLine("openParamSearchInTree();");
        w.writeLine("executeSearchIfPresent();");
        w.closeBlock();
        // Прямой клик (hasSearchForm=false): грид уже открыт — просто дождёмся его стабилизации.
        w.openBlock("else if (navigationOk)");
        w.writeLine("waitForGridSettle();");
        w.closeBlock();
        w.writeLine("cachedNavigationOk = navigationOk;");
        w.openBlock("if (!navigationOk)");
        w.writeLine("System.out.println(\"Could not navigate to entity: \" + entityName);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // openParamSearchInTree: после открытия окна «Дерево поисков» делает двойной клик по узлу
        // «по параметрам», чтобы справа отрисовалась форма параметров. Если двойной клик
        // проигнорирован, откатывается на одиночный клик через JS.
        w.openBlock("protected void openParamSearchInTree()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Ждём появления узла дерева до 4с.
        w.writeLine("WebElement node = null;");
        w.writeLine("long deadline = System.currentTimeMillis() + 4000;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("List<WebElement> hits = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tree-node-text')][contains(normalize-space(.),'\\u043f\\u043e \\u043f\\u0430\\u0440\\u0430\\u043c\\u0435\\u0442\\u0440\\u0430\\u043c')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.),'\\u043f\\u043e \\u043f\\u0430\\u0440\\u0430\\u043c\\u0435\\u0442\\u0440\\u0430\\u043c')]]\"));");
        w.openBlock("for (WebElement h : hits)");
        w.openBlock("try");
        w.openBlock("if (h.isDisplayed())");
        w.writeLine("node = h;");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (node != null)");
        w.writeLine("break;");
        w.closeBlock();
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.openBlock("if (node == null)");
        w.writeLine("System.out.println(\"openParamSearchInTree: tree node '\\u043f\\u043e \\u043f\\u0430\\u0440\\u0430\\u043c\\u0435\\u0442\\u0440\\u0430\\u043c' not found within 4s — search window may not be open\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"openParamSearchInTree: double-clicking 'по параметрам'\");");
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(node).doubleClick().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception eDbl)");
        w.writeLine("System.out.println(\"  double-click failed: \" + eDbl.getClass().getSimpleName() + \" — falling back to JS click\");");
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].click(); arguments[0].click();\", node);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored2)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("Thread.sleep(1500);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"openParamSearchInTree failed: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // executeSearchIfPresent: жмёт «Выполнить поиск», если кнопка видна, иначе ничего не делает.
        // Вызывается после навигации, чтобы грид результатов был наполнен до старта тестов.
        w.openBlock("protected void executeSearchIfPresent()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        // Опрашиваем кнопку до 5с — страница может ещё грузиться после клика «Найти».
        w.writeLine("WebElement btn = null;");
        w.writeLine("long deadline = System.currentTimeMillis() + 5000;");
        w.openBlock("while (System.currentTimeMillis() < deadline && btn == null)");
        w.writeLine("btn = findVisibleSearchButton();");
        w.openBlock("if (btn == null)");
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (btn == null)");
        // Диагностический дамп — чтобы видеть, что было на странице
        w.writeLine("String url = \"\";");
        w.writeLine("String title = \"\";");
        w.openBlock("try");
        w.writeLine("url = driver.getCurrentUrl();");
        w.writeLine("title = driver.getTitle();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"executeSearchIfPresent: '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a' button NOT FOUND after 5s. URL=\" + url + \" Title='\" + title + \"'\");");
        // Выводим всё, что содержит «Выпол», чтобы увидеть варианты кнопок
        w.writeLine("List<WebElement> hints = driver.findElements(By.xpath(\"//*[contains(normalize-space(.), '\\u0412\\u044b\\u043f\\u043e\\u043b')]\"));");
        w.writeLine("int dumpCount = 0;");
        w.openBlock("for (WebElement h : hints)");
        w.openBlock("try");
        w.openBlock("if (!h.isDisplayed() || dumpCount >= 5)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String t = h.getTagName() + \"[\" + (h.getAttribute(\"class\") == null ? \"\" : h.getAttribute(\"class\")) + \"]: \" + h.getText().trim();");
        w.openBlock("if (t.length() > 200)");
        w.writeLine("t = t.substring(0, 200) + \"...\";");
        w.closeBlock();
        w.writeLine("System.out.println(\"  hint: \" + t);");
        w.writeLine("dumpCount++;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"executeSearchIfPresent: clicking <\" + btn.getTagName() + \"> with text '\" + btn.getText().trim() + \"'\");");
        w.writeLine("clickSafely(btn);");
        // Вместо слепой паузы 2с ждём, пока грид перестанет меняться.
        w.writeLine("waitForGridSettle();");
        w.writeLine("List<WebElement> gridRows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row\"));");
        w.writeLine("long visibleRows = gridRows.stream().filter(WebElement::isDisplayed).count();");
        w.writeLine("System.out.println(\"executeSearchIfPresent: result grid now has \" + visibleRows + \" visible row(s)\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"executeSearchIfPresent failed: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // findVisibleSearchButton: возвращает первый видимый триггер «Выполнить поиск» или null.
        // Отсеивает контейнеры/окна, в тексте которых есть подпись, но которые не являются кнопками.
        w.openBlock("private WebElement findVisibleSearchButton()");
        w.openBlock("try");
        w.writeLine("List<WebElement> candidates = driver.findElements(By.xpath(");
        // div исключён — заголовок окна дерева поисков тоже содержит эту подпись.
        w.writeLine("    \"//*[self::button or self::a or self::input or self::span or self::td or self::em]\"");
        w.writeLine("    + \"[contains(normalize-space(.), '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a')\"");
        w.writeLine("    + \"   or contains(@title, '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a')\"");
        w.writeLine("    + \"   or contains(@aria-label, '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a')\"");
        w.writeLine("    + \"   or contains(@data-qtip, '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a')\"");
        w.writeLine("    + \"   or contains(@value, '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a')]\"));");
        w.openBlock("for (WebElement c : candidates)");
        w.openBlock("try");
        w.openBlock("if (!c.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        // У настоящей кнопки текст короткий. Заголовок окна дерева поисков — 200+ символов, иначе
        // клик ушёл бы в него.
        w.writeLine("String txt = c.getText() == null ? \"\" : c.getText().trim();");
        w.openBlock("if (txt.length() > 60)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("return c;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return null;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return null;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Навигация E3Core: каскадные меню ExtJS с рекурсивным спуском по подменю
        w.openBlock("private void navigateE3Core(String entityName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"\\u041d\\u0421\\u0418\", \"\\u041e\\u0442\\u0447\\u0451\\u0442\\u044b\", \"\\u0421\\u0435\\u0440\\u0432\\u0438\\u0441\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("menuBtn.click();");
        w.writeLine("Thread.sleep(300);");
        w.openBlock("if (descendMenu(entityName, \"\\u041d\\u0430\\u0439\\u0442\\u0438\", 3, new java.util.HashSet<>()))");
        w.writeLine("navigationOk = true;");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored)");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored2)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock(); // end for
        w.closeBlock(); // end try
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock(); // end navigateE3Core
        w.writeLine();

        // addViaMenu: открывает диалог «Добавить» через главное меню — тот же путь, что
        // navigateE3Core для «Найти», но кликает пункт «Добавить» в подменю сущности.
        // Форма создания открывается именно так, а не из карточки записи (в карточке есть
        // только «Сохранить Изменения» / «Удалить»).
        w.openBlock("protected boolean addViaMenu(String entityName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        // Очистка перед открытием меню: закрываем все x-window'ы и шлём ESC. Без этого, если
        // предыдущий тест оставил диалог с ошибками валидации, новый «Добавить» открывается
        // поверх него, и waitForAddForm видит старую кнопку «Готово» — данные уйдут в чужой диалог.
        w.openBlock("try");
        w.writeLine("java.util.List<WebElement> closes = driver.findElements(By.cssSelector(\".x-window .x-tool-close, .x-window .x-window-close\"));");
        w.openBlock("for (WebElement c : closes)");
        w.openBlock("try");
        w.openBlock("if (c.isDisplayed())");
        w.writeLine("c.click(); Thread.sleep(150);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"\\u041d\\u0421\\u0418\", \"\\u041e\\u0442\\u0447\\u0451\\u0442\\u044b\", \"\\u0421\\u0435\\u0440\\u0432\\u0438\\u0441\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("menuBtn.click();");
        w.writeLine("Thread.sleep(300);");
        // Добавить = "Добавить"
        w.openBlock("if (descendMenu(entityName, \"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\", 3, new java.util.HashSet<>()))");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored)");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored2)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"addViaMenu failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // descendMenu: рекурсивный обход подменю
        w.writeLine("/**");
        w.writeLine(" * Searches the currently open ExtJS menus (and their submenus, up to maxDepth)");
        w.writeLine(" * for an item whose text matches entityName by per-word stems (so \"\\u0414\\u043e\\u043b\\u0436\\u043d\\u043e\\u0441\\u0442\\u043d\\u043e\\u0435 \\u043b\\u0438\\u0446\\u043e\" finds");
        w.writeLine(" * \"\\u0414\\u043e\\u043b\\u0436\\u043d\\u043e\\u0441\\u0442\\u043d\\u044b\\u0435 \\u043b\\u0438\\u0446\\u0430\" too). If found, hovers it and clicks \"\\u041d\\u0430\\u0439\\u0442\\u0438\" (or \"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\") in the");
        w.writeLine(" * revealed submenu; falls back to a direct click on the item itself.");
        w.writeLine(" */");
        w.openBlock("private boolean descendMenu(String entityName, String actionName, int maxDepth, java.util.Set<String> tried) throws InterruptedException");
        w.writeLine("String pred = entityStemPredicate(entityName, \"text()\");");
        w.writeLine("String predDeep = entityStemPredicate(entityName, \"normalize-space(.)\");");
        w.writeLine("String itemXpath = \"//div[contains(@class,'x-menu')]\"");
        w.writeLine("    + \"//span[contains(@class,'x-menu-item-text')][\" + pred + \"]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-menu')]\"");
        w.writeLine("    + \"//a[contains(@class,'x-menu-item')][\" + predDeep + \"]\";");
        w.writeLine("List<WebElement> directHits = driver.findElements(By.xpath(itemXpath));");
        // Стем-предикат матчит по основам слов (Должностн*+лиц*) → совпадает СРАЗУ с несколькими
        // похожими пунктами («Должностное лицо» И «Должностные лица»). Чтобы наводиться на НУЖНЫЙ
        // (а не на список без «Добавить»), сперва пробуем пункты с ТОЧНЫМ именем сущности.
        w.writeLine("java.util.List<WebElement> ordered = new java.util.ArrayList<>();");
        w.writeLine("java.util.List<WebElement> __rest = new java.util.ArrayList<>();");
        w.writeLine("String __wantName = entityName.trim();");
        w.openBlock("for (WebElement __h : directHits)");
        w.openBlock("try");
        w.writeLine("String __t = __h.getText().trim();");
        w.writeLine("if (__t.equalsIgnoreCase(__wantName)) ordered.add(__h); else __rest.add(__h);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.writeLine("__rest.add(__h);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("ordered.addAll(__rest);");
        w.openBlock("for (WebElement item : ordered)");
        w.openBlock("try");
        w.openBlock("if (!item.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(item).perform();");
        w.writeLine("Thread.sleep(250);");
        w.writeLine("WebElement actionBtn = findVisibleActionBtn(actionName);");
        // Fallback на «Открыть» только когда искали «Найти» — это исторический эквивалент.
        // Для «Добавить»/«Удалить» fallback'а нет.
        w.openBlock("if (actionBtn == null && \"\\u041d\\u0430\\u0439\\u0442\\u0438\".equals(actionName))");
        w.writeLine("actionBtn = findVisibleActionBtn(\"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\");");
        w.closeBlock();
        w.openBlock("if (actionBtn != null)");
        // Пробуем несколько стратегий клика — пункты меню ExtJS иногда игнорируют обычный
        // WebElement.click(), т.к. обработчик висит на mousedown/mouseup или соседнем элементе.
        w.writeLine("String actionLabel = actionBtn.getText().trim();");
        w.writeLine("System.out.println(\"descendMenu: trying to click '\" + actionLabel + \"' for entity '\" + entityName + \"'\");");
        w.writeLine("tryClickAllWays(actionBtn);");
        w.writeLine("Thread.sleep(700);");
        w.writeLine("captureNavScreenshot(entityName, \"after-action\");");
        w.writeLine("return true;");
        w.closeBlock();
        // Direct-click по пункту меню допустим ТОЛЬКО для навигации (Найти/Открыть) — клик по
        // самому пункту открывает поиск сущности. Для «Добавить»/«Удалить» такой клик откроет НЕ
        // ту форму (поиск вместо добавления), поэтому пункт без нужного подменю ПРОПУСКАЕМ и
        // пробуем следующий совпавший пункт / подменю (например singular «Должностное лицо» с
        // «Добавить», когда первым совпал plural «Должностные лица» без него).
        w.openBlock("if (\"\\u041d\\u0430\\u0439\\u0442\\u0438\".equals(actionName) || \"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\".equals(actionName))");
        w.writeLine("System.out.println(\"descendMenu: no 'Найти'/'Открыть' submenu — clicking item directly for '\" + entityName + \"'\");");
        w.writeLine("tryClickAllWays(item);");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("captureNavScreenshot(entityName, \"after-direct\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("System.out.println(\"descendMenu: пункт '\" + entityName + \"' без подменю '\" + actionName + \"' — пробуем следующий совпавший пункт/подменю\");");
        w.closeBlock(); // end try (for-item)
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Шаг 2: напрямую не нашли — ищем родительские пункты с подменю и рекурсивно спускаемся
        w.openBlock("if (maxDepth <= 0)");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("String parentXpath = \"//div[contains(@class,'x-menu')]\"");
        w.writeLine("    + \"//a[contains(@class,'x-menu-item')]\";");
        w.writeLine("List<WebElement> parents = driver.findElements(By.xpath(parentXpath));");
        w.writeLine("java.util.LinkedHashSet<String> parentTexts = new java.util.LinkedHashSet<>();");
        w.openBlock("for (WebElement p : parents)");
        w.openBlock("try");
        w.openBlock("if (!p.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String t = p.getText().trim();");
        w.openBlock("if (t.isEmpty() || t.contains(entityName) || tried.contains(t))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("parentTexts.add(t);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("for (String parentText : parentTexts)");
        w.writeLine("tried.add(parentText);");
        w.openBlock("try");
        // Перенаходим по полному видимому тексту. Текст лежит во вложенном
        // <span class="x-menu-item-text">, поэтому берём текст потомков через normalize-space(.).
        w.openBlock("if (parentText.contains(\"'\"))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("List<WebElement> candidates = driver.findElements(By.xpath(");
        w.writeLine("    \"//div[contains(@class,'x-menu')]//a[contains(@class,'x-menu-item')][contains(normalize-space(.), '\" + parentText + \"')]\"));");
        w.writeLine("WebElement candidate = null;");
        w.openBlock("for (WebElement c : candidates)");
        w.openBlock("try");
        w.openBlock("if (c.isDisplayed())");
        w.writeLine("candidate = c;");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (candidate == null)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(candidate).perform();");
        w.writeLine("Thread.sleep(250);");
        w.openBlock("if (descendMenu(entityName, actionName, maxDepth - 1, tried))");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock(); // end descendMenu
        w.writeLine();

        // entityStemPredicate: строит XPath-предикат, совпадающий по основам слов.
        // Для «Должностное лицо» → contains(text(),'Должностн') and contains(text(),'лиц').
        // Это покрывает пункты меню ExtJS в другом склонении (множественное/родительный и т.п.).
        w.writeLine("/** Builds an XPath predicate that matches an entity by per-word stems (handles Russian declensions). */");
        w.openBlock("protected String entityStemPredicate(String entityName, String textFn)");
        w.writeLine("String[] words = entityName.split(\"[\\\\s/]+\");");
        w.writeLine("StringBuilder sb = new StringBuilder();");
        w.writeLine("boolean first = true;");
        w.openBlock("for (String word : words)");
        w.writeLine("word = word.trim();");
        w.openBlock("if (word.isEmpty() || word.contains(\"'\"))");
        w.writeLine("continue;");
        w.closeBlock();
        // Основа: у длинного слова отбрасываем 2 последних символа; короткие оставляем как есть.
        w.writeLine("String stem = word.length() <= 3 ? word : word.substring(0, word.length() - 2);");
        w.openBlock("if (!first)");
        w.writeLine("sb.append(\" and \");");
        w.closeBlock();
        w.writeLine("sb.append(\"contains(\").append(textFn).append(\", '\").append(stem).append(\"')\");");
        w.writeLine("first = false;");
        w.closeBlock();
        w.openBlock("if (sb.length() == 0)");
        // Откат на точный contains(), если основы не построились (например, во всех словах апострофы)
        w.writeLine("sb.append(\"contains(\").append(textFn).append(\", '\").append(entityName.replace(\"'\", \"\")).append(\"')\");");
        w.closeBlock();
        w.writeLine("return sb.toString();");
        w.closeBlock();
        w.writeLine();

        // findVisibleActionBtn: находит видимый пункт меню с нужной подписью действия (Найти, Открыть, …).
        w.writeLine("/** Returns the last currently-visible menu item whose label contains actionName, or null. */");
        w.openBlock("protected WebElement findVisibleActionBtn(String actionName)");
        w.writeLine("List<WebElement> hits = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-menu-item-text')][contains(text(),'\" + actionName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.), '\" + actionName + \"')]\"));");
        w.writeLine("WebElement last = null;");
        w.openBlock("for (WebElement b : hits)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed())");
        w.writeLine("last = b;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return last;");
        w.closeBlock();
        w.writeLine();

        // tryClickAllWays: пробует обычный click, затем Actions.click(), затем JS-клик.
        // Пункты меню ExtJS иногда игнорируют один из вариантов из-за привязки событий.
        w.writeLine("/** Try standard click, then Actions.click(), then JS click — to defeat ExtJS event-binding quirks. */");
        w.openBlock("protected void tryClickAllWays(WebElement el)");
        w.openBlock("try");
        w.writeLine("el.click();");
        w.writeLine("System.out.println(\"  click strategy 1 (plain): OK\");");
        w.writeLine("return;");
        w.closeBlock();
        w.openBlock("catch (Exception e1)");
        w.writeLine("System.out.println(\"  click strategy 1 (plain) failed: \" + e1.getClass().getSimpleName());");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(el).click().perform();");
        w.writeLine("System.out.println(\"  click strategy 2 (Actions): OK\");");
        w.writeLine("return;");
        w.closeBlock();
        w.openBlock("catch (Exception e2)");
        w.writeLine("System.out.println(\"  click strategy 2 (Actions) failed: \" + e2.getClass().getSimpleName());");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].click();\", el);");
        w.writeLine("System.out.println(\"  click strategy 3 (JS): OK\");");
        w.closeBlock();
        w.openBlock("catch (Exception e3)");
        w.writeLine("System.out.println(\"  click strategy 3 (JS) failed: \" + e3.getClass().getSimpleName());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // captureNavScreenshot: сохраняет скриншот в target/screenshots/post-nav-<entity>-<phase>.png
        // и логирует видимые заголовки табов/панелей, чтобы понять, куда мы попали.
        w.writeLine("/** Saves a screenshot and logs visible tab strip titles after a navigation attempt. */");
        w.openBlock("private void captureNavScreenshot(String entityName, String phase)");
        w.openBlock("try");
        w.writeLine("java.io.File src = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.FILE);");
        w.writeLine("java.nio.file.Path dir = java.nio.file.Path.of(\"target/screenshots\");");
        w.writeLine("java.nio.file.Files.createDirectories(dir);");
        w.writeLine("String safe = entityName.replaceAll(\"[^a-zA-Z0-9\\u0400-\\u04FF]+\", \"_\");");
        w.writeLine("String fname = \"post-nav-\" + safe + \"-\" + phase + \".png\";");
        w.writeLine("java.nio.file.Files.copy(src.toPath(), dir.resolve(fname), java.nio.file.StandardCopyOption.REPLACE_EXISTING);");
        w.writeLine("System.out.println(\"  screenshot: target/screenshots/\" + fname);");
        // Логируем видимые заголовки таб-стрипа
        w.writeLine("List<WebElement> tabs = driver.findElements(By.cssSelector(\".x-tab-strip-text, .x-tab-strip-active\"));");
        w.writeLine("StringBuilder tabList = new StringBuilder();");
        w.openBlock("for (WebElement t : tabs)");
        w.openBlock("try");
        w.openBlock("if (t.isDisplayed())");
        w.writeLine("if (tabList.length() > 0) tabList.append(\" | \");");
        w.writeLine("tabList.append(t.getText().trim());");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"  visible tabs: \" + (tabList.length() == 0 ? \"<none>\" : tabList.toString()));");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Универсальная навигация
        w.openBlock("private void navigateGeneric(String entityName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String xpath = \"//a[contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //span[contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //button[contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //div[contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //li[contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //*[contains(@title, '\" + entityName + \"')]\";");
        w.writeLine("WebElement el = driver.findElement(By.xpath(xpath));");
        w.writeLine("el.click();");
        w.writeLine("wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(\"body\")));");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("navigationOk = true;");
        w.closeBlock();
        w.openBlock("catch (Throwable e)");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: считаем, что навигация удалась
        w.openBlock("protected void assumeNavigated()");
        w.writeLine("Assumptions.assumeTrue(navigationOk, \"Skipped: could not navigate to entity\");");
        w.closeBlock();
        w.writeLine();

        // Хелпер: выбрать первую запись
        w.openBlock("protected void selectFirstRecord()");
        w.openBlock("try");
        w.writeLine("WebElement row = driver.findElement(By.cssSelector(\".x-grid3-row, tr.data-row, tr[data-index='0'], tbody tr:first-child\"));");
        w.writeLine("row.click();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"No records found to select\");");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // openViaExtApi: дёргает ExtJS API напрямую через JavaScript, минуя Selenium-Actions.
        // На стенде стоит ExtJS 3 (классы x-grid3-*), синтетические DOM-события которой
        // не активируют rowdblclick. Зато прямой вызов через `grid.fireEvent('rowdblclick', ...)`
        // обходит это ограничение и реально открывает «Единый объект».
        // Стратегия: ищем DOM-элемент .x-grid3 → берём его id → Ext.getCmp(id) даёт компонент.
        w.openBlock("protected boolean openViaExtApi()");
        w.openBlock("try");
        w.writeLine("Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        // Скоупимся на АКТИВНОЕ окно (Ext.WindowMgr.getActive()) — это окно, в которое мы только
        // что навигировали в setUp. Без скоупа на стенде, где открыто несколько окон поиска
        // одновременно, выбирается грид из чужого окна, и dblclick уходит «не туда».
        w.writeLine("    + \"  var aw = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : ((Ext.WindowManager && Ext.WindowManager.getActive) ? Ext.WindowManager.getActive() : null);\"");
        w.writeLine("    + \"  var root = (aw && aw.getEl) ? (aw.getEl().dom || aw.getEl()) : document;\"");
        w.writeLine("    + \"  var nodes = root.querySelectorAll('.x-grid3, .x-grid-panel, .x-grid');\"");
        w.writeLine("    + \"  var grids = [];\"");
        w.writeLine("    + \"  for (var i = 0; i < nodes.length; i++) {\"");
        w.writeLine("    + \"    var n = nodes[i];\"");
        w.writeLine("    + \"    if (n.offsetWidth === 0 || n.offsetHeight === 0) continue;\"");
        w.writeLine("    + \"    var id = n.id; if (!id) continue;\"");
        w.writeLine("    + \"    var cmp = Ext.getCmp ? Ext.getCmp(id) : null;\"");
        w.writeLine("    + \"    if (!cmp) {\"");
        // Подняться к ближайшему элементу с id, который зарегистрирован в Ext
        w.writeLine("    + \"      var p = n; while (p && p.parentElement) { if (p.id && Ext.getCmp && Ext.getCmp(p.id)) { cmp = Ext.getCmp(p.id); break; } p = p.parentElement; }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"    if (cmp && cmp.fireEvent && cmp.getStore) grids.push(cmp);\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  if (grids.length === 0) return 'no-grid';\"");
        // Выбираем грид с наибольшим числом ВИДИМЫХ отрендеренных строк (это всегда таблица
        // результатов поиска, а не какой-нибудь служебный грид-меню/история с большим store).
        w.writeLine("    + \"  var best = null; var bestVisible = -1;\"");
        w.writeLine("    + \"  for (var j = 0; j < grids.length; j++) {\"");
        w.writeLine("    + \"    var g = grids[j]; var s = g.getStore && g.getStore(); var c = s ? s.getCount() : 0;\"");
        w.writeLine("    + \"    if (c === 0) continue;\"");
        // Считаем строки .x-grid3-row внутри DOM-узла этого грида, у которых offsetHeight>0
        w.writeLine("    + \"    var dom = g.getEl ? (g.getEl().dom || g.getEl()) : null; if (!dom) continue;\"");
        w.writeLine("    + \"    var rows = dom.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"    var visRows = 0; for (var rr = 0; rr < rows.length; rr++) { if (rows[rr].offsetHeight > 0 && rows[rr].offsetWidth > 0) visRows++; }\"");
        w.writeLine("    + \"    if (visRows > bestVisible) { best = g; bestVisible = visRows; }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  if (!best || bestVisible <= 0) return 'empty-store';\"");
        w.writeLine("    + \"  var record = best.getStore().getAt(0); var view = best.view || best.getView();\"");
        // Явно ВЫДЕЛЯЕМ строку 0 в SelectionModel грида. Без этого fireEvent('rowdblclick')
        // открывает карточку, но строка результатов остаётся невыделенной — и последующее
        // удаление/изменение применяется «не к той» записи или ни к чему.
        w.writeLine("    + \"  try { var sm = best.getSelectionModel && best.getSelectionModel(); if (sm) { if (sm.selectRow) sm.selectRow(0); else if (sm.select) sm.select(record || 0); } } catch (se) {}\"");
        w.writeLine("    + \"  best.fireEvent('rowdblclick', best, 0, null);\"");
        w.writeLine("    + \"  best.fireEvent('itemdblclick', view, record, null, 0, null);\"");
        // Дополнительно — bubble cell-click событие в DOM, на случай если в обработчике этого нужно
        w.writeLine("    + \"  try { if (view && view.getRow) { var rowEl = view.getRow(0); if (rowEl) { var cells = rowEl.querySelectorAll('.x-grid3-cell'); if (cells.length) cells[0].dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window})); } } } catch (ee) {}\"");
        w.writeLine("    + \"  return 'fired:' + bestVisible + '/' + (best.getStore ? best.getStore().getCount() : '?');\"");
        w.writeLine("    + \"} catch (e) { return 'err:' + e.message; }\");");
        w.writeLine("System.out.println(\"openViaExtApi: \" + result);");
        w.writeLine("return result != null && String.valueOf(result).startsWith(\"fired\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"openViaExtApi error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();


        // selectAndOpenRecord: документированный пользователем сценарий E3Core:
        //   1. найти первую видимую строку результирующего грида,
        //   2. одиночный клик по ней (выделение),
        //   3. dblclick по той же строке — откроется «Единый объект» / карточка.
        // ExtJS API (openViaExtApi) оставлен как fallback на стенды без бубликабельных
        // событий — но как ПЕРВЫЙ выбор он промахивается мимо result-grid, если рядом
        // есть другие гриды (дерево поисков и пр.).
        w.openBlock("protected boolean selectAndOpenRecord()");
        w.writeLine("return selectAndOpenRecordAtIndex(0);");
        w.closeBlock();
        w.writeLine();

        // Открывает запись по индексу в самой большой видимой группе строк (то есть в
        // таблице результатов). idx=0 — первая, idx=1 — вторая и т.д. Используется
        // testUpdate, чтобы при отказе одной записи перейти к следующей.
        w.openBlock("protected boolean selectAndOpenRecordAtIndex(int idx)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Ищем видимую строку в гриде результатов. На одном экране может быть НЕСКОЛЬКО гридов
        // (параметры поиска + результаты + side-панели). Группируем видимые .x-grid3-row по
        // родительскому ext-гриду и берём строку из САМОЙ БОЛЬШОЙ группы — это всегда грид
        // результатов поиска (10 строк), а не грид параметров (1-3 строки).
        w.writeLine("WebElement firstRow = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        // Скоуп на активное Ext-окно — в нашем целевом window, а не в соседнем поисковом.
        w.writeLine("    \"var aw = (typeof Ext !== 'undefined' && Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null;\"");
        w.writeLine("    + \"var root = (aw && aw.getEl) ? (aw.getEl().dom || aw.getEl()) : document;\"");
        w.writeLine("    + \"var rows = root.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"var groups = {};\"");
        w.writeLine("    + \"for (var i = 0; i < rows.length; i++) {\"");
        w.writeLine("    + \"  var r = rows[i]; if (r.offsetHeight === 0 || r.offsetWidth === 0) continue;\"");
        w.writeLine("    + \"  var p = r.parentElement;\"");
        w.writeLine("    + \"  while (p && !(p.classList && (p.classList.contains('x-grid3') || p.classList.contains('x-grid-panel') || p.classList.contains('x-grid')))) p = p.parentElement;\"");
        w.writeLine("    + \"  var key = p ? (p.id || p.className) : 'none';\"");
        w.writeLine("    + \"  if (!groups[key]) groups[key] = []; groups[key].push(r);\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"var bestKey = null, bestCount = 0;\"");
        w.writeLine("    + \"for (var k in groups) { if (groups[k].length > bestCount) { bestCount = groups[k].length; bestKey = k; } }\"");
        w.writeLine("    + \"console.log('selectAndOpenRecord: group sizes = ' + Object.keys(groups).map(function(k){return k+':'+groups[k].length;}).join(', '));\"");
        // Возвращаем ВНУТРЕННЮЮ ЯЧЕЙКУ первой строки, а не саму <tr>. ExtJS 3 ловит
        // rowdblclick через cellmousedown — кликать надо в .x-grid3-cell.
        w.writeLine("    + \"if (!bestKey) return null;\"");
        w.writeLine("    + \"var idx = arguments[0]|0; if (idx < 0) idx = 0;\"");
        w.writeLine("    + \"if (groups[bestKey].length <= idx) return null;\"");
        w.writeLine("    + \"var row = groups[bestKey][idx];\"");
        w.writeLine("    + \"var cells = row.querySelectorAll('.x-grid3-cell, .x-grid-cell, td');\"");
        // Кликаем НЕ по первой ячейке (это колонка номера строки и dblclick на ней
        // часто не открывает запись), а по первой ячейке С ТЕКСТОМ длиннее 2 символов —
        // это надёжная DATA-ячейка.
        w.writeLine("    + \"for (var ci = 0; ci < cells.length; ci++) {\"");
        w.writeLine("    + \"  var ce = cells[ci]; if (ce.offsetHeight <= 0 || ce.offsetWidth <= 0) continue;\"");
        w.writeLine("    + \"  var t = (ce.innerText || ce.textContent || '').trim();\"");
        w.writeLine("    + \"  if (t.length > 2 && !/^\\\\d+$/.test(t)) { window.__lastSelectedCellText = t; return ce; }\"");
        w.writeLine("    + \"}\"");
        // Fallback: первая видимая ячейка (как было)
        w.writeLine("    + \"for (var ci = 0; ci < cells.length; ci++) { var ce = cells[ci]; if (ce.offsetHeight > 0 && ce.offsetWidth > 0) { window.__lastSelectedCellText = (ce.innerText||'').trim(); return ce; } }\"");
        w.writeLine("    + \"return row;\", idx);");
        // Доп. диагностика: выводим в Java-лог group sizes + первую строку каждой группы.
        // Раньше эта инфа писалась в console.log браузера и не доходила до отчёта; теперь
        // видно В КАКОЙ грид попадает клик (results vs параметрический vs дерево поисков).
        w.openBlock("try");
        w.writeLine("Object diag = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var aw = (typeof Ext !== 'undefined' && Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null;\"");
        w.writeLine("    + \"var root = (aw && aw.getEl) ? (aw.getEl().dom || aw.getEl()) : document;\"");
        w.writeLine("    + \"var rows = root.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"var groups = {};\"");
        w.writeLine("    + \"for (var i = 0; i < rows.length; i++) {\"");
        w.writeLine("    + \"  var r = rows[i]; if (r.offsetHeight === 0 || r.offsetWidth === 0) continue;\"");
        w.writeLine("    + \"  var p = r.parentElement;\"");
        w.writeLine("    + \"  while (p && !(p.classList && (p.classList.contains('x-grid3') || p.classList.contains('x-grid-panel') || p.classList.contains('x-grid')))) p = p.parentElement;\"");
        w.writeLine("    + \"  var key = p ? (p.id || p.className).substring(0, 40) : 'none';\"");
        w.writeLine("    + \"  if (!groups[key]) groups[key] = []; groups[key].push(r);\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"var out = '';\"");
        w.writeLine("    + \"for (var k in groups) { out += '\\\\n  group=[' + k + '] count=' + groups[k].length + ' firstRowText=[' + (groups[k][0].innerText || '').replace(/\\\\n/g,'|').substring(0,80) + ']'; }\"");
        w.writeLine("    + \"return out;\");");
        w.writeLine("System.out.println(\"selectAndOpenRecord: visible grid groups:\" + diag);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("if (firstRow != null)");
        // Логируем какой текст в выбранной ячейке — для диагностики попали ли мы в реальную
        // строку данных или в параметрический грид/заголовок.
        w.openBlock("try");
        w.writeLine("Object cellTxt = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"return window.__lastSelectedCellText || ''\");");
        w.writeLine("System.out.println(\"selectAndOpenRecord: будем дбл-кликать на ячейку с текстом '\" + cellTxt + \"' (row idx=\" + idx + \")\");");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"selectAndOpenRecord: physical click+dblclick on row idx=\" + idx + \" of largest visible grid group\");");
        // Шаг 1: одиночный клик для выделения строки
        w.openBlock("try");
        w.writeLine("firstRow.click();");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  single-click failed: \" + e.getMessage());");
        w.closeBlock();
        // Шаг 2: двойной клик через Actions, с запасным JS dispatchEvent
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).doubleClick().perform();");
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window, detail: 2}));\",");
        w.writeLine("    firstRow);");
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.openBlock("catch (Exception e2)");
        w.writeLine("System.out.println(\"  dblclick failed: \" + e2.getMessage());");
        w.closeBlock();
        w.closeBlock();
        // Если карточка реально открылась — отлично
        w.openBlock("if (waitForCardLoaded(8))");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("System.out.println(\"selectAndOpenRecord: physical dblclick did not open a card — trying ExtJS API fallback\");");
        w.closeBlock();
        // Fallback: ExtJS API
        w.openBlock("if (openViaExtApi())");
        w.writeLine("boolean opened = waitUntil(d -> isOnRecordCard() || isDialogOpen(), 8, \"card after ExtJS API\");");
        w.openBlock("if (opened)");
        w.writeLine("System.out.println(\"selectAndOpenRecord: opened via ExtJS API fireEvent('rowdblclick')\");");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"selectAndOpenRecord: NO row found and ExtJS API failed\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"selectAndOpenRecord error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: проверить наличие ошибок
        w.openBlock("protected boolean isErrorPresent()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> errors = driver.findElements(By.cssSelector(\".error, .alert-danger, .validation-error, .error-message, .x-window-dlg .ext-mb-error, .x-form-invalid\"));");
        w.writeLine("return !errors.isEmpty() && errors.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // openResultRowByText: открывает ИМЕННО ту запись грида результатов, чья строка содержит
        // marker (а не «самую большую группу строк», которой после фильтр-поиска часто оказывается
        // property-grid уже открытой карточки). Игнорирует строки внутри property-grid. Физический
        // double-click по data-ячейке строки → карточка записи открывается. Возвращает true/false.
        w.openBlock("protected boolean openResultRowByText(String marker)");
        w.openBlock("if (marker == null || marker.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        // Сначала закрываем возможную открытую карточку, чтобы грид результатов был на переднем плане.
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("WebElement cell = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var m=arguments[0]; var rows=document.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"for (var i=0;i<rows.length;i++){ var r=rows[i]; if (r.offsetHeight<=0||r.offsetWidth<=0) continue;\"");
        // пропускаем строки property-grid (карточка), нам нужен грид результатов
        w.writeLine("    + \"  var pg=r.closest?r.closest('.x-property-grid, .x-grid-property'):null; if (pg) continue;\"");
        w.writeLine("    + \"  var t=(r.innerText||r.textContent||''); if (t.indexOf(m)<0) continue;\"");
        w.writeLine("    + \"  var cells=r.querySelectorAll('.x-grid3-cell, .x-grid-cell, td');\"");
        w.writeLine("    + \"  for (var c=0;c<cells.length;c++){ var ce=cells[c]; if (ce.offsetHeight<=0) continue; var ct=(ce.innerText||'').trim(); if (ct.length>2 && !/^\\\\d+$/.test(ct)) { try{ce.scrollIntoView(true);}catch(e){} return ce; } }\"");
        w.writeLine("    + \"  return r; }\"");
        w.writeLine("    + \"return null;\", marker);");
        w.openBlock("if (cell == null)");
        w.writeLine("System.out.println(\"openResultRowByText: строка с '\" + marker + \"' не найдена в гриде результатов\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(cell).doubleClick().perform();");
        w.writeLine("Thread.sleep(1200);");
        w.writeLine("System.out.println(\"openResultRowByText: открыли запись с '\" + marker + \"'\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"openResultRowByText failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: принять alert
        w.openBlock("protected void acceptAlertIfPresent()");
        w.openBlock("try");
        w.writeLine("Alert alert = driver.switchTo().alert();");
        w.writeLine("alert.accept();");
        w.closeBlock();
        w.openBlock("catch (NoAlertPresentException ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // capturePopupText: печатает и возвращает текст самого верхнего видимого .x-window /
        // .x-message-box (если есть). Текст нужен, чтобы включить причину отказа («Необходимо
        // обязательно указать значения свойств: X») в сообщение assert/fail.
        w.openBlock("protected String capturePopupText(String tag)");
        w.writeLine("StringBuilder collected = new StringBuilder();");
        w.openBlock("try");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        // Сначала пробуем ExtJS Ext.MessageBox: его контейнер обычно .x-window-dlg
        w.writeLine("List<WebElement> mboxes = driver.findElements(By.cssSelector(\".ext-mb-text, .x-window-dlg .x-window-body, .x-message-box .x-window-body\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement m : mboxes)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed() && shown < 3)");
        w.writeLine("String t = m.getText() == null ? \"\" : m.getText().trim();");
        w.openBlock("if (!t.isEmpty())");
        w.writeLine("System.out.println(\"  [popup \" + tag + \"]: '\" + t.replace(\"\\n\", \" | \") + \"'\");");
        w.writeLine("if (collected.length() > 0) collected.append(\" | \");");
        w.writeLine("collected.append(t.replace(\"\\n\", \" | \"));");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Fallback: вообще любой .x-window header+body, если выше ничего не нашлось
        w.openBlock("if (shown == 0)");
        w.writeLine("List<WebElement> winds = driver.findElements(By.cssSelector(\".x-window\"));");
        w.openBlock("for (WebElement w2 : winds)");
        w.openBlock("try");
        w.openBlock("if (w2.isDisplayed() && shown < 3)");
        w.writeLine("String t = w2.getText() == null ? \"\" : w2.getText().trim();");
        w.openBlock("if (!t.isEmpty() && t.length() < 300)");
        w.writeLine("System.out.println(\"  [popup \" + tag + \" window]: '\" + t.replace(\"\\n\", \" | \") + \"'\");");
        w.writeLine("if (collected.length() > 0) collected.append(\" | \");");
        w.writeLine("collected.append(t.replace(\"\\n\", \" | \"));");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        // Для отчёта скриним всплывающее окно, если оно было обнаружено (валидация, инфо,
        // подтверждение и т.п.) — чтобы каждое попап-окно попадало в галерею скриншотов.
        w.openBlock("if (collected.length() > 0)");
        w.writeLine("try { shot(\"popup-\" + tag, \"POPUP\"); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("return collected.toString();");
        w.closeBlock();
        w.writeLine();

        // confirmDialogYes: после Удалить / в Архив E3Core открывает ExtJS-confirm («Да/Нет»),
        // и без подтверждения операция не применяется. Ждём диалог до 3с и кликаем «Да»
        // (либо «Yes», «OK», «Подтвердить») если он появился.
        w.openBlock("protected boolean confirmDialogYes()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        // confirmDialogYes: ждём popup до 3.5с — на некоторых стендах подтверждение
        // прилетает позже (после round-trip к серверу).
        w.writeLine("long deadline = System.currentTimeMillis() + 3500;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("List<WebElement> btns = driver.findElements(By.xpath(");
        w.writeLine("    \"//div[contains(@class,'x-window') or contains(@class,'x-message-box')]//button[\"");
        w.writeLine("    + \" normalize-space(.)='\\u0414\\u0430'\"");
        w.writeLine("    + \" or normalize-space(.)='Yes'\"");
        w.writeLine("    + \" or normalize-space(.)='OK'\"");
        w.writeLine("    + \" or normalize-space(.)='\\u041f\\u043e\\u0434\\u0442\\u0432\\u0435\\u0440\\u0434\\u0438\\u0442\\u044c'\"");
        w.writeLine("    + \"]\"");
        w.writeLine("    + \" | //button[contains(@class,'x-btn')][.//span[\"");
        w.writeLine("    + \" normalize-space(.)='\\u0414\\u0430'\"");
        w.writeLine("    + \" or normalize-space(.)='Yes'\"");
        w.writeLine("    + \" or normalize-space(.)='OK'\"");
        w.writeLine("    + \" or normalize-space(.)='\\u041f\\u043e\\u0434\\u0442\\u0432\\u0435\\u0440\\u0434\\u0438\\u0442\\u044c'\"");
        w.writeLine("    + \"]]\"");
        w.writeLine("));");
        w.openBlock("for (WebElement b : btns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed() && b.isEnabled())");
        w.writeLine("System.out.println(\"confirmDialogYes: clicking '\" + b.getText().trim() + \"'\");");
        // Для отчёта скриним окно подтверждения (Да/Нет/OK) до того, как закроем его кликом.
        w.writeLine("try { shot(\"confirm-dialog\", \"POPUP\"); } catch (Exception ignored) {}");
        w.writeLine("tryClickAllWays(b);");
        w.writeLine("Thread.sleep(400);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("Thread.sleep(150);");
        w.closeBlock();
        w.writeLine("System.out.println(\"confirmDialogYes: no confirm dialog appeared in 3s\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"confirmDialogYes error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: открыть поиск по имени узла дерева. Сначала пробует двойной клик; если дерево
        // не видно (например, окно дерева закрылось после первого поиска), переоткрывает его
        // через clickEntityMenuItem(entityName(), «Найти») и повторяет.
        w.openBlock("protected void openSearch(String searchName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Схлопываем повторы пробелов — в XML-подписях иногда двойные пробелы («по  параметрам»),
        // которые не совпадут с текстом узла дерева с одинарным пробелом.
        w.writeLine("String trimmed = searchName.trim().replaceAll(\"\\\\s+\", \" \");");
        w.writeLine("WebElement searchLink = findTreeNode(trimmed);");
        w.openBlock("if (searchLink == null)");
        // Откат 1: дерево могло закрыться. Заново вызываем меню, чтобы вернуть его.
        w.writeLine("System.out.println(\"openSearch: tree node '\" + trimmed + \"' not visible — re-opening tree via clickEntityMenuItem('Найти')\");");
        w.writeLine("clickEntityMenuItem(entityName(), new String[]{ \"\\u041d\\u0430\\u0439\\u0442\\u0438\" });");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.writeLine("searchLink = findTreeNode(trimmed);");
        w.closeBlock();
        w.openBlock("if (searchLink == null)");
        // Откат 2: именованные поиски («Поиск ОГСК», «Поиск объединений») часто живут как прямые
        // пункты подменю сущности, а не как узлы дерева. Кликаем по имени поиска напрямую через
        // тот же хелпер с подбором по основам слов.
        w.writeLine("System.out.println(\"openSearch: '\" + trimmed + \"' not a tree node — trying as direct menu item\");");
        w.writeLine("boolean directClicked = clickEntityMenuItem(entityName(), new String[]{ trimmed });");
        w.openBlock("if (directClicked)");
        // Для именованных поисков грид результатов отрисовывается сам; двойной клик/submit не нужны.
        w.writeLine("waitForGridSettle();");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"Could not open search: \" + searchName + \" (neither tree node nor direct menu item)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(searchLink).doubleClick().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception eDbl)");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].click(); arguments[0].click();\", searchLink);");
        w.closeBlock();
        // Ждём отрисовки формы до 3с — опрашиваем каждые 250мс на любой видимый input.
        // Без этого быстрые тесты доходят до fillSearchParam до отрисовки формы поиска.
        w.writeLine("long formDeadline = System.currentTimeMillis() + 3000;");
        w.writeLine("boolean formRendered = false;");
        w.openBlock("while (System.currentTimeMillis() < formDeadline)");
        w.writeLine("Thread.sleep(200);");
        w.writeLine("List<WebElement> probes = driver.findElements(By.cssSelector(\"input:not([type='hidden']):not(.x-combo-noedit), textarea\"));");
        w.openBlock("if (probes.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("formRendered = true; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (!formRendered)");
        w.writeLine("System.out.println(\"openSearch: search form did not render after dbl-click on '\" + trimmed + \"' — this build may run the search directly without showing a parameter form\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"openSearch failed: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // findTreeNode: находит именованный лист в открытом окне дерева поисков, или null если его нет.
        w.openBlock("private WebElement findTreeNode(String trimmedName)");
        w.openBlock("try");
        w.writeLine("List<WebElement> hits = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class, 'x-tree-node-text')][contains(normalize-space(.), '\" + trimmedName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.), '\" + trimmedName + \"')]]\"));");
        w.openBlock("for (WebElement h : hits)");
        w.openBlock("try");
        w.openBlock("if (h.isDisplayed())");
        w.writeLine("return h;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("return null;");
        w.closeBlock();
        w.writeLine();
        w.writeLine();

        // Хелпер: выполнить поиск
        w.openBlock("protected void executeSearch()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // В формах поиска E3Core кнопка submit — «Готово» (диалоги в стиле PropertyGrid);
        // на некоторых страницах с гридом — «Выполнить поиск»/«Найти». Пробуем все три по
        // убыванию вероятности и берём первую видимую.
        w.writeLine("String[] candidates = { \"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\", \"\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a\", \"\\u041d\\u0430\\u0439\\u0442\\u0438\" };");
        w.openBlock("for (String label : candidates)");
        w.openBlock("try");
        w.writeLine("List<WebElement> btns = driver.findElements(By.xpath(");
        w.writeLine("    \"//button[contains(normalize-space(.), '\" + label + \"')]\"");
        w.writeLine("    + \" | //input[@value='\" + label + \"']\"");
        w.writeLine("    + \" | //button[.//span[contains(normalize-space(.), '\" + label + \"')]]\"));");
        w.openBlock("for (WebElement b : btns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed() && b.isEnabled())");
        w.writeLine("System.out.println(\"executeSearch: clicking '\" + label + \"'\");");
        w.writeLine("clickSafely(b);");
        w.writeLine("Thread.sleep(800);");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"Could not execute search — none of [Готово, Выполнить поиск, Найти] visible\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"executeSearch error: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: проверить результат поиска
        w.openBlock("protected boolean isSearchResultPresent()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement resultTable = driver.findElement(By.cssSelector(\".x-grid3, .x-grid-panel, table.result, .search-results, .grid-view, table\"));");
        w.writeLine("return resultTable.isDisplayed();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: открыть таб. Поддерживает ExtJS 3 (.x-tab-strip-text) и 4/5 (.x-tab-inner,
        // .x-tab-button, role="tab"), плюс обычный HTML и варианты с ARIA-ролями. Табы находятся
        // ВНУТРИ открытой карточки записи — если карточка не открыта, метод (закономерно) упадёт.
        w.openBlock("protected boolean openTab(String tabName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Проход 1: классические элементы таб-стрипа ExtJS.
        w.writeLine("List<WebElement> candidates = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tab-strip-text')][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'x-tab-inner')][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tab')][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'x-tab-text')][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //*[@role='tab'][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //li[contains(@class,'tab')][contains(normalize-space(.), '\" + tabName + \"')]\"));");
        w.openBlock("for (WebElement c : candidates)");
        w.openBlock("try");
        w.openBlock("if (c.isDisplayed())");
        w.writeLine("clickSafely(c);");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Проход 2: вид «Единый объект» — группы свойств («Сведения», «История», «Документы»)
        // здесь строки грида/списка справа, а не таб-стрип. Кликаем любую видимую строку, ссылку
        // или div, в тексте которых есть имя таба.
        w.writeLine("List<WebElement> fallback = driver.findElements(By.xpath(");
        w.writeLine("    \"//*[self::div or self::a or self::td or self::span or self::tr]\"");
        w.writeLine("    + \"[contains(normalize-space(.), '\" + tabName + \"')]\"));");
        w.openBlock("for (WebElement c : fallback)");
        w.openBlock("try");
        w.openBlock("if (!c.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        // Отсеиваем кандидатов со слишком длинным текстом — это обёртки страницы, а не нужная нам
        // узкая строка. Подписи табов обычно короткие (до 80 символов).
        w.writeLine("String txt = c.getText() == null ? \"\" : c.getText().trim();");
        w.openBlock("if (txt.length() == 0 || txt.length() > 80)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("System.out.println(\"openTab: trying <\" + c.getTagName() + \"> '\" + txt + \"'\");");
        w.writeLine("tryClickAllWays(c);");
        w.writeLine("Thread.sleep(400);");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Проход 3: узел левого дерева ExtJS внутри карточки (дочерние узлы вроде
        // «Повестка совещания»). Узел может быть свёрнут под групповым, поэтому предыдущие проходы
        // ничего не видят. Раскрываем все видимые «плюсы», затем кликаем узел.
        w.writeLine("String treeXpath = \"//span[contains(@class,'x-tree-node-text')][contains(normalize-space(.), '\" + tabName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.), '\" + tabName + \"')]]\";");
        w.writeLine("List<WebElement> treeHits = driver.findElements(By.xpath(treeXpath));");
        w.writeLine("boolean anyTreeVisible = false;");
        w.openBlock("for (WebElement h : treeHits)");
        w.writeLine("try { if (h.isDisplayed()) { anyTreeVisible = true; break; } } catch (Exception ignored) {}");
        w.closeBlock();
        // Узел ещё не виден → раскрываем свёрнутые групповые узлы (классы ExtJS 2/3/4).
        w.openBlock("if (!anyTreeVisible)");
        w.writeLine("List<WebElement> expanders = driver.findElements(By.cssSelector(");
        w.writeLine("    \".x-tree-elbow-plus, .x-tree-elbow-end-plus, .x-tree-ec-icon, .x-tree3-node-joint, .x-grid-group-hd\"));");
        w.openBlock("for (WebElement ex : expanders)");
        w.writeLine("try { if (ex.isDisplayed()) { clickSafely(ex); Thread.sleep(150); } } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("treeHits = driver.findElements(By.xpath(treeXpath));");
        w.closeBlock();
        w.openBlock("for (WebElement h : treeHits)");
        w.openBlock("try");
        w.openBlock("if (!h.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(h).perform();");
        w.writeLine("System.out.println(\"openTab: clicking tree node '\" + tabName + \"'\");");
        w.writeLine("tryClickAllWays(h);");
        w.writeLine("Thread.sleep(400);");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"Could not open tab: \" + tabName);");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not open tab '\" + tabName + \"': \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // openRecordCard: открывает карточку записи, чтобы можно было работать с её таб-гридами.
        // Пробует несколько стратегий по очереди (двойной клик, контекстное меню «Изменить»/
        // «Открыть», тулбар, Enter, поиск по меню) и останавливается на первой, открывшей карточку.
        w.openBlock("protected boolean openRecordCard()");
        // Кэш: если предыдущий вызов openRecordCard в этом классе уже провалил все пять стратегий,
        // сразу возвращаем false. Экономит ~30-50с на каждый следующий testGrid* в классе.
        w.openBlock("if (cardOpenAttempted)");
        w.openBlock("if (!cachedCardOpenOk)");
        w.writeLine("System.out.println(\"openRecordCard: cached miss — skipping retry\");");
        w.closeBlock();
        w.writeLine("return cachedCardOpenOk;");
        w.closeBlock();
        w.writeLine("cardOpenAttempted = true;");
        // Стратегия 0: ExtJS API. На ExtJS-гридах синтетический click из Selenium не активирует
        // row-dblclick, зато прямой вызов через JS работает.
        w.openBlock("if (openViaExtApi())");
        w.writeLine("boolean opened0 = waitUntil(d -> isOnRecordCard(), 8, \"card after ExtJS API\");");
        w.openBlock("if (opened0)");
        w.writeLine("System.out.println(\"openRecordCard: opened via ExtJS API\");");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row, tbody tr\"));");
        w.writeLine("WebElement firstRow = null;");
        w.openBlock("for (WebElement r : rows)");
        w.openBlock("try");
        w.openBlock("if (r.isDisplayed())");
        w.writeLine("firstRow = r; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (firstRow == null)");
        w.writeLine("System.out.println(\"openRecordCard: no visible row to open\");");
        w.writeLine("return false;");
        w.closeBlock();
        // Стратегия 1: выделить, затем открыть. Стенду нужны два отдельных клика: первый выделяет
        // строку, второй открывает вид «Единый объект». Быстрый Actions.doubleClick() склеивает
        // клики слишком плотно, и ExtJS не срабатывает. Поэтому делаем явно клик → пауза → клик
        // → ждём появления карточки до 5с (загрузка может быть медленной).
        w.openBlock("try");
        w.writeLine("new Actions(driver)");
        w.writeLine("    .moveToElement(firstRow)");
        w.writeLine("    .click()");
        w.writeLine("    .pause(Duration.ofMillis(400))");
        w.writeLine("    .click()");
        w.writeLine("    .perform();");
        w.writeLine("boolean opened1 = waitUntil(d -> isOnRecordCard(), 6, \"card after select+open\");");
        w.openBlock("if (opened1)");
        w.writeLine("System.out.println(\"openRecordCard: opened via select + open (two clicks)\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 1b: нативный doubleClick — для сборок, где работает один быстрый двойной клик
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).doubleClick().perform();");
        w.writeLine("boolean opened1b = waitUntil(d -> isOnRecordCard(), 5, \"card after double-click\");");
        w.openBlock("if (opened1b)");
        w.writeLine("System.out.println(\"openRecordCard: opened via double-click\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 1c: нативный dblclick через JS — ExtJS 3 слушает сырое DOM-событие, а Actions
        // от Selenium иногда генерирует два отдельных `click`. Диспатч реального `dblclick`
        // MouseEvent через JS обходит это.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window}));\",");
        w.writeLine("    firstRow);");
        w.writeLine("boolean opened1c = waitUntil(d -> isOnRecordCard(), 5, \"card after JS dblclick\");");
        w.openBlock("if (opened1c)");
        w.writeLine("System.out.println(\"openRecordCard: opened via JS dblclick event\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 1d: правый клик → «Загрузить выбранные объекты в дерево». После выполнения
        // объект загружается в отдельное окно «Единый объект», которое мы и хотим обнаружить.
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).contextClick().perform();");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("List<WebElement> loadItems = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-menu-item-text')][contains(normalize-space(.), '\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u0438\\u0442\\u044c \\u0432\\u044b\\u0431\\u0440\\u0430\\u043d\\u043d\\u044b\\u0435')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.), '\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u0438\\u0442\\u044c \\u0432\\u044b\\u0431\\u0440\\u0430\\u043d\\u043d\\u044b\\u0435')]\"));");
        w.openBlock("for (WebElement m : loadItems)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed())");
        w.writeLine("System.out.println(\"openRecordCard: clicking 'Загрузить выбранные объекты в дерево' from context menu\");");
        w.writeLine("tryClickAllWays(m);");
        w.writeLine("boolean opened1d = waitUntil(d -> isOnRecordCard(), 4, \"card after load-to-tree\");");
        w.openBlock("if (opened1d)");
        w.writeLine("System.out.println(\"openRecordCard: opened via 'Загрузить выбранные объекты в дерево'\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Закрываем оставшееся контекстное меню
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 2: правый клик + «Изменить»/«Открыть»
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).contextClick().perform();");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("List<WebElement> menuItems = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-menu-item-text')][contains(normalize-space(.),'\\u0418\\u0437\\u043c\\u0435\\u043d\\u0438\\u0442\\u044c') or contains(normalize-space(.),'\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.),'\\u0418\\u0437\\u043c\\u0435\\u043d\\u0438\\u0442\\u044c') or contains(normalize-space(.),'\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c')]\"));");
        w.openBlock("for (WebElement m : menuItems)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed())");
        w.writeLine("clickSafely(m); Thread.sleep(800);");
        w.openBlock("if (isOnRecordCard())");
        w.writeLine("System.out.println(\"openRecordCard: opened via right-click menu '\" + m.getText().trim() + \"'\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Закрываем контекстное меню, если ещё открыто
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 3: выделить + кликнуть «Изменить» в тулбаре
        w.openBlock("try");
        w.writeLine("firstRow.click(); Thread.sleep(300);");
        w.writeLine("List<WebElement> editBtns = driver.findElements(By.xpath(");
        w.writeLine("    \"//button[contains(normalize-space(.),'\\u0418\\u0437\\u043c\\u0435\\u043d\\u0438\\u0442\\u044c') or contains(normalize-space(.),'\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c')]\"));");
        w.openBlock("for (WebElement b : editBtns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed())");
        w.writeLine("clickSafely(b); Thread.sleep(800);");
        w.openBlock("if (isOnRecordCard())");
        w.writeLine("System.out.println(\"openRecordCard: opened via toolbar 'Изменить'\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 4: Enter на выделенной строке — часть ExtJS-гридов открывает запись по клавише
        w.openBlock("try");
        w.writeLine("firstRow.click(); Thread.sleep(200);");
        w.writeLine("firstRow.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("boolean opened4 = waitUntil(d -> isOnRecordCard(), 3, \"card after Enter\");");
        w.openBlock("if (opened4)");
        w.writeLine("System.out.println(\"openRecordCard: opened via Enter key\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 5: надёжный поиск по меню через clickEntityMenuItem (подбор по основам слов).
        // Пробует все варианты действия «открыть запись»: «Изменить», «Редактировать», «Открыть»,
        // «Просмотр», «Карточка», «Свойства», «Подробнее», «Просмотреть».
        w.openBlock("try");
        w.writeLine("firstRow.click(); Thread.sleep(300);");
        w.writeLine("boolean clicked = clickEntityMenuItem(entityName(), new String[]{");
        w.writeLine("    \"\\u0418\\u0437\\u043c\\u0435\\u043d\\u0438\\u0442\\u044c\",");
        w.writeLine("    \"\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u0442\\u044c\",");
        w.writeLine("    \"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\",");
        w.writeLine("    \"\\u041f\\u0440\\u043e\\u0441\\u043c\\u043e\\u0442\\u0440\",");
        w.writeLine("    \"\\u041a\\u0430\\u0440\\u0442\\u043e\\u0447\\u043a\\u0430\",");
        w.writeLine("    \"\\u0421\\u0432\\u043e\\u0439\\u0441\\u0442\\u0432\\u0430\",");
        w.writeLine("    \"\\u041f\\u043e\\u0434\\u0440\\u043e\\u0431\\u043d\\u0435\\u0435\",");
        w.writeLine("    \"\\u041f\\u0440\\u043e\\u0441\\u043c\\u043e\\u0442\\u0440\\u0435\\u0442\\u044c\"});");
        w.writeLine("Thread.sleep(800);");
        w.openBlock("if (clicked && isOnRecordCard())");
        w.writeLine("System.out.println(\"openRecordCard: opened via clickEntityMenuItem\");");
        w.writeLine("cachedCardOpenOk = true;");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"openRecordCard: all five strategies failed (double-click, right-click, toolbar, Enter, robust menu)\");");
        // Диагностика: что на странице?
        w.openBlock("try");
        w.writeLine("List<WebElement> btns = driver.findElements(By.cssSelector(\"button, a.x-btn, input[type='button']\"));");
        w.writeLine("int dumped = 0;");
        w.openBlock("for (WebElement b : btns)");
        w.openBlock("try");
        w.openBlock("if (!b.isDisplayed() || dumped >= 12)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String txt = b.getText() == null ? \"\" : b.getText().trim();");
        w.openBlock("if (txt.isEmpty() || txt.length() > 60)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  visible button: '\" + txt + \"' (class='\" + b.getAttribute(\"class\") + \"')\");");
        w.writeLine("dumped++;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: проверить, отображается ли грид
        w.openBlock("protected boolean isGridDisplayed(String gridName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement grid = driver.findElement(By.cssSelector(\".x-grid3, .x-grid-panel, table, .grid-view, .data-grid\"));");
        w.writeLine("return grid.isDisplayed();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Перегрузка без title — вызовы без заголовка идут с title=null.
        w.openBlock("protected void fillSearchParam(String paramName, String value)");
        w.writeLine("fillSearchParam(paramName, null, value);");
        w.closeBlock();
        w.writeLine();

        // Хелпер: заполнить параметр поиска, перебирая стратегии локаторов. Стратегия «по подписи»
        // использует русский title из SearchParam.title (например, «Наименование ГСК/ОГСК»), который
        // и виден в форме, а не техническое имя «GBS_NAME». Без этого заполнение по техническому
        // имени тихо проваливается, и поиск идёт с пустыми параметрами.
        w.openBlock("protected void fillSearchParam(String paramName, String title, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("WebElement param = null;");
        w.writeLine("String strategy = \"\";");
        // Стратегия 1: прямой name/id
        w.openBlock("try");
        w.writeLine("List<WebElement> direct = driver.findElements(By.cssSelector(\"[name='\" + paramName + \"'], [id='\" + paramName + \"']\"));");
        w.openBlock("for (WebElement e : direct)");
        w.openBlock("if (e.isDisplayed() && e.isEnabled())");
        w.writeLine("param = e; strategy = \"name/id direct\"; break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Стратегия 2: префикс id (ExtJS добавляет авто-суффикс)
        w.openBlock("if (param == null)");
        w.openBlock("try");
        w.writeLine("List<WebElement> prefix = driver.findElements(By.cssSelector(\"[id^='\" + paramName + \"-']\"));");
        w.openBlock("for (WebElement e : prefix)");
        w.openBlock("if (e.isDisplayed() && e.isEnabled() && (\"input\".equals(e.getTagName()) || \"textarea\".equals(e.getTagName())))");
        w.writeLine("param = e; strategy = \"id prefix\"; break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Стратегия 3: по подписи — сначала русский title (то, что реально отрисовано), затем
        // откат на техническое имя. Подпись может быть <label>, заголовком form-item ExtJS или
        // заголовком колонки в таблице property-grid.
        w.openBlock("if (param == null)");
        w.openBlock("try");
        w.writeLine("String[] labelTexts = title == null || title.isBlank()");
        w.writeLine("    ? new String[]{ paramName }");
        w.writeLine("    : new String[]{ title, paramName };");
        w.writeLine("List<WebElement> labels = new java.util.ArrayList<>();");
        w.openBlock("for (String lt : labelTexts)");
        w.writeLine("labels.addAll(driver.findElements(By.xpath(");
        w.writeLine("    \"//label[contains(normalize-space(.), '\" + lt + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-form-item-label')][contains(normalize-space(.), '\" + lt + \"')]\"");
        w.writeLine("    + \" | //td[contains(@class,'x-form-item-label')][contains(normalize-space(.), '\" + lt + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'x-form-item-label')][contains(normalize-space(.), '\" + lt + \"')]\")));");
        w.closeBlock();
        w.openBlock("for (WebElement lbl : labels)");
        w.openBlock("if (!lbl.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("List<WebElement> nearby = lbl.findElements(By.xpath(\"./following::input[not(@type='hidden')][1] | ./parent::*//input[not(@type='hidden')] | ./following::textarea[1]\"));");
        w.openBlock("for (WebElement e : nearby)");
        w.openBlock("if (e.isDisplayed() && e.isEnabled())");
        w.writeLine("param = e; strategy = \"by label\"; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (param != null)");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Стратегия 4 (PropertyGrid E3Core): форма — таблица из 2 колонок «Наименование/Значение»,
        // где поля не настоящие <input>, пока не кликнуть ячейку значения (тогда появляется
        // inline-редактор). Используется, когда обычный поиск по input не сработал.
        w.openBlock("if (param == null)");
        w.writeLine("boolean clicked = fillPropertyGridCell(title != null && !title.isBlank() ? title : paramName, value);");
        w.openBlock("if (clicked)");
        w.writeLine("System.out.println(\"fillSearchParam '\" + paramName + \"' = '\" + value + \"' (via: PropertyGrid cell)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Стратегия 5: ExtJS API. Если ни обычный input, ни PropertyGrid-ячейка не сработали,
        // дёргаем поле напрямую через Ext-API по fieldLabel/name. Работает даже когда DOM-инпут
        // для поля не отрисован.
        w.writeLine("String labelToTry = title != null && !title.isBlank() ? title : paramName;");
        w.openBlock("if (setFieldViaExtApi(labelToTry, value))");
        w.writeLine("System.out.println(\"fillSearchParam '\" + paramName + \"' = '\" + value + \"' (via: Ext API)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Также пробуем по второму имени
        w.openBlock("if (!labelToTry.equals(paramName) && setFieldViaExtApi(paramName, value))");
        w.writeLine("System.out.println(\"fillSearchParam '\" + paramName + \"' = '\" + value + \"' (via: Ext API by name)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();

        w.openBlock("if (param != null)");
        w.writeLine("System.out.println(\"fillSearchParam '\" + paramName + \"' = '\" + value + \"' (via: \" + strategy + \")\");");
        w.openBlock("try");
        w.writeLine("param.clear();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("param.sendKeys(value);");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("System.out.println(\"Could not fill search param: \" + paramName + (title == null ? \"\" : \" (title='\" + title + \"')\") + \" (no input matched name/id/label/PropertyGrid)\");");
        // Диагностический дамп: выводим все видимые input/textarea, чтобы видеть, что реально на
        // странице, а не подбирать селекторы вслепую.
        w.openBlock("try");
        w.writeLine("List<WebElement> allInputs = driver.findElements(By.cssSelector(\"input:not([type='hidden']), textarea\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement inp : allInputs)");
        w.openBlock("try");
        w.openBlock("if (!inp.isDisplayed() || shown >= 12)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String tag = inp.getTagName();");
        w.writeLine("String nm = inp.getAttribute(\"name\");");
        w.writeLine("String id = inp.getAttribute(\"id\");");
        w.writeLine("String cls = inp.getAttribute(\"class\");");
        w.writeLine("String ph = inp.getAttribute(\"placeholder\");");
        w.writeLine("System.out.println(\"  available <\" + tag + \"> name='\" + nm + \"' id='\" + id + \"' class='\" + cls + \"' placeholder='\" + ph + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (allInputs.isEmpty())");
        w.writeLine("System.out.println(\"  (no visible inputs on the page at all — search form may not be rendered)\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not fill search param '\" + paramName + \"': \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: проверить наличие заголовка колонки в любой таблице.
        // Покрывает ExtJS 3 (.x-grid3-hd*), ExtJS 4/5 (.x-column-header, .x-column-header-text) и
        // обычный HTML (<th>, <td.header>). Нормализует пробелы, чтобы XML-заголовки с неразрывными
        // или двойными пробелами совпадали с отрисованным текстом.
        // fillPropertyGridCell: формы поиска и добавления E3Core рисуются как ExtJS PropertyGrid из
        // 2 колонок («Наименование» / «Значение»). Ячейки значений не настоящие input, пока по ним
        // не кликнуть — клик превращает их в inline-редактор. Поэтому:
        //   1. находим строку, чья левая ячейка содержит подпись поля (русский title вроде
        //      «Наименование ГСК/ОГСК» или «Кадастровый номер»)
        //   2. кликаем правую ячейку (или строку), чтобы вызвать inline-редактор
        // waitForCardLoaded: «Единый объект» открывается быстро, но данные внутри подгружаются
        // отдельно (видно по индикатору «Загрузка данных...»). Пока данные не пришли, в карточке
        // НЕТ ни кнопки «Редактирование», ни PropertyGrid'а с полями. Этот хелпер ждёт пока:
        //   1. индикатор загрузки исчезнет, ИЛИ
        //   2. появится кнопка «Редактирование», ИЛИ
        //   3. появится список property-групп («Сведения», «История», «Документы»)
        // Используется ПОСЛЕ openRecordCard / selectAndOpenRecord, перед clickEditDropdownAction.
        w.openBlock("protected boolean waitForCardLoaded(int seconds)");
        w.writeLine("boolean ok = waitUntil(d -> {");
        w.writeLine("    try {");
        // Сигнал A: видна кнопка «Редактирование» — главный признак «карточка готова».
        w.writeLine("        List<WebElement> edit = driver.findElements(By.xpath(\"//button[contains(normalize-space(.), '\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435')]\"));");
        w.writeLine("        if (edit.stream().anyMatch(WebElement::isDisplayed)) return true;");
        // Сигнал B: строки групп свойств. Требуем минимум 2 разных ярлыка — иначе случайное
        // вхождение слова «Сведения» в главном меню даёт ложное срабатывание.
        w.writeLine("        List<WebElement> grp = driver.findElements(By.xpath(\"//*[contains(normalize-space(.), '\\u0421\\u0432\\u0435\\u0434\\u0435\\u043d\\u0438\\u044f')] | //*[contains(normalize-space(.), '\\u0418\\u0441\\u0442\\u043e\\u0440\\u0438\\u044f')] | //*[contains(normalize-space(.), '\\u0414\\u043e\\u043a\\u0443\\u043c\\u0435\\u043d\\u0442\\u044b')]\"));");
        w.writeLine("        long groupsVisible = grp.stream().filter(WebElement::isDisplayed).count();");
        // Требуем все три ярлыка — на главном UI отдельные слова могут встретиться (например в
        // дереве сущностей), но все три вместе видны только на карточке.
        w.writeLine("        if (groupsVisible >= 3) return true;");
        // Сигнал C: модальное .x-window с формой
        w.writeLine("        List<WebElement> winForm = driver.findElements(By.cssSelector(\".x-window .x-form-field, .x-window input.x-form-text\"));");
        w.writeLine("        if (winForm.stream().anyMatch(WebElement::isDisplayed)) return true;");
        // Сигнал D: виден индикатор «Загрузка данных...» — карточка ещё грузится
        w.writeLine("        List<WebElement> loading = driver.findElements(By.xpath(\"//*[contains(normalize-space(.), '\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u043a\\u0430 \\u0434\\u0430\\u043d\\u043d\\u044b\\u0445')]\"));");
        w.writeLine("        if (loading.stream().anyMatch(WebElement::isDisplayed)) return false;");
        w.writeLine("        return false;");
        w.writeLine("    } catch (Exception e) { return false; }");
        w.writeLine("}, seconds, \"card data loaded\");");
        // ДИАГНОСТИКА на таймауте — выводим что РЕАЛЬНО видно на странице, чтобы понять, какой
        // сигнал готовности добавить под конкретный стенд.
        w.openBlock("if (!ok)");
        w.writeLine("dumpCardDiagnostics();");
        w.closeBlock();
        w.writeLine("return ok;");
        w.closeBlock();
        w.writeLine();

        // dumpCardDiagnostics: на таймауте waitForCardLoaded — печатает что видно на странице.
        // Список видимых кнопок (текст), список лейблов толщиной с заголовок, видимые .x-window'ы.
        w.openBlock("protected void dumpCardDiagnostics()");
        w.openBlock("try");
        w.writeLine("System.out.println(\"--- card diagnostics (waitForCardLoaded timed out) ---\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.writeLine("List<WebElement> btns = driver.findElements(By.cssSelector(\"button, .x-btn\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement b : btns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed() && shown < 30)");
        w.writeLine("String t = b.getText() == null ? \"\" : b.getText().trim();");
        w.openBlock("if (!t.isEmpty())");
        w.writeLine("System.out.println(\"  visible button: '\" + t + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("List<WebElement> windows = driver.findElements(By.cssSelector(\".x-window\"));");
        w.writeLine("long visibleWindows = windows.stream().filter(WebElement::isDisplayed).count();");
        w.writeLine("System.out.println(\"  visible .x-window count: \" + visibleWindows);");
        w.writeLine("List<WebElement> headers = driver.findElements(By.cssSelector(\".x-window-header, .x-panel-header, h1, h2\"));");
        w.writeLine("int hShown = 0;");
        w.openBlock("for (WebElement h : headers)");
        w.openBlock("try");
        w.openBlock("if (h.isDisplayed() && hShown < 10)");
        w.writeLine("String t = h.getText() == null ? \"\" : h.getText().trim();");
        w.openBlock("if (!t.isEmpty())");
        w.writeLine("System.out.println(\"  visible header: '\" + t + \"'\");");
        w.writeLine("hShown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Tree nodes + tab labels — критично для дочерних сущностей из дерева: показывает РЕАЛЬНЫЕ
        // подписи узлов карточки, чтобы понять, почему openTab(NODE_NAME) не нашёл узел.
        w.writeLine("List<WebElement> treeNodes = driver.findElements(By.cssSelector(\".x-tree-node-text, .x-tree3-node-text\"));");
        w.writeLine("int tShown = 0;");
        w.openBlock("for (WebElement n : treeNodes)");
        w.openBlock("try");
        w.openBlock("if (n.isDisplayed() && tShown < 40)");
        w.writeLine("String t = n.getText() == null ? \"\" : n.getText().trim();");
        w.openBlock("if (!t.isEmpty())");
        w.writeLine("System.out.println(\"  tree node: '\" + t + \"'\");");
        w.writeLine("tShown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("List<WebElement> tabLabels = driver.findElements(By.cssSelector(\".x-tab-strip-text, .x-tab-inner, .x-tab-text\"));");
        w.writeLine("int tabShown = 0;");
        w.openBlock("for (WebElement tl : tabLabels)");
        w.openBlock("try");
        w.openBlock("if (tl.isDisplayed() && tabShown < 20)");
        w.writeLine("String t = tl.getText() == null ? \"\" : tl.getText().trim();");
        w.openBlock("if (!t.isEmpty())");
        w.writeLine("System.out.println(\"  tab: '\" + t + \"'\");");
        w.writeLine("tabShown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"--- end card diagnostics ---\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"dumpCardDiagnostics error: \" + e.getMessage());");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // clickEditDropdownAction: на «Едином объекте» CRUD-действия (Сохранить Изменения,
        // Удалить, Лог.изменить, в Архив) лежат В ВЫПАДАЮЩЕМ СПИСКЕ кнопки «Редактирование»,
        // которая находится в нижней панели карточки записи, А НЕ в главном меню сущности.
        // 1. Найти и кликнуть кнопку «Редактирование» внизу карточки.
        // 2. Дождаться выпадающего меню.
        // 3. Кликнуть пункт с нужным именем (Удалить / в Архив / Лог.изменить / Сохранить
        //    Изменения).
        // ensureSvedeniyaTabActive: на карточке записи есть несколько табов (Сведения,
        // История, Документы). После открытия карточки активным обычно идёт «Сведения»,
        // но если ранее тест уже работал с этим типом записи, ExtJS может запомнить
        // последний активный таб (Документы) и открыть карточку на нём. Этот хелпер
        // принудительно кликает по табу с заголовком начинающимся на «Сведения», чтобы
        // fillX и clickEditDropdownAction работали с правильным разделом карточки.
        w.openBlock("protected boolean ensureSvedeniyaTabActive()");
        w.openBlock("try");
        // ВАЖНО: ищем ТОЛЬКО таб-стрип (x-tab-strip-text / x-tab-inner), НЕ строки в
        // навигационном дереве сверху. У навигационного дерева ячейки .x-grid3-cell-inner —
        // если кликнуть туда, ExtJS откроет другой раздел карточки.
        w.writeLine("List<WebElement> tabs = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tab-strip-text') or contains(@class,'x-tab-inner')]\"");
        w.writeLine("    + \"[starts-with(normalize-space(.), '\\u0421\\u0432\\u0435\\u0434\\u0435\\u043d\\u0438\\u044f')]\"));");
        w.openBlock("for (WebElement t : tabs)");
        w.openBlock("try");
        w.openBlock("if (t.isDisplayed())");
        w.writeLine("String txt = t.getText().trim();");
        w.writeLine("System.out.println(\"ensureSvedeniyaTabActive: кликаем таб-стрип '\" + txt + \"'\");");
        w.writeLine("tryClickAllWays(t);");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"ensureSvedeniyaTabActive: таб-стрип 'Сведения...' не найден\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"ensureSvedeniyaTabActive failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        w.openBlock("protected boolean clickEditDropdownAction(String actionName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        // ВАЖНО: ждём пока карточка полностью прогрузится, иначе кнопка «Редактирование» ещё не отрисована
        w.writeLine("waitForCardLoaded(8);");
        w.openBlock("try");
        // Ищем кнопку «Редактирование» (кнопка тулбара внизу карточки)
        w.writeLine("List<WebElement> editBtns = driver.findElements(By.xpath(");
        w.writeLine("    \"//button[contains(normalize-space(.), '\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-btn')][.//span[contains(normalize-space(.), '\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435')]]\"));");
        w.writeLine("WebElement editBtn = null;");
        w.openBlock("for (WebElement b : editBtns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed())");
        w.writeLine("editBtn = b; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (editBtn == null)");
        w.writeLine("System.out.println(\"clickEditDropdownAction: 'Редактирование' button not visible on card\");");
        // Принудительно печатаем что РЕАЛЬНО на странице — даже если waitForCardLoaded
        // ложно вернул true, мы тут увидим, какие кнопки/заголовки на экране, и поймём,
        // открылась ли карточка вообще.
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("return false;");
        w.closeBlock();
        // Кликаем кнопку «Редактирование» через tryClickAllWays
        w.writeLine("System.out.println(\"clickEditDropdownAction: clicking 'Редактирование' to open dropdown\");");
        w.writeLine("tryClickAllWays(editBtn);");
        w.writeLine("Thread.sleep(500);");
        // Теперь ищем пункт действия в открывшемся выпадающем меню
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-menu-item-text')][contains(normalize-space(.), '\" + actionName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.), '\" + actionName + \"')]\"));");
        w.openBlock("for (WebElement m : items)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed())");
        w.writeLine("System.out.println(\"clickEditDropdownAction: clicking '\" + actionName + \"'\");");
        w.writeLine("tryClickAllWays(m);");
        w.writeLine("Thread.sleep(700);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"clickEditDropdownAction: action '\" + actionName + \"' not found in dropdown\");");
        // Для диагностики выводим видимые пункты меню
        w.writeLine("List<WebElement> anyItems = driver.findElements(By.cssSelector(\".x-menu-item-text\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement m : anyItems)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed() && shown < 10)");
        w.writeLine("System.out.println(\"  dropdown has: '\" + m.getText().trim() + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Закрываем выпадающее меню
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"clickEditDropdownAction failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // clickGridRefresh: жмёт «зелёную» кнопку обновления грида (PagingToolbar.doRefresh) —
        // ровно то, что делает пользователь внизу таблицы. Это РЕАЛЬНЫЙ серверный round-trip:
        // store.reload() перечитывает данные с сервера и ВЫБРАСЫВАЕТ несохранённые phantom-строки,
        // поэтому после refresh счётчик/стор отражают истину сервера (а не локальный мусор).
        // Возвращает true если обновление было запущено.
        w.openBlock("protected boolean clickGridRefresh()");
        w.openBlock("try");
        w.writeLine("Object r = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { if (typeof Ext==='undefined') return 'no-ext';\"");
        w.writeLine("    + \" var gs=[]; if (Ext.ComponentQuery && Ext.ComponentQuery.query) gs=Ext.ComponentQuery.query('editorgrid,gridpanel,grid');\"");
        w.writeLine("    + \" else if (Ext.ComponentMgr && Ext.ComponentMgr.all){ var a=Ext.ComponentMgr.all.items||[]; for(var i=0;i<a.length;i++){var c=a[i]; if(c&&c.getStore&&c.getColumnModel) gs.push(c);} }\"");
        // РАБОЧИЙ ГРИД = нижний дата-грид с панелью пагинации в активном окне (как locate/count),
        // а НЕ «самый большой». Иначе обновляли не тот грид.
        w.writeLine("    + \" var aw=(Ext.WindowMgr&&Ext.WindowMgr.getActive)?Ext.WindowMgr.getActive():null; var awDom=(aw&&aw.getEl)?(aw.getEl().dom||aw.getEl()):null;\"");
        w.writeLine("    + \" var best=null,bestDom=null,bestScore=-1; for(var i=0;i<gs.length;i++){ var g=gs[i]; if(!g.rendered||!g.getStore) continue; var dom=null; try{var el=g.getEl?g.getEl():null; dom=el?(el.dom||el):null;}catch(e){} if(!dom||dom.offsetWidth<=0) continue;\"");
        w.writeLine("    + \"  var isProp=false; try{isProp=(g.getXType&&g.getXType()==='propertygrid')||!!g.propertyNames;}catch(e){} if(isProp) continue;\"");
        w.writeLine("    + \"  var paging=false; try{paging=!!dom.querySelector('.x-tbar-page-number,.x-tbar-page-next,.x-tbar-loading')||!!(g.getBottomToolbar&&g.getBottomToolbar());}catch(e){}\"");
        w.writeLine("    + \"  var inActive=awDom?(awDom===dom||awDom.contains(dom)):true; var score=(inActive?1000:0)+(paging?400:0); if(score>bestScore){bestScore=score;best=g;bestDom=dom;} }\"");
        w.writeLine("    + \" if(!best) return 'no-grid'; window.__t2grid=best; window.__t2dom=bestDom; var s=best.getStore(); var did=[];\"");
        // ВАЖНО: НЕ вызываем s.rejectChanges() здесь — он откатывает строки, помеченные на удаление
        //    (modified), и несохранённые правки, чем ломал delete (строка восстанавливалась → gone=false).
        //    Обновление делаем через зелёную кнопку тулбара + серверный reload.
        // 1) Кнопка обновления paging-тулбара (как пользователь жмёт «зелёную» иконку).
        w.writeLine("    + \" try{ var bb=best.getBottomToolbar?best.getBottomToolbar():null; if(bb && bb.doRefresh){ bb.doRefresh(); did.push('doRefresh'); } }catch(e){}\"");
        // 2) Реальный серверный round-trip — перечитать стор.
        w.writeLine("    + \" try{ s.reload(); did.push('reload'); }catch(e){ did.push('reload-err'); }\"");
        w.writeLine("    + \" return did.length?did.join(','):'noop'; } catch(e){ return 'err:'+e.message; }\");");
        w.writeLine("System.out.println(\"clickGridRefresh: \" + r);");
        w.writeLine("boolean refreshOk = (r != null && !\"noop\".equals(r) && !String.valueOf(r).startsWith(\"err\") && !\"no-grid\".equals(r) && !\"no-ext\".equals(r));");
        // Физически кликаем «зелёную» кнопку обновления В ПАНЕЛИ РАБОЧЕГО ГРИДА (window.__t2dom),
        // а не любую .x-tbar-loading на странице — на некоторых сборках doRefresh недоступен.
        w.openBlock("try");
        w.writeLine("WebElement rbtn = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var d=window.__t2dom||document; var b=d.querySelector('.x-tbar-loading, .x-tbar-page-refresh');\"");
        // Если стандартных классов нет — ищем кнопку по подсказке «Обновить» (data-qtip/title) в панели.
        w.writeLine("    + \" if(!b){ var cand=d.querySelectorAll('button, .x-btn, .x-tbar-page-refresh, [data-qtip], [title]'); for(var i=0;i<cand.length;i++){ var c=cand[i]; var tip=(c.getAttribute&&(c.getAttribute('data-qtip')||c.getAttribute('title')||c.getAttribute('aria-label')))||''; if(tip.indexOf('\\u041e\\u0431\\u043d\\u043e\\u0432')>=0){ b=c; break; } } }\"");
        w.writeLine("    + \" return b||null; } catch(e){ return null; }\");");
        w.openBlock("if (rbtn != null && rbtn.isDisplayed())");
        w.writeLine("tryClickAllWays(rbtn);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("Thread.sleep(2500);");
        w.writeLine("return refreshOk;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"clickGridRefresh failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // waitForLoadMask: ждёт пока исчезнут ExtJS load-mask (.ext-el-mask/.x-mask) и окна
        // «Выполнение операции…»/«Загрузка данных…». Нужен перед кликом по тулбару «Редактирование»:
        // спиннер перехватывал клик (ElementClickInterceptedException) и «Сохранить Изменения»
        // не находилось в дропдауне.
        w.openBlock("protected void waitForLoadMask(int seconds)");
        w.openBlock("try");
        w.writeLine("long deadline = System.currentTimeMillis() + seconds * 1000L;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("Boolean busy = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var m=document.querySelectorAll('.ext-el-mask, .x-mask, .x-mask-loading'); for(var i=0;i<m.length;i++){ if(m[i].offsetWidth>0||m[i].offsetHeight>0) return true; }\"");
        w.writeLine("    + \" var ws=document.querySelectorAll('.x-window'); for(var j=0;j<ws.length;j++){ var wn=ws[j]; if(wn.offsetWidth<=0) continue; var t=(wn.innerText||''); if(t.indexOf('\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0435\\u043d\\u0438\\u0435 \\u043e\\u043f\\u0435\\u0440\\u0430\\u0446\\u0438\\u0438')>=0 || t.indexOf('\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u043a\\u0430 \\u0434\\u0430\\u043d\\u043d\\u044b\\u0445')>=0) return true; }\"");
        w.writeLine("    + \" return false; } catch(e){ return false; }\");");
        w.openBlock("if (!Boolean.TRUE.equals(busy))");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("Thread.sleep(150);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // dismissErrorPopup: если на экране висит окно серверной ОШИБКИ (напр. SP_GSK_S_CAUSE /
        // trunc(date)), закрывает его кнопкой OK и возвращает true. В отличие от проверки в конце
        // теста (которая честно фейлит), этот метод нужен ВО ВРЕМЯ заполнения inline-строки: на
        // стенде коммит ячейки/даты может сразу выбросить серверный попап, который перехватывает
        // клики и не даёт дозаполнить остальные поля. Закрываем и продолжаем.
        w.openBlock("protected boolean dismissErrorPopup()");
        w.openBlock("try");
        w.writeLine("Boolean hasErr = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var ws=document.querySelectorAll('.x-window'); for (var i=0;i<ws.length;i++){ var wn=ws[i]; if (wn.offsetWidth<=0) continue; var t=(wn.innerText||''); if (t.indexOf('\\u041e\\u0428\\u0418\\u0411\\u041a\\u0410')>=0 || t.toLowerCase().indexOf('trunc')>=0 || t.indexOf('SP_')>=0) return true; } return false; } catch(e){ return false; }\");");
        w.openBlock("if (!Boolean.TRUE.equals(hasErr))");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [dismissErrorPopup] серверный попап обнаружен — закрываем OK и продолжаем\");");
        // Кликаем OK (или «Закрыть»/«Да») чтобы убрать модальное окно ошибки и продолжить ввод.
        w.writeLine("boolean closed = clickButtonByText(\"OK\");");
        w.writeLine("if (!closed) closed = clickButtonByText(\"\\u0417\\u0430\\u043a\\u0440\\u044b\\u0442\\u044c\");");
        w.writeLine("if (!closed) closed = clickButtonByText(\"\\u0414\\u0430\");");
        // Фолбэк: ESC закрывает верхнее модальное окно.
        w.openBlock("if (!closed)");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("closed = true;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("try { Thread.sleep(300); } catch (InterruptedException ignored) {}");
        w.writeLine("return closed;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [dismissErrorPopup] failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // setFieldViaExtApi: устанавливает значение ExtJS form-field'а по его fieldLabel
        // или name через ExtJS API. Это работает даже там, где DOM-инпут не существует
        // (например в свёрнутом PropertyGrid). Возвращает true если поле найдено и значение
        // установлено.
        w.openBlock("protected boolean setFieldViaExtApi(String labelOrName, String value)");
        w.openBlock("try");
        w.writeLine("Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var target = arguments[0]; var val = arguments[1];\"");
        w.writeLine("    + \"  var fields = [];\"");
        w.writeLine("    + \"  if (Ext.ComponentQuery) fields = Ext.ComponentQuery.query('field');\"");
        w.writeLine("    + \"  if (fields.length === 0 && Ext.ComponentMgr) {\"");
        w.writeLine("    + \"    Ext.ComponentMgr.all.each(function(c) { if (c.setValue && c.fieldLabel !== undefined) fields.push(c); });\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  var match = fields.filter(function(f) { return (f.fieldLabel && f.fieldLabel.indexOf(target) >= 0) || (f.name && f.name === target) || (f.boxLabel && f.boxLabel.indexOf(target) >= 0); });\"");
        w.writeLine("    + \"  if (match.length === 0) return 'no-field';\"");
        w.writeLine("    + \"  match[0].setValue(val);\"");
        w.writeLine("    + \"  return 'set';\"");
        w.writeLine("    + \"} catch (e) { return 'err:' + e.message; }\",");
        w.writeLine("    labelOrName, value);");
        w.openBlock("if (\"set\".equals(result))");
        w.writeLine("System.out.println(\"setFieldViaExtApi: '\" + labelOrName + \"' = '\" + value + \"' (via Ext.field.setValue)\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        //   3. вводим значение в появившийся input
        //   4. Tab для фиксации
        // Возвращает true при успехе.
        w.openBlock("protected boolean fillPropertyGridCell(String rowLabel, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Ищем строки PropertyGrid. В ExtJS 3 это .x-grid3-row, в v4+ — .x-grid-row, каждая с
        // двумя ячейками (подпись, значение). Берём строку, в первой ячейке которой есть подпись.
        w.writeLine("String labelLc = rowLabel == null ? \"\" : rowLabel.toLowerCase();");
        // Убираем хвостовую «*», которой PropertyGrid помечает обязательные поля.
        w.writeLine("String cleanLabel = rowLabel == null ? \"\" : rowLabel.replace(\"*\", \"\").trim();");
        w.writeLine("List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row, table.x-grid-table tr, tr.x-grid-data-row\"));");
        w.writeLine("WebElement targetValueCell = null;");
        w.openBlock("for (WebElement row : rows)");
        w.openBlock("try");
        w.openBlock("if (!row.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("List<WebElement> cells = row.findElements(By.cssSelector(\"td, .x-grid3-cell, .x-grid-cell\"));");
        w.openBlock("if (cells.size() < 2)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String leftText = cells.get(0).getText();");
        w.openBlock("if (leftText == null)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String leftClean = leftText.replace(\"*\", \"\").trim();");
        w.writeLine("boolean leftLc = leftClean.toLowerCase().contains(cleanLabel.toLowerCase());");
        w.writeLine("boolean labelLc2 = cleanLabel.toLowerCase().contains(leftClean.toLowerCase());");
        w.openBlock("if (leftClean.equalsIgnoreCase(cleanLabel) || leftLc || labelLc2)");
        w.writeLine("targetValueCell = cells.get(cells.size() - 1);");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (targetValueCell == null)");
        w.writeLine("return false;");
        w.closeBlock();
        // Кликаем ячейку значения дважды — ExtJS PropertyGrid иногда требует два клика
        // (первый выделяет строку, второй активирует редактор).
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(targetValueCell).click().pause(150).click().perform();");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Теперь должен быть виден input inline-редактора. Вводим в него.
        w.writeLine("List<WebElement> editors = driver.findElements(By.cssSelector(\".x-grid-editor input, .x-form-text:not(.x-combo-noedit), input.x-form-field\"));");
        w.openBlock("for (WebElement ed : editors)");
        w.openBlock("try");
        w.openBlock("if (ed.isDisplayed() && ed.isEnabled())");
        w.openBlock("try");
        w.writeLine("ed.clear();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("ed.sendKeys(value);");
        w.writeLine("ed.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"fillPropertyGridCell error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        w.openBlock("protected boolean isColumnPresent(String columnTitle)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String t = columnTitle == null ? \"\" : columnTitle.trim();");
        // Убираем суффикс «_», который подбор по основам добавляет для различения.
        w.writeLine("if (t.endsWith(\"_\")) t = t.substring(0, t.length() - 1);");
        w.writeLine("List<WebElement> headers = driver.findElements(By.xpath(");
        w.writeLine("    \"//th[contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //td[contains(@class,'header')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid3-hd')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid3-hd-inner')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-column-header')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'x-column-header-text')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-column-header')][contains(normalize-space(.), '\" + t + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid-header')]//*[self::div or self::span or self::a][contains(normalize-space(.), '\" + t + \"')]\"));");
        w.writeLine("return headers.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: проверить наличие кнопки с заданным текстом
        w.openBlock("protected boolean isButtonPresent(String buttonText)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> buttons = driver.findElements(By.xpath(\"//button[contains(text(), '\" + buttonText + \"')] | //input[@value='\" + buttonText + \"'] | //a[contains(text(), '\" + buttonText + \"')]\"));");
        w.writeLine("return buttons.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();

        // Хелпер: проверить, открыто ли окно-диалог ExtJS
        w.openBlock("protected boolean isDialogOpen()");
        w.openBlock("try");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.writeLine("java.util.List<WebElement> windows = driver.findElements(By.cssSelector(\".x-window\"));");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("return windows.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // isOnRecordCard: true, если мы на странице деталей записи. E3Core показывает её в
        // нескольких вариантах:
        //   1. классический модальный диалог (.x-window)
        //   2. страница с таб-стрипом ExtJS
        //   3. навигированная страница «Единый объект» со списком групп свойств справа
        //      (строки «Сведения», «История», «Документы») и тулбаром снизу с «Редактирование»,
        //      «Обновить», «Печать...» — этот вариант на текущем стенде.
        // Для (3) проверяем текст заголовка или нижний тулбар.
        w.openBlock("protected boolean isOnRecordCard()");
        w.openBlock("if (isDialogOpen())");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        // Сигнал B: текст «Единый объект» — точный признак карточки записи. Таб-стрип
        // (.x-tab-strip-text) для проверки не используем: он есть и на странице поиска, что давало
        // ложное срабатывание (карточка считалась открытой, хотя мы ещё в списке результатов).
        w.writeLine("List<WebElement> ed = driver.findElements(By.xpath(\"//*[contains(normalize-space(.), '\\u0415\\u0434\\u0438\\u043d\\u044b\\u0439 \\u043e\\u0431\\u044a\\u0435\\u043a\\u0442')]\"));");
        w.openBlock("if (ed.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("return true;");
        w.closeBlock();
        // Сигнал C: кнопка тулбара «Редактирование», встречается только в карточке
        w.writeLine("List<WebElement> editBtn = driver.findElements(By.xpath(\"//button[contains(normalize-space(.), '\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435')]\"));");
        w.openBlock("if (editBtn.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: безопасный клик с обработкой перехвата оверлеями
        w.openBlock("protected void clickSafely(WebElement element)");
        w.openBlock("try");
        w.writeLine("element.click();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("// Click intercepted or stale — try via JavaScript");
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].click();\", element);");
        w.closeBlock();
        w.openBlock("catch (Exception e2)");
        w.writeLine("throw new RuntimeException(\"Cannot click element: \" + e2.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: клик по кнопке по видимому тексту, с обработкой перехвата
        w.openBlock("protected boolean clickButtonByText(String buttonText)");
        w.openBlock("try");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.writeLine("// Wait for any loading masks to disappear");
        w.writeLine("java.util.List<WebElement> masks = driver.findElements(By.cssSelector(\".ext-el-mask, .x-mask\"));");
        w.writeLine("long waitMasks = System.currentTimeMillis() + 2000;");
        w.openBlock("while (!masks.isEmpty() && masks.stream().anyMatch(WebElement::isDisplayed) && System.currentTimeMillis() < waitMasks)");
        w.openBlock("try");
        w.writeLine("Thread.sleep(100);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.writeLine("masks = driver.findElements(By.cssSelector(\".ext-el-mask, .x-mask\"));");
        w.closeBlock();
        // Ищем по normalize-space(.), а не по прямому text-node: ExtJS оборачивает подпись кнопки
        // в <span>, а сами кнопки часто <a class="x-btn">. Плюс fallback на
        // <a class="x-btn"><span>...</span></a>.
        w.writeLine("String xp = \"//button[contains(normalize-space(.), '\" + buttonText + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-btn')][.//span[contains(normalize-space(.), '\" + buttonText + \"')]]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-btn')][contains(normalize-space(.), '\" + buttonText + \"')]\"");
        w.writeLine("    + \" | //input[@type='button'][@value='\" + buttonText + \"' or contains(@value,'\" + buttonText + \"')]\";");
        w.writeLine("java.util.List<WebElement> btns = driver.findElements(By.xpath(xp));");
        w.writeLine("WebElement target = null;");
        w.openBlock("for (WebElement b : btns)");
        w.openBlock("try");
        w.openBlock("if (b.isDisplayed() && b.isEnabled())");
        w.writeLine("target = b; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (target == null)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("System.out.println(\"clickButtonByText: '\" + buttonText + \"' not found (matched \" + btns.size() + \" total, none displayed/enabled)\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("clickSafely(target);");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("System.out.println(\"Could not click button '\" + buttonText + \"': \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: видна ли сейчас кнопка с заданным текстом (без клика по ней).
        w.openBlock("protected boolean isButtonVisible(String buttonText)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("List<WebElement> btns = driver.findElements(By.xpath(");
        w.writeLine("    \"//button[contains(normalize-space(.), '\" + buttonText + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-btn')][.//span[contains(normalize-space(.), '\" + buttonText + \"')]]\"));");
        w.writeLine("return btns.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: сделать скриншот при ошибке
        w.openBlock("protected void takeScreenshot(String testName)");
        w.openBlock("try");
        w.writeLine("File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);");
        w.writeLine("Path dir = Path.of(\"target/screenshots\");");
        w.writeLine("Files.createDirectories(dir);");
        w.writeLine("String fileName = testName.replaceAll(\"[^a-zA-Z0-9а-яА-Я]\", \"_\") + \".png\";");
        w.writeLine("Files.copy(src.toPath(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);");
        w.writeLine("System.out.println(\"Screenshot saved: target/screenshots/\" + fileName);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not take screenshot: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Хелпер: сбросить состояние, закрыв открытые диалоги
        w.openBlock("protected void resetState()");
        w.openBlock("try");
        w.writeLine("// Close any open ExtJS dialogs/windows");
        w.writeLine("java.util.List<WebElement> closeBtns = driver.findElements(By.cssSelector(\".x-tool-close, .x-window-close\"));");
        w.openBlock("for (WebElement btn : closeBtns)");
        w.openBlock("try");
        w.writeLine("if (btn.isDisplayed()) btn.click();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("// Press Escape to close any remaining dialogs");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();

        // ====== Хелперы пошаговых скриншотов + утилиты проверок ======

        // entityName(): русское имя сущности, как оно в меню лаунчера — используется
        // menuAction/openSearch/openRecordCard. Каждый сгенерированный подкласс переопределяет
        // метод, возвращая свою константу ENTITY_NAME («ГСК/ОГСК», «Совещание», …). По умолчанию
        // откатывается на транслитерированное имя класса, чтобы не было NPE, если подкласс забыл.
        w.openBlock("protected String entityName()");
        w.writeLine("return shotEntityName();");
        w.closeBlock();
        w.writeLine();

        // shotEntityName(): транслитерированное ASCII-имя для имён файлов скриншотов. Отдельно от
        // entityName(), т.к. нужны имена вида «GSKOGSK_testX_*.png», а не кириллические.
        w.openBlock("protected String shotEntityName()");
        w.writeLine("String n = getClass().getSimpleName();");
        w.openBlock("if (n.endsWith(\"Test\"))");
        w.writeLine("n = n.substring(0, n.length() - 4);");
        w.closeBlock();
        w.writeLine("return n;");
        w.closeBlock();
        w.writeLine();

        // shot(step): нумерованный скриншот для текущего теста. Файлы попадают в target/screenshots/
        // с именем <Entity>_<testMethod>_<step#>_<step>_<status>.png, так что листинг каталога
        // читается как раскадровка прогона теста.
        w.openBlock("protected void shot(String step)");
        w.writeLine("shot(step, \"OK\");");
        w.closeBlock();
        w.writeLine();

        w.openBlock("protected void shot(String step, String status)");
        w.openBlock("try");
        w.writeLine("stepCounter++;");
        w.writeLine("String safeStep = step == null ? \"step\" : step.replaceAll(\"[^a-zA-Z0-9а-яА-Я_-]\", \"_\");");
        w.writeLine("String safeEntity = shotEntityName().replaceAll(\"[^a-zA-Z0-9а-яА-Я_-]\", \"_\");");
        w.writeLine("String fname = String.format(\"%s_%s_%02d_%s_%s.png\", safeEntity, currentTestName, stepCounter, safeStep, status);");
        w.writeLine("File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);");
        w.writeLine("Path dir = Path.of(\"target/screenshots\");");
        w.writeLine("Files.createDirectories(dir);");
        w.writeLine("Files.copy(src.toPath(), dir.resolve(fname), StandardCopyOption.REPLACE_EXISTING);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"shot('\" + step + \"') failed: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // gridContainsRow(marker): true, если хоть одна видимая строка грида РЕЗУЛЬТАТОВ содержит
        // marker. Через JS находим самый большой грид на странице (та же эвристика, что в
        // captureFirstResultRowSignature) — это и есть таблица результатов; параметрический грид
        // формы поиска обычно из 1-3 строк и отсеивается автоматически, иначе был бы ложный PASS.
        w.openBlock("protected boolean gridContainsRow(String marker)");
        w.openBlock("if (marker == null || marker.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var marker = arguments[0];\"");
        w.writeLine("    + \"var rows = document.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"var groups = {};\"");
        w.writeLine("    + \"for (var i = 0; i < rows.length; i++) {\"");
        w.writeLine("    + \"  var r = rows[i]; if (r.offsetHeight === 0 || r.offsetWidth === 0) continue;\"");
        w.writeLine("    + \"  var p = r.parentElement;\"");
        w.writeLine("    + \"  while (p && !(p.classList && (p.classList.contains('x-grid3') || p.classList.contains('x-grid-panel') || p.classList.contains('x-grid')))) p = p.parentElement;\"");
        w.writeLine("    + \"  var key = p ? (p.id || p.className) : 'none';\"");
        w.writeLine("    + \"  if (!groups[key]) groups[key] = []; groups[key].push(r);\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"var bestKey = null, bestCount = 0;\"");
        w.writeLine("    + \"for (var k in groups) { if (groups[k].length > bestCount) { bestCount = groups[k].length; bestKey = k; } }\"");
        w.writeLine("    + \"if (!bestKey) return false;\"");
        w.writeLine("    + \"var resultRows = groups[bestKey];\"");
        w.writeLine("    + \"for (var i = 0; i < resultRows.length; i++) {\"");
        w.writeLine("    + \"  var txt = resultRows[i].innerText || resultRows[i].textContent || '';\"");
        w.writeLine("    + \"  if (txt.indexOf(marker) >= 0) return true;\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"return false;\",");
        w.writeLine("    marker);");
        w.writeLine("return Boolean.TRUE.equals(result);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"gridContainsRow failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // getVisibleRowCount(): считает видимые строки грида. Нужен CRUD-тестам для проверки дельт.
        w.openBlock("protected int getVisibleRowCount()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row, tbody tr\"));");
        w.writeLine("return (int) rows.stream().filter(WebElement::isDisplayed).count();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return 0;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // captureFirstResultRowSignature(): возвращает текст ПЕРВОЙ видимой строки САМОГО
        // БОЛЬШОГО грида на странице (как selectAndOpenRecord) — то есть строки, которую
        // мы готовы открыть на редактирование/удаление. С этого текста срезаем ведущую
        // ячейку «номер строки» (число), чтобы маркер не сматчился с «новой первой
        // строкой» после того как нашу запись удалят. Используется testDelete: захватываем
        // подпись ДО открытия карточки, потом после удаления проверяем что её больше нет.
        w.openBlock("protected String captureFirstResultRowSignature()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("Object raw = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var aw = (typeof Ext !== 'undefined' && Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null;\"");
        w.writeLine("    + \"var root = (aw && aw.getEl) ? (aw.getEl().dom || aw.getEl()) : document;\"");
        w.writeLine("    + \"var rows = root.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"var groups = {};\"");
        w.writeLine("    + \"for (var i = 0; i < rows.length; i++) {\"");
        w.writeLine("    + \"  var r = rows[i]; if (r.offsetHeight === 0 || r.offsetWidth === 0) continue;\"");
        w.writeLine("    + \"  var p = r.parentElement;\"");
        w.writeLine("    + \"  while (p && !(p.classList && (p.classList.contains('x-grid3') || p.classList.contains('x-grid-panel') || p.classList.contains('x-grid')))) p = p.parentElement;\"");
        w.writeLine("    + \"  var key = p ? (p.id || p.className) : 'none';\"");
        w.writeLine("    + \"  if (!groups[key]) groups[key] = []; groups[key].push(r);\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"var bestKey = null, bestCount = 0;\"");
        w.writeLine("    + \"for (var k in groups) { if (groups[k].length > bestCount) { bestCount = groups[k].length; bestKey = k; } }\"");
        w.writeLine("    + \"if (!bestKey) return '';\"");
        w.writeLine("    + \"var r0 = groups[bestKey][0];\"");
        w.writeLine("    + \"return r0.innerText || r0.textContent || '';\");");
        w.writeLine("String full = raw == null ? \"\" : raw.toString().trim();");
        w.openBlock("if (full.isEmpty())");
        w.writeLine("return \"\";");
        w.closeBlock();
        // Срезаем ведущие строки, которые выглядят как «номер строки» (чисто цифры
        // или пусто) — оставляем только содержимое последующих ячеек, чтобы маркер
        // искался по реальным данным, а не по нумерации.
        w.writeLine("String[] lines = full.split(\"\\r?\\n\");");
        w.writeLine("StringBuilder sig = new StringBuilder();");
        w.openBlock("for (String line : lines)");
        w.writeLine("String t = line == null ? \"\" : line.trim();");
        w.openBlock("if (sig.length() == 0 && (t.isEmpty() || t.matches(\"\\\\d+\")))");
        w.writeLine("continue;");
        w.closeBlock();
        w.openBlock("if (sig.length() > 0)");
        w.writeLine("sig.append('\\n');");
        w.closeBlock();
        w.writeLine("sig.append(t);");
        w.closeBlock();
        // Убираем хвостовые пробелы.
        w.writeLine("return sig.toString().trim();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"captureFirstResultRowSignature failed: \" + e.getMessage());");
        w.writeLine("return \"\";");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // gridContainsAny(values): true если ХОТЯ БЫ одно из переданных значений
        // содержится в видимом гриде (использует gridContainsRow по очереди). Нужно для
        // testCreate: после save мы знаем КАКИЕ значения вписывали и проверяем по любому
        // из них — типовой случай, когда marker-поле не отображается в результирующей
        // таблице, но другое заполненное поле (например, Тип/Адрес) — отображается.
        w.openBlock("protected boolean gridContainsAny(java.util.Collection<String> values)");
        w.openBlock("if (values == null || values.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("for (String v : values)");
        w.openBlock("if (v == null || v.isEmpty())");
        w.writeLine("continue;");
        w.closeBlock();
        w.openBlock("if (gridContainsRow(v))");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine();

        // gridStoreContainsText(marker): ищет marker в данных ВСЕХ ExtJS grid store на странице.
        // В отличие от gridContainsRow (который видит только отрендеренные DOM-строки),
        // этот метод обходит ВСЕ записи store, включая те, что на «следующих страницах»
        // пейджинации. Используется testCreate как fallback если маркер не видно в DOM.
        w.openBlock("protected boolean gridStoreContainsText(String marker)");
        w.openBlock("if (marker == null || marker.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"if (typeof Ext === 'undefined') return false;\"");
        w.writeLine("    + \"var marker = arguments[0];\"");
        w.writeLine("    + \"var grids = Ext.ComponentQuery ? Ext.ComponentQuery.query('gridpanel,grid') : [];\"");
        w.writeLine("    + \"if (!grids || grids.length === 0) {\"");
        w.writeLine("    + \"  grids = [];\"");
        w.writeLine("    + \"  Ext.ComponentMgr.all.each(function(c) { if (c.getStore && c.getColumnModel) grids.push(c); });\"");
        w.writeLine("    + \"}\"");
        // Отсеиваем propertygrid'ы (параметрическая форма поиска): они содержат значения,
        // которые тест сам туда вписал, и давали ложный positive когда result-grid пуст.
        w.writeLine("    + \"grids = grids.filter(function(g) {\"");
        w.writeLine("    + \"  var xt = (g.getXType && g.getXType()) || g.xtype || '';\"");
        w.writeLine("    + \"  if (xt === 'propertygrid' || xt === 'property') return false;\"");
        w.writeLine("    + \"  var cls = (g.el && g.el.dom && g.el.dom.className) || '';\"");
        w.writeLine("    + \"  if (cls.indexOf('x-property-grid') >= 0) return false;\"");
        w.writeLine("    + \"  return true;\"");
        w.writeLine("    + \"});\"");
        w.writeLine("    + \"for (var gi = 0; gi < grids.length; gi++) {\"");
        w.writeLine("    + \"  var store = grids[gi].getStore ? grids[gi].getStore() : null;\"");
        w.writeLine("    + \"  if (!store) continue;\"");
        w.writeLine("    + \"  var count = store.getCount ? store.getCount() : 0;\"");
        w.writeLine("    + \"  for (var ri = 0; ri < count; ri++) {\"");
        w.writeLine("    + \"    var rec = store.getAt(ri);\"");
        w.writeLine("    + \"    if (!rec || !rec.data) continue;\"");
        w.writeLine("    + \"    for (var key in rec.data) {\"");
        w.writeLine("    + \"      var val = rec.data[key];\"");
        w.writeLine("    + \"      if (val != null && String(val).indexOf(marker) >= 0) return true;\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"return false;\",");
        w.writeLine("    marker);");
        w.writeLine("return Boolean.TRUE.equals(result);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"gridStoreContainsText failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // matchesMask(value, mask): true, если value соответствует маске в стиле E3Core/ExtJS.
        // Грамматика маски (должна совпадать с TestDataFactory.generateFromMask):
        //   цифра  : '9', '0', '#'
        //   буква  : 'a', 'A', 'L'
        //   любой  : 'X', 'x', '*', '?'
        //   прочие символы — литералы.
        // Пример: "99.99.9999" → ^\d{2}\.\d{2}\.\d{4}$
        w.openBlock("protected boolean matchesMask(String value, String mask)");
        w.openBlock("if (value == null || mask == null || mask.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("StringBuilder regex = new StringBuilder(\"^\");");
        w.openBlock("for (int i = 0; i < mask.length(); i++)");
        w.writeLine("char c = mask.charAt(i);");
        w.openBlock("if (c == '9' || c == '0' || c == '#')");
        w.writeLine("regex.append(\"\\\\d\");");
        w.closeBlock();
        w.openBlock("else if (c == 'a' || c == 'A' || c == 'L')");
        w.writeLine("regex.append(\"[A-Za-zА-Яа-я]\");");
        w.closeBlock();
        w.openBlock("else if (c == 'X' || c == 'x' || c == '*' || c == '?')");
        w.writeLine("regex.append(\".\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("regex.append(\"$\");");
        w.writeLine("return value.matches(regex.toString());");
        w.closeBlock();
        w.writeLine();

        // assertCoverage(found, expected, threshold, missing, ctx): жёстко проверяет, что доля
        // найденных элементов достигает порога, и выводит, чего не хватает.
        // Основной инструмент для testFieldsPresent и testGrid* вместо тихих println-логов.
        w.openBlock("protected void assertCoverage(int found, int expected, double threshold, java.util.List<String> missing, String ctx)");
        w.openBlock("if (expected <= 0)");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("double coverage = (double) found / (double) expected;");
        w.writeLine("String msg = String.format(\"%s: coverage %.0f%% (%d of %d), threshold %.0f%%. Missing: %s\",");
        w.writeLine("    ctx, coverage * 100.0, found, expected, threshold * 100.0,");
        w.writeLine("    (missing == null || missing.isEmpty()) ? \"-\" : String.join(\", \", missing));");
        w.writeLine("org.junit.jupiter.api.Assertions.assertTrue(coverage >= threshold, msg);");
        w.closeBlock();
        w.writeLine();

        // refreshGrid(): после деструктивного теста (удаление/архивирование) заново запускает пустой
        // поиск, чтобы грид результатов отражал текущее состояние для следующего теста в цепочке @Order.
        w.openBlock("protected void refreshGrid()");
        w.openBlock("try");
        w.writeLine("executeSearchIfPresent();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // loadRowIntoTree(): правый клик по первой строке грида результатов, затем клик
        // «Загрузить выбранные объекты в дерево» — штатный для E3Core способ открыть карточку
        // «Единый объект». Возвращает true, если пункт был нажат. Дальше вызывающий код должен
        // через waitUntil(isOnRecordCard()) убедиться, что карточка открылась. Вызывается в конце
        // testFieldsPresent, чтобы следующие testGrid* стартовали с уже загруженной карточкой.
        w.openBlock("protected boolean loadRowIntoTree()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row, tbody tr\"));");
        w.writeLine("WebElement firstRow = null;");
        w.openBlock("for (WebElement r : rows)");
        w.openBlock("try");
        w.openBlock("if (r.isDisplayed())");
        w.writeLine("firstRow = r; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (firstRow == null)");
        w.writeLine("System.out.println(\"loadRowIntoTree: no visible row in the result grid\");");
        w.writeLine("return false;");
        w.closeBlock();
        // Сначала ОДИНОЧНЫЙ клик — выделяем строку. «Загрузить выбранные объекты»
        // работает с выделенной строкой; без явного выделения contextClick на части
        // стендов открывает меню, но строка остаётся невыделенной.
        w.openBlock("try");
        w.writeLine("firstRow.click();");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Правый клик по первой строке для открытия контекстного меню
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).contextClick().perform();");
        w.writeLine("Thread.sleep(600);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"loadRowIntoTree: contextClick failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        // Ищем «Загрузить выбранные объекты в дерево» в контекстном меню
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-menu-item-text')][contains(normalize-space(.), '\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u0438\\u0442\\u044c \\u0432\\u044b\\u0431\\u0440\\u0430\\u043d\\u043d\\u044b\\u0435')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.), '\\u0417\\u0430\\u0433\\u0440\\u0443\\u0437\\u0438\\u0442\\u044c \\u0432\\u044b\\u0431\\u0440\\u0430\\u043d\\u043d\\u044b\\u0435')]\"));");
        w.openBlock("for (WebElement m : items)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed())");
        w.writeLine("System.out.println(\"loadRowIntoTree: clicking '\" + m.getText().trim() + \"' from context menu\");");
        w.writeLine("tryClickAllWays(m);");
        w.writeLine("Thread.sleep(800);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Диагностический дамп: какие пункты были в контекстном меню? Полезно, когда меню
        // открылось, но нужного пункта в нём нет.
        w.writeLine("List<WebElement> anyItems = driver.findElements(By.cssSelector(\".x-menu-item-text\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement m : anyItems)");
        w.openBlock("try");
        w.openBlock("if (m.isDisplayed() && shown < 10)");
        w.writeLine("System.out.println(\"  context menu has: '\" + m.getText().trim() + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (shown == 0)");
        w.writeLine("System.out.println(\"loadRowIntoTree: context menu didn't open after right-click\");");
        w.closeBlock();
        w.writeLine("System.out.println(\"loadRowIntoTree: 'Загрузить выбранные объекты в дерево' not found\");");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"loadRowIntoTree failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // clickEntityMenuItem: надёжная замена хрупкому menuAction. Использует подбор по основам
        // слов (как descendMenu) вместо точного contains(text()). Простой contains(text(),'ГСК/ОГСК')
        // не совпадает с пунктами, которые ExtJS рисует с лишними пробелами, NBSP или обёртками.
        w.openBlock("protected boolean clickEntityMenuItem(String entityName, String[] actionsToTry)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"\\u041d\\u0421\\u0418\", \"\\u041e\\u0442\\u0447\\u0451\\u0442\\u044b\", \"\\u0421\\u0435\\u0440\\u0432\\u0438\\u0441\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("tryClickAllWays(menuBtn);");
        w.writeLine("Thread.sleep(400);");
        w.writeLine("String pred = entityStemPredicate(entityName, \"text()\");");
        w.writeLine("String predDeep = entityStemPredicate(entityName, \"normalize-space(.)\");");
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(");
        w.writeLine("    \"//div[contains(@class,'x-menu')]//span[contains(@class,'x-menu-item-text')][\" + pred + \"]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-menu')]//a[contains(@class,'x-menu-item')][\" + predDeep + \"]\"));");
        w.openBlock("for (WebElement item : items)");
        w.openBlock("try");
        w.openBlock("if (!item.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(item).perform();");
        w.writeLine("Thread.sleep(250);");
        w.openBlock("for (String action : actionsToTry)");
        w.writeLine("WebElement actionBtn = findVisibleActionBtn(action);");
        w.openBlock("if (actionBtn != null)");
        w.writeLine("System.out.println(\"clickEntityMenuItem: clicking '\" + action + \"' under '\" + entityName + \"' (menu: \" + menuName + \")\");");
        w.writeLine("tryClickAllWays(actionBtn);");
        w.writeLine("Thread.sleep(1200);");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Throwable ignored)");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Throwable ignored2)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"clickEntityMenuItem: no action from \" + java.util.Arrays.toString(actionsToTry) + \" found under '\" + entityName + \"' in any subsystem menu\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // ====== Явные ожидания + замер времени шагов ======

        // waitUntil: опрашивает условие, пока оно не станет true или не истечёт таймаут. По таймауту
        // логирует и возвращает false (не бросает исключение), чтобы медленный рендер ExtJS не валил тест.
        w.openBlock("protected boolean waitUntil(java.util.function.Function<WebDriver, Boolean> cond, int seconds, String desc)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(100));");
        w.openBlock("try");
        w.writeLine("new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(cond);");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (org.openqa.selenium.TimeoutException e)");
        w.writeLine("System.out.println(\"waitUntil(\" + desc + \"): timeout after \" + seconds + \"s\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // ===== Справочник-в-дереве (новый тип): ПКМ по узлу-контейнеру → подменю сущности → «Добавить».
        // Для справочников вида «Справочник причин отмены» → «Причина отмены» → «Добавить» (модалка с «Готово»).
        w.openBlock("protected boolean addViaTreeContextMenu(String containerNode, String entitySubmenu)");
        w.openBlock("try");
        w.writeLine("WebElement node = findTreeNodeByText(containerNode);");
        w.openBlock("if (node == null)");
        w.writeLine("System.out.println(\"addViaTreeContextMenu: tree node not found: \" + containerNode);");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("new Actions(driver).contextClick(node).perform();");
        w.writeLine("Thread.sleep(700);");
        w.writeLine("WebElement sub = findVisibleMenuItem(entitySubmenu);");
        w.openBlock("if (sub == null)");
        w.writeLine("System.out.println(\"addViaTreeContextMenu: context submenu not found: \" + entitySubmenu);");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(sub).perform();");
        w.writeLine("Thread.sleep(600);");
        w.writeLine("WebElement add = findVisibleMenuItem(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\");");
        w.openBlock("if (add == null)");
        w.writeLine("System.out.println(\"addViaTreeContextMenu: 'Добавить' not found in submenu\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("tryClickAllWays(add);");
        w.writeLine("Thread.sleep(900);");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"addViaTreeContextMenu error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // findTreeNodeByText: первый видимый узел левого дерева, чей текст содержит подстроку.
        w.openBlock("protected WebElement findTreeNodeByText(String text)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> nodes = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tree-node-text')][contains(normalize-space(.),'\" + text + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.),'\" + text + \"')]]\"));");
        w.openBlock("for (WebElement n : nodes)");
        w.writeLine("try { if (n.isDisplayed()) return n; } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("return null;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // findVisibleMenuItem: первый видимый пункт ExtJS-меню (.x-menu-item) с заданной подписью.
        w.openBlock("protected WebElement findVisibleMenuItem(String text)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(");
        w.writeLine("    \"//*[contains(@class,'x-menu-item-text')][contains(normalize-space(.),'\" + text + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-menu-item')][contains(normalize-space(.),'\" + text + \"')]\"));");
        w.openBlock("for (WebElement it : items)");
        w.writeLine("try { if (it.isDisplayed()) return it; } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("return null;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // selectTreeRecordByPrefix: выбрать (клик) первую запись-узел дерева вида «<префикс> - …».
        w.openBlock("protected WebElement selectTreeRecordByPrefix(String prefix)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> nodes = driver.findElements(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tree-node-text')][contains(normalize-space(.),'\" + prefix + \" -')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.),'\" + prefix + \" -')]]\"));");
        w.openBlock("for (WebElement n : nodes)");
        w.openBlock("try");
        w.openBlock("if (n.isDisplayed())");
        w.writeLine("tryClickAllWays(n);");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("return n;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return null;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return null;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // selectTreeNodeByText: найти и кликнуть узел дерева, содержащий подстроку (выбор своей записи
        // по уникальному имени для update/delete в справочнике-в-дереве).
        w.openBlock("protected boolean selectTreeNodeByText(String text)");
        w.writeLine("WebElement n = findTreeNodeByText(text);");
        w.openBlock("if (n == null)");
        w.writeLine("System.out.println(\"selectTreeNodeByText: node not found: \" + text);");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("tryClickAllWays(n);");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine();

        // С кэшем на класс: если первый вызов waitForDialog истёк по таймауту, ставим
        // addDialogFailed=true, и последующие вызовы сразу возвращают false. Экономит ~8с на каждый
        // CRUD-тест после первого провала.
        w.openBlock("protected boolean waitForDialog()");
        w.openBlock("if (addDialogFailed)");
        w.writeLine("System.out.println(\"waitForDialog: cached miss — skipping 4s wait\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("boolean opened = waitUntil(d -> isDialogOpen(), 4, \"dialog open\");");
        w.openBlock("if (!opened)");
        w.writeLine("addDialogFailed = true;");
        w.closeBlock();
        w.writeLine("return opened;");
        w.closeBlock();
        w.writeLine();

        w.openBlock("protected boolean waitForDialogClose()");
        // 2с достаточно — реальное закрытие диалога ExtJS занимает <500мс, а большее ожидание
        // зря тратит время на каждый submit при отказе формы.
        w.writeLine("return waitUntil(d -> !isDialogOpen(), 2, \"dialog close\");");
        w.closeBlock();
        w.writeLine();

        // waitForAddForm: после Edit>Добавить форма создания записи может открыться двумя
        // способами — как модальное окно .x-window ИЛИ как навигированная карточка («Единый
        // объект»: форма встроена в страницу, отдельного окна нет). isDialogOpen() ловит только
        // первый случай, поэтому на «Едином объекте» testCreate ложно скипался. Считаем форму
        // открытой, если виден диалог ИЛИ кнопка сохранения формы «Готово».
        w.openBlock("protected boolean waitForAddForm()");
        w.writeLine("return waitUntil(d -> isDialogOpen() || isButtonVisible(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"), 6, \"add form\");");
        w.closeBlock();
        w.writeLine();

        // waitForGridSettle: ждёт, пока число видимых строк не стабилизируется на ~500мс.
        // Используется после Create/Delete/Archive/Search, чтобы грид отражал новое состояние.
        // В первые 3с не принимаем 0 строк — даём гриду фору на загрузку, прежде чем признать его
        // «стабильно пустым» (по-настоящему пустые результаты проходят быстро).
        w.openBlock("protected boolean waitForGridSettle()");
        w.writeLine("final long started = System.currentTimeMillis();");
        w.writeLine("final int[] prev = { Integer.MIN_VALUE };");
        w.writeLine("final long[] stableSince = { -1L };");
        w.writeLine("return waitUntil(d -> {");
        w.writeLine("    int n = getVisibleRowCount();");
        w.writeLine("    if (n == prev[0]) {");
        w.writeLine("        if (stableSince[0] < 0) stableSince[0] = System.currentTimeMillis();");
        w.writeLine("        boolean stableLongEnough = System.currentTimeMillis() - stableSince[0] >= 500;");
        w.writeLine("        if (!stableLongEnough) return false;");
        w.writeLine("        if (n == 0 && System.currentTimeMillis() - started < 3000) return false;");
        w.writeLine("        return true;");
        w.writeLine("    }");
        w.writeLine("    prev[0] = n;");
        w.writeLine("    stableSince[0] = System.currentTimeMillis();");
        w.writeLine("    return false;");
        w.writeLine("}, 10, \"grid settle\");");
        w.closeBlock();
        w.writeLine();

        // step: замеряет кусок работы теста и логирует «[step] name: NNNms». Парсер HTML-отчёта
        // вытаскивает эти строки, так что каждый тест получает мини-диаграмму распределения времени.
        w.openBlock("protected <T> T step(String name, java.util.function.Supplier<T> body)");
        w.writeLine("long t0 = System.currentTimeMillis();");
        w.openBlock("try");
        w.writeLine("return body.get();");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("long dt = System.currentTimeMillis() - t0;");
        w.writeLine("System.out.println(\"[step] \" + name + \": \" + dt + \"ms\");");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        w.openBlock("protected void step(String name, Runnable body)");
        w.writeLine("step(name, () -> { body.run(); return (Void) null; });");
        w.closeBlock();
        w.writeLine();

        // logSearchParams: печатает блок, который HTML/CSV-отчёт может извлечь, показывая, какие
        // именно значения ушли в форму. Без этого тест поиска — чёрный ящик: непонятно, какое
        // значение было передано.
        w.openBlock("protected void logSearchParams(String searchName, java.util.LinkedHashMap<String, String> params)");
        w.writeLine("System.out.println(\"=== Search params for '\" + searchName + \"' ===\");");
        w.openBlock("for (java.util.Map.Entry<String, String> e : params.entrySet())");
        w.writeLine("System.out.println(\"  \" + e.getKey() + \" = '\" + e.getValue() + \"'\");");
        w.closeBlock();
        w.writeLine("System.out.println(\"==========================================\");");
        w.closeBlock();

        w.closeBlock(); // end class

        w.writeToFile(dir, "BaseTest.java");
    }

    private void generateTestData(Path srcDir, String basePackage) throws IOException {
        Path dir = srcDir.resolve(basePackage.replace('.', '/'));
        JavaFileWriter w = new JavaFileWriter();

        w.writeLine("package " + basePackage + ";");
        w.writeLine();
        w.openBlock("public final class TestData");
        w.writeLine("public static final String BASE_URL = \"" + config.getBaseUrl() + "\";");
        w.writeLine("public static final String LOGIN = \"" + config.getLogin() + "\";");
        w.writeLine("public static final String PASSWORD = \"" + config.getPassword() + "\";");
        w.writeLine("public static final String SITE_TYPE = \"" + config.getSiteType() + "\";");
        w.writeLine("public static final String SUBSYSTEM_NAME = \"" + (config.getSubsystemName() != null ? config.getSubsystemName() : "") + "\";");
        w.writeLine("public static final String TEST_LEVEL = \"" + config.getTestLevel() + "\";");
        w.writeLine("public static final boolean SMOKE_ALL_SUBSYSTEMS = " + config.isSmokeAllSubsystems() + ";");
        w.writeLine();
        w.writeLine("private TestData() {}");
        w.closeBlock();

        w.writeToFile(dir, "TestData.java");
    }

}
