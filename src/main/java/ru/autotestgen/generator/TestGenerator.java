package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.model.AppModel;
import ru.autotestgen.model.EntityObject;

import java.io.IOException;
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

        // Generate Page Objects and Test classes for each entity
        PageObjectWriter pageWriter = new PageObjectWriter(basePackage);
        TestClassWriter testWriter = new TestClassWriter(basePackage, config.getTestLevel());

        for (EntityObject entity : model.getEntities()) {
            pageWriter.write(entity, srcDir);
            String disabledReason = computeDisabledReason(entity, model);
            testWriter.write(entity, model, srcDir, disabledReason);
        }

        // Generate SubsystemsSmokeTest (only when smoke-all-subsystems is enabled)
        if (config.isSmokeAllSubsystems()) {
            generateSubsystemsSmokeTest(srcDir, basePackage);
        }
    }

    /**
     * Returns a non-null reason when an entity should be @Disabled — typically because it is a
     * read-only tab/grid inside another entity's card, not a standalone menu page.
     * Heuristic: entity has NO CRUD operations AND its name stem-matches the name of a
     * Properties[stereoType="Grid"] group inside another entity. Entities with their own CRUD
     * stay active — they usually have a menu entry even if they also appear as a grid in a parent.
     */
    private String computeDisabledReason(EntityObject entity, AppModel model) {
        if (entity.hasCrudOperations()) return null;
        for (EntityObject other : model.getEntities()) {
            if (other.getGuid() != null && other.getGuid().equals(entity.getGuid())) continue;
            for (var pg : other.getPropertyGroups()) {
                if (!"Grid".equals(pg.getStereoType())) continue;
                String gridName = pg.getName();
                if (gridName == null || gridName.isEmpty()) continue;
                // Skip self-collection grids: a grid named after its own host entity is the
                // collection view of itself, not a tab in a different parent. Without this,
                // singular dictionary entities (e.g. "Причина смены...") get mistakenly
                // disabled because their plural counterpart hosts an identically-named grid.
                if (gridName.equalsIgnoreCase(other.getName())) continue;
                if (nameStemsMatch(entity.getName(), gridName)) {
                    return "Tab/grid '" + gridName + "' inside parent '" + other.getName()
                        + "' — not standalone";
                }
            }
        }
        return null;
    }

    /** True when two entity names match per-word by shared stems (handles singular/plural). */
    private static boolean nameStemsMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String[] aw = a.split("[\\s/]+");
        String[] bw = b.split("[\\s/]+");
        if (aw.length != bw.length) return false;
        for (int i = 0; i < aw.length; i++) {
            String wa = aw[i].toLowerCase();
            String wb = bw[i].toLowerCase();
            if (wa.isEmpty() || wb.isEmpty()) return false;
            int needed = Math.min(Math.min(wa.length(), wb.length()) - 2, 6);
            if (needed < 2) {
                if (!wa.equals(wb)) return false;
            } else {
                if (!wa.startsWith(wb.substring(0, needed)) && !wb.startsWith(wa.substring(0, needed))) {
                    return false;
                }
            }
        }
        return true;
    }

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

        // discoverSubsystems()
        w.writeLine("/** Returns display names of all visible subsystem tiles on the launcher screen. */");
        w.openBlock("private static List<String> discoverSubsystems()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.openBlock("try");
        w.writeLine("List<WebElement> tiles = driver.findElements(By.xpath(\"//b\"));");
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

        // selectSubsystem(name)
        w.writeLine("/** Double-clicks the tile of the given subsystem and verifies the launcher was left. */");
        w.openBlock("private static boolean selectSubsystem(String name)");
        w.openBlock("try");
        w.writeLine("WebElement tile = wait.until(ExpectedConditions.elementToBeClickable(");
        w.writeLine("    By.xpath(\"//b[contains(text(), '\" + name + \"')]\")));");
        w.writeLine("new Actions(driver).doubleClick(tile).perform();");
        w.writeLine("Thread.sleep(2000);");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));");
        w.openBlock("try");
        // Check 1: menu button with this name appeared (most reliable when names match)
        w.writeLine("List<WebElement> menuBtns = driver.findElements(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + name + \"')]\"));");
        w.openBlock("if (menuBtns.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("System.out.println(\"Subsystem '\" + name + \"' selected (menu button)\");");
        w.writeLine("return true;");
        w.closeBlock();
        // Check 2: any subsystem tab is visible
        w.writeLine("List<WebElement> tabs = driver.findElements(By.cssSelector(\".x-tab-strip-text, .x-tab-strip-active\"));");
        w.openBlock("if (tabs.stream().anyMatch(WebElement::isDisplayed))");
        w.writeLine("System.out.println(\"Subsystem '\" + name + \"' selected (tab strip visible)\");");
        w.writeLine("return true;");
        w.closeBlock();
        // Check 3: launcher header is gone
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
        w.writeLine("System.out.println(\"Subsystem selection failed for '\" + name + \"': \" + e.getMessage());");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock(); // end selectSubsystem
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
        w.writeLine();

        // BeforeAll - get driver from SharedDriver
        w.writeLine("@BeforeAll");
        w.openBlock("void initDriver()");
        w.writeLine("driver = SharedDriver.getDriver();");
        w.writeLine("wait = SharedDriver.getWait();");
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
        w.writeLine("Thread.sleep(2000);");
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
        w.openBlock("private String entityStemPredicate(String entityName, String textFn)");
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
        w.openBlock("private WebElement findVisibleActionBtn(String actionName)");
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
        w.openBlock("private void tryClickAllWays(WebElement el)");
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

        // Helper: open search by tree-node name (uses double-click — ExtJS tree leaves only render on dblclick).
        w.openBlock("protected void openSearch(String searchName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        // Collapse runs of whitespace — XML labels sometimes have double spaces ("по  параметрам")
        // that won't match the single-space tree-node text.
        w.writeLine("String trimmed = searchName.trim().replaceAll(\"\\\\s+\", \" \");");
        w.writeLine("WebElement searchLink = driver.findElement(By.xpath(");
        w.writeLine("    \"//span[contains(@class, 'x-tree-node-text')][contains(normalize-space(.), '\" + trimmed + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'x-tree-node-anchor')][.//span[contains(normalize-space(.), '\" + trimmed + \"')]]\"));");
        w.openBlock("try");
        w.writeLine("new Actions(driver).moveToElement(searchLink).doubleClick().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception eDbl)");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].click(); arguments[0].click();\", searchLink);");
        w.closeBlock();
        w.writeLine("Thread.sleep(1200);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not open search: \" + searchName);");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Helper: execute search
        w.openBlock("protected void executeSearch()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement searchBtn = driver.findElement(By.xpath(");
        w.writeLine("    \"//button[contains(text(), '\\u041d\\u0430\\u0439\\u0442\\u0438')]\"");
        w.writeLine("    + \" | //input[@value='\\u041d\\u0430\\u0439\\u0442\\u0438']\"");
        w.writeLine("    + \" | //button[contains(@class, 'x-btn-text')][contains(text(), '\\u041d\\u0430\\u0439\\u0442\\u0438')]\"");
        w.writeLine("    + \" | //button[.//span[contains(text(), '\\u041d\\u0430\\u0439\\u0442\\u0438')]]\"");
        w.writeLine("    + \" | //button[contains(@class, 'search-btn')]\"));");
        w.writeLine("searchBtn.click();");
        w.writeLine("Thread.sleep(500);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not execute search\");");
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

        // Helper: open tab
        w.openBlock("protected void openTab(String tabName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement tab = driver.findElement(By.xpath(");
        w.writeLine("    \"//span[contains(@class,'x-tab-strip-text')][contains(text(),'\" + tabName + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'tab')][contains(text(),'\" + tabName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class,'tab')][contains(text(),'\" + tabName + \"')]\"");
        w.writeLine("    + \" | //li[contains(text(),'\" + tabName + \"')]\"));");
        w.writeLine("tab.click();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not open tab: \" + tabName);");
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

        // Helper: fill search parameter by name
        w.openBlock("protected void fillSearchParam(String paramName, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement param = driver.findElement(By.cssSelector(\"[name='\" + paramName + \"'], [id='\" + paramName + \"']\"));");
        w.writeLine("param.clear();");
        w.writeLine("param.sendKeys(value);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Could not fill search param: \" + paramName);");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Helper: check if a column header is present in any table
        w.openBlock("protected boolean isColumnPresent(String columnTitle)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("List<WebElement> headers = driver.findElements(By.xpath(");
        w.writeLine("    \"//th[contains(text(), '\" + columnTitle + \"')]\"");
        w.writeLine("    + \" | //td[contains(@class,'header')][contains(text(), '\" + columnTitle + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid3-hd-inner')][contains(text(), '\" + columnTitle + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid3-hd')][contains(text(), '\" + columnTitle + \"')]\"));");
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
