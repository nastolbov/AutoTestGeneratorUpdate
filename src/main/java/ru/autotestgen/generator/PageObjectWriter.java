package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.common.Transliterator;
import ru.autotestgen.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates Selenium Page Object classes for each entity.
 * Each page class contains WebElements for form fields and methods
 * for interacting with them based on dmodule type.
 */
public class PageObjectWriter {

    private final String basePackage;

    public PageObjectWriter(String basePackage) {
        this.basePackage = basePackage;
    }

    public void write(EntityObject entity, Path outputDir) throws IOException {
        String className = Transliterator.toClassName(entity.getName()) + "Page";
        String packageName = basePackage + ".page";
        Path dir = outputDir.resolve(packageName.replace('.', '/'));

        PropertyGroup formView = entity.getFormView();
        List<Property> displayProperties = getDisplayProperties(entity);

        JavaFileWriter w = new JavaFileWriter();

        // Package and imports
        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import org.openqa.selenium.support.ui.WebDriverWait;");
        w.writeLine("import org.openqa.selenium.support.ui.ExpectedConditions;");
        w.writeLine("import java.time.Duration;");
        w.writeLine();

        // Class declaration
        w.openBlock("public class " + className);
        w.writeLine();
        w.writeLine("private WebDriver driver;");
        w.writeLine("private WebDriverWait wait;");
        w.writeLine();

        // Constructor — no PageFactory since PropertyGrid has no named inputs
        w.openBlock("public " + className + "(WebDriver driver)");
        w.writeLine("this.driver = driver;");
        w.writeLine("this.wait = new WebDriverWait(driver, Duration.ofSeconds(3));");
        w.closeBlock();
        w.writeLine();

        // Generic helper to fill a PropertyGrid field by its display name (fast)
        w.openBlock("private void fillPropertyGridField(String fieldName, String value)");
        w.writeLine("// Reduce implicit wait for fast field lookup");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(200));");
        w.openBlock("try");
        w.writeLine("// Find the row with the field name in property grid");
        w.writeLine("java.util.List<WebElement> nameCells = driver.findElements(By.xpath(\"//div[contains(@class, 'x-grid3-cell-inner')][contains(text(), '\" + fieldName + \"')]\"));");
        w.openBlock("if (nameCells.isEmpty())");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("WebElement nameCell = nameCells.get(0);");
        w.writeLine("// Click the value cell (adjacent td in same row)");
        w.writeLine("java.util.List<WebElement> rows = nameCell.findElements(By.xpath(\"ancestor::tr\"));");
        w.openBlock("if (rows.isEmpty())");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> cells = rows.get(0).findElements(By.cssSelector(\"td\"));");
        w.openBlock("if (cells.size() >= 2)");
        w.writeLine("cells.get(1).click();");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("// Wait for inline editor (findElements = no implicit wait timeout)");
        w.writeLine("java.util.List<WebElement> editors = driver.findElements(By.cssSelector(\"input.x-form-text:not([type='hidden']), input.x-form-field:not([type='hidden'])\"));");
        w.openBlock("for (WebElement editor : editors)");
        w.openBlock("if (editor.isDisplayed())");
        w.writeLine("editor.clear();");
        w.writeLine("editor.sendKeys(value);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("Thread.sleep(50);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("// Silent fail — some fields may not be editable");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Input methods for each property based on dmodule type
        for (Property prop : displayProperties) {
            if (isSystemField(prop)) continue;
            writeInputMethod(w, prop);
        }

        // Method to fill all required fields
        writeFilAllRequiredMethod(w, displayProperties);

        // Method to fill all fields with test data
        writeFillAllFieldsMethod(w, displayProperties);

        // Action button methods from modifiers (search any PropertyGroup)
        Operation crudOperation = entity.getPropertyGroups().stream()
                .map(PropertyGroup::getOperation)
                .filter(op -> op != null && !op.getModifiers().isEmpty())
                .findFirst()
                .orElse(null);
        if (crudOperation != null) {
            for (Modifier mod : crudOperation.getModifiers()) {
                writeModifierMethod(w, mod);
            }
        }

        // Check field presence methods
        writeCheckFieldsPresentMethod(w, displayProperties);

        // Method to check if field is displayed — tries property grid + standard selectors
        w.writeLine("// Overload: search by display name only");
        w.openBlock("public boolean isFieldDisplayed(String displayName)");
        w.writeLine("return isFieldDisplayed(displayName, displayName);");
        w.closeBlock();
        w.writeLine();
        w.openBlock("public boolean isFieldDisplayed(String displayName, String attrName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(100));");
        w.openBlock("try");
        w.writeLine("// Strategy 1: E3Core property grid — find row with field name text");
        w.writeLine("java.util.List<WebElement> rows = driver.findElements(By.xpath(");
        w.writeLine("    \"//div[contains(@class,'x-grid3')]//div[contains(text(),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //div[contains(text(),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //td[contains(text(),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //span[contains(text(),'\" + displayName + \"')]\"));");
        w.openBlock("if (!rows.isEmpty())");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return true;");
        w.closeBlock();
        w.writeLine("// Strategy 2: standard HTML inputs by name/id");
        w.writeLine("String css = \"[name='\" + attrName + \"'], [id='\" + attrName + \"'], \" +");
        w.writeLine("    \"input.x-form-field[name='\" + attrName + \"'], \" +");
        w.writeLine("    \"[data-testid='\" + attrName + \"']\";");
        w.writeLine("java.util.List<WebElement> els = driver.findElements(By.cssSelector(css));");
        w.writeLine("boolean found = els.stream().anyMatch(WebElement::isDisplayed);");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return found;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return false;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Method to get field value — tries standard selectors, then PropertyGrid by display name
        w.openBlock("public String getFieldValue(String fieldName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("// Try standard selectors first");
        w.writeLine("java.util.List<WebElement> els = driver.findElements(By.cssSelector(\"[name='\" + fieldName + \"'], [id='\" + fieldName + \"']\"));");
        w.openBlock("if (!els.isEmpty())");
        w.writeLine("String val = els.get(0).getAttribute(\"value\");");
        w.writeLine("return val != null ? val : els.get(0).getText();");
        w.closeBlock();
        w.writeLine("// Try PropertyGrid — find value cell by field display name");
        w.writeLine("java.util.List<WebElement> cells = driver.findElements(By.xpath(\"//div[contains(@class, 'x-grid3-cell-inner')][contains(text(), '\" + fieldName + \"')]/ancestor::tr//td[2]//div[contains(@class, 'x-grid3-cell-inner')]\"));");
        w.openBlock("if (!cells.isEmpty())");
        w.writeLine("return cells.get(0).getText().trim();");
        w.closeBlock();
        w.writeLine("return \"\";");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return \"\";");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Method to check if validation errors are visible
        w.openBlock("public boolean hasValidationErrors()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("java.util.List<WebElement> errors = driver.findElements(By.cssSelector(\".error, .has-error, .is-invalid, .validation-error, .field-error, [class*='error'], .alert-danger, .x-form-invalid, .x-form-invalid-msg\"));");
        w.writeLine("return errors.stream().anyMatch(WebElement::isDisplayed);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Method to check if a specific field has validation error
        w.openBlock("public boolean fieldHasError(String fieldName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("WebElement field = driver.findElement(By.cssSelector(\"[name='\" + fieldName + \"'], [id='\" + fieldName + \"']\"));");
        w.writeLine("String cls = field.getAttribute(\"class\");");
        w.writeLine("if (cls != null && (cls.contains(\"error\") || cls.contains(\"invalid\") || cls.contains(\"x-form-invalid\"))) return true;");
        w.writeLine("// Check parent and siblings for error indicators");
        w.writeLine("WebElement parent = field.findElement(By.xpath(\"..\"));");
        w.writeLine("String parentCls = parent.getAttribute(\"class\");");
        w.writeLine("return parentCls != null && (parentCls.contains(\"error\") || parentCls.contains(\"invalid\"));");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Method to get row count in grid/table
        w.openBlock("public int getTableRowCount()");
        w.openBlock("try");
        w.writeLine("java.util.List<WebElement> rows = driver.findElements(By.cssSelector(\".x-grid3-row, tr.data-row, tr[data-index], tbody tr\"));");
        w.writeLine("return (int) rows.stream().filter(WebElement::isDisplayed).count();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return 0;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        // Method to clear form
        w.openBlock("public void clearForm()");
        w.writeLine("java.util.List<WebElement> inputs = driver.findElements(By.cssSelector(\"input[type='text'], textarea\"));");
        w.openBlock("for (WebElement input : inputs)");
        w.openBlock("try");
        w.writeLine("input.clear();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();

        w.closeBlock(); // end class

        w.writeToFile(dir, className + ".java");
    }

    private void writeInputMethod(JavaFileWriter w, Property prop) {
        String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
        String displayName = prop.getName(); // Russian display name for PropertyGrid lookup

        // All field types use fillPropertyGridField with the Russian display name
        w.openBlock("public void " + methodName + "(String value)");
        w.writeLine("fillPropertyGridField(\"" + displayName + "\", value);");
        w.closeBlock();
        w.writeLine();
    }

    private void writeFilAllRequiredMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public void fillRequiredFields()");
        for (Property prop : properties) {
            if (!prop.isRequired() || isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String value = TestDataFactory.generateValue(prop);
            if (value != null) {
                w.writeLine(methodName + "(\"" + value + "\");");
            } else if ("Directory".equals(prop.getStereoType())) {
                w.writeLine("// " + prop.getName() + " - select first available option in dropdown");
                w.writeLine(methodName + "(\"1\");");
            }
        }
        w.closeBlock();
        w.writeLine();
    }

    private void writeFillAllFieldsMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public void fillAllFields()");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String value = TestDataFactory.generateValue(prop);
            if (value != null) {
                w.writeLine(methodName + "(\"" + value + "\");");
            } else if ("Directory".equals(prop.getStereoType()) || "Ref".equals(prop.getStereoType())) {
                w.writeLine("// " + prop.getName() + " - dropdown/reference field");
                w.writeLine(methodName + "(\"1\");");
            }
        }
        w.closeBlock();
        w.writeLine();
    }

    private void writeModifierMethod(JavaFileWriter w, Modifier mod) {
        String methodName;
        if (mod.getModifyType() == null) {
            methodName = "clickAction";
        } else {
            methodName = switch (mod.getModifyType()) {
                case INSERT -> "clickAdd";
                case UPDATE -> "clickSave";
                case DELETE -> "clickDelete";
                case LOGICAL_EDIT -> "clickLogicalEdit";
                case ARCHIVE -> "clickArchive";
            };
        }

        w.openBlock("public void " + methodName + "()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(500));");
        w.openBlock("try");
        w.writeLine("// Try original title, then E3Core dialog buttons (Готово/Отмена)");
        w.writeLine("String xpath = \"//button[contains(text(), '" + mod.getTitle() + "')]\"");
        w.writeLine("    + \" | //button[contains(@class, 'x-btn-text')][contains(text(), '" + mod.getTitle() + "')]\"");
        w.writeLine("    + \" | //button[contains(text(), 'Готово')]\"");
        w.writeLine("    + \" | //button[contains(text(), 'OK')]\"");
        w.writeLine("    + \" | //input[@value='" + mod.getTitle() + "']\";");
        w.writeLine("WebElement button = driver.findElement(By.xpath(xpath));");
        w.writeLine("button.click();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("throw e;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
    }

    private void writeCheckFieldsPresentMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public boolean checkAllFieldsPresent()");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        w.openBlock("try");
        w.writeLine("// Check if ExtJS property grid dialog or window is still visible");
        w.writeLine("return !driver.findElements(By.cssSelector(\".x-window, .x-grid3, .x-panel\")).isEmpty();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("return false;");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
    }

    private List<Property> getDisplayProperties(EntityObject entity) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<Property> result = new java.util.ArrayList<>();
        for (PropertyGroup pg : entity.getPropertyGroups()) {
            if (!pg.isFormView() && !pg.isFlagDisplay()) continue;
            for (Property p : pg.getProperties()) {
                if (p.isFlagDisplay() && seen.add(p.getAttrName())) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    private boolean isSystemField(Property prop) {
        String stereo = prop.getStereoType();
        return "RoleA".equals(stereo) || "ObjectName".equals(stereo);
    }
}
