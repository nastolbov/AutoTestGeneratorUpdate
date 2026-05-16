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
            testWriter.write(entity, model, srcDir);
        }
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
        w.writeLine("                    <forkCount>4</forkCount>");
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
        w.writeLine();
        w.writeLine("/**");
        w.writeLine(" * Shared browser instance for all test classes.");
        w.writeLine(" * Login and subsystem selection happen once; the driver is reused across all tests.");
        w.writeLine(" */");
        w.openBlock("public class SharedDriver");
        w.writeLine();
        w.writeLine("private static WebDriver driver;");
        w.writeLine("private static WebDriverWait wait;");
        w.writeLine("private static boolean initialized = false;");
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

        // initialize()
        w.openBlock("private static void initialize()");

        // Browser setup
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
        w.writeLine();

        // Login
        w.writeLine("// Login");
        w.writeLine("driver.get(TestData.BASE_URL);");
        w.openBlock("if (!TestData.LOGIN.isEmpty())");
        w.openBlock("try");
        w.writeLine("WebElement loginField = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(\"input[name='login'], input[name='username'], input[type='text']\")));");
        w.writeLine("loginField.clear();");
        w.writeLine("loginField.sendKeys(TestData.LOGIN);");
        w.writeLine("WebElement passField = driver.findElement(By.cssSelector(\"input[name='password'], input[type='password']\"));");
        w.writeLine("passField.clear();");
        w.writeLine("passField.sendKeys(TestData.PASSWORD);");
        w.writeLine("// Try multiple selectors for login button");
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
        w.writeLine("// Fallback: find any button or input[type=button] on the page");
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
        w.closeBlock();
        w.writeLine();

        // Subsystem selection
        w.writeLine("// Select subsystem after login (E3Core-specific)");
        w.writeLine("String subsystemName = TestData.SUBSYSTEM_NAME;");
        w.openBlock("if (subsystemName != null && !subsystemName.isEmpty())");
        w.openBlock("try");
        w.writeLine("WebElement subsystem = wait.until(ExpectedConditions.elementToBeClickable(");
        w.writeLine("    By.xpath(\"//b[contains(text(), '\" + subsystemName + \"')]\")));");
        w.writeLine("new Actions(driver).doubleClick(subsystem).perform();");
        w.writeLine("System.out.println(\"Subsystem '\" + subsystemName + \"' selected\");");
        w.writeLine("Thread.sleep(2000);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"Subsystem selection failed: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        w.writeLine("initialized = true;");
        w.writeLine("Runtime.getRuntime().addShutdownHook(new Thread(() -> {");
        w.writeLine("    if (driver != null) driver.quit();");
        w.writeLine("}));");

        w.closeBlock(); // end initialize()

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

        // Helper: navigate to entity
        w.openBlock("protected void navigateToEntity(String entityName, String featureName)");
        w.writeLine("navigationOk = false;");
        w.openBlock("if (\"e3core\".equals(TestData.SITE_TYPE))");
        w.writeLine("navigateE3Core(entityName);");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("navigateGeneric(entityName);");
        w.closeBlock();
        w.openBlock("if (!navigationOk)");
        w.writeLine("System.out.println(\"Could not navigate to entity: \" + entityName);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // E3Core navigation: ExtJS cascading menus
        w.openBlock("private void navigateE3Core(String entityName)");
        w.writeLine("// Reduce implicit wait for fast menu scanning");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("String[] menuButtons = {TestData.SUBSYSTEM_NAME, \"\\u041d\\u0421\\u0418\", \"\\u041e\\u0442\\u0447\\u0451\\u0442\\u044b\", \"\\u0421\\u0435\\u0440\\u0432\\u0438\\u0441\"};");
        w.openBlock("for (String menuName : menuButtons)");
        w.openBlock("try");
        w.writeLine("WebElement menuBtn = driver.findElement(By.xpath(\"//button[contains(@class, 'x-btn-text')][contains(text(), '\" + menuName + \"')]\"));");
        w.writeLine("menuBtn.click();");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("String itemXpath = \"//span[contains(@class, 'x-menu-item-text')][contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //a[contains(@class, 'x-menu-item')][contains(text(), '\" + entityName + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class, 'x-menu')]//*[contains(text(), '\" + entityName + \"')]\";");
        w.writeLine("List<WebElement> items = driver.findElements(By.xpath(itemXpath));");
        w.openBlock("if (!items.isEmpty())");
        w.writeLine("new Actions(driver).moveToElement(items.get(0)).perform();");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("List<WebElement> findBtns = driver.findElements(By.xpath(\"//span[contains(@class, 'x-menu-item-text')][contains(text(), '\\u041d\\u0430\\u0439\\u0442\\u0438')] | //a[contains(@class, 'x-menu-item')][contains(text(), '\\u041d\\u0430\\u0439\\u0442\\u0438')]\"));");
        w.openBlock("if (!findBtns.isEmpty())");
        w.writeLine("findBtns.get(findBtns.size() - 1).click();");
        w.writeLine("Thread.sleep(1000);");
        w.writeLine("navigationOk = true;");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("items.get(0).click();");
        w.writeLine("wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(\"body\")));");
        w.writeLine("Thread.sleep(500);");
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

        // Helper: open search
        w.openBlock("protected void openSearch(String searchName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement searchLink = driver.findElement(By.xpath(");
        w.writeLine("    \"//span[contains(@class, 'x-tree-node-text')][contains(text(), '\" + searchName + \"')]\"");
        w.writeLine("    + \" | //span[contains(text(), '\" + searchName + \"')]\"");
        w.writeLine("    + \" | //a[contains(text(), '\" + searchName + \"')]\"));");
        w.writeLine("searchLink.click();");
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
        w.writeLine();
        w.writeLine("private TestData() {}");
        w.closeBlock();

        w.writeToFile(dir, "TestData.java");
    }
}
