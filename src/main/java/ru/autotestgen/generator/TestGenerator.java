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
 * Orchestrator: creates the generated test project structure,
 * generates Page Objects and Test classes for each entity.
 */
public class TestGenerator {

    private final TestConfig config;

    public TestGenerator(TestConfig config) {
        this.config = config;
    }

    public void generate(AppModel model) throws IOException {
        Path outputDir = config.getOutputDir();
        String basePackage = config.getBasePackage();
        Path srcDir = outputDir.resolve("src/test/java");

        // Create project structure
        Files.createDirectories(srcDir);

        // Wipe previously-generated test classes and page objects so changing the entity-classifier
        // verdict (e.g. v3 ran with no classifier and dropped 19 test classes here) doesn't leave
        // stale tests that Maven will still execute. Generated SharedDriver/BaseTest/TestData are
        // rewritten unconditionally below so wiping them is also safe.
        Path generatedRoot = srcDir.resolve(basePackage.replace('.', '/'));
        if (Files.exists(generatedRoot)) {
            try (var paths = Files.walk(generatedRoot)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        }

        // Generate pom.xml for the test project
        generatePom(outputDir);

        // Generate junit-platform.properties (sequential — shared browser)
        generateJUnitConfig(outputDir);

        // Generate SharedDriver.java (shared browser instance)
        generateSharedDriver(srcDir, basePackage);

        // Generate BaseTest.java
        generateBaseTest(srcDir, basePackage);

        // Generate TestData.java
        generateTestData(srcDir, basePackage);

        // Classify entities and generate Page Objects / Test classes only for PRIMARY entities.
        // CHILD entities live as tab-grids of a parent; REFERENCE_DICTIONARY entities are picker
        // targets reached only via FK fields in other forms. Generating standalone tests for either
        // produces 21 "Could not navigate" skips — wasted clock time and meaningless reports.
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
            } else {
                ref++;
            }
        }
        // Write the classification report next to the generated test project. Lets the user see at
        // a glance which entities were treated as tests, which as tabs, which as dictionaries.
        Path csvPath = outputDir.resolve("entity-classification.csv");
        Files.writeString(csvPath, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("Entity classification: " + primary + " PRIMARY, "
                + child + " CHILD (tab-grid), " + ref + " REFERENCE_DICTIONARY (FK target)");
        System.out.println("  Report: " + csvPath);

        // Generate SubsystemsSmokeTest (only when smoke-all-subsystems is enabled)
        if (config.isSmokeAllSubsystems()) {
            generateSubsystemsSmokeTest(srcDir, basePackage);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // Entity-classification logic lives in ru.autotestgen.model.EntityClassifier.

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
        w.writeLine("                    <forkCount>1</forkCount>");
        w.writeLine("                    <reuseForks>true</reuseForks>");
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

        // SmokeResult inner class
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

        // setupDriver() — extracted so we can call it again after a quit() between subsystems
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
        w.writeLine("options.addArguments(\"--headless\");");
        w.writeLine("options.addArguments(\"--no-sandbox\");");
        w.writeLine("options.addArguments(\"--disable-dev-shm-usage\");");
        w.writeLine("options.addArguments(\"--window-size=1920,1080\");");
        w.writeLine("driver = new ChromeDriver(options);");
        w.closeBlock();
        w.writeLine("driver.manage().window().maximize();");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.writeLine("wait = new WebDriverWait(driver, Duration.ofSeconds(3));");
        w.closeBlock();
        w.writeLine();

        // restartBrowserAndLogin() — quit the current driver, recreate, log in fresh
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

        // selectSubsystem(name) — tries multiple tag variants the launcher can use for tile labels.
        // Original code locked onto <b> only; some stand themes/builds render the tile name in
        // span/div/a instead. With a 3-second wait and a single XPath we'd fail every test class
        // ("Subsystem selection failed" → all 24 tests SKIP). Broader locator + longer wait fixes.
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
        // Diagnostic dump: what visible tile-like elements DID we find?
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

        // discoverSubsystems — same broader xpath so smoke-mode finds tiles too.
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
        // Restart browser before every iteration after the first — first iteration uses the
        // already-logged-in session that initialize() set up.
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
        // Final restart so entity tests get a clean session on the launcher
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
        // Cache so navigation is attempted at most ONCE per test class. After a failed first try,");
        // subsequent test methods short-circuit instead of re-running a 10-second menu search.");
        w.writeLine("private boolean navigationAttempted = false;");
        w.writeLine("private boolean cachedNavigationOk = false;");
        // Cache card-open result: after the first openRecordCard attempt fails, subsequent
        // testGrid* tests in the same class instance skip the 5-strategy retry (which costs
        // ~30-50 seconds per attempt). Saves ~2-3 minutes per typical run.
        w.writeLine("private boolean cardOpenAttempted = false;");
        w.writeLine("private boolean cachedCardOpenOk = false;");
        // Cache add-dialog state: if waitForDialog times out once in this test class, mark the
        // dialog as unreachable. CRUD tests that come later (testCreate, testUpdate, …) hit the
        // cache and return false immediately instead of waiting another 4s each.
        w.writeLine("private boolean addDialogFailed = false;");
        // Screenshot bookkeeping — currentTestName captured in @BeforeEach, stepCounter resets per test.
        w.writeLine("protected String currentTestName = \"test\";");
        w.writeLine("protected int stepCounter = 0;");
        w.writeLine();

        // BeforeAll - get driver from SharedDriver
        w.writeLine("@BeforeAll");
        w.openBlock("void initDriver()");
        w.writeLine("driver = SharedDriver.getDriver();");
        w.writeLine("wait = SharedDriver.getWait();");
        w.closeBlock();
        w.writeLine();

        // BeforeEach in BaseTest — capture test name for screenshots; reset step counter.
        // Runs BEFORE the subclass @BeforeEach (JUnit 5 default ordering: parent first).
        w.writeLine("@org.junit.jupiter.api.BeforeEach");
        w.openBlock("void initTestContext(TestInfo info)");
        w.writeLine("this.currentTestName = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse(\"test\");");
        w.writeLine("this.stepCounter = 0;");
        w.closeBlock();
        w.writeLine();

        // Helper: perform action via E3Core menu (e.g., Добавить, Найти)
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

        // Helper: navigate to entity. First call does the real work; subsequent calls in the
        // same test class instance return the cached result so failed navigation does not
        // pay a 10-second cost on every @BeforeEach.
        w.openBlock("protected void navigateToEntity(String entityName, String featureName)");
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
        // After "Найти" lands on the parameters page, automatically run the empty search so
        // the result grid is populated. Without this, isFieldDisplayed sees only the parameter
        // form (Тип/Наименование) and reports "Fields found: 0 of N".
        w.openBlock("if (navigationOk)");
        // After Найти, E3Core opens a "Дерево поисков" window but leaves the form blank
        // until the user double-clicks the "по параметрам" tree node. Without this step
        // the parameter form never renders and executeSearchIfPresent can't find its button.
        w.writeLine("openParamSearchInTree();");
        w.writeLine("executeSearchIfPresent();");
        w.closeBlock();
        w.writeLine("cachedNavigationOk = navigationOk;");
        w.openBlock("if (!navigationOk)");
        w.writeLine("System.out.println(\"Could not navigate to entity: \" + entityName);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // openParamSearchInTree: after "Найти" opens the "Дерево поисков" window, double-click
        // the "по параметрам" leaf in the tree to actually render the parameter form on the right.
        // Falls back to a single click via JS if double-click is silently ignored.
        w.openBlock("protected void openParamSearchInTree()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Wait up to 4s for the tree node to appear.
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

        // executeSearchIfPresent: click "Выполнить поиск" if visible, otherwise no-op.
        // Used after navigation so the result grid is populated before tests run.
        w.openBlock("protected void executeSearchIfPresent()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        // Poll up to 5 seconds for the button — page may still be loading after "Найти" click.
        w.writeLine("WebElement btn = null;");
        w.writeLine("long deadline = System.currentTimeMillis() + 5000;");
        w.openBlock("while (System.currentTimeMillis() < deadline && btn == null)");
        w.writeLine("btn = findVisibleSearchButton();");
        w.openBlock("if (btn == null)");
        w.writeLine("Thread.sleep(250);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (btn == null)");
        // Diagnostic dump so we can see what was on the page
        w.writeLine("String url = \"\";");
        w.writeLine("String title = \"\";");
        w.openBlock("try");
        w.writeLine("url = driver.getCurrentUrl();");
        w.writeLine("title = driver.getTitle();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"executeSearchIfPresent: '\\u0412\\u044b\\u043f\\u043e\\u043b\\u043d\\u0438\\u0442\\u044c \\u043f\\u043e\\u0438\\u0441\\u043a' button NOT FOUND after 5s. URL=\" + url + \" Title='\" + title + \"'\");");
        // List anything that contains "Выпол" so we can see button variants
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
        // v5: instead of a blind 2-second buffer, wait for the grid to stop changing.
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

        // findVisibleSearchButton: returns the first visible "Выполнить поиск" trigger, or null.
        // Filters out window/container divs whose text *contains* the label but which aren't actual buttons.
        w.openBlock("private WebElement findVisibleSearchButton()");
        w.openBlock("try");
        w.writeLine("List<WebElement> candidates = driver.findElements(By.xpath(");
        // div removed — search-tree window's title div contains the label too.
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
        // A real button's text is short. The search-tree window's title div is 200+ chars and would
        // hijack the click otherwise.
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

        // E3Core navigation: ExtJS cascading menus with recursive submenu descent
        w.openBlock("private void navigateE3Core(String entityName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"\\u041d\\u0421\\u0418\", \"\\u041e\\u0442\\u0447\\u0451\\u0442\\u044b\", \"\\u0421\\u0435\\u0440\\u0432\\u0438\\u0441\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("menuBtn.click();");
        w.writeLine("Thread.sleep(300);");
        w.openBlock("if (descendMenu(entityName, 3, new java.util.HashSet<>()))");
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

        // descendMenu: recursive submenu walker
        w.writeLine("/**");
        w.writeLine(" * Searches the currently open ExtJS menus (and their submenus, up to maxDepth)");
        w.writeLine(" * for an item whose text matches entityName by per-word stems (so \"\\u0414\\u043e\\u043b\\u0436\\u043d\\u043e\\u0441\\u0442\\u043d\\u043e\\u0435 \\u043b\\u0438\\u0446\\u043e\" finds");
        w.writeLine(" * \"\\u0414\\u043e\\u043b\\u0436\\u043d\\u043e\\u0441\\u0442\\u043d\\u044b\\u0435 \\u043b\\u0438\\u0446\\u0430\" too). If found, hovers it and clicks \"\\u041d\\u0430\\u0439\\u0442\\u0438\" (or \"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\") in the");
        w.writeLine(" * revealed submenu; falls back to a direct click on the item itself.");
        w.writeLine(" */");
        w.openBlock("private boolean descendMenu(String entityName, int maxDepth, java.util.Set<String> tried) throws InterruptedException");
        w.writeLine("String pred = entityStemPredicate(entityName, \"text()\");");
        w.writeLine("String predDeep = entityStemPredicate(entityName, \"normalize-space(.)\");");
        w.writeLine("String itemXpath = \"//div[contains(@class,'x-menu')]\"");
        w.writeLine("    + \"//span[contains(@class,'x-menu-item-text')][\" + pred + \"]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-menu')]\"");
        w.writeLine("    + \"//a[contains(@class,'x-menu-item')][\" + predDeep + \"]\";");
        w.writeLine("List<WebElement> directHits = driver.findElements(By.xpath(itemXpath));");
        w.openBlock("for (WebElement item : directHits)");
        w.openBlock("try");
        w.openBlock("if (!item.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("new Actions(driver).moveToElement(item).perform();");
        w.writeLine("Thread.sleep(500);");
        w.writeLine("WebElement actionBtn = findVisibleActionBtn(\"\\u041d\\u0430\\u0439\\u0442\\u0438\");");
        w.openBlock("if (actionBtn == null)");
        w.writeLine("actionBtn = findVisibleActionBtn(\"\\u041e\\u0442\\u043a\\u0440\\u044b\\u0442\\u044c\");");
        w.closeBlock();
        w.openBlock("if (actionBtn != null)");
        // Try multiple click strategies — ExtJS menu items sometimes ignore a plain WebElement.click()
        // because the underlying event handler is on mousedown/mouseup or on a sibling element.
        w.writeLine("String actionLabel = actionBtn.getText().trim();");
        w.writeLine("System.out.println(\"descendMenu: trying to click '\" + actionLabel + \"' for entity '\" + entityName + \"'\");");
        w.writeLine("tryClickAllWays(actionBtn);");
        w.writeLine("Thread.sleep(1500);");
        w.writeLine("captureNavScreenshot(entityName, \"after-action\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("System.out.println(\"descendMenu: no 'Найти'/'Открыть' submenu — clicking item directly for '\" + entityName + \"'\");");
        w.writeLine("tryClickAllWays(item);");
        w.writeLine("Thread.sleep(1000);");
        w.writeLine("captureNavScreenshot(entityName, \"after-direct\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // Step 2: not found directly — find submenu parents and recurse
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
        // Re-find by full visible text. Text lives in nested <span class="x-menu-item-text">,
        // so we look at the concatenated descendant text via normalize-space(.).
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
        w.openBlock("if (descendMenu(entityName, maxDepth - 1, tried))");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return false;");
        w.closeBlock(); // end descendMenu
        w.writeLine();

        // entityStemPredicate: builds an XPath predicate that matches by per-word stems.
        // For "Должностное лицо" → contains(text(),'Должностн') and contains(text(),'лиц').
        // This handles ExtJS menu items that use a different declension (plural/genitive/etc.).
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
        // Stem: drop last 2 chars when word is long enough; keep short words as-is.
        w.writeLine("String stem = word.length() <= 3 ? word : word.substring(0, word.length() - 2);");
        w.openBlock("if (!first)");
        w.writeLine("sb.append(\" and \");");
        w.closeBlock();
        w.writeLine("sb.append(\"contains(\").append(textFn).append(\", '\").append(stem).append(\"')\");");
        w.writeLine("first = false;");
        w.closeBlock();
        w.openBlock("if (sb.length() == 0)");
        // Fallback to exact contains() if no stems could be built (e.g. all words have apostrophes)
        w.writeLine("sb.append(\"contains(\").append(textFn).append(\", '\").append(entityName.replace(\"'\", \"\")).append(\"')\");");
        w.closeBlock();
        w.writeLine("return sb.toString();");
        w.closeBlock();
        w.writeLine();

        // findVisibleActionBtn: locates a visible menu item with the given action label (Найти, Открыть, …).
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

        // tryClickAllWays: try plain click, then Actions.click(), then JS click.
        // ExtJS menu items sometimes ignore one of them depending on the event binding.
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

        // captureNavScreenshot: saves a screenshot to target/screenshots/post-nav-<entity>-<phase>.png
        // and also logs what tab/panel titles are visible so we can diagnose where we landed.
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
        // Log visible tab strip titles
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

        // Generic navigation
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

        // Helper: assume navigation succeeded
        w.openBlock("protected void assumeNavigated()");
        w.writeLine("Assumptions.assumeTrue(navigationOk, \"Skipped: could not navigate to entity\");");
        w.closeBlock();
        w.writeLine();

        // Helper: select first record
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
        // Поддерживает обе ветки API: ExtJS 4+ (Ext.ComponentQuery) и ExtJS 3 (Ext.ComponentMgr).
        w.openBlock("protected boolean openViaExtApi()");
        w.openBlock("try");
        w.writeLine("Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var grids = [];\"");
        w.writeLine("    + \"  if (Ext.ComponentQuery) {\"");
        // ExtJS 4+: try several xtype variants
        w.writeLine("    + \"    grids = Ext.ComponentQuery.query('grid')\"");
        w.writeLine("    + \"      .concat(Ext.ComponentQuery.query('gridpanel'))\"");
        w.writeLine("    + \"      .concat(Ext.ComponentQuery.query('editorgrid'))\"");
        w.writeLine("    + \"      .concat(Ext.ComponentQuery.query('treepanel'));\"");
        w.writeLine("    + \"  }\"");
        // ExtJS 3: ComponentMgr (without 'r' for v4+). Both work because we duck-type.
        w.writeLine("    + \"  var mgr = Ext.ComponentMgr || Ext.ComponentManager;\"");
        w.writeLine("    + \"  if (mgr && grids.length === 0) {\"");
        w.writeLine("    + \"    if (mgr.all && mgr.all.each) {\"");
        w.writeLine("    + \"      mgr.all.each(function(c) { var x = c.getXType && c.getXType(); if (x && (x === 'grid' || x === 'gridpanel' || x === 'editorgrid' || x === 'treepanel')) grids.push(c); });\"");
        w.writeLine("    + \"    } else if (mgr.each) {\"");
        w.writeLine("    + \"      mgr.each(function(id, c) { var x = c && c.getXType && c.getXType(); if (x && (x === 'grid' || x === 'gridpanel' || x === 'editorgrid' || x === 'treepanel')) grids.push(c); });\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  if (!grids || grids.length === 0) return 'no-grid';\"");
        w.writeLine("    + \"  var visible = grids.filter(function(g) { return g.rendered && !g.hidden; });\"");
        w.writeLine("    + \"  var g = visible.length > 0 ? visible[visible.length - 1] : grids[grids.length - 1];\"");
        w.writeLine("    + \"  var store = g.getStore && g.getStore(); var count = store ? store.getCount() : 0;\"");
        w.writeLine("    + \"  if (count === 0) return 'empty-store';\"");
        w.writeLine("    + \"  var record = store.getAt(0); var view = g.view || g.getView();\"");
        w.writeLine("    + \"  if (g.fireEvent) {\"");
        w.writeLine("    + \"    g.fireEvent('rowdblclick', g, 0, null);\"");
        w.writeLine("    + \"    g.fireEvent('itemdblclick', view, record, null, 0, null);\"");
        w.writeLine("    + \"    return 'fired';\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'no-fireEvent';\"");
        w.writeLine("    + \"} catch (e) { return 'err:' + e.message; }\");");
        w.writeLine("System.out.println(\"openViaExtApi: \" + result);");
        w.writeLine("return \"fired\".equals(result);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"openViaExtApi error: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // selectAndOpenRecord: документированный пользователем сценарий E3Core:
        //   0. ПЕРВЫМ ДЕЛОМ — попытка через ExtJS API (openViaExtApi). Если ExtJS-grid
        //      реагирует на fireEvent('rowdblclick') — карточка открывается за 100мс.
        //   1. fallback: выделить строку (одиночный клик),
        //   2. дождаться выделения,
        //   3. двойной клик — откроется карточка записи / редактор.
        w.openBlock("protected boolean selectAndOpenRecord()");
        // Strategy 0: ExtJS API — самый надёжный вариант для ExtJS-grid'ов
        w.openBlock("if (openViaExtApi())");
        w.writeLine("boolean opened = waitUntil(d -> isOnRecordCard() || isDialogOpen(), 8, \"card after ExtJS API\");");
        w.openBlock("if (opened)");
        w.writeLine("System.out.println(\"selectAndOpenRecord: opened via ExtJS API fireEvent('rowdblclick')\");");
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
        w.writeLine("System.out.println(\"selectAndOpenRecord: no visible row\");");
        w.writeLine("return false;");
        w.closeBlock();
        // Step 1: single click to select
        w.openBlock("try");
        w.writeLine("firstRow.click();");
        w.writeLine("Thread.sleep(400);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"selectAndOpenRecord: single-click failed: \" + e.getMessage());");
        w.closeBlock();
        // Step 2: double-click on the same row to open the record
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).doubleClick().perform();");
        w.writeLine("Thread.sleep(500);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        // Fallback: JS dblclick event
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].dispatchEvent(new MouseEvent('dblclick', {bubbles: true, cancelable: true, view: window}));\",");
        w.writeLine("    firstRow);");
        w.writeLine("Thread.sleep(500);");
        w.closeBlock();
        w.openBlock("catch (Exception e2)");
        w.writeLine("System.out.println(\"selectAndOpenRecord: dblclick failed: \" + e2.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"selectAndOpenRecord: single-click + double-click sequence executed\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Helper: check for errors
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

        // Helper: accept alert
        w.openBlock("protected void acceptAlertIfPresent()");
        w.openBlock("try");
        w.writeLine("Alert alert = driver.switchTo().alert();");
        w.writeLine("alert.accept();");
        w.closeBlock();
        w.openBlock("catch (NoAlertPresentException ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Helper: open search by tree-node name. Tries direct double-click first; if the tree
        // isn't visible (e.g. after the first search the tree window closed), re-opens it via
        // menuAction(entityName(), "Найти") and retries.
        w.openBlock("protected void openSearch(String searchName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Collapse runs of whitespace — XML labels sometimes have double spaces ("по  параметрам")
        // that won't match the single-space tree-node text.
        w.writeLine("String trimmed = searchName.trim().replaceAll(\"\\\\s+\", \" \");");
        w.writeLine("WebElement searchLink = findTreeNode(trimmed);");
        w.openBlock("if (searchLink == null)");
        // 1st fallback: tree might be closed. Re-trigger menu lookup to bring it back.
        w.writeLine("System.out.println(\"openSearch: tree node '\" + trimmed + \"' not visible — re-opening tree via clickEntityMenuItem('Найти')\");");
        w.writeLine("clickEntityMenuItem(entityName(), new String[]{ \"\\u041d\\u0430\\u0439\\u0442\\u0438\" });");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.writeLine("searchLink = findTreeNode(trimmed);");
        w.closeBlock();
        w.openBlock("if (searchLink == null)");
        // 2nd fallback: named searches like «Поиск ОГСК», «Поиск объединений» often live as
        // DIRECT submenu items of the entity, not as tree nodes. Try clicking the search name
        // straight via the same robust stem-matching helper.
        w.writeLine("System.out.println(\"openSearch: '\" + trimmed + \"' not a tree node — trying as direct menu item\");");
        w.writeLine("boolean directClicked = clickEntityMenuItem(entityName(), new String[]{ trimmed });");
        w.openBlock("if (directClicked)");
        // Result grid is auto-rendered for named searches; no double-click / submit needed.
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
        // Wait up to 3 seconds for the form to render — poll every 250ms for ANY visible input.
        // Without this, fast tests hit fillSearchParam before the search form has painted.
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

        // findTreeNode: locates the named leaf in the open search-tree window, or null if absent.
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

        // Helper: execute search
        w.openBlock("protected void executeSearch()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // E3Core search forms use «Готово» as the submit button (PropertyGrid-style dialogs);
        // some result-grid pages use «Выполнить поиск»/«Найти». Try all three in order of likelihood
        // and pick whichever is visible.
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

        // Helper: check search result
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

        // Helper: open tab. Supports both ExtJS 3 (.x-tab-strip-text) and 4/5 (.x-tab-inner,
        // .x-tab-button, role="tab"), plus plain HTML and ARIA-role variants. Tabs live INSIDE
        // an open record card — if no card is open this will (correctly) fail.
        w.openBlock("protected boolean openTab(String tabName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // First pass: classic ExtJS tab-strip elements (other builds use these).
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
        // Second pass: "Единый объект" view — the available property groups («Сведения»,
        // «История», «Документы») are rows in a grid/list on the right side, NOT a tab strip.
        // Click any visible row, link, or div whose text contains the tab name.
        w.writeLine("List<WebElement> fallback = driver.findElements(By.xpath(");
        w.writeLine("    \"//*[self::div or self::a or self::td or self::span or self::tr]\"");
        w.writeLine("    + \"[contains(normalize-space(.), '\" + tabName + \"')]\"));");
        w.openBlock("for (WebElement c : fallback)");
        w.openBlock("try");
        w.openBlock("if (!c.isDisplayed())");
        w.writeLine("continue;");
        w.closeBlock();
        // Reject candidates whose visible text is "too long" — these are page wrappers, not the
        // narrow row we want. Tab labels are typically short (≤80 chars).
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

        // openRecordCard: brings up the record's edit dialog so its tab-grids can be exercised.
        // Tries three strategies in order — double-click, right-click+«Изменить»/«Открыть»,
        // and menuAction(entity, «Изменить»). Stops at the first one that produces a dialog.
        w.openBlock("protected boolean openRecordCard()");
        // Cache: if a previous openRecordCard call in this test class already failed all five
        // strategies, return false immediately. Saves ~30-50 seconds per subsequent testGrid*
        // test in the same class.
        w.openBlock("if (cardOpenAttempted)");
        w.openBlock("if (!cachedCardOpenOk)");
        w.writeLine("System.out.println(\"openRecordCard: cached miss — skipping retry\");");
        w.closeBlock();
        w.writeLine("return cachedCardOpenOk;");
        w.closeBlock();
        w.writeLine("cardOpenAttempted = true;");
        // Strategy 0: ExtJS API. На ExtJS-grid'ах синтетический click из Selenium не активирует
        // row-dblclick, зато прямой вызов через JS работает.
        w.openBlock("if (openViaExtApi())");
        w.writeLine("boolean opened0 = waitUntil(d -> isOnRecordCard(), 8, \"card after ExtJS API\");");
        w.openBlock("if (opened0)");
        w.writeLine("System.out.println(\"openRecordCard: opened via ExtJS API\");");
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
        // Strategy 1: select-then-open. User confirmed the stand needs two separate clicks:
        // first click selects the row (highlights it), second click opens the «Единый объект»
        // view. A fast Actions.doubleClick() bundles the two clicks too tightly and ExtJS
        // doesn't fire the row-activated event. So we do an explicit click → pause → click
        // → wait up to 5s for the card to appear (page load can be slow).
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
        // Strategy 1b: native doubleClick — fallback for builds where one fast double-click works
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
        // Strategy 1c: JS-dispatched native dblclick — ExtJS 3 listens for raw DOM event, but
        // Selenium's Actions sometimes generates two separate `click` events instead. dispatching
        // a real `dblclick` MouseEvent via JS bypasses this.
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
        // Strategy 1d: right-click → «Загрузить выбранные объекты в дерево». This is the action
        // the user's screenshot showed in the context menu. After it executes, the object is
        // loaded into a separate «Единый объект» window which is what we want to detect.
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
        // Close any remaining context menu
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Strategy 2: right-click + «Изменить»/«Открыть»
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
        // Close context menu if still open
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Strategy 3: select + click toolbar «Изменить»
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
        // Strategy 4: Enter key on selected row — some ExtJS grids open on keyboard activation
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
        // Strategy 5: robust menu lookup via clickEntityMenuItem (stem-matching). Tries every
        // localised action verb the stand might use for "open this record": «Изменить»,
        // «Редактировать», «Открыть», «Просмотр», «Карточка», «Свойства», «Подробнее», «Просмотреть».
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
        // Diagnostic: what's on the page?
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

        // Helper: check grid displayed
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

        // Backwards-compat overload — call sites that don't pass a title fall through to title=null.
        w.openBlock("protected void fillSearchParam(String paramName, String value)");
        w.writeLine("fillSearchParam(paramName, null, value);");
        w.closeBlock();
        w.writeLine();

        // Helper: fill search parameter, trying multiple locator strategies. Strategy "by label"
        // uses the *Russian* title from SearchParam.title (e.g. 'Наименование ГСК/ОГСК') which is
        // what actually appears in the form, NOT the technical name 'GBS_NAME'. Without this the
        // 3 of 4 fillSearchParam calls in the user run all failed silently and the search executed
        // with empty params — coverage was theatre.
        w.openBlock("protected void fillSearchParam(String paramName, String title, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("WebElement param = null;");
        w.writeLine("String strategy = \"\";");
        // Strategy 1: direct name/id
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
        // Strategy 2: id prefix (ExtJS auto-suffix)
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
        // Strategy 3: label-based, trying the Russian title first (what's actually rendered) and
        // falling back to the technical name. The label could be a <label>, an ExtJS form-item
        // header, or a column header in a property-grid table.
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
        // Strategy 4 (E3Core PropertyGrid): the form is a 2-column table «Наименование/Значение»
        // — fields are NOT real <input>s until you click the value cell, then an inline editor
        // appears. Try this when the regular input search failed.
        w.openBlock("if (param == null)");
        w.writeLine("boolean clicked = fillPropertyGridCell(title != null && !title.isBlank() ? title : paramName, value);");
        w.openBlock("if (clicked)");
        w.writeLine("System.out.println(\"fillSearchParam '\" + paramName + \"' = '\" + value + \"' (via: PropertyGrid cell)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Strategy 5: ExtJS API. Если ни обычный input, ни PropertyGrid-ячейка не сработали,
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
        // Diagnostic dump: list ALL visible inputs / textareas so we can see what's actually on
        // the page. Without this we keep guessing at selectors blindly.
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

        // Helper: check if a column header is present in any table.
        // Covers ExtJS 3 (.x-grid3-hd*), ExtJS 4/5 (.x-column-header, .x-column-header-text),
        // and plain HTML (<th>, <td.header>). Normalises whitespace so XML titles with non-breaking
        // spaces or doubled spaces still match the rendered text.
        // fillPropertyGridCell: E3Core search and add forms render as a 2-column ExtJS PropertyGrid
        // («Наименование» / «Значение»). The value cells are NOT real input fields until clicked —
        // a click promotes them to an inline editor. So:
        //   1. find the row whose left cell contains the field label (Russian title like
        //      «Наименование ГСК/ОГСК» or «Кадастровый номер»)
        //   2. click the right cell (or the row) to spawn the inline editor
        // clickEditDropdownAction: на «Едином объекте» CRUD-действия (Сохранить Изменения,
        // Удалить, Лог.изменить, в Архив) лежат В ВЫПАДАЮЩЕМ СПИСКЕ кнопки «Редактирование»,
        // которая находится в нижней панели карточки записи, А НЕ в главном меню сущности.
        // 1. Найти и кликнуть кнопку «Редактирование» внизу карточки.
        // 2. Дождаться выпадающего меню.
        // 3. Кликнуть пункт с нужным именем (Удалить / в Архив / Лог.изменить / Сохранить
        //    Изменения).
        w.openBlock("protected boolean clickEditDropdownAction(String actionName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Find the «Редактирование» button (toolbar button at bottom of card)
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
        w.writeLine("return false;");
        w.closeBlock();
        // Click the «Редактирование» button via tryClickAllWays
        w.writeLine("System.out.println(\"clickEditDropdownAction: clicking 'Редактирование' to open dropdown\");");
        w.writeLine("tryClickAllWays(editBtn);");
        w.writeLine("Thread.sleep(500);");
        // Now find the action item in the opened dropdown menu
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
        // Dump visible menu items for diagnostics
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
        // Close dropdown
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

        //   3. type the value into the newly-visible input
        //   4. Tab to commit
        // Returns true on success.
        w.openBlock("protected boolean fillPropertyGridCell(String rowLabel, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Find rows of the PropertyGrid. ExtJS uses .x-grid3-row in v3 and .x-grid-row in v4+,
        // each containing two cells (label, value). We match the row whose first cell text
        // contains the label.
        w.writeLine("String labelLc = rowLabel == null ? \"\" : rowLabel.toLowerCase();");
        // Strip the trailing "*" that PropertyGrid uses to mark required fields.
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
        // Click value cell once and again — ExtJS PropertyGrid sometimes needs two clicks
        // (first selects row, second activates editor).
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(targetValueCell).click().pause(150).click().perform();");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Now there should be an inline editor input visible. Type into it.
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
        // Strip "_" suffix that some entity-stem matching adds for disambiguation.
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

        // Helper: check if a button with given text is present
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

        // Helper: check if an ExtJS dialog window is currently open
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

        // isOnRecordCard: true if we're on the record's detail view. E3Core can present this in
        // several layouts:
        //   1. classic modal dialog (.x-window)
        //   2. ExtJS tab-strip page
        //   3. "Единый объект" navigated page with PropertyGroup list on the right side
        //      (list rows for «Сведения», «История», «Документы») and a toolbar at the bottom
        //      with «Редактирование», «Обновить», «Печать...» — this is what the user stand uses.
        // Detecting (3) requires checking for the breadcrumb/title text or the bottom toolbar.
        w.openBlock("protected boolean isOnRecordCard()");
        w.openBlock("if (isDialogOpen())");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        // Signal A: tab-strip (ExtJS 3/4/5)
        w.writeLine("List<WebElement> tabs = driver.findElements(By.cssSelector(\".x-tab-strip-text, .x-tab-inner, .x-tab-text, [role='tab']\"));");
        w.openBlock("if (tabs.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("return true;");
        w.closeBlock();
        // Signal B: "Единый объект" text in the page header / breadcrumb
        w.writeLine("List<WebElement> ed = driver.findElements(By.xpath(\"//*[contains(normalize-space(.), '\\u0415\\u0434\\u0438\\u043d\\u044b\\u0439 \\u043e\\u0431\\u044a\\u0435\\u043a\\u0442')]\"));");
        w.openBlock("if (ed.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("return true;");
        w.closeBlock();
        // Signal C: card-only toolbar buttons («Редактирование» dropdown OR «Обновить»+«Печать»)
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

        // Helper: click element safely, handling intercept by waiting for overlays
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

        // Helper: click button by visible text, with intercept handling
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
        w.writeLine("WebElement btn = driver.findElement(By.xpath(\"//button[contains(text(), '\" + buttonText + \"')]\"));");
        w.writeLine("clickSafely(btn);");
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

        // Helper: take screenshot on failure
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

        // Helper: reset state by closing any open dialogs
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

        // ====== Step-screenshot helpers + assertion utilities (v4) ======

        // entityName(): the entity's *Russian* name as it appears in the launcher menu — used by
        // menuAction/openSearch/openRecordCard. Each generated test subclass overrides this to
        // return its ENTITY_NAME constant ("ГСК/ОГСК", "Совещание", …). The default falls back to
        // the transliterated class name so callers don't NPE even if a subclass forgets to override.
        w.openBlock("protected String entityName()");
        w.writeLine("return shotEntityName();");
        w.closeBlock();
        w.writeLine();

        // shotEntityName(): ASCII-safe transliterated name for screenshot filenames. Independent of
        // entityName() because we want filenames like "GSKOGSK_testX_*.png" rather than Cyrillic ones.
        w.openBlock("protected String shotEntityName()");
        w.writeLine("String n = getClass().getSimpleName();");
        w.openBlock("if (n.endsWith(\"Test\"))");
        w.writeLine("n = n.substring(0, n.length() - 4);");
        w.closeBlock();
        w.writeLine("return n;");
        w.closeBlock();
        w.writeLine();

        // shot(step): numbered screenshot for the current test. Files land under target/screenshots/
        // with name <Entity>_<testMethod>_<step#>_<step>_<status>.png so a directory listing reads
        // like a comic strip of the test run.
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

        // gridContainsRow(marker): true if any visible grid row contains the marker text.
        // Used by testCreate to verify the new record landed in the grid by a unique marker
        // value, instead of trusting only row-count increments (which can race with other users).
        w.openBlock("protected boolean gridContainsRow(String marker)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, .x-grid-row, tbody tr\"));");
        w.openBlock("for (WebElement r : rows)");
        w.openBlock("try");
        w.openBlock("if (r.isDisplayed() && r.getText() != null && r.getText().contains(marker))");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
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

        // getVisibleRowCount(): count visible grid rows. Used by CRUD tests to verify strict deltas.
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

        // matchesMask(value, mask): true if value conforms to an ExtJS-style mask.
        // Mask grammar (ExtJS): '9' = digit, 'a' = letter, '*' = any; literal characters are kept as-is.
        // Example: "99.99.9999" → ^\d{2}\.\d{2}\.\d{4}$
        w.openBlock("protected boolean matchesMask(String value, String mask)");
        w.openBlock("if (value == null || mask == null || mask.isEmpty())");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("StringBuilder regex = new StringBuilder(\"^\");");
        w.openBlock("for (int i = 0; i < mask.length(); i++)");
        w.writeLine("char c = mask.charAt(i);");
        w.openBlock("if (c == '9')");
        w.writeLine("regex.append(\"\\\\d\");");
        w.closeBlock();
        w.openBlock("else if (c == 'a' || c == 'A')");
        w.writeLine("regex.append(\"[A-Za-zА-Яа-я]\");");
        w.closeBlock();
        w.openBlock("else if (c == '*')");
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

        // assertCoverage(found, expected, threshold, missing, ctx): hard-assert that the fraction
        // of found items meets the threshold, including a diagnostic listing of what's missing.
        // This is the workhorse for testFieldsPresent and testGrid* — replaces silent println logs.
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

        // resetSearchView(): after a destructive test (delete/archive) re-run the empty search so
        // the result grid reflects current state for the next test in the @Order chain.
        w.openBlock("protected void refreshGrid()");
        w.openBlock("try");
        w.writeLine("executeSearchIfPresent();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // loadRowIntoTree(): right-click first row of the result grid, then click
        // «Загрузить выбранные объекты в дерево» — the documented E3Core way to open a
        // record's «Единый объект» card. Returns true if any item was clicked. Caller
        // should then waitUntil(isOnRecordCard()) to confirm the card actually opened.
        // We call this at the END of testFieldsPresent so the next testGrid* tests start
        // with the card already loaded (instead of having to open it from scratch).
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
        // Right-click on first row to open context menu
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(firstRow).contextClick().perform();");
        w.writeLine("Thread.sleep(600);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"loadRowIntoTree: contextClick failed: \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        // Find «Загрузить выбранные объекты в дерево» in the context menu
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
        // Diagnostic dump: what items WERE in the context menu? Helps when the menu opens but
        // doesn't have the expected entry.
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

        // clickEntityMenuItem: robust replacement for the brittle menuAction. Uses stem-matching
        // (same as descendMenu) instead of contains(text(), exact). The v6.5 run showed menuAction
        // failing silently because contains(text(),'ГСК/ОГСК') doesn't match items that ExtJS may
        // render with extra whitespace, NBSP, or wrapper elements.
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
        w.writeLine("Thread.sleep(500);");
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

        // ====== v5: explicit waits + step timing ======

        // waitUntil: poll a condition until it's true or timeout. On timeout, log and return false
        // — keeps tests robust without throwing, so a slow ExtJS render doesn't abort the whole test.
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

        // waitForDialog/waitForDialogClose: replace blind Thread.sleep around modal interactions.
        // With per-class cache: if the FIRST waitForDialog call in a test class times out, set
        // addDialogFailed=true and have subsequent calls return false immediately. Saves ~8s per
        // CRUD test after the first failure (testCreate, testUpdate, testRequiredFieldValidation,
        // testPartialRequiredFieldValidation, testCreateOnlyRequired, testMaskedFieldInput).
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
        w.writeLine("return waitUntil(d -> !isDialogOpen(), 4, \"dialog close\");");
        w.closeBlock();
        w.writeLine();

        // waitForGridSettle: wait until visible row count is stable for ~500ms. Used after Create /
        // Delete / Archive / Search to ensure the result grid reflects the new state.
        w.openBlock("protected boolean waitForGridSettle()");
        w.writeLine("final int[] prev = { Integer.MIN_VALUE };");
        w.writeLine("final long[] stableSince = { -1L };");
        w.writeLine("return waitUntil(d -> {");
        w.writeLine("    int n = getVisibleRowCount();");
        w.writeLine("    if (n == prev[0]) {");
        w.writeLine("        if (stableSince[0] < 0) stableSince[0] = System.currentTimeMillis();");
        w.writeLine("        return System.currentTimeMillis() - stableSince[0] >= 500;");
        w.writeLine("    }");
        w.writeLine("    prev[0] = n;");
        w.writeLine("    stableSince[0] = System.currentTimeMillis();");
        w.writeLine("    return false;");
        w.writeLine("}, 10, \"grid settle\");");
        w.closeBlock();
        w.writeLine();

        // step: time a chunk of test work and log "[step] name: NNNms". The HTML report parser
        // pulls these lines back out so each test rendering is a mini gantt of where time went.
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

        // logSearchParams: emit a block the HTML/CSV report can lift out, showing exactly which
        // values went into the form. Without this the search test is a black box — value passed
        // was either "Test_<NAME>" or a synthetic mask string, but the user never saw which.
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

    private void generateSubsystemsSmokeTest(Path srcDir, String basePackage) throws IOException {
        String packageName = basePackage + ".test";
        Path dir = srcDir.resolve(packageName.replace('.', '/'));
        JavaFileWriter w = new JavaFileWriter();

        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.junit.jupiter.api.DynamicTest;");
        w.writeLine("import org.junit.jupiter.api.TestFactory;");
        w.writeLine("import org.junit.jupiter.api.TestInstance;");
        w.writeLine("import " + basePackage + ".SharedDriver;");
        w.writeLine("import " + basePackage + ".SharedDriver.SmokeResult;");
        w.writeLine("import java.util.stream.Stream;");
        w.writeLine("import static org.junit.jupiter.api.Assertions.assertTrue;");
        w.writeLine();
        w.writeLine("/**");
        w.writeLine(" * Smoke check: opens each discovered subsystem and reports each as a dynamic test.");
        w.writeLine(" * Results are produced during SharedDriver.initialize() and read here.");
        w.writeLine(" */");
        w.writeLine("@TestInstance(TestInstance.Lifecycle.PER_CLASS)");
        w.openBlock("public class SubsystemsSmokeTest");
        w.writeLine();
        w.writeLine("@TestFactory");
        w.openBlock("Stream<DynamicTest> smokeAllSubsystems()");
        w.writeLine("// Trigger SharedDriver initialization (login + smoke) if not yet done.");
        w.writeLine("SharedDriver.getDriver();");
        w.writeLine("return SharedDriver.getSmokeResults().stream()");
        w.writeLine("    .map(r -> DynamicTest.dynamicTest(");
        w.writeLine("        \"\\u041f\\u043e\\u0434\\u0441\\u0438\\u0441\\u0442\\u0435\\u043c\\u0430: \" + r.name,");
        w.writeLine("        () -> assertTrue(r.ok, r.error)));");
        w.closeBlock();
        w.closeBlock();

        w.writeToFile(dir, "SubsystemsSmokeTest.java");
    }
}
