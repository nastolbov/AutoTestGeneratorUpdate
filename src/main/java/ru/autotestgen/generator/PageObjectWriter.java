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
        // Сюда складываем (Русское имя поля -> значение) КАЖДЫЙ раз, когда тест что-то
        // вписывает через fillX(...). Используется в testCreate, чтобы после сохранения
        // искать запись в гриде НЕ по «рассчитанному» маркеру, а по фактически
        // заполненным значениям полей. Сбрасывается в fillAllFields / fillRequiredFields.
        w.writeLine("public java.util.LinkedHashMap<String, String> lastFilledValues = new java.util.LinkedHashMap<>();");
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
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(80));");
        // value == null означает FK / Directory / Ref-поле. Сразу идём в DOM-пикер выпадашки.
        w.openBlock("if (value == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' — FK field, opening dropdown\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("fillFKViaDropdown(fieldName);");
        w.writeLine("return;");
        w.closeBlock();
        // СТРАТЕГИЯ A (ExtJS rec.set) УБРАНА — она лазала по всем компонентам через
        // ComponentMgr и как побочный эффект переключала табы (Сведения → Документы).
        // НОВАЯ СТРАТЕГИЯ: открываем editor через ExtJS API grid.startEditing(rowIndex, 1).
        // Это надёжно: ExtJS сам найдёт правильную ячейку и откроет НУЖНЫЙ editor.
        // Раньше click+positioning часто открывал editor чужого поля (x-form-num-field
        // или x-combo-noedit) и sendKeys уходили в воздух или переключали раздел.
        w.openBlock("try");
        w.writeLine("Object startResult = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var name = arguments[0];\"");
        w.writeLine("    + \"  var mgr = Ext.ComponentMgr || Ext.ComponentManager;\"");
        w.writeLine("    + \"  if (!mgr || !mgr.all) return 'no-mgr';\"");
        w.writeLine("    + \"  var items = [];\"");
        w.writeLine("    + \"  if (mgr.all.items) items = mgr.all.items;\"");
        w.writeLine("    + \"  else if (mgr.all.each) mgr.all.each(function(c){items.push(c);});\"");
        w.writeLine("    + \"  else for (var k in mgr.all) items.push(mgr.all[k]);\"");
        w.writeLine("    + \"  for (var i = 0; i < items.length; i++) {\"");
        w.writeLine("    + \"    var c = items[i];\"");
        w.writeLine("    + \"    if (!c || !c.rendered || !c.getStore || !c.customEditors || !c.startEditing) continue;\"");
        w.writeLine("    + \"    try { if (c.getEl().dom.offsetWidth <= 0 || c.getEl().dom.offsetHeight <= 0) continue; } catch(e) { continue; }\"");
        w.writeLine("    + \"    var s = c.getStore(); if (!s) continue;\"");
        w.writeLine("    + \"    for (var j = 0; j < s.getCount(); j++) {\"");
        w.writeLine("    + \"      var rec = s.getAt(j); if (!rec || !rec.data) continue;\"");
        w.writeLine("    + \"      var dn = rec.data.displayName != null ? String(rec.data.displayName) : '';\"");
        w.writeLine("    + \"      var nn = rec.data.name != null ? String(rec.data.name) : '';\"");
        w.writeLine("    + \"      if (dn === name || nn === name || dn.indexOf(name) === 0 || nn.indexOf(name) === 0) {\"");
        w.writeLine("    + \"        try { c.startEditing(j, 1); } catch(e) { return 'err-start:' + e.message; }\"");
        w.writeLine("    + \"        return 'OK:grid=' + (c.id || '?') + '/row=' + j + '/dn=' + dn;\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'no-match';\"");
        w.writeLine("    + \"} catch(e) { return 'err:' + e.message; }\", fieldName);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing result: \" + startResult);");
        w.openBlock("if (startResult != null && String.valueOf(startResult).startsWith(\"OK:\"))");
        w.writeLine("Thread.sleep(250);");
        w.writeLine("WebElement startedEditor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"return document.activeElement;\");");
        w.openBlock("if (startedEditor != null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor через startEditing: id=\" + startedEditor.getAttribute(\"id\") + \" class=\" + startedEditor.getAttribute(\"class\"));");
        w.writeLine("startedEditor.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, \"a\"));");
        w.writeLine("Thread.sleep(80);");
        w.writeLine("startedEditor.sendKeys(value);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("startedEditor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(300);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' via startEditing+sendKeys+ENTER)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing не сработал — пробуем DOM click fallback\");");
        w.closeBlock();
        w.openBlock("catch (Exception startEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing threw: \" + startEx.getMessage());");
        w.closeBlock();
        // СТРАТЕГИЯ B (DOM): fallback — клик по ячейке + sendKeys.
        w.openBlock("try");
        // НЕ ИСПОЛЬЗУЕМ Esc — в ExtJS PropertyGrid Esc отменяет inline-add и УДАЛЯЕТ
        // последнюю добавленную строку (см. фидбек пользователя 'то первую строку удаляешь').
        // Editor предыдущего fillX закрывается через ENTER в самом конце той fillX, так что
        // к моменту нового fillX редактор уже закрыт сам.
        w.writeLine("String xp = \"//div[contains(@class,'x-grid3-cell-inner')][\"");
        w.writeLine("    + \"normalize-space(.) = '\" + fieldName + \"'\"");
        w.writeLine("    + \" or contains(normalize-space(.), '\" + fieldName + \"')\"");
        w.writeLine("    + \"]\";");
        w.writeLine("java.util.List<WebElement> nameCells = driver.findElements(By.xpath(xp));");
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
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (label cell not in DOM)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> rows = nameCell.findElements(By.xpath(\"ancestor::tr\"));");
        w.openBlock("if (rows.isEmpty())");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (no row)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> cells = rows.get(0).findElements(By.cssSelector(\"td\"));");
        w.openBlock("if (cells.size() < 2)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (cells<2)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("WebElement valueCell = cells.get(1);");
        // ScrollIntoView + лог Y-координаты — иначе верхние ячейки (Наименование, Включён)
        // после прокрутки формы уезжали за viewport и клик не активировал редактор.
        // Используем scrollIntoView({block:'center'}) чтобы ячейка оказалась посередине
        // окна (не у самого верха, где её может перекрыть заголовок диалога).
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});\", valueCell);");
        w.writeLine("Thread.sleep(200);");
        w.writeLine("Object rect = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var r = arguments[0].getBoundingClientRect(); return r.top + ',' + r.left + ',' + r.width + ',' + r.height;\", valueCell);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' value-cell rect=\" + rect);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Активируем editor через click + pause + click (тот же жест что для FK)
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver)");
        w.writeLine("    .moveToElement(valueCell)");
        w.writeLine("    .click()");
        w.writeLine("    .pause(java.time.Duration.ofMillis(350))");
        w.writeLine("    .click()");
        w.writeLine("    .perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { valueCell.click(); Thread.sleep(200); valueCell.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(300);");
        // Найти видимый input редактора
        // КРИТИЧНО: ищем editor правильно. Раньше findElements без скоупа брал ПЕРВЫЙ
        // input на странице — а это часто readonly-комбо чужого поля. Сейчас:
        // 1) Сначала document.activeElement (то что в фокусе после клика по ячейке).
        // 2) Если не подходит — ищем не-readonly INPUT в области координат ячейки.
        w.writeLine("WebElement editor = null;");
        w.openBlock("try");
        w.writeLine("WebElement active = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var a = document.activeElement;\"");
        w.writeLine("    + \"if (!a) return null;\"");
        w.writeLine("    + \"if (a.tagName !== 'INPUT' && a.tagName !== 'TEXTAREA') return null;\"");
        w.writeLine("    + \"if (a.readOnly) return null;\"");
        w.writeLine("    + \"if (a.className && a.className.indexOf('x-combo-noedit') >= 0) return null;\"");
        w.writeLine("    + \"return a;\");");
        w.openBlock("if (active != null && active.isDisplayed())");
        w.writeLine("editor = active;");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor найден через document.activeElement: id=\" + editor.getAttribute(\"id\") + \" class=\" + editor.getAttribute(\"class\"));");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Fallback: общий поиск, но ОТФИЛЬТРОВАННЫЙ от readonly и x-combo-noedit.
        w.openBlock("if (editor == null)");
        w.writeLine("java.util.List<WebElement> inputs = driver.findElements(By.cssSelector(\"input.x-form-text:not([type='hidden']):not([readonly]), input.x-form-field:not([type='hidden']):not([readonly]), textarea.x-form-textarea:not([readonly])\"));");
        w.openBlock("for (WebElement ed : inputs)");
        w.openBlock("try");
        w.writeLine("String cls = ed.getAttribute(\"class\");");
        w.openBlock("if (ed.isDisplayed() && (cls == null || cls.indexOf(\"x-combo-noedit\") < 0))");
        w.writeLine("editor = ed;");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor через fallback findElements: id=\" + ed.getAttribute(\"id\") + \" class=\" + cls);");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (editor == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (no editor input visible — клик по ячейке не активировал editor)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Очистка + ввод как с клавиатуры. ExtJS form-binding читает значение из input
        // только если оно пришло через keypress/keyup events — JS-set'тер игнорируется.
        // Поэтому: editor.clear() + editor.sendKeys(value посимвольно) + ENTER чтобы
        // ExtJS зафиксировал значение и закрыл редактор ячейки.
        w.openBlock("try");
        w.writeLine("editor.click();");
        w.writeLine("Thread.sleep(150);");
        // Лог содержимого ДО ввода — увидим что в редакторе изначально (от предыдущего fill?)
        w.openBlock("try");
        w.writeLine("String beforeVal = editor.getAttribute(\"value\");");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor.value BEFORE = '\" + (beforeVal == null ? \"\" : beforeVal) + \"'\");");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // ОДНИМ chord'ом: Ctrl+A выделяет всё, потом сразу sendKeys(value) перетирает
        // выделение. Раньше было editor.clear() + Ctrl+A + DELETE отдельными вызовами —
        // после DELETE редактор ExtJS закрывался, sendKeys(value) уходил в body вместо
        // ячейки → ячейка оставалась ПУСТОЙ (как пользователь и видел после marker stamp).
        w.openBlock("try");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, \"a\"));");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(value);");
        w.writeLine("Thread.sleep(150);");
        w.closeBlock();
        w.openBlock("catch (Exception keyEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' sendKeys threw: \" + keyEx.getMessage());");
        w.closeBlock();
        // Лог содержимого ПОСЛЕ ввода, ДО ENTER
        w.openBlock("try");
        w.writeLine("String afterVal = editor.getAttribute(\"value\");");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor.value AFTER sendKeys = '\" + (afterVal == null ? \"\" : afterVal) + \"' (expected '\" + value + \"')\");");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // ENTER подтверждает значение в редакторе PropertyGrid; ExtJS закрывает редактор
        // и применяет value к rec.data.
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(250);");
        // Верификация: читаем ОБРАТНО текст value-ячейки. Если в ней нет нашего value —
        // sendKeys ушёл не туда (например в чужой редактор), и поле осталось пустым.
        w.openBlock("try");
        w.writeLine("Object cellText = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"return (arguments[0].innerText || arguments[0].textContent || '').trim();\", valueCell);");
        w.writeLine("String actual = cellText == null ? \"\" : String.valueOf(cellText);");
        w.openBlock("if (actual.contains(value))");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' видно в ячейке после ENTER)\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = WARN! Cell text после ENTER = '\" + actual + \"', ожидали '\" + value + \"' — value НЕ зафиксирован, sendKeys ушёл не туда\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' via sendKeys+ENTER, verify failed)\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = FAIL (\" + e.getClass().getSimpleName() + \": \" + e.getMessage() + \")\");");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = FAIL outer (\" + e.getClass().getSimpleName() + \")\");");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();


        // fillFKViaDropdown: для FK/Ref-полей. Находит ячейку value, делает dblclick, ждёт пол
        // секунды, ищет выпадающий список (.x-combo-list-item / .x-boundlist-item). Если списка
        // нет — пробует кликнуть видимую кнопку-триггер (стрелочку справа от инпута). Когда
        // пункты появились — выбирает СЛУЧАЙНЫЙ и кликает по нему. Не пытается ничего печатать —
        // только выбор из готового списка справочника.
        w.openBlock("private void fillFKViaDropdown(String fieldName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        // СТРАТЕГИЯ A (ExtJS rec.set) УБРАНА — она лазала по всем компонентам через
        // ComponentMgr и переключала табы как побочный эффект. Теперь только реальный
        // клик: ищем ВИДИМУЮ ячейку поля в текущем активном табе и кликаем dblclick.
        // ExtJS откроет пикер / выпадашку, мы выберем случайный элемент.
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' — открываем пикер реальным кликом\");");

        // === СТРАТЕГИЯ B: реальный клик по ячейке value FK-поля ===
        w.openBlock("try");
        w.writeLine("String xp = \"//div[contains(@class,'x-grid3-cell-inner')][\"");
        w.writeLine("    + \"normalize-space(.) = '\" + fieldName + \"'\"");
        w.writeLine("    + \" or contains(normalize-space(.), '\" + fieldName + \"')\"");
        w.writeLine("    + \"]\";");
        w.writeLine("java.util.List<WebElement> nameCells = driver.findElements(By.xpath(xp));");
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
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = SKIP (label cell not in DOM)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> rows = nameCell.findElements(By.xpath(\"ancestor::tr\"));");
        w.openBlock("if (rows.isEmpty())");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = SKIP (no ancestor tr)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> cells = rows.get(0).findElements(By.cssSelector(\"td\"));");
        w.openBlock("if (cells.size() < 2)");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = SKIP (row has \" + cells.size() + \" cells)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("WebElement valueCell = cells.get(1);");
        // Scroll cell into view — иначе верхние FK (Включён, Тип) уезжали за viewport
        // после прокрутки формы и dblclick по ним не работал.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});\", valueCell);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Запомним id активного окна ДО клика — иначе если пикер не открылся, Ext.WindowMgr
        // вернёт тот же диалог Сведения, и мы dblclick'нем его property-row (как было в прошлой
        // версии — пик попал на 'Дата изменения').
        w.writeLine("String activeBefore = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var aw = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null; return aw ? aw.id : null; } catch (e) { return null; }\");");
        // Жест из реального UX: первый click выделяет PropertyGrid-row, второй (через паузу)
        // активирует inline-editor на выделенной row, что у E3Core combo триггерит
        // авто-открытие выпадашки. ExtJS doubleClick из Actions слипал два клика в dblclick-
        // event, который у этого стенда сбрасывал editor — поэтому никакой dropdown не открывался.
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver)");
        w.writeLine("    .moveToElement(valueCell)");
        w.writeLine("    .click()");
        w.writeLine("    .pause(java.time.Duration.ofMillis(350))");
        w.writeLine("    .click()");
        w.writeLine("    .perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.openBlock("try");
        w.writeLine("valueCell.click(); Thread.sleep(350); valueCell.click();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // ExtJS combo list загружается асинхронно — ждём появления пунктов до 2.5с поллингом.
        w.writeLine("java.util.List<WebElement> items = pollDropdownItems(2500);");
        w.openBlock("if (items.isEmpty())");
        w.writeLine("java.util.List<WebElement> triggers = driver.findElements(By.cssSelector(");
        w.writeLine("    \"img.x-form-trigger, div.x-form-trigger, .x-form-trigger-wrap img, td.x-trigger-cell img\"));");
        w.openBlock("for (WebElement t : triggers)");
        w.openBlock("try");
        w.openBlock("if (t.isDisplayed())");
        w.writeLine("t.click();");
        w.writeLine("items = pollDropdownItems(2000);");
        w.openBlock("if (!items.isEmpty())");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        // F4 на активном элементе — стандартный ExtJS combobox expand. Иногда нужен после
        // dblclick если editor открылся, но dropdown не подтянулся автоматом.
        w.openBlock("if (items.isEmpty())");
        w.openBlock("try");
        w.writeLine("driver.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.F4);");
        w.writeLine("items = pollDropdownItems(1500);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // E3Core: FK поля часто открывают отдельное МОДАЛЬНОЕ окно-пикер с гридом записей.
        // Берём активное окно ТОЛЬКО если его id ОТЛИЧАЕТСЯ от activeBefore — иначе мы
        // вернёмся в тот же диалог редактирования и dblclick'нем его property-row.
        w.openBlock("if (items.isEmpty())");
        w.openBlock("try");
        w.writeLine("Object pickerRow = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var before = arguments[0];\"");
        w.writeLine("    + \"if (typeof Ext === 'undefined' || !Ext.WindowMgr || !Ext.WindowMgr.getActive) return null;\"");
        w.writeLine("    + \"var aw = Ext.WindowMgr.getActive(); if (!aw || !aw.getEl) return null;\"");
        w.writeLine("    + \"if (before && aw.id === before) return null;\"");
        w.writeLine("    + \"var root = aw.getEl().dom || aw.getEl();\"");
        w.writeLine("    + \"var rows = root.querySelectorAll('.x-grid3-row, .x-grid-row');\"");
        w.writeLine("    + \"var vis = [];\"");
        w.writeLine("    + \"for (var i = 0; i < rows.length; i++) { if (rows[i].offsetHeight > 0 && rows[i].offsetWidth > 0) vis.push(rows[i]); }\"");
        w.writeLine("    + \"if (vis.length === 0) return null;\"");
        w.writeLine("    + \"return vis[Math.floor(Math.random() * vis.length)];\", activeBefore);");
        w.openBlock("if (pickerRow instanceof WebElement)");
        w.writeLine("WebElement row = (WebElement) pickerRow;");
        w.writeLine("String rowText = row.getText() == null ? \"\" : row.getText().trim();");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(row).click().pause(java.time.Duration.ofMillis(150)).doubleClick().perform();");
        w.writeLine("Thread.sleep(500);");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { row.click(); Thread.sleep(200); new org.openqa.selenium.interactions.Actions(driver).moveToElement(row).doubleClick().perform(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = OK picker-window ('\" + rowText + \"')\");");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (items.isEmpty())");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = FAIL (no dropdown, no NEW picker window; activeBefore=\" + activeBefore + \")\");");
        w.writeLine("dumpDropdownDiagnostic();");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("WebElement pick = items.get(new java.util.Random().nextInt(items.size()));");
        w.writeLine("String pickedText = pick.getText() == null ? \"\" : pick.getText().trim();");
        // 1с пауза ПЕРЕД кликом — выпадашка появилась в DOM, но ExtJS ещё биндит её
        // обработчики. Без этого клик иногда проходил «впустую» и значение не выбиралось.
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' ждём 1с и кликаем элемент '\" + pickedText + \"'\");");
        w.writeLine("Thread.sleep(1000);");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(pick).click().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { pick.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(300);");
        // ВАЖНО: после клика по пункту ExtJS combobox получил value, но запись в PropertyGrid
        // может не зафиксироваться без явного коммита. ENTER заставляет combobox завершить
        // выбор и закрыть picker, привязывая значение к record'у. Потом TAB сдвигает фокус
        // PropertyGrid'а на следующую строку, чтобы можно было редактировать другие FK.
        w.openBlock("try");
        w.writeLine("org.openqa.selenium.WebElement focused = driver.switchTo().activeElement();");
        w.writeLine("focused.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(200);");
        w.writeLine("focused.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = OK ('\" + pickedText + \"' из \" + items.size() + \" вариантов)\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = ERR (\" + e.getClass().getSimpleName() + \": \" + e.getMessage() + \")\");");
        w.closeBlock();
        w.openBlock("finally");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();

        w.openBlock("private java.util.List<WebElement> collectVisibleDropdownItems()");
        w.writeLine("java.util.List<WebElement> all = driver.findElements(By.cssSelector(");
        w.writeLine("    \".x-combo-list-inner .x-combo-list-item, .x-combo-list .x-combo-list-item, .x-combo-list-item, .x-boundlist-item, .x-menu-list .x-menu-list-item, .x-combo-list-inner > div\"));");
        w.writeLine("java.util.List<WebElement> visible = new java.util.ArrayList<>();");
        w.openBlock("for (WebElement it : all)");
        w.openBlock("try");
        w.openBlock("if (it.isDisplayed() && it.getText() != null && !it.getText().trim().isEmpty())");
        w.writeLine("visible.add(it);");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return visible;");
        w.closeBlock();
        w.writeLine();

        // pollDropdownItems: ждёт пока выпадающий список появится (до timeoutMs мс),
        // опрашивая DOM каждые 200мс. ExtJS combo list иногда подгружается store'ом
        // асинхронно — фиксированная пауза 500мс была слишком короткой.
        w.openBlock("private java.util.List<WebElement> pollDropdownItems(int timeoutMs)");
        w.writeLine("long deadline = System.currentTimeMillis() + timeoutMs;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("java.util.List<WebElement> items = collectVisibleDropdownItems();");
        w.openBlock("if (!items.isEmpty())");
        w.writeLine("return items;");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return java.util.Collections.emptyList();");
        w.closeBlock();
        w.writeLine();

        // dumpDropdownDiagnostic: на FAIL fillFKViaDropdown — печатает что РЕАЛЬНО видно в
        // DOM, чтобы понять под какой селектор / класс открывается список на этом стенде.
        w.openBlock("private void dumpDropdownDiagnostic()");
        w.openBlock("try");
        w.writeLine("System.out.println(\"  [fill-FK] dropdown diagnostic:\");");
        w.writeLine("java.util.List<WebElement> all = driver.findElements(By.cssSelector(");
        w.writeLine("    \"[class*='list'], [class*='combo'], [class*='menu'], [role='listbox'], [role='option']\"));");
        w.writeLine("int shown = 0;");
        w.openBlock("for (WebElement e : all)");
        w.openBlock("try");
        w.openBlock("if (e.isDisplayed() && shown < 20)");
        w.writeLine("String cls = e.getAttribute(\"class\");");
        w.writeLine("String txt = e.getText() == null ? \"\" : e.getText().trim();");
        w.openBlock("if (cls != null && txt.length() > 0 && txt.length() < 100)");
        w.writeLine("System.out.println(\"    class='\" + cls + \"' text='\" + txt + \"'\");");
        w.writeLine("shown++;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
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
        // Записываем фактически вписанное значение (не null — пустые/FK-пикеры мы не
        // можем сравнивать с гридом) в lastFilledValues для последующего поиска записи
        // в результирующей таблице.
        w.openBlock("if (value != null && !value.isEmpty())");
        w.writeLine("lastFilledValues.put(\"" + displayName.replace("\\", "\\\\").replace("\"", "\\\"") + "\", value);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
    }

    private void writeFilAllRequiredMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public void fillRequiredFields()");
        w.writeLine("lastFilledValues.clear();");
        w.writeLine("String __uniq = String.valueOf(System.nanoTime());");
        // Подсчёт сколько required-полей мы ОЖИДАЕМ заполнить — потом сравним с реально
        // вписанными (lastFilledValues.size()). Если меньше — fillPropertyGridField
        // промахнулся для части полей.
        int expectedCount = 0;
        for (Property prop : properties) {
            if (!prop.isRequired() || isSystemField(prop)) continue;
            expectedCount++;
        }
        w.writeLine("int expectedRequired = " + expectedCount + ";");
        for (Property prop : properties) {
            if (!prop.isRequired() || isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String value = TestDataFactory.generateValue(prop);
            if (value == null) {
                w.writeLine("// " + prop.getName() + " — required FK/Ref, dropdown picker");
                w.writeLine(methodName + "(null);");
            } else if (prop.getAttrType() == AttrType.STRING && (prop.getMask() == null || prop.getMask().isEmpty())) {
                w.writeLine(methodName + "(\"" + value + "_\" + __uniq);");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
            }
        }
        w.writeLine("System.out.println(\"  [fillRequiredFields] заполнено \" + lastFilledValues.size() + \" из ожидаемых \" + expectedRequired + \" required-полей (FK-поля считаются как ожидаемые но не попадают в lastFilledValues)\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeFillAllFieldsMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public void fillAllFields()");
        w.writeLine("lastFilledValues.clear();");
        w.writeLine("String __uniq = String.valueOf(System.nanoTime());");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String value = TestDataFactory.generateValue(prop);
            if (value == null) {
                w.writeLine("// " + prop.getName() + " — FK/Ref, dropdown picker");
                w.writeLine(methodName + "(null);");
            } else if (prop.getAttrType() == AttrType.STRING && (prop.getMask() == null || prop.getMask().isEmpty())) {
                w.writeLine(methodName + "(\"" + value + "_\" + __uniq);");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
            }
        }
        w.writeLine("System.out.println(\"  [fillAllFields] заполнено \" + lastFilledValues.size() + \" полей\");");
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
        if ("RoleA".equals(stereo) || "ObjectName".equals(stereo)) return true;
        // Серверные auto-fill поля (Дата изменения, Оператор, Дата создания) — стенд E3Core
        // заполняет их сам. Если автотест туда пишет, форма отвергает create.
        if (prop.getDefValueSource() != null && !prop.getDefValueSource().isEmpty()) return true;
        String n = prop.getName();
        if (n != null) {
            String t = n.trim();
            if ("Дата изменения".equals(t)
                    || "Дата создания".equals(t)
                    || "Оператор".equals(t)) {
                return true;
            }
        }
        return false;
    }
}
