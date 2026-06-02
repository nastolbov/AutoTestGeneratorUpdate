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
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(80));");
        // value == null означает FK / Directory / Ref-поле. Сразу идём в DOM-пикер выпадашки.
        w.openBlock("if (value == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' — FK field, opening dropdown\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("fillFKViaDropdown(fieldName);");
        w.writeLine("return;");
        w.closeBlock();

        // === STRATEGY A: ExtJS API direct commit ===
        // The DOM-input path below writes to <input>, but PropertyGrid IGNORES our 'input'/
        // 'change' events on this stand — every typed value is lost on save and the server
        // replies 'Необходимо обязательно указать значения свойств: …'. Same record-set
        // approach already works for FK fields in fillFKViaDropdown (rec.set('value', x)) —
        // applying it to text/date fields fixes the empty-record save.
        w.openBlock("try");
        w.writeLine("Object apiRes = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var aw = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null;\"");
        w.writeLine("    + \"  if (!aw) return 'no-window';\"");
        w.writeLine("    + \"  var fieldName = arguments[0]; var value = arguments[1];\"");
        w.writeLine("    + \"  var grids = []; var queue = [aw]; var seen = {};\"");
        w.writeLine("    + \"  while (queue.length) {\"");
        w.writeLine("    + \"    var c = queue.shift(); if (!c || (c.id && seen[c.id])) continue; if (c.id) seen[c.id] = true;\"");
        w.writeLine("    + \"    if (c.getStore && c.getStore() && c.getStore().getCount) grids.push(c);\"");
        w.writeLine("    + \"    if (c.items && c.items.items) { for (var i = 0; i < c.items.items.length; i++) queue.push(c.items.items[i]); }\"");
        w.writeLine("    + \"    else if (c.items && c.items.each) { c.items.each(function(child){ queue.push(child); }); }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  for (var gi = 0; gi < grids.length; gi++) {\"");
        w.writeLine("    + \"    var grid = grids[gi]; var st = grid.getStore();\"");
        w.writeLine("    + \"    for (var ri = 0; ri < st.getCount(); ri++) {\"");
        w.writeLine("    + \"      var rec = st.getAt(ri); if (!rec || !rec.data) continue;\"");
        w.writeLine("    + \"      var dn = rec.data.displayName != null ? String(rec.data.displayName) : '';\"");
        w.writeLine("    + \"      var nn = rec.data.name != null ? String(rec.data.name) : '';\"");
        // Match strictly first, then prefix match (handles trailing ' *' marker for required).
        w.writeLine("    + \"      if (dn === fieldName || nn === fieldName || dn.indexOf(fieldName) === 0 || nn.indexOf(fieldName) === 0 || fieldName.indexOf(dn) === 0 || fieldName.indexOf(nn) === 0) {\"");
        w.writeLine("    + \"        try { rec.set('value', value); } catch (eS) { return 'set-err:' + eS.message; }\"");
        w.writeLine("    + \"        if (grid.view && grid.view.refresh) try { grid.view.refresh(); } catch (eR) {}\"");
        w.writeLine("    + \"        return 'OK';\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'no-match';\"");
        w.writeLine("    + \"} catch (e) { return 'err:' + e.message; }\", fieldName, value);");
        w.openBlock("if (apiRes != null && \"OK\".equals(String.valueOf(apiRes)))");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' via ExtJS API)\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' API path returned: \" + apiRes + \" — falling back to DOM clicks\");");
        w.closeBlock();
        w.openBlock("catch (Exception apiEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' API path threw: \" + apiEx.getMessage());");
        w.closeBlock();

        // === STRATEGY B: DOM clicks fallback (original code below) ===
        // ДЛЯ ТЕКСТ/ДАТА ПОЛЕЙ: rec.set + c.source НЕ достаточно — сервер всё равно
        // считает поле пустым ("Необходимо обязательно указать значения свойств..."). Нужна
        // активация editor'а ячейки и реальный ввод значения, как это делает оператор —
        // ExtJS внутри сам прокинет value в form data.
        w.openBlock("try");
        w.writeLine("String xpStr = xpathLiteral(fieldName);");
        w.writeLine("String xp = \"//div[contains(@class,'x-grid3-cell-inner')][\"");
        w.writeLine("    + \"normalize-space(.) = \" + xpStr");
        w.writeLine("    + \" or contains(normalize-space(.), \" + xpStr + \")\"");
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
        w.writeLine("try { valueCell.click(); Thread.sleep(50); valueCell.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(50);");
        // Найти видимый input редактора
        w.writeLine("java.util.List<WebElement> inputs = driver.findElements(By.cssSelector(\"input.x-form-text:not([type='hidden']), input.x-form-field:not([type='hidden']), textarea.x-form-textarea\"));");
        w.writeLine("WebElement editor = null;");
        w.openBlock("for (WebElement ed : inputs)");
        w.openBlock("try");
        w.openBlock("if (ed.isDisplayed())");
        w.writeLine("editor = ed; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (editor == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = SKIP (no editor input visible)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Очистка + ввод. JS-путь надёжнее sendKeys для ExtJS — пробрасывает change-event так,
        // что ExtJS field фиксирует значение в своей модели и далее в form data.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"var inp = arguments[0]; var v = arguments[1];\"");
        w.writeLine("    + \"inp.focus();\"");
        w.writeLine("    + \"var setter = Object.getOwnPropertyDescriptor(inp.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype, 'value').set;\"");
        w.writeLine("    + \"setter.call(inp, v);\"");
        w.writeLine("    + \"inp.dispatchEvent(new Event('input', {bubbles: true}));\"");
        w.writeLine("    + \"inp.dispatchEvent(new Event('change', {bubbles: true}));\", editor, value);");
        // 100мс — компромисс: меньше провоцирует ExtJS на drop-edit, но не тормозит сильно.
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("Thread.sleep(120);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' via editor input)\");");
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
        // СТРАТЕГИЯ A (по требованию заказчика): без кликов и UI-жестов.
        // Через ExtJS API находим PropertyGrid'у этого поля customEditor.field — это ComboBox.
        // Триггерим store.load() и ждём пока справочник догрузится (до 3с). Затем берём из
        // store случайную запись и ставим rec.set('value', displayValue) в PropertyGrid.
        // Если ничего не нашли (нет combo / пустой store даже после load) — переходим к
        // СТРАТЕГИИ B (клики по ячейке как fallback).
        w.openBlock("try");
        w.writeLine("Object loadInfo = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var name = arguments[0];\"");
        w.writeLine("    + \"  var all = Ext.ComponentMgr && Ext.ComponentMgr.all ? Ext.ComponentMgr.all : (Ext.ComponentManager && Ext.ComponentManager.all ? Ext.ComponentManager.all : null);\"");
        w.writeLine("    + \"  if (!all) return 'no-mgr';\"");
        w.writeLine("    + \"  var items = [];\"");
        w.writeLine("    + \"  if (all.items) items = all.items;\"");
        w.writeLine("    + \"  else if (all.each) all.each(function(c){items.push(c);});\"");
        w.writeLine("    + \"  else for (var k in all) items.push(all[k]);\"");
        w.writeLine("    + \"  for (var i = 0; i < items.length; i++) {\"");
        w.writeLine("    + \"    var c = items[i]; if (!c || !c.rendered || !c.getStore || !c.customEditors) continue;\"");
        w.writeLine("    + \"    if (c.getEl && c.getEl().dom && (c.getEl().dom.offsetWidth === 0 || c.getEl().dom.offsetHeight === 0)) continue;\"");
        w.writeLine("    + \"    var s = c.getStore(); if (!s) continue;\"");
        w.writeLine("    + \"    for (var j = 0; j < s.getCount(); j++) {\"");
        w.writeLine("    + \"      var rec = s.getAt(j); if (!rec || !rec.data) continue; var d = rec.data;\"");
        w.writeLine("    + \"      var dn = d.displayName != null ? String(d.displayName) : '';\"");
        w.writeLine("    + \"      var nn = d.name != null ? String(d.name) : '';\"");
        w.writeLine("    + \"      if (dn === name || nn === name || dn.indexOf(name) === 0 || nn.indexOf(name) === 0) {\"");
        w.writeLine("    + \"        var key = nn || dn; var ed = c.customEditors[key];\"");
        // Если нет под прямым ключом — пробуем по всем ключам найти редактор у которого
        // dataIndex/name/displayName совпадает с искомым name.
        w.writeLine("    + \"        if (!ed) {\"");
        w.writeLine("    + \"          var allKeys = c.customEditors ? Object.keys(c.customEditors) : [];\"");
        w.writeLine("    + \"          for (var ki = 0; ki < allKeys.length; ki++) {\"");
        w.writeLine("    + \"            var k = allKeys[ki]; var maybe = c.customEditors[k];\"");
        w.writeLine("    + \"            if (k === name || k.indexOf(name) === 0 || name.indexOf(k) === 0) { ed = maybe; key = k; break; }\"");
        w.writeLine("    + \"          }\"");
        w.writeLine("    + \"        }\"");
        w.writeLine("    + \"        if (!ed) return 'no-editor:' + key + ' keys=' + (c.customEditors ? Object.keys(c.customEditors).join(',') : 'NONE');\"");
        w.writeLine("    + \"        var cb = ed.field || ed;\"");
        w.writeLine("    + \"        if (!cb || !cb.getStore) return 'no-combo:' + key + ' cls=' + (cb && cb.constructor ? (cb.constructor.name || cb.xtype || 'unknown') : 'null');\"");
        w.writeLine("    + \"        var st = cb.getStore();\"");
        w.writeLine("    + \"        var cnt = st && st.getCount ? st.getCount() : 0;\"");
        // Сохраняем grid+rec+cb в окне для второго JS-вызова (после ожидания загрузки).
        w.writeLine("    + \"        window.__fkCtx = { propGrid: c, propRec: rec, combo: cb, store: st, key: key };\"");
        // Триггерим store.load если пустой
        w.writeLine("    + \"        if (cnt === 0 && st.load) { try { st.load(); } catch (le) {} }\"");
        w.writeLine("    + \"        return 'found:' + key + '/count=' + cnt;\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'no-match';\"");
        w.writeLine("    + \"} catch(e) { return 'err:' + e.message; }\", fieldName);");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' lookup: \" + loadInfo);");
        w.openBlock("if (loadInfo != null && String.valueOf(loadInfo).startsWith(\"found:\"))");
        // Полл-ждём пока store догрузится (до 400мс). Шаг 50мс.
        w.writeLine("long deadline = System.currentTimeMillis() + 400;");
        w.writeLine("int storeCount = 0;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("Object c = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"return (window.__fkCtx && window.__fkCtx.store && window.__fkCtx.store.getCount) ? window.__fkCtx.store.getCount() : 0;\");");
        w.writeLine("storeCount = c == null ? 0 : ((Number) c).intValue();");
        w.openBlock("if (storeCount > 0)");
        w.writeLine("break;");
        w.closeBlock();
        w.writeLine("Thread.sleep(50);");
        w.closeBlock();
        w.openBlock("if (storeCount == 0)");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = FAIL (store stayed empty after load)\");");
        w.writeLine("return;");
        w.closeBlock();
        // Берём случайную запись и ставим в PropertyGrid
        w.writeLine("Object setResult = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  var ctx = window.__fkCtx; if (!ctx) return 'no-ctx';\"");
        w.writeLine("    + \"  var st = ctx.store; var cb = ctx.combo; var rec = ctx.propRec; var grid = ctx.propGrid;\"");
        w.writeLine("    + \"  var n = st.getCount(); if (n === 0) return 'empty-after-load';\"");
        w.writeLine("    + \"  var idx = Math.floor(Math.random() * n);\"");
        w.writeLine("    + \"  var fkRec = st.getAt(idx);\"");
        w.writeLine("    + \"  var vf = cb.valueField || 'id'; var df = cb.displayField || 'name';\"");
        w.writeLine("    + \"  var fkId = fkRec.get ? fkRec.get(vf) : null;\"");
        w.writeLine("    + \"  var fkDisp = fkRec.get ? fkRec.get(df) : '';\"");
        // Ставим displayValue в PropertyGrid record. Если у combo есть rawValue / valueField,
        // PropertyGrid рендерит displayValue, но при сохранении передаёт реальный id.
        w.writeLine("    + \"  try { rec.set('value', fkDisp); } catch (eS1) { try { rec.set('value', fkId); } catch (eS2) {} }\"");
        w.writeLine("    + \"  if (grid.view && grid.view.refresh) try { grid.view.refresh(); } catch (eR) {}\"");
        w.writeLine("    + \"  return 'OK:' + fkDisp + '(id=' + fkId + ')';\"");
        w.writeLine("    + \"} catch(e) { return 'err:' + e.message; }\");");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = OK via ExtJS API (\" + setResult + \")\");");
        w.writeLine("return;");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' ExtJS API didn't find combo — trying DOM clicks fallback\");");
        w.closeBlock();
        w.openBlock("catch (Exception apiEx)");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' API path threw: \" + apiEx.getMessage());");
        w.closeBlock();

        // === СТРАТЕГИЯ B: DOM клики (fallback) ===
        w.openBlock("try");
        w.writeLine("String xpStr = xpathLiteral(fieldName);");
        w.writeLine("String xp = \"//div[contains(@class,'x-grid3-cell-inner')][\"");
        w.writeLine("    + \"normalize-space(.) = \" + xpStr");
        w.writeLine("    + \" or contains(normalize-space(.), \" + xpStr + \")\"");
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
        // ExtJS combo list загружается асинхронно — ждём появления пунктов до 320мс поллингом.
        w.writeLine("java.util.List<WebElement> items = pollDropdownItems(320);");
        w.openBlock("if (items.isEmpty())");
        w.writeLine("java.util.List<WebElement> triggers = driver.findElements(By.cssSelector(");
        w.writeLine("    \"img.x-form-trigger, div.x-form-trigger, .x-form-trigger-wrap img, td.x-trigger-cell img\"));");
        w.openBlock("for (WebElement t : triggers)");
        w.openBlock("try");
        w.openBlock("if (t.isDisplayed())");
        w.writeLine("t.click();");
        w.writeLine("items = pollDropdownItems(250);");
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
        w.writeLine("items = pollDropdownItems(200);");
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
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(pick).click().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { pick.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(150);");
        // ВАЖНО: после клика по пункту ExtJS combobox получил value, но запись в PropertyGrid
        // может не зафиксироваться без явного коммита. ENTER заставляет combobox завершить
        // выбор и закрыть picker, привязывая значение к record'у. Потом TAB сдвигает фокус
        // PropertyGrid'а на следующую строку, чтобы можно было редактировать другие FK.
        w.openBlock("try");
        w.writeLine("org.openqa.selenium.WebElement focused = driver.switchTo().activeElement();");
        w.writeLine("focused.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("focused.sendKeys(org.openqa.selenium.Keys.TAB);");
        w.writeLine("Thread.sleep(150);");
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
        // опрашивая DOM каждые 25мс. ExtJS combo list иногда подгружается store'ом
        // асинхронно.
        w.openBlock("private java.util.List<WebElement> pollDropdownItems(int timeoutMs)");
        w.writeLine("long deadline = System.currentTimeMillis() + timeoutMs;");
        w.openBlock("while (System.currentTimeMillis() < deadline)");
        w.writeLine("java.util.List<WebElement> items = collectVisibleDropdownItems();");
        w.openBlock("if (!items.isEmpty())");
        w.writeLine("return items;");
        w.closeBlock();
        w.openBlock("try");
        w.writeLine("Thread.sleep(25);");
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

        // Runtime helpers that make generated values unique per run (anti-collision).
        writeUniqueValueHelpers(w);

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

    // Runtime helpers emitted into every page object so generated fill-values are UNIQUE per run.
    // Static values (Test_GBS_NAME, INN 123456789012, ...) collide with records left by previous
    // runs and the server rejects the insert on unique-constrained fields — that was the real
    // cause of "запись не сохранилась". See TestDataFactory.generateValueCode.
    private void writeUniqueValueHelpers(JavaFileWriter w) {
        // XPath 1.0 has no way to escape quote characters inside string literals. If a field
        // name contains a ' (apostrophe) we MUST emit it as concat('part', "'", 'rest') instead
        // of '...field name with '...'. Without this, a single quote in a Russian field name
        // (e.g. «Тип'объекта») produces invalid XPath and the element is never found.
        w.writeLine("private static String xpathLiteral(String s)");
        w.writeLine("{");
        w.writeLine("    if (s == null) return \"''\";");
        w.writeLine("    if (s.indexOf('\\'') < 0) return \"'\" + s + \"'\";");
        w.writeLine("    if (s.indexOf('\"') < 0)  return \"\\\"\" + s + \"\\\"\";");
        w.writeLine("    StringBuilder sb = new StringBuilder(\"concat(\");");
        w.writeLine("    String[] parts = s.split(\"'\", -1);");
        w.writeLine("    for (int i = 0; i < parts.length; i++) {");
        w.writeLine("        if (i > 0) sb.append(\", \\\"'\\\", \");");
        w.writeLine("        sb.append(\"'\").append(parts[i]).append(\"'\");");
        w.writeLine("    }");
        w.writeLine("    sb.append(\")\");");
        w.writeLine("    return sb.toString();");
        w.writeLine("}");
        w.writeLine();
        w.writeLine("// --- unique-value helpers (per-run anti-collision) ---");
        w.writeLine("private static final java.util.concurrent.atomic.AtomicLong UNIQ_SEQ =");
        w.writeLine("    new java.util.concurrent.atomic.AtomicLong(System.nanoTime());");
        w.writeLine();
        w.writeLine("/** Short per-run-unique suffix for free-text values (names etc.). */");
        w.openBlock("protected static String uniqSuffix()");
        w.writeLine("return Long.toString(Math.abs(UNIQ_SEQ.incrementAndGet()) % 100000000L);");
        w.closeBlock();
        w.writeLine();
        w.writeLine("/** Mask-conforming value whose DIGIT positions are filled with per-run-unique");
        w.writeLine(" *  digits (letters/any stay deterministic, separators kept literally). Keeps the");
        w.writeLine(" *  field valid (INN length, cadastral №, ...) while avoiding cross-run collisions. */");
        w.openBlock("protected static String uniqDigits(String mask)");
        w.writeLine("String pool = Long.toString(Math.abs(UNIQ_SEQ.incrementAndGet()))");
        w.writeLine("    + Long.toString(Math.abs(System.nanoTime()));");
        w.writeLine("StringBuilder sb = new StringBuilder();");
        w.writeLine("int p = 0;");
        w.openBlock("for (int i = 0; i < mask.length(); i++)");
        w.writeLine("char c = mask.charAt(i);");
        w.openBlock("if (c == '9' || c == '0' || c == '#')");
        w.writeLine("sb.append(pool.charAt(p % pool.length())); p++;");
        w.closeBlock();
        w.openBlock("else if (c == 'a' || c == 'A' || c == 'L' || c == 'X' || c == 'x' || c == '*' || c == '?')");
        w.writeLine("sb.append((char) ('A' + (i % 26)));");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("sb.append(c);");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return sb.toString();");
        w.closeBlock();
        w.writeLine();
    }

    private void writeFilAllRequiredMethod(JavaFileWriter w, List<Property> properties) {
        w.openBlock("public void fillRequiredFields()");
        // KEY: каждое обязательное поле ДОЛЖНО быть заполнено, иначе сервер вернёт ошибку
        // валидации и testCreate провалится. generateValueCode даёт УНИКАЛЬНОЕ per-run значение
        // (см. writeUniqueValueHelpers), чтобы повторные прогоны не конфликтовали по уникальным
        // полям. Для FK/Ref значение null — fillPropertyGridField идёт в DOM-пикер выпадашки.
        for (Property prop : properties) {
            if (!prop.isRequired() || isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String code = TestDataFactory.generateValueCode(prop);
            if ("null".equals(code)) {
                w.writeLine("// " + prop.getName() + " — required FK/Ref, dropdown picker");
                w.writeLine(methodName + "(null);");
            } else {
                w.writeLine(methodName + "(" + code + ");");
            }
        }
        w.closeBlock();
        w.writeLine();
    }

    private void writeFillAllFieldsMethod(JavaFileWriter w, List<Property> properties) {
        // Plain entry point: fill every field.
        w.openBlock("public void fillAllFields()");
        w.writeLine("fillAllFields(java.util.Collections.<String>emptySet());");
        w.closeBlock();
        w.writeLine();
        // Overload that SKIPS the given display names. testCreate stamps a unique marker into
        // one field first, then calls this with that field's name so fillAllFields does NOT
        // overwrite the marker with the default test value (which broke the post-save
        // gridContainsRow check — record saved, but under 'Test_…' instead of the marker).
        w.openBlock("public void fillAllFields(java.util.Set<String> skipDisplayNames)");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String code = TestDataFactory.generateValueCode(prop);
            // SKIP all OPTIONAL fields. Rationale (verified on the GSK stand):
            //  - testCreateOnlyRequired (only required fields) PASSes;
            //  - testCreate adding even one optional field (e.g. КДЗ date) FAILs save.
            // The server enforces semantic invariants the XML doesn't model: date-range checks
            // ('КДЗ >= НДЗ'), FK-to-context validity, ИНН length, etc. We have no XML signal to
            // synthesize a valid optional value, so any optional fill is a save-killing gamble.
            // Net effect: testCreate becomes 'testCreateOnlyRequired + marker' — guaranteed
            // save round-trip. The marker still verifies record persistence end-to-end.
            if (!prop.isRequired()) {
                w.writeLine("// " + prop.getName() + " — optional, skipped to keep save valid");
                continue;
            }
            w.openBlock("if (!skipDisplayNames.contains(\"" + prop.getName() + "\"))");
            if ("null".equals(code)) {
                // Required FK / Ref — null триггерит DOM-пикер выпадашки в fillPropertyGridField.
                w.writeLine("// " + prop.getName() + " — required FK/Ref, dropdown picker");
                w.writeLine(methodName + "(null);");
            } else {
                w.writeLine(methodName + "(" + code + ");");
            }
            w.closeBlock();
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
