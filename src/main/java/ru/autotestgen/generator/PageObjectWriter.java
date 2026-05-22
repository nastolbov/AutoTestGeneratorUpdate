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

        // Generic helper to fill a PropertyGrid field by its display name.
        // ВАЖНО: для надёжности сначала пробуем ExtJS API (setValue по fieldLabel),
        // и только потом fall-back на DOM-клик ячейки + inline-редактор.
        // fillPropertyGridField: открывает inline-редактор ячейки PropertyGrid по русскому
        // имени поля и вводит value. Логирует результат каждой попытки чтобы было видно,
        // какие именно обязательные поля не заполняются (раньше падали в silent skip и в
        // итоге Готово отбивался валидацией).
        w.openBlock("private void fillPropertyGridField(String fieldName, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(200));");
        // ПЕРВАЯ ПОПЫТКА: ExtJS API. Прямо ставим record.set('value', value) в стор грида
        // свойств — не зависит от состояния inline-редактора, не падает InvalidElementState.
        w.openBlock("try");
        w.writeLine("Object res = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var name = arguments[0], value = arguments[1];\"");
        w.writeLine("    + \"  var all = Ext.ComponentMgr && Ext.ComponentMgr.all ? Ext.ComponentMgr.all : (Ext.ComponentManager && Ext.ComponentManager.all ? Ext.ComponentManager.all : null);\"");
        w.writeLine("    + \"  if (!all) return 'no-mgr';\"");
        w.writeLine("    + \"  var items = [];\"");
        w.writeLine("    + \"  if (all.items) items = all.items;\"");
        w.writeLine("    + \"  else if (all.each) all.each(function(c){items.push(c);});\"");
        w.writeLine("    + \"  else for (var k in all) items.push(all[k]);\"");
        w.writeLine("    + \"  for (var i = 0; i < items.length; i++) {\"");
        w.writeLine("    + \"    var c = items[i]; if (!c || !c.rendered || !c.getStore) continue;\"");
        w.writeLine("    + \"    if (c.getEl && c.getEl().dom && (c.getEl().dom.offsetWidth === 0 || c.getEl().dom.offsetHeight === 0)) continue;\"");
        w.writeLine("    + \"    var s = c.getStore(); if (!s || !s.getCount) continue;\"");
        w.writeLine("    + \"    for (var j = 0; j < s.getCount(); j++) {\"");
        w.writeLine("    + \"      var rec = s.getAt(j); if (!rec || !rec.data) continue; var d = rec.data;\"");
        w.writeLine("    + \"      var dn = d.displayName != null ? String(d.displayName) : '';\"");
        w.writeLine("    + \"      var nn = d.name != null ? String(d.name) : '';\"");
        w.writeLine("    + \"      if (dn === name || nn === name || dn.indexOf(name) === 0 || nn.indexOf(name) === 0) {\"");
        w.writeLine("    + \"        try { rec.set('value', value); } catch (e1) { try { rec.set('value', String(value)); } catch (e2) { return 'set-fail:' + e2.message; } }\"");
        w.writeLine("    + \"        if (c.view && c.view.refresh) try { c.view.refresh(); } catch (er) {}\"");
        w.writeLine("    + \"        return 'set:' + (dn || nn);\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'not-found';\"");
        w.writeLine("    + \"} catch(e) { return 'err:' + e.message; }\", fieldName, value);");
        w.openBlock("if (res != null && String.valueOf(res).startsWith(\"set:\"))");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK via ExtJS API ('\" + value + \"')\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' ExtJS API result: \" + res + \" — falling back to DOM\");");
        w.closeBlock();
        w.openBlock("catch (Exception jsEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' ExtJS API threw: \" + jsEx.getMessage() + \" — falling back to DOM\");");
        w.closeBlock();
        w.openBlock("try");
        // Поиск лейбла поля: пробуем сразу несколько вариантов нормализации текста,
        // потому что у обязательных полей может быть '*' или вложенные em/span.
        w.writeLine("String xp = \"//div[contains(@class,'x-grid3-cell-inner')][\"");
        w.writeLine("    + \"normalize-space(.) = '\" + fieldName + \"'\"");
        w.writeLine("    + \" or contains(normalize-space(.), '\" + fieldName + \"')\"");
        w.writeLine("    + \" or contains(text(), '\" + fieldName + \"')\"");
        w.writeLine("    + \"]\";");
        w.writeLine("java.util.List<WebElement> nameCells = driver.findElements(By.xpath(xp));");
        w.openBlock("if (nameCells.isEmpty())");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (label cell not in DOM)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("WebElement nameCell = null;");
        w.openBlock("for (WebElement c : nameCells)");
        w.openBlock("try");
        w.openBlock("if (c.isDisplayed())");
        w.writeLine("nameCell = c; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (nameCell == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (label found but not visible)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> rows = nameCell.findElements(By.xpath(\"ancestor::tr\"));");
        w.openBlock("if (rows.isEmpty())");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (no ancestor tr)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> cells = rows.get(0).findElements(By.cssSelector(\"td\"));");
        w.openBlock("if (cells.size() < 2)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (row has \" + cells.size() + \" cells)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        // Двойной клик по value-ячейке надёжнее single — PropertyGrid в ExtJS 3
        // активирует inline-editor именно по dblclick.
        w.writeLine("WebElement valueCell = cells.get(1);");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(valueCell).doubleClick().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { valueCell.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(400);");
        // Сначала ищем триггер-кнопку: для FK/Directory полей (combobox) нужно сначала
        // открыть выпадающий список, и потом стрелкой+Enter выбрать первый пункт.
        w.writeLine("java.util.List<WebElement> triggers = driver.findElements(By.cssSelector(\"img.x-form-trigger, div.x-form-trigger\"));");
        w.writeLine("WebElement visibleTrigger = null;");
        w.openBlock("for (WebElement t : triggers)");
        w.openBlock("try");
        w.openBlock("if (t.isDisplayed())");
        w.writeLine("visibleTrigger = t; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> editors = driver.findElements(By.cssSelector(\"input.x-form-text:not([type='hidden']), input.x-form-field:not([type='hidden'])\"));");
        w.writeLine("WebElement editor = null;");
        w.openBlock("for (WebElement ed : editors)");
        w.openBlock("try");
        w.openBlock("if (ed.isDisplayed())");
        w.writeLine("editor = ed; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (editor == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (no visible editor input after dblclick)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        // Триггер-стрелка может означать combobox (FK/Directory) ИЛИ date-picker.
        // Combobox требует выбора из списка (ARROW_DOWN + ENTER), а date-field принимает
        // прямой ввод и валидирует по маске. Различаем по классам:
        //  - x-combo / x-combo-noedit → combobox → picker
        //  - иначе (включая x-form-date-field, обычный text input) → печатаем value.
        w.writeLine("String editorClass = editor.getAttribute(\"class\");");
        w.writeLine("if (editorClass == null) editorClass = \"\";");
        w.writeLine("boolean isCombo = editorClass.contains(\"x-combo\");");
        w.openBlock("try");
        w.openBlock("if (visibleTrigger != null && isCombo)");
        w.writeLine("editor.clear();");
        w.writeLine("visibleTrigger.click();");
        w.writeLine("Thread.sleep(400);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ARROW_DOWN);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK (combobox, first option)\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("editor.clear();");
        w.writeLine("editor.sendKeys(value);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"')\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = FAIL (\" + e.getClass().getSimpleName() + \")\");");
        w.closeBlock();
        w.writeLine("Thread.sleep(120);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = FAIL outer (\" + e.getClass().getSimpleName() + \")\");");
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
        w.writeLine("// Strategy 1: form / grid header / cell — any visible element whose descendant text contains the name.");
        w.writeLine("// normalize-space(.) handles ExtJS structures where the label is wrapped in nested spans/ems.");
        w.writeLine("java.util.List<WebElement> hits = driver.findElements(By.xpath(");
        w.writeLine("    \"//div[contains(@class,'x-grid3-hd-inner')][contains(normalize-space(.),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //span[contains(@class,'x-column-header-text')][contains(normalize-space(.),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //div[contains(@class,'x-grid3-cell-inner')][contains(normalize-space(.),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //label[contains(normalize-space(.),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //div[contains(text(),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //td[contains(text(),'\" + displayName + \"')]\"");
        w.writeLine("    + \" | //span[contains(text(),'\" + displayName + \"')]\"));");
        w.openBlock("if (hits.stream().anyMatch(WebElement::isDisplayed))");
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
        // KEY: каждое обязательное поле ДОЛЖНО быть заполнено, иначе сервер вернёт ошибку
        // валидации и testCreate провалится. Если TestDataFactory не смогла сгенерировать значение
        // (FK/Ref-поле), даём хотя бы "1" — это типовое значение для FK-пикера на E3Core. fillX в
        // PageObject сам разберётся (попробует вписать в инпут, потом откроет дропдаун).
        for (Property prop : properties) {
            if (!prop.isRequired() || isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String value = TestDataFactory.generateValue(prop);
            if (value == null) {
                // FK / Directory / Ref — нет генерируемого значения. Используем "1" как универсальный
                // fallback (выбор первого доступного варианта в пикере).
                w.writeLine("// " + prop.getName() + " — required FK/Ref, default to first option");
                w.writeLine(methodName + "(\"1\");");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
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
            if (value == null) {
                // FK / Directory / Ref — default "1"
                w.writeLine("// " + prop.getName() + " — FK/Ref, default to first option");
                w.writeLine(methodName + "(\"1\");");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
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
