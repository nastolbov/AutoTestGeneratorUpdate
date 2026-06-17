package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.common.Transliterator;
import ru.autotestgen.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Генерирует классы Page Object (Selenium) для каждой сущности.
 * Каждый класс страницы содержит WebElement-ы для полей формы и методы
 * для работы с ними в зависимости от типа dmodule.
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

        // Пакет и импорты
        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import org.openqa.selenium.support.ui.WebDriverWait;");
        w.writeLine("import org.openqa.selenium.support.ui.ExpectedConditions;");
        w.writeLine("import java.time.Duration;");
        w.writeLine();

        // Объявление класса
        w.openBlock("public class " + className);
        w.writeLine();
        w.writeLine("private WebDriver driver;");
        w.writeLine("private WebDriverWait wait;");
        // Хранит пары (русское имя поля, значение) для каждого вызова fillX(...).
        // Используется в testCreate для поиска записи в гриде по заполненным значениям.
        // Сбрасывается в fillAllFields / fillRequiredFields.
        w.writeLine("public java.util.LinkedHashMap<String, String> lastFilledValues = new java.util.LinkedHashMap<>();");
        // Уже опробованные значения FK/списков по имени поля: при переподборе (после отказа
        // валидации) берём следующее НЕ пробованное значение, а не случайное.
        w.writeLine("private final java.util.Map<String, java.util.Set<String>> triedDropdownValues = new java.util.HashMap<>();");
        w.writeLine();

        // Конструктор: без PageFactory, так как у PropertyGrid нет именованных инпутов.
        w.openBlock("public " + className + "(WebDriver driver)");
        w.writeLine("this.driver = driver;");
        w.writeLine("this.wait = new WebDriverWait(driver, Duration.ofSeconds(3));");
        w.closeBlock();
        w.writeLine();

        // fillPropertyGridField: заполняет поле PropertyGrid по русскому имени.
        // Сначала ExtJS API (startEditing), затем запасной вариант — DOM-клик ячейки
        // и inline-редактор. Логирует результат каждой попытки.
        w.openBlock("private void fillPropertyGridField(String fieldName, String value)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(80));");
        // value == null означает FK/Directory/Ref-поле: сразу открываем DOM-пикер выпадающего списка.
        w.openBlock("if (value == null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' — FK field, opening dropdown\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("fillFKViaDropdown(fieldName);");
        w.writeLine("return;");
        w.closeBlock();
        // Открываем редактор через ExtJS API grid.startEditing(rowIndex, 1):
        // ExtJS сам найдёт нужную ячейку и откроет правильный редактор.
        w.openBlock("try");
        w.writeLine("Object startResult = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try {\"");
        w.writeLine("    + \"  if (typeof Ext === 'undefined') return 'no-ext';\"");
        w.writeLine("    + \"  var name = arguments[0];\"");
        w.writeLine("    + \"  var mgr = Ext.ComponentMgr || Ext.ComponentManager;\"");
        w.writeLine("    + \"  if (!mgr || !mgr.all) return 'no-mgr';\"");
        w.writeLine("    + \"  var allItems = [];\"");
        w.writeLine("    + \"  if (mgr.all.items) allItems = mgr.all.items;\"");
        w.writeLine("    + \"  else if (mgr.all.each) mgr.all.each(function(c){allItems.push(c);});\"");
        w.writeLine("    + \"  else for (var k in mgr.all) allItems.push(mgr.all[k]);\"");
        // Ограничиваем поиск активным окном, иначе startEditing может попасть в чужой
        // PropertyGrid (например, в параметры поиска вместо карточки сведений).
        w.writeLine("    + \"  var activeWin = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null;\"");
        w.writeLine("    + \"  var activeDom = (activeWin && activeWin.getEl) ? (activeWin.getEl().dom || activeWin.getEl()) : null;\"");
        w.writeLine("    + \"  var items = [];\"");
        w.writeLine("    + \"  if (activeDom) {\"");
        w.writeLine("    + \"    for (var i = 0; i < allItems.length; i++) {\"");
        w.writeLine("    + \"      var c = allItems[i]; if (!c || !c.getEl) continue;\"");
        w.writeLine("    + \"      try { var dom = c.getEl().dom; if (dom && activeDom.contains(dom)) items.push(c); } catch(e) {}\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  if (items.length === 0) items = allItems;\"");
        w.writeLine("    + \"  for (var i = 0; i < items.length; i++) {\"");
        w.writeLine("    + \"    var c = items[i];\"");
        w.writeLine("    + \"    if (!c || !c.rendered || !c.getStore || !c.customEditors || !c.startEditing) continue;\"");
        w.writeLine("    + \"    try { if (c.getEl().dom.offsetWidth <= 0 || c.getEl().dom.offsetHeight <= 0) continue; } catch(e) { continue; }\"");
        w.writeLine("    + \"    var s = c.getStore(); if (!s) continue;\"");
        w.writeLine("    + \"    for (var j = 0; j < s.getCount(); j++) {\"");
        w.writeLine("    + \"      var rec = s.getAt(j); if (!rec || !rec.data) continue;\"");
        w.writeLine("    + \"      var dn = rec.data.displayName != null ? String(rec.data.displayName) : '';\"");
        w.writeLine("    + \"      var nn = rec.data.name != null ? String(rec.data.name) : '';\"");
        // Убираем хвостовую звёздочку '*' (признак обязательности) и пробелы:
        // на стенде displayName может приходить со звёздочкой, а в модели её нет.
        w.writeLine("    + \"      var dnClean = dn.replace(/\\\\s*\\\\*\\\\s*$/, '').trim();\"");
        w.writeLine("    + \"      var nnClean = nn.replace(/\\\\s*\\\\*\\\\s*$/, '').trim();\"");
        w.writeLine("    + \"      if (dnClean === name || nnClean === name) {\"");
        w.writeLine("    + \"        try { c.startEditing(j, 1); } catch(e) { return 'err-start:' + e.message; }\"");
        w.writeLine("    + \"        return 'OK:grid=' + (c.id || '?') + '/row=' + j + '/dn=' + dn + '/nn=' + nn;\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        // Запасной вариант: если в активном окне не нашли — обходим все видимые PropertyGrid.
        // Активное окно может не покрывать всю карточку (например, грид рендерится в отдельном слое).
        w.writeLine("    + \"  for (var i = 0; i < allItems.length; i++) {\"");
        w.writeLine("    + \"    var c = allItems[i];\"");
        w.writeLine("    + \"    if (!c || !c.rendered || !c.getStore || !c.customEditors || !c.startEditing) continue;\"");
        w.writeLine("    + \"    try { if (c.getEl().dom.offsetWidth <= 0 || c.getEl().dom.offsetHeight <= 0) continue; } catch(e) { continue; }\"");
        w.writeLine("    + \"    var s = c.getStore(); if (!s) continue;\"");
        w.writeLine("    + \"    for (var j = 0; j < s.getCount(); j++) {\"");
        w.writeLine("    + \"      var rec = s.getAt(j); if (!rec || !rec.data) continue;\"");
        w.writeLine("    + \"      var dn = rec.data.displayName != null ? String(rec.data.displayName) : '';\"");
        w.writeLine("    + \"      var nn = rec.data.name != null ? String(rec.data.name) : '';\"");
        w.writeLine("    + \"      var dnClean = dn.replace(/\\\\s*\\\\*\\\\s*$/, '').trim();\"");
        w.writeLine("    + \"      var nnClean = nn.replace(/\\\\s*\\\\*\\\\s*$/, '').trim();\"");
        w.writeLine("    + \"      if (dnClean === name || nnClean === name) {\"");
        w.writeLine("    + \"        try { c.startEditing(j, 1); } catch(e) { return 'err-start-fb:' + e.message; }\"");
        w.writeLine("    + \"        return 'OK-fallback:grid=' + (c.id || '?') + '/row=' + j + '/dn=' + dn + '/nn=' + nn;\"");
        w.writeLine("    + \"      }\"");
        w.writeLine("    + \"    }\"");
        w.writeLine("    + \"  }\"");
        w.writeLine("    + \"  return 'no-match';\"");
        w.writeLine("    + \"} catch(e) { return 'err:' + e.message; }\", fieldName);");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing result: \" + startResult);");
        w.openBlock("if (startResult != null && (String.valueOf(startResult).startsWith(\"OK:\") || String.valueOf(startResult).startsWith(\"OK-fallback:\")))");
        w.writeLine("Thread.sleep(250);");
        w.writeLine("WebElement startedEditor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"return document.activeElement;\");");
        w.openBlock("if (startedEditor != null)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor через startEditing: id=\" + startedEditor.getAttribute(\"id\") + \" class=\" + startedEditor.getAttribute(\"class\"));");
        // Сначала очищаем поле, потом заполняем (для update, иначе новое значение
        // накладывается на старое). Очистка через JS (value=''), значение вводится
        // с клавиатуры (sendKeys). Ctrl+A — страховка после JS-очистки.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", startedEditor);");
        w.writeLine("Thread.sleep(60);");
        w.writeLine("startedEditor.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, \"a\"));");
        w.writeLine("Thread.sleep(60);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("startedEditor.sendKeys(value);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("startedEditor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(300);");
        // ENTER подтверждает ячейку, но keynav ExtJS PropertyGrid сразу открывает редактор
        // следующей строки, который может записаться поверх обязательного поля.
        // Поэтому гасим любой активный редактор гридов (stopEditing(false)) и делаем blur.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { if (typeof Ext==='undefined') return;\"");
        w.writeLine("    + \" var gs=[]; if (Ext.ComponentQuery && Ext.ComponentQuery.query) gs=Ext.ComponentQuery.query('propertygrid,editorgrid,grid');\"");
        w.writeLine("    + \" else if (Ext.ComponentMgr && Ext.ComponentMgr.all){ var a=Ext.ComponentMgr.all.items||[]; for(var i=0;i<a.length;i++){var c=a[i]; if(c&&c.stopEditing) gs.push(c);} }\"");
        w.writeLine("    + \" for (var i=0;i<gs.length;i++){ try{ if(gs[i].stopEditing) gs[i].stopEditing(false); }catch(e){} }\"");
        w.writeLine("    + \" try{ if(document.activeElement && document.activeElement.blur) document.activeElement.blur(); }catch(e){}\"");
        w.writeLine("    + \"} catch(e){}\");");
        w.writeLine("Thread.sleep(120);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' = OK ('\" + value + \"' via startEditing+clear+sendKeys+ENTER+stopEditing)\");");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(2));");
        w.writeLine("return;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing не сработал — пробуем DOM click fallback\");");
        w.closeBlock();
        w.openBlock("catch (Exception startEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' startEditing threw: \" + startEx.getMessage());");
        w.closeBlock();
        // Запасной вариант через DOM: клик по ячейке + sendKeys.
        w.openBlock("try");
        // Esc не используем: в ExtJS PropertyGrid он отменяет inline-add и удаляет
        // последнюю добавленную строку. Редактор предыдущего fillX уже закрыт по ENTER.
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
        // Прокручиваем ячейку в центр окна, иначе верхние ячейки уезжают за область
        // видимости и клик не активирует редактор. scrollIntoView({block:'center'})
        // держит ячейку посередине, где её не перекроет заголовок диалога.
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
        // Активируем редактор через click + pause + click (тот же жест, что и для FK).
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
        // Находим видимый input редактора:
        // 1) сначала document.activeElement (то, что в фокусе после клика по ячейке);
        // 2) если не подходит — ищем не readonly INPUT в области координат ячейки.
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
        // Запасной вариант: общий поиск с отсевом readonly и x-combo-noedit.
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
        // Очистка и ввод с клавиатуры. ExtJS читает значение из input только если оно
        // пришло через события клавиатуры, JS-setter игнорируется. Поэтому очистка,
        // посимвольный sendKeys(value) и ENTER, чтобы ExtJS зафиксировал значение.
        w.openBlock("try");
        w.writeLine("editor.click();");
        w.writeLine("Thread.sleep(150);");
        // Лог содержимого до ввода — что в редакторе изначально (от предыдущего fill).
        w.openBlock("try");
        w.writeLine("String beforeVal = editor.getAttribute(\"value\");");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' editor.value BEFORE = '\" + (beforeVal == null ? \"\" : beforeVal) + \"'\");");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Одним chord: Ctrl+A выделяет всё, затем sendKeys(value) перетирает выделение.
        w.openBlock("try");
        // Сначала очистка (JS value=''+input), затем ввод с клавиатуры — без наложения старого значения.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
        w.writeLine("Thread.sleep(60);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, \"a\"));");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(value);");
        w.writeLine("Thread.sleep(150);");
        w.closeBlock();
        w.openBlock("catch (Exception keyEx)");
        w.writeLine("System.out.println(\"  [fill] '\" + fieldName + \"' sendKeys threw: \" + keyEx.getMessage());");
        w.closeBlock();
        // Лог содержимого после ввода, до ENTER.
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
        // Проверка: читаем обратно текст value-ячейки. Если в ней нет нашего value —
        // sendKeys ушёл не туда (например, в чужой редактор), и поле осталось пустым.
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


        // fillFKViaDropdown: для FK/Ref-полей. Находит ячейку value, делает dblclick, ждёт
        // и ищет выпадающий список (.x-combo-list-item / .x-boundlist-item). Если списка
        // нет — пробует кликнуть видимую кнопку-триггер (стрелку справа от инпута). Когда
        // пункты появились — выбирает случайный и кликает по нему. Ничего не печатает,
        // только выбор из готового списка справочника.
        w.openBlock("private boolean fillFKViaDropdown(String fieldName)");
        w.writeLine("driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(300));");
        // selected=true, если удалось выбрать НОВОЕ (не пробованное ранее) значение списка.
        // Используется при переподборе после отказа валидации (repickDropdown).
        w.writeLine("boolean selected = false;");
        w.writeLine("java.util.Set<String> triedHere = triedDropdownValues.computeIfAbsent(fieldName, k -> new java.util.HashSet<>());");
        // Только реальный клик: ищем видимую ячейку поля в текущем активном табе
        // и делаем dblclick. ExtJS откроет пикер / выпадающий список, выбираем
        // случайный элемент.
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' — открываем пикер реальным кликом\");");

        // Реальный клик по ячейке value FK-поля.
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
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> rows = nameCell.findElements(By.xpath(\"ancestor::tr\"));");
        w.openBlock("if (rows.isEmpty())");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = SKIP (no ancestor tr)\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("java.util.List<WebElement> cells = rows.get(0).findElements(By.cssSelector(\"td\"));");
        w.openBlock("if (cells.size() < 2)");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = SKIP (row has \" + cells.size() + \" cells)\");");
        w.writeLine("return false;");
        w.closeBlock();
        w.writeLine("WebElement valueCell = cells.get(1);");
        // Прокручиваем ячейку в зону видимости — иначе верхние FK (Включён, Тип)
        // уезжают за пределы экрана после прокрутки формы и dblclick по ним не работает.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});\", valueCell);");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Запоминаем id активного окна до клика: если пикер не открылся, Ext.WindowMgr
        // вернёт тот же диалог Сведения, и мы сделаем dblclick по его property-row.
        w.writeLine("String activeBefore = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var aw = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null; return aw ? aw.id : null; } catch (e) { return null; }\");");
        // Имитация реального жеста: первый click выделяет PropertyGrid-row, второй (через паузу)
        // активирует inline-редактор на выделенной строке, что у combo E3Core запускает
        // авто-открытие выпадающего списка. ExtJS doubleClick из Actions склеивал два клика
        // в один dblclick, который на этом стенде сбрасывал редактор, и список не открывался.
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
        // Список combo ExtJS загружается асинхронно — опрашиваем DOM до появления пунктов, до 2.5с.
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
        // F4 на активном элементе — стандартное раскрытие combobox ExtJS. Иногда нужно после
        // dblclick, если редактор открылся, но список не подтянулся автоматически.
        w.openBlock("if (items.isEmpty())");
        w.openBlock("try");
        w.writeLine("driver.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.F4);");
        w.writeLine("items = pollDropdownItems(1500);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        // E3Core: FK-поля часто открывают отдельное модальное окно-пикер с гридом записей.
        // Берём активное окно только если его id отличается от activeBefore — иначе
        // вернёмся в тот же диалог редактирования и сделаем dblclick по его property-row.
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
        w.writeLine("triedHere.add(rowText);");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = OK picker-window ('\" + rowText + \"')\");");
        w.writeLine("return true;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (items.isEmpty())");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' = FAIL (no dropdown, no NEW picker window; activeBefore=\" + activeBefore + \")\");");
        w.writeLine("dumpDropdownDiagnostic();");
        w.writeLine("return false;");
        w.closeBlock();
        // Берём первый НЕ пробованный пункт (при переподборе после отказа валидации это даст
        // следующее значение, а не то же самое). Если все варианты уже пробованы — случайный.
        w.writeLine("WebElement pick = null;");
        w.writeLine("String pickedText = \"\";");
        w.openBlock("for (WebElement it : items)");
        w.writeLine("String t = it.getText() == null ? \"\" : it.getText().trim();");
        w.openBlock("if (!triedHere.contains(t))");
        w.writeLine("pick = it; pickedText = t; selected = true; break;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (pick == null)");
        w.writeLine("pick = items.get(new java.util.Random().nextInt(items.size()));");
        w.writeLine("pickedText = pick.getText() == null ? \"\" : pick.getText().trim();");
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' все \" + items.size() + \" вариантов уже пробованы — берём случайный\");");
        w.closeBlock();
        w.writeLine("triedHere.add(pickedText);");
        // Пауза 1с перед кликом: список уже в DOM, но ExtJS ещё привязывает обработчики.
        // Без паузы клик иногда проходил впустую и значение не выбиралось.
        w.writeLine("System.out.println(\"  [fill-FK] '\" + fieldName + \"' ждём 1с и кликаем элемент '\" + pickedText + \"'\");");
        w.writeLine("Thread.sleep(1000);");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(pick).click().perform();");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("try { pick.click(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine("Thread.sleep(300);");
        // После клика по пункту combobox ExtJS получил value, но запись в PropertyGrid
        // может не зафиксироваться без явного подтверждения. ENTER завершает выбор и
        // закрывает пикер, привязывая значение к записи. TAB не используем: он уводит
        // фокус на следующую строку PropertyGrid, и та входит в режим редактирования.
        w.openBlock("try");
        w.writeLine("org.openqa.selenium.WebElement focused = driver.switchTo().activeElement();");
        w.writeLine("focused.sendKeys(org.openqa.selenium.Keys.ENTER);");
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
        w.writeLine("return selected;");
        w.closeBlock();
        w.writeLine();

        // repickDropdown: повторно открыть список поля и выбрать СЛЕДУЮЩЕЕ не пробованное значение.
        // Возвращает true, если удалось выбрать новое значение (есть смысл повторить сохранение).
        // Используется в testCreate, когда сервер отклонил save с popup'ом по полю-списку
        // (например «Тип ГСК/ОГСК», где некоторые значения создавать нельзя).
        w.openBlock("public boolean repickDropdown(String fieldName)");
        w.writeLine("System.out.println(\"  [repick] переподбираем значение списка '\" + fieldName + \"'\");");
        w.writeLine("return fillFKViaDropdown(fieldName);");
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

        // pollDropdownItems: ждёт появления выпадающего списка (до timeoutMs мс),
        // опрашивая DOM каждые 200мс. Список combo ExtJS подгружается store асинхронно,
        // поэтому фиксированной паузы 500мс не хватало.
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

        // dumpDropdownDiagnostic: при сбое fillFKViaDropdown печатает, что реально видно
        // в DOM, чтобы понять, под какой селектор / класс открывается список на этом стенде.
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

        // Методы ввода для каждого свойства по типу dmodule
        for (Property prop : displayProperties) {
            if (isSystemField(prop)) continue;
            writeInputMethod(w, prop);
        }

        // Метод заполнения всех обязательных полей
        writeFilAllRequiredMethod(w, displayProperties);

        // Метод заполнения всех полей тестовыми данными
        writeFillAllFieldsMethod(w, displayProperties);

        // Методы кнопок действий из модификаторов (ищем в любой PropertyGroup)
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

        // Методы проверки наличия полей
        writeCheckFieldsPresentMethod(w, displayProperties);

        // Метод проверки отображения поля — пробует PropertyGrid и стандартные селекторы
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

        // Метод получения значения поля — стандартные селекторы, затем PropertyGrid по отображаемому имени
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

        // Метод проверки видимости ошибок валидации
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

        // Метод проверки ошибки валидации у конкретного поля
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

        // Метод подсчёта строк в гриде/таблице
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

        // Метод очистки формы
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

        w.closeBlock(); // конец класса

        w.writeToFile(dir, className + ".java");
    }

    private void writeInputMethod(JavaFileWriter w, Property prop) {
        String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
        String displayName = prop.getName(); // русское имя для поиска в PropertyGrid

        // Все типы полей используют fillPropertyGridField с русским отображаемым именем
        w.openBlock("public void " + methodName + "(String value)");
        w.writeLine("fillPropertyGridField(\"" + displayName + "\", value);");
        // Записываем фактически вписанное значение (не null: пустые/FK-пикеры нельзя
        // сравнивать с гридом) в lastFilledValues для последующего поиска записи
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
        // Считаем, сколько обязательных полей ожидаем заполнить — потом сравним с реально
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
            } else if (prop.getMask() != null && !prop.getMask().isEmpty()) {
                // masked-поле: набираем только символы плейсхолдеров (маска подставит ч/мин/разделители)
                w.writeLine(methodName + "(\"" + TestDataFactory.maskTypingValue(prop.getMask()) + "\");");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
            }
        }
        w.writeLine("System.out.println(\"  [fillRequiredFields] заполнено \" + lastFilledValues.size() + \" из ожидаемых \" + expectedRequired + \" required-полей (FK-поля считаются как ожидаемые но не попадают в lastFilledValues)\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeFillAllFieldsMethod(JavaFileWriter w, List<Property> properties) {
        // fillAllFields() заполняет всё. fillAllFieldsExcept(skip) пропускает поле
        // с переданным displayName (нужно для testCreate: маркер заменяет это заполнение,
        // иначе sendKeys приклеивает маркер к значению поля).
        w.openBlock("public void fillAllFields()");
        w.writeLine("fillAllFieldsExcept(null);");
        w.closeBlock();
        w.writeLine();
        w.openBlock("public void fillAllFieldsExcept(String skipDisplayName)");
        w.writeLine("lastFilledValues.clear();");
        w.writeLine("String __uniq = String.valueOf(System.nanoTime());");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            String methodName = "fill" + Transliterator.toClassName(prop.getAttrName());
            String displayName = prop.getName().replace("\\", "\\\\").replace("\"", "\\\"");
            String value = TestDataFactory.generateValue(prop);
            w.openBlock("if (skipDisplayName == null || !skipDisplayName.equals(\"" + displayName + "\"))");
            if (value == null) {
                w.writeLine("// " + prop.getName() + " — FK/Ref, dropdown picker");
                w.writeLine(methodName + "(null);");
            } else if (prop.getAttrType() == AttrType.STRING && (prop.getMask() == null || prop.getMask().isEmpty())) {
                w.writeLine(methodName + "(\"" + value + "_\" + __uniq);");
            } else if (prop.getMask() != null && !prop.getMask().isEmpty()) {
                // masked-поле: набираем только символы плейсхолдеров (маска подставит ч/мин/разделители)
                w.writeLine(methodName + "(\"" + TestDataFactory.maskTypingValue(prop.getMask()) + "\");");
            } else {
                w.writeLine(methodName + "(\"" + value + "\");");
            }
            w.closeBlock();
        }
        w.writeLine("System.out.println(\"  [fillAllFields] заполнено \" + lastFilledValues.size() + \" полей (skip=\" + skipDisplayName + \")\");");
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
        // Серверные авто-заполняемые поля (Дата изменения, Оператор, Дата создания) —
        // стенд E3Core заполняет их сам. Если автотест туда пишет, форма отвергает create.
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
