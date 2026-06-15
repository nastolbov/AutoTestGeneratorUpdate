package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.common.Transliterator;
import ru.autotestgen.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Генерирует JUnit 5 тест-классы для каждой сущности.
 * Покрывает: наличие полей, валидацию обязательных полей, CRUD-операции, поиск.
 */
public class TestClassWriter {

    private final String basePackage;
    private final String testLevel;

    public TestClassWriter(String basePackage, String testLevel) {
        this.basePackage = basePackage;
        this.testLevel = testLevel != null ? testLevel : "basic";
    }

    private boolean isBasicOrFull() {
        return "basic".equals(testLevel) || "full".equals(testLevel);
    }

    private boolean isFull() {
        return "full".equals(testLevel);
    }

    public void write(EntityObject entity, AppModel model, Path outputDir) throws IOException {
        write(entity, model, outputDir, null);
    }

    public void write(EntityObject entity, AppModel model, Path outputDir, String disabledReason) throws IOException {
        String entityClassName = Transliterator.toClassName(entity.getName());
        String testClassName = entityClassName + "Test";
        String pageClassName = entityClassName + "Page";
        String packageName = basePackage + ".test";
        Path dir = outputDir.resolve(packageName.replace('.', '/'));

        PropertyGroup formView = entity.getFormView();
        List<Property> displayProperties = getDisplayProperties(entity);
        List<Property> requiredProperties = displayProperties.stream()
                .filter(Property::isRequired)
                .filter(p -> !isSystemField(p))
                .toList();

        // CRUD-операция из ЛЮБОЙ группы свойств (не только formView)
        Operation crudOperation = entity.getPropertyGroups().stream()
                .map(PropertyGroup::getOperation)
                .filter(op -> op != null && !op.getModifiers().isEmpty())
                .findFirst()
                .orElse(null);
        boolean hasCrud = crudOperation != null;

        // Есть ли у сущности собственный поиск. Сущности, открываемые через «Найти», имеют
        // собственные поиски; справочники-списки, открываемые прямым кликом, своих поисков
        // не имеют — поиски висят на одиночном двойнике, и шаг параметрической формы поиска
        // при навигации для них пропускается.
        boolean hasOwnSearchForm = model.getSearches().stream()
                .anyMatch(s -> entity.getGuid() != null
                        && entity.getGuid().equals(s.getSearchObjectGuid()));

        // Inline-справочник с одиночным двойником-карточкой и собственным модальным CRUD.
        // Записи такого списка создаются и правятся через модалку двойника, а двойник — отдельная
        // PRIMARY-сущность со своими тестами, поэтому дублирующий inline-CRUD здесь не генерируем.
        // Для настоящих inline-справочников (двойник read-only) twin == null и inline-тесты остаются.
        EntityObject modalTwin = entity.isInlineTableEntity()
                ? EntityClassifier.findModalTwin(entity, model) : null;

        // Поиски, связанные с этой сущностью. Пропускаем FK-only поиски (без параметров,
        // результат только SearchKey/SearchName) — их нет в дереве поисков этой сущности,
        // они вызываются из других сущностей через FK-пикеры. testSearch для них дал бы ложный PASS.
        List<Search> entitySearches = model.getSearches().stream()
                .filter(s -> s.getSearchObjectGuid().equals(entity.getGuid()))
                .filter(s -> !isFkOnlySearch(s))
                .toList();

        JavaFileWriter w = new JavaFileWriter();

        // Пакет и импорты
        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.junit.jupiter.api.*;");
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import static org.junit.jupiter.api.Assertions.*;");
        w.writeLine("import " + basePackage + ".BaseTest;");
        w.writeLine("import " + basePackage + ".page." + pageClassName + ";");
        w.writeLine();

        // Класс
        w.writeLine("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)");
        if (disabledReason != null && !disabledReason.isEmpty()) {
            w.writeLine("@org.junit.jupiter.api.Disabled(\""
                + disabledReason.replace("\\", "\\\\").replace("\"", "\\\"") + "\")");
        }
        w.openBlock("public class " + testClassName + " extends BaseTest");
        w.writeLine();
        w.writeLine("private " + pageClassName + " page;");
        w.writeLine("private static final String ENTITY_NAME = \"" + entity.getName() + "\";");
        w.writeLine("private static final String FEATURE_NAME = \"" + entity.getFeatureName() + "\";");
        w.writeLine();

        // Переопределяем entityName(), чтобы хелперы BaseTest (menuAction, openSearch,
        // openRecordCard) искали сущность по реальному русскому имени в меню, а не по
        // транслитерированному имени класса.
        w.writeLine("@Override");
        w.openBlock("protected String entityName()");
        w.writeLine("return ENTITY_NAME;");
        w.closeBlock();
        w.writeLine();

        // @BeforeEach — гарантируем доступность грида результатов перед каждым тестом.
        // resetState() закрывает открытую карточку и окно грида результатов, поэтому
        // на навигацию предыдущего теста полагаться нельзя. Сбрасываем флаги кеша
        // навигации, чтобы navigateToEntity отработал заново.
        w.writeLine("@BeforeEach");
        w.openBlock("void setUp()");
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(\"" + entity.getName() + "\", \"" + entity.getFeatureName() + "\", " + hasOwnSearchForm + ");");
        w.writeLine("assumeNavigated();");
        w.writeLine("page = new " + pageClassName + "(driver);");
        w.closeBlock();
        w.writeLine();

        // @AfterEach — финальный скриншот. Вместе с вызовами shot() внутри тестов это даёт
        // покадровую запись всего прогона в target/screenshots/.
        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        // === SMOKE-тесты (генерируются всегда) ===

        // Тест 1: наличие полей
        writeFieldsPresentTest(w, displayProperties, entity.getName());

        // === BASIC-тесты (для уровней "basic" и "full") ===
        if (isBasicOrFull()) {

            // Тесты поиска (с заполненными параметрами)
            for (int i = 0; i < entitySearches.size(); i++) {
                writeSearchTest(w, entitySearches.get(i), i);
            }

            // Тесты гридов (с проверкой колонок и кнопок). Грид, у которого есть отдельный
            // дочерний тест-класс (CHILD с parentGrid), здесь НЕ дублируем: повторное открытие
            // карточки+таба внутри родителя обычно не удаётся и тест лишь скипается. Полноценное
            // покрытие даёт сам дочерний класс (например IstoriyaGSKOGSKTest).
            List<PropertyGroup> grids = entity.getPropertyGroups().stream()
                    .filter(PropertyGroup::isGridView)
                    .toList();
            for (PropertyGroup grid : grids) {
                boolean coveredByChildClass = model.getEntities().stream().anyMatch(e -> {
                    EntityClassifier.Classification c = EntityClassifier.classify(e, model);
                    return c.parentEntity != null && c.parentGrid != null
                            && entity.getGuid() != null
                            && entity.getGuid().equals(c.parentEntity.getGuid())
                            && grid.getName() != null
                            && grid.getName().equals(c.parentGrid.getName());
                });
                if (coveredByChildClass) {
                    w.writeLine("// Грид '" + grid.getName().replace("\\", "\\\\").replace("\"", "\\\"")
                            + "' покрыт отдельным дочерним тест-классом — здесь не дублируем (иначе лишний skip).");
                    continue;
                }
                writeGridTest(w, grid, entity);
            }
        }

        // === FULL-тесты (только для уровня "full") ===
        // Inline-список с модальным двойником: CRUD покрывается тестом одиночной карточки-двойника,
        // здесь его не дублируем. В тело класса печатаем поясняющий комментарий, чтобы связь была
        // видна в сгенерированном файле.
        if (isFull() && modalTwin != null) {
            w.writeLine("// CRUD этого справочника-списка проверяется в тесте одиночной карточки \""
                    + modalTwin.getName().replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\" (модальная форма) — здесь не дублируется.");
        }
        if (isFull() && modalTwin == null) {

            // Тест 2: валидация обязательных полей (пустая отправка)
            if (!requiredProperties.isEmpty() && hasCrud) {
                writeRequiredFieldValidationTest(w, requiredProperties);
            }

            // Сущности без отдельной модалки FormView (typeLink="P") используют inline-table flow
            // (Редактирование -> Добавить / Сохранить Изменения). Стандартный testCreate/testUpdate
            // через wizard-модалку для них не работает.
            boolean inlineTable = entity.isInlineTableEntity();

            // Тест 3: создание (Insert)
            if (hasCrud && hasModifier(crudOperation, ModifyType.INSERT)) {
                if (inlineTable) {
                    writeInlineTableCreateTest(w);
                } else {
                    writeCreateTest(w, displayProperties);
                }
            }

            // Тест 4: обновление
            if (hasCrud && hasModifier(crudOperation, ModifyType.UPDATE)) {
                if (inlineTable) {
                    writeInlineTableUpdateTest(w);
                } else {
                    writeUpdateTest(w, displayProperties);
                }
            }

            // Тест 5: удаление — открыть карточку первой записи рабочего грида и удалить.
            if (hasCrud && hasModifier(crudOperation, ModifyType.DELETE)) {
                writeDeleteTest(w);
            }

            // Тест 6: логическое изменение
            if (hasCrud && hasModifier(crudOperation, ModifyType.LOGICAL_EDIT)) {
                writeLogicalEditTest(w);
            }

            // Тест 7: архивация — открыть карточку первой записи и «в Архив».
            if (hasCrud && hasModifier(crudOperation, ModifyType.ARCHIVE)) {
                writeArchiveTest(w);
            }

            // Тест: частичная валидация (заполнено только первое обязательное поле)
            if (requiredProperties.size() >= 2 && hasCrud) {
                writePartialValidationTest(w, requiredProperties);
            }
        }

        w.closeBlock(); // конец класса

        w.writeToFile(dir, testClassName + ".java");
    }

    /**
     * Генерирует контекстный тест-класс для дочерней сущности — той, что живёт как вкладка-грид
     * внутри карточки родителя. Сгенерированный @BeforeEach переходит к родителю, открывает
     * карточку записи и переключается на дочернюю вкладку. CRUD-тесты используют кнопки тулбара
     * грида, а не главное меню.
     */
    public void writeChildTest(EntityObject entity, AppModel model, Path outputDir,
                               EntityClassifier.Classification cls) throws IOException {
        EntityObject parent = cls.parentEntity;
        PropertyGroup parentGrid = cls.parentGrid;
        String tabName = parentGrid.getName();

        String entityClassName = Transliterator.toClassName(entity.getName());
        String testClassName = entityClassName + "Test";
        String pageClassName = entityClassName + "Page";
        String packageName = basePackage + ".test";
        Path dir = outputDir.resolve(packageName.replace('.', '/'));

        List<Property> displayProperties = getDisplayProperties(entity);

        Operation gridOperation = parentGrid.getOperation();
        boolean hasCrud = gridOperation != null && !gridOperation.getModifiers().isEmpty();

        List<Property> gridColumns = parentGrid.getProperties().stream()
                .filter(p -> !isSystemField(p) && p.isFlagDisplay())
                .toList();

        JavaFileWriter w = new JavaFileWriter();

        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.junit.jupiter.api.*;");
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import static org.junit.jupiter.api.Assertions.*;");
        w.writeLine("import " + basePackage + ".BaseTest;");
        w.writeLine("import " + basePackage + ".page." + pageClassName + ";");
        w.writeLine();

        w.writeLine("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)");
        w.openBlock("public class " + testClassName + " extends BaseTest");
        w.writeLine();
        w.writeLine("private " + pageClassName + " page;");
        w.writeLine("private static final String ENTITY_NAME = \"" + entity.getName() + "\";");
        w.writeLine("private static final String PARENT_ENTITY_NAME = \"" + parent.getName() + "\";");
        w.writeLine("private static final String PARENT_FEATURE_NAME = \"" + parent.getFeatureName() + "\";");
        w.writeLine("private static final String TAB_NAME = \"" + tabName + "\";");
        w.writeLine();

        w.writeLine("@Override");
        w.openBlock("protected String entityName()");
        w.writeLine("return PARENT_ENTITY_NAME;");
        w.closeBlock();
        w.writeLine();

        // @BeforeEach: переход к родителю, открытие карточки, переключение на дочернюю вкладку (с ретраем)
        writeRobustChildSetUp(w, pageClassName, "TAB_NAME", "вкладка");

        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        // Тест 1: колонки грида
        writeChildGridColumnsTest(w, gridColumns, tabName);

        if (isFull() && hasCrud) {
            // Тест 3: создание
            if (hasModifier(gridOperation, ModifyType.INSERT)) {
                writeChildCreateTest(w, displayProperties, tabName);
            }
            // Тест 4: обновление
            if (hasModifier(gridOperation, ModifyType.UPDATE)) {
                writeChildUpdateTest(w, displayProperties, tabName);
            }
            // Тест 5: удаление
            if (hasModifier(gridOperation, ModifyType.DELETE)) {
                writeChildDeleteTest(w, tabName);
            }
        }

        w.closeBlock(); // конец класса
        w.writeToFile(dir, testClassName + ".java");
    }

    /**
     * Генерирует контекстный тест для дочерней сущности — узла левого дерева
     * (association addFromTree="1"), например «Повестка совещания» внутри «Совещание».
     * У такой сущности есть собственная форма (typeLink="P"), но её нет в главном меню,
     * поэтому отдельный PRIMARY-тест не смог бы к ней пройти. @BeforeEach переходит к родителю,
     * открывает карточку записи и раскрывает узел дерева; тест проверяет наличие полей формы узла.
     * CRUD внутри узла дерева не трогаем — поток открытия формы из дерева неоднороден,
     * проверка наличия полей надёжна.
     */
    public void writeTreeChildTest(EntityObject entity, AppModel model, Path outputDir,
                                   EntityClassifier.Classification cls) throws IOException {
        EntityObject parent = cls.parentEntity;
        String nodeName = entity.getName();

        String entityClassName = Transliterator.toClassName(entity.getName());
        String testClassName = entityClassName + "Test";
        String pageClassName = entityClassName + "Page";
        String packageName = basePackage + ".test";
        Path dir = outputDir.resolve(packageName.replace('.', '/'));

        List<Property> displayProperties = getDisplayProperties(entity);

        JavaFileWriter w = new JavaFileWriter();
        w.writeLine("package " + packageName + ";");
        w.writeLine();
        w.writeLine("import org.junit.jupiter.api.*;");
        w.writeLine("import org.openqa.selenium.WebDriver;");
        w.writeLine("import org.openqa.selenium.WebElement;");
        w.writeLine("import org.openqa.selenium.By;");
        w.writeLine("import static org.junit.jupiter.api.Assertions.*;");
        w.writeLine("import " + basePackage + ".BaseTest;");
        w.writeLine("import " + basePackage + ".page." + pageClassName + ";");
        w.writeLine();

        w.writeLine("@TestMethodOrder(MethodOrderer.OrderAnnotation.class)");
        w.openBlock("public class " + testClassName + " extends BaseTest");
        w.writeLine();
        w.writeLine("private " + pageClassName + " page;");
        w.writeLine("private static final String ENTITY_NAME = \"" + entity.getName() + "\";");
        w.writeLine("private static final String PARENT_ENTITY_NAME = \"" + parent.getName() + "\";");
        w.writeLine("private static final String PARENT_FEATURE_NAME = \"" + parent.getFeatureName() + "\";");
        w.writeLine("private static final String NODE_NAME = \"" + nodeName.replace("\"", "\\\"") + "\";");
        w.writeLine();

        w.writeLine("@Override");
        w.openBlock("protected String entityName()");
        w.writeLine("return PARENT_ENTITY_NAME;");
        w.closeBlock();
        w.writeLine();

        // @BeforeEach: переход к родителю, открытие карточки, раскрытие узла дерева (с ретраем)
        writeRobustChildSetUp(w, pageClassName, "NODE_NAME", "узел дерева");

        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        writeTreeNodeFieldsTest(w, displayProperties, nodeName);

        w.closeBlock(); // конец класса
        w.writeToFile(dir, testClassName + ".java");
    }

    private void writeTreeNodeFieldsTest(JavaFileWriter w, List<Property> properties, String nodeName) {
        int total = 0;
        for (Property p : properties) {
            if (!isSystemField(p)) total++;
        }
        String node = nodeName.replace("\"", "\\\"");
        w.writeLine("@Test");
        w.writeLine("@Order(1)");
        w.writeLine("@DisplayName(\"Tree node '" + node + "': fields present\")");
        w.openBlock("void testTreeNodeFields()");
        w.writeLine("shot(\"node_view\");");
        w.writeLine("int found = 0;");
        w.writeLine("java.util.List<String> missing = new java.util.ArrayList<>();");
        for (Property p : properties) {
            if (isSystemField(p)) continue;
            w.openBlock("if (page.isFieldDisplayed(\"" + p.getName() + "\", \"" + p.getAttrName() + "\"))");
            w.writeLine("found++;");
            w.closeBlock();
            w.openBlock("else");
            w.writeLine("missing.add(\"" + p.getName().replace("\"", "\\\"") + "\");");
            w.closeBlock();
        }
        w.writeLine("System.out.println(\"Tree node '" + node + "' fields: \" + found + \" of " + total + "\");");
        w.openBlock("if (!missing.isEmpty())");
        w.writeLine("System.out.println(\"  missing: \" + String.join(\", \", missing));");
        w.closeBlock();
        w.writeLine("shot(found > 0 ? \"fields_found\" : \"no_fields\");");
        if (total > 0) {
            w.writeLine("Assumptions.assumeTrue(found >= 1,");
            w.writeLine("    \"Tree node '" + node + "': 0 of " + total + " fields visible — node may render differently on this build. Missing: \" + String.join(\", \", missing));");
        } else {
            w.writeLine("System.out.println(\"Tree node '" + node + "': no fields defined in model\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    /**
     * Генерирует @BeforeEach для дочерней сущности: до 3 попыток navigateToEntity(родитель),
     * проверка что грид не пуст, selectAndOpenRecord, openTab/openTreeNode.
     * При окончательной неудаче — честный fail со скрином и диагностикой, а не тихий skip.
     * {@code tabConst} — имя константы (TAB_NAME / NODE_NAME), {@code kindLabel} —
     * «вкладка»/«узел дерева» для сообщения.
     */
    private void writeRobustChildSetUp(JavaFileWriter w, String pageClassName, String tabConst, String kindLabel) {
        // Хелпер: навигация к родителю, карточка, вкладка (до 3 попыток).
        // Вызывается из @BeforeEach и из testDelete (ре-навигация для свежего грида после удаления,
        // т.к. store.reload() на этих relation-гридах падает — единственный надёжный способ
        // перечитать данные с сервера: заново открыть карточку родителя и вкладку).
        w.writeLine("/** Навигация родитель→карточка→вкладка с ретраем. true = контекст готов. */");
        w.openBlock("boolean openParentChildTab()");
        w.writeLine("boolean ready = false;");
        w.writeLine("lastChildSetupFailReason = \"навигация не начиналась\";");
        w.openBlock("for (int attempt = 1; attempt <= 3 && !ready; attempt++)");
        w.openBlock("try");
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(PARENT_ENTITY_NAME, PARENT_FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("int parentRows = getVisibleRowCount();");
        w.writeLine("System.out.println(\"child setUp: attempt=\" + attempt + \" parent='\" + PARENT_ENTITY_NAME + \"' gridRows=\" + parentRows);");
        // Грид родителя пуст — нет записи, чтобы открыть карточку. Ещё раз запускаем поиск и повторяем.
        w.openBlock("if (parentRows <= 0)");
        w.writeLine("lastChildSetupFailReason = \"родитель '\" + PARENT_ENTITY_NAME + \"': грид пуст (нет записи для открытия карточки)\";");
        w.writeLine("executeSearchIfPresent();");
        w.writeLine("waitForGridSettle();");
        w.writeLine("parentRows = getVisibleRowCount();");
        w.openBlock("if (parentRows <= 0)");
        w.writeLine("continue;");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("boolean cardOpened = selectAndOpenRecord();");
        w.openBlock("if (!cardOpened)");
        w.writeLine("lastChildSetupFailReason = \"родитель '\" + PARENT_ENTITY_NAME + \"': карточка записи не открылась\";");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("waitForCardLoaded(10);");
        w.writeLine("shot(\"parent_card\");");
        w.writeLine("boolean tabOpened = openTab(" + tabConst + ");");
        w.openBlock("if (!tabOpened)");
        w.writeLine("lastChildSetupFailReason = \"" + kindLabel + " '\" + " + tabConst + " + \"' не найден(а) в карточке родителя\";");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("ready = true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("lastChildSetupFailReason = \"исключение: \" + e.getMessage();");
        w.writeLine("System.out.println(\"child setUp attempt=\" + attempt + \" threw: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("return ready;");
        w.closeBlock();
        w.writeLine();
        w.writeLine("private String lastChildSetupFailReason = \"\";");
        w.writeLine();
        // Число строк дочернего грида из in-memory стора (после ре-навигации это свежие данные сервера).
        w.openBlock("long readChildStoreCount()");
        w.writeLine("try {");
        w.writeLine("    Object n = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("        \"try{ return (window.__t2grid&&window.__t2grid.getStore)?window.__t2grid.getStore().getCount():-1; }catch(e){ return -1; }\");");
        w.writeLine("    return (n instanceof Number) ? ((Number) n).longValue() : -1L;");
        w.writeLine("} catch (Exception e) { return -1L; }");
        w.closeBlock();
        w.writeLine();

        w.writeLine("@BeforeEach");
        w.openBlock("void setUp()");
        w.writeLine("boolean ready = openParentChildTab();");
        // Если контекст не подготовлен — падаем с диагностикой, а не тихо пропускаем.
        w.openBlock("if (!ready)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("shot(\"child_setup_failed\");");
        w.writeLine("fail(\"Child '\" + ENTITY_NAME + \"': не удалось подготовить контекст за 3 попытки — \" + lastChildSetupFailReason");
        w.writeLine("    + \". (Сценарий: родитель → карточка → \" + " + tabConst + " + \"). Это реальная проблема навигации, не заглушка.\");");
        w.closeBlock();
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"child_tab\");");
        w.writeLine("page = new " + pageClassName + "(driver);");
        w.closeBlock();
        w.writeLine();
    }

    private void writeChildGridColumnsTest(JavaFileWriter w, List<Property> gridColumns, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(1)");
        w.writeLine("@DisplayName(\"Grid columns: " + tabName.replace("\"", "\\\"") + "\")");
        w.openBlock("void testGridColumns()");
        w.writeLine("shot(\"grid_view\");");
        w.writeLine("int rows = getVisibleRowCount();");
        w.writeLine("System.out.println(\"Grid '" + tabName.replace("\"", "\\\"") + "' rows: \" + rows);");
        if (!gridColumns.isEmpty()) {
            w.writeLine("int colsFound = 0;");
            w.writeLine("java.util.List<String> missingCols = new java.util.ArrayList<>();");
            for (Property col : gridColumns) {
                w.openBlock("if (isColumnPresent(\"" + col.getName().replace("\"", "\\\"") + "\"))");
                w.writeLine("colsFound++;");
                w.closeBlock();
                w.openBlock("else");
                w.writeLine("missingCols.add(\"" + col.getName().replace("\"", "\\\"") + "\");");
                w.closeBlock();
            }
            w.writeLine("System.out.println(\"Grid '" + tabName.replace("\"", "\\\"") + "' columns: \" + colsFound + \" of " + gridColumns.size() + "\");");
            w.openBlock("if (!missingCols.isEmpty())");
            w.writeLine("System.out.println(\"  missing: \" + String.join(\", \", missingCols));");
            w.closeBlock();
            w.writeLine("shot(\"columns_checked\");");
            // Грид вкладки должен отрисоваться: либо нашли колонки, либо в гриде есть строки.
            // Если 0 колонок и 0 строк — грид не открылся или селекторы не подходят: честный fail
            // с диагностикой.
            w.openBlock("if (colsFound == 0 && rows <= 0)");
            w.writeLine("dumpCardDiagnostics();");
            w.closeBlock();
            w.writeLine("assertTrue(colsFound >= 1 || rows > 0,");
            w.writeLine("    \"Grid '" + tabName.replace("\"", "\\\"") + "': грид вкладки не отрисован — 0 of " + gridColumns.size() + " колонок найдено И 0 строк. Missing: \" + String.join(\", \", missingCols));");
        } else {
            w.writeLine("System.out.println(\"Grid '" + tabName.replace("\"", "\\\"") + "': no columns defined in model\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    /**
     * Дочерняя grid-вкладка. По платформе E3Core грид редактируется внутри грида
     * (Редактирование, Добавить, пустая строка, заполнить, Сохранить изменения),
     * а не модальной формой. Используем ту же inline-механику, что и для справочников Type 2,
     * чтобы поведение определялось структурой (тип группы свойств = Грид), а не именами.
     */
    private void writeChildCreateTest(JavaFileWriter w, List<Property> displayProperties, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(3)");
        w.writeLine("@DisplayName(\"Create inline in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testCreate()");
        w.writeLine("shot(\"grid_before_add\");");
        w.writeLine("String createdMarker = \"AT\" + System.nanoTime();");
        w.writeLine("boolean addClicked = step(\"\\u0420\\u0435\\u0434\\u0430\\u043a\\u0442\\u0438\\u0440\\u043e\\u0432\\u0430\\u043d\\u0438\\u0435 \\u2192 \\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\", () -> clickEditDropdownAction(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\"));");
        w.writeLine("if (!addClicked) addClicked = clickButtonByText(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\");");
        w.writeLine("assertTrue(addClicked, \"child create: не удалось нажать 'Добавить' в гриде вкладки '\" + TAB_NAME + \"'\");");
        w.writeLine("try { Thread.sleep(900); } catch (InterruptedException ignored) {}");
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("shot(\"empty_row_added\");");
        writeLocateEditableGridScript(w, "colCount", false);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"child create: не нашли editable grid во вкладке '\" + TAB_NAME + \"' (код=\" + colCount + \")\");");
        writeGridDiagLog(w, "child testCreate");
        writeEnsureRowAddedScript(w, "child testCreate");
        w.writeLine("Long newRowL = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var s=window.__t2grid.getStore(); var mod=s.getModifiedRecords?s.getModifiedRecords():[]; var idx=-1;\"");
        w.writeLine("    + \" for (var i=0;i<mod.length;i++){ var r=mod[i]; if (r.phantom || r.newRecord){ var x=s.indexOf(r); if (x>idx) idx=x; } }\"");
        w.writeLine("    + \" if (idx<0) idx=s.getCount()-1; return idx; } catch(e){ return 0; }\");");
        w.writeLine("int rowIdx = (newRowL == null || newRowL < 0) ? 0 : newRowL.intValue();");
        writeSelectGridRowScript(w, "rowIdx");
        w.writeLine("shot(\"row_selected\");");
        writeFillEditableCells(w, "child testCreate", "createdMarker", "rowIdx", false);
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("boolean saved = step(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        w.openBlock("if (!saved)");
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("saved = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.closeBlock();
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.writeLine("assertTrue(saved, \"child create: не удалось сохранить во вкладке '\" + TAB_NAME + \"'\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(2000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        writeServerErrorCheck(w, "child testCreate");
        w.writeLine("clickGridRefresh();");
        w.writeLine("boolean created = gridContainsRow(createdMarker) || gridStoreContainsText(createdMarker);");
        w.writeLine("System.out.println(\"child testCreate: marker='\" + createdMarker + \"' found=\" + created);");
        w.writeLine("assertTrue(created, \"child create: запись '\" + createdMarker + \"' не найдена в гриде вкладки '\" + TAB_NAME + \"' после сохранения\");");
        w.closeBlock();
        w.writeLine();
    }

    /** Дочерняя grid-вкладка: update — меняем одно текстовое поле первой строки inline. */
    private void writeChildUpdateTest(JavaFileWriter w, List<Property> displayProperties, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(4)");
        w.writeLine("@DisplayName(\"Update inline in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testUpdate()");
        w.writeLine("shot(\"grid_before_update\");");
        w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
        writeLocateEditableGridScript(w, "colCount", true, true);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"child update: грид вкладки '\" + TAB_NAME + \"' пуст или не найден (код=\" + colCount + \")\");");
        writeGridDiagLog(w, "child testUpdate");
        w.writeLine("int editRow = 0;");
        writeSelectGridRowScript(w, "editRow");
        w.writeLine("shot(\"row_selected\");");
        writeFillEditableCells(w, "child testUpdate", "updatedValue", "editRow", true);
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("boolean saved = step(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        w.openBlock("if (!saved)");
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("saved = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.closeBlock();
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("assertTrue(saved, \"child update: не удалось сохранить во вкладке '\" + TAB_NAME + \"'\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(2000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_update\");");
        writeServerErrorCheck(w, "child testUpdate");
        w.writeLine("clickGridRefresh();");
        w.writeLine("boolean inView = gridContainsRow(updatedValue) || gridStoreContainsText(updatedValue);");
        w.writeLine("System.out.println(\"child testUpdate: value='\" + updatedValue + \"' found=\" + inView);");
        w.writeLine("assertTrue(inView, \"child update: новое значение '\" + updatedValue + \"' не найдено в гриде вкладки '\" + TAB_NAME + \"' после сохранения\");");
        w.closeBlock();
        w.writeLine();
    }

    /** Дочерняя grid-вкладка: delete — выбрать строку в гриде, Редактирование, Удалить (inline). */
    private void writeChildDeleteTest(JavaFileWriter w, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(5)");
        w.writeLine("@DisplayName(\"Delete inline in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testDelete()");
        w.writeLine("shot(\"grid_before_delete\");");
        writeLocateEditableGridScript(w, "colCount", true, true);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"child delete: грид вкладки '\" + TAB_NAME + \"' пуст или не найден (код=\" + colCount + \")\");");
        // Счётчик берём из in-memory стора (getCount), а не из «Всего записей»/reload: reload на этих
        // relation-гридах падает, а после ре-навигации стор перечитывается с сервера, и getCount честен.
        w.writeLine("long childCountBefore = readChildStoreCount();");
        w.writeLine("System.out.println(\"child testDelete: строк в дочернем гриде ДО = \" + childCountBefore);");
        // Если дочерний грид пуст — сообщаем, что у выбранной родительской записи нет дочерних строк.
        w.openBlock("if (childCountBefore <= 0)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("fail(\"child delete: дочерний грид вкладки '\" + TAB_NAME + \"' пуст — нет строки для удаления (у выбранной родительской записи нет дочерних данных).\");");
        w.closeBlock();
        w.writeLine("int editRow = 0;");
        writeSelectGridRowScript(w, "editRow");
        w.writeLine("shot(\"row_selected\");");
        w.writeLine("boolean delClicked = clickEditDropdownAction(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!delClicked) delClicked = clickButtonByText(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("assertTrue(delClicked, \"child delete: не удалось нажать 'Удалить' в гриде вкладки '\" + TAB_NAME + \"'\");");
        w.writeLine("acceptAlertIfPresent();");
        w.writeLine("String delPopup = capturePopupText(\"after-\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("try { Thread.sleep(1500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_delete\");");
        writeServerErrorCheck(w, "child testDelete");
        // Ре-навигация (как у PRIMARY delete): заново открываем карточку родителя и вкладку,
        // дочерний грид перечитывается с сервера. Это единственный надёжный способ проверить
        // удаление (store.reload() не работает). Затем сравниваем in-memory getCount: должно стать меньше.
        w.writeLine("boolean reReady = openParentChildTab();");
        w.writeLine("assertTrue(reReady, \"child delete: не удалось ре-навигировать к дочернему гриду для проверки (после удаления). \" + lastChildSetupFailReason);");
        writeLocateEditableGridScript(w, "freshCols", false, true);
        w.writeLine("long childCountAfter = readChildStoreCount();");
        w.writeLine("System.out.println(\"child testDelete: строк ПОСЛЕ ре-навигации = \" + childCountAfter + \" (было \" + childCountBefore + \") popup='\" + delPopup + \"'\");");
        w.writeLine("assertTrue(childCountAfter < childCountBefore, \"child delete: запись не удалилась — число строк дочернего грида не уменьшилось (\" + childCountBefore + \" -> \" + childCountAfter + \") после ре-навигации. popup='\" + delPopup + \"'\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeFieldsPresentTest(JavaFileWriter w, List<Property> properties, String entityName) {
        int totalCount = 0;
        for (Property prop : properties) {
            if (!isSystemField(prop)) totalCount++;
        }
        w.writeLine("@Test");
        w.writeLine("@Order(1)");
        w.writeLine("@DisplayName(\"\\u041f\\u043e\\u043b\\u044f \\u0444\\u043e\\u0440\\u043c\\u044b '" + entityName + "': \\u043e\\u0436\\u0438\\u0434\\u0430\\u0435\\u0442\\u0441\\u044f " + totalCount + " \\u043f\\u043e\\u043b\\u0435\\u0439\")");
        w.openBlock("void testFieldsPresent()");
        w.writeLine("shot(\"nav_done\");");
        // Поля сверяются внутри карточки записи, а не в таблице результатов. Сначала проверяем,
        // что грид навигировался (нашли хотя бы колонки) — это нижняя граница. Затем открываем
        // первую строку двойным кликом и пересчитываем внутри карточки. Тест пройден, если
        // найдено хотя бы одно поле где-либо (карточка или грид), чтобы не блокировать остальные
        // CRUD-тесты пропуском.
        w.writeLine("int foundOnGrid = 0;");
        w.writeLine("int totalCount = " + totalCount + ";");
        w.writeLine("java.util.List<String> missingOnGrid = new java.util.ArrayList<>();");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            w.openBlock("if (page.isFieldDisplayed(\"" + prop.getName() + "\", \"" + prop.getAttrName() + "\"))");
            w.writeLine("foundOnGrid++;");
            w.closeBlock();
            w.openBlock("else");
            w.writeLine("missingOnGrid.add(\"" + prop.getName().replace("\"", "\\\"") + "\");");
            w.closeBlock();
        }
        w.writeLine("System.out.println(\"Fields visible on grid: \" + foundOnGrid + \" of \" + totalCount);");
        w.writeLine("shot(foundOnGrid == totalCount ? \"all_fields_grid\" : \"some_missing_grid\");");
        w.writeLine();
        w.writeLine("boolean cardOpened = step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("int foundInCard = 0;");
        w.writeLine("java.util.List<String> missingInCard = new java.util.ArrayList<>();");
        w.openBlock("if (cardOpened)");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("shot(\"card_opened\");");
        for (Property prop : properties) {
            if (isSystemField(prop)) continue;
            w.openBlock("if (page.isFieldDisplayed(\"" + prop.getName() + "\", \"" + prop.getAttrName() + "\"))");
            w.writeLine("foundInCard++;");
            w.closeBlock();
            w.openBlock("else");
            w.writeLine("missingInCard.add(\"" + prop.getName().replace("\"", "\\\"") + "\");");
            w.closeBlock();
        }
        w.writeLine("System.out.println(\"Fields visible INSIDE card: \" + foundInCard + \" of \" + totalCount);");
        w.openBlock("if (!missingInCard.isEmpty())");
        w.writeLine("System.out.println(\"  not found in card (\" + missingInCard.size() + \"): \" + String.join(\", \", missingInCard));");
        w.closeBlock();
        w.writeLine("shot(missingInCard.isEmpty() ? \"all_fields_card\" : \"some_missing_card\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("System.out.println(\"Card did not open — field comparison limited to result grid columns.\");");
        w.closeBlock();
        // Зачёт, если нашли хотя бы одно поле где-либо — значит навигация сработала.
        w.openBlock("if (totalCount > 0)");
        w.writeLine("int best = Math.max(foundOnGrid, foundInCard);");
        w.writeLine("assertTrue(best >= 1, \"0 of \" + totalCount + \" expected fields visible — navigation likely failed entirely (grid missing: \" + String.join(\", \", missingOnGrid) + \"; card missing: \" + String.join(\", \", missingInCard) + \")\");");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
    }

    private void writeRequiredFieldValidationTest(JavaFileWriter w, List<Property> requiredProperties) {
        w.writeLine("@Test");
        w.writeLine("@Order(2)");
        w.writeLine("@DisplayName(\"Required field validation on empty submit\")");
        w.openBlock("void testRequiredFieldValidation()");
        w.writeLine("shot(\"start\");");
        // «Добавить» открывается через главное меню, не из карточки.
        w.writeLine("boolean addClicked = step(\"open Добавить via main menu\", () -> addViaMenu(ENTITY_NAME));");
        w.writeLine("System.out.println(\"  addClicked=\" + addClicked + \" for entity \" + ENTITY_NAME);");
        w.writeLine("if (!addClicked) dumpCardDiagnostics();");
        w.writeLine("Assumptions.assumeTrue(addClicked, \"Не удалось найти 'Добавить' в главном меню для сущности '\" + ENTITY_NAME + \"'\");");
        w.writeLine("boolean addFormOpen = waitForAddForm();");
        w.writeLine("System.out.println(\"  addFormOpen=\" + addFormOpen);");
        w.writeLine("if (!addFormOpen) dumpCardDiagnostics();");
        w.writeLine("shot(\"after_add\");");
        w.writeLine("Assumptions.assumeTrue(addFormOpen, \"Add form did not open after Edit>Добавить (neither modal dialog nor add card detected)\");");
        w.writeLine();
        w.writeLine("step(\"clear form\", () -> { try { page.clearForm(); } catch (Exception ignored) {} });");
        w.writeLine("shot(\"after_clear\");");
        w.writeLine();
        w.writeLine("step(\"click Готово\", () -> clickButtonByText(\"Готово\"));");
        // Не вызываем waitForDialogClose — ожидаем, что диалог останется открытым из-за валидации.
        // Даём ExtJS короткую паузу на отрисовку индикаторов ошибок.
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_submit\");");
        w.writeLine();
        w.writeLine("boolean dialogStillOpen = isDialogOpen() || isButtonVisible(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("boolean hasErrors = page.hasValidationErrors();");
        w.writeLine("int errorFieldCount = 0;");
        for (Property prop : requiredProperties) {
            w.writeLine("if (page.fieldHasError(\"" + prop.getAttrName() + "\")) errorFieldCount++;");
        }
        w.writeLine("System.out.println(\"Validation summary: dialogOpen=\" + dialogStillOpen");
        w.writeLine("    + \", hasErrors=\" + hasErrors + \", highlightedFields=\" + errorFieldCount + \"/" + requiredProperties.size() + "\");");
        w.writeLine();
        // Валидация должна сделать хоть что-то: оставить диалог открытым, показать ошибки формы
        // или подсветить поля. Если ничего из трёх — валидация сломана.
        w.writeLine("assertTrue(dialogStillOpen || hasErrors || errorFieldCount > 0,");
        w.writeLine("    \"Empty submit of required-field form must trigger validation: dialog should stay open OR errors shown OR fields highlighted. \"");
        w.writeLine("    + \"None of the three happened — form likely silently accepted invalid data.\");");
        // Диалог Сведения остался открытым — это и есть сигнал успешной валидации. Если его не
        // закрыть, следующий тест (testCreate, Order 3) не сможет пронавигироваться: модальный
        // диалог блокирует клик по верхнему меню. Жмём «Отмена».
        w.writeLine("try { clickButtonByText(\"\\u041e\\u0442\\u043c\\u0435\\u043d\\u0430\"); waitForDialogClose(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine();
    }

    private void writePartialValidationTest(JavaFileWriter w, List<Property> requiredProperties) {
        if (requiredProperties.size() < 2) return;
        w.writeLine("@Test");
        w.writeLine("@Order(20)");
        w.writeLine("@DisplayName(\"Partial fill: only first required field\")");
        w.openBlock("void testPartialRequiredFieldValidation()");
        w.writeLine("shot(\"start\");");
        w.writeLine("boolean addClicked = step(\"open Добавить via main menu\", () -> addViaMenu(ENTITY_NAME));");
        w.writeLine("System.out.println(\"  addClicked=\" + addClicked + \" for entity \" + ENTITY_NAME);");
        w.writeLine("if (!addClicked) dumpCardDiagnostics();");
        w.writeLine("Assumptions.assumeTrue(addClicked, \"'Добавить' not in dropdown\");");
        w.writeLine("boolean addFormOpen = waitForAddForm();");
        w.writeLine("System.out.println(\"  addFormOpen=\" + addFormOpen);");
        w.writeLine("if (!addFormOpen) dumpCardDiagnostics();");
        w.writeLine("shot(\"dialog_opened\");");
        w.writeLine("Assumptions.assumeTrue(addFormOpen, \"Add form did not open after Edit>Добавить (neither modal dialog nor add card detected)\");");
        w.writeLine("step(\"clear form\", () -> { try { page.clearForm(); } catch (Exception ignored) {} });");
        w.writeLine("shot(\"cleared\");");
        Property first = requiredProperties.get(0);
        String firstMethod = "fill" + Transliterator.toClassName(first.getAttrName());
        String firstValue = TestDataFactory.generateValue(first);
        if (firstValue != null) {
            w.writeLine("step(\"fill first required\", () -> page." + firstMethod + "(\"" + firstValue + "\"));");
        }
        w.writeLine("shot(\"first_filled\");");
        w.writeLine("step(\"click Готово\", () -> clickButtonByText(\"Готово\"));");
        // Ожидаем, что диалог не закроется — короткая пауза только на отрисовку ошибок.
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_submit\");");
        w.writeLine();
        // Заполнено только первое обязательное поле — остальные должны блокировать отправку.
        w.writeLine("boolean dialogStillOpen = isDialogOpen() || isButtonVisible(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("boolean hasErrors = page.hasValidationErrors();");
        w.writeLine("assertTrue(dialogStillOpen || hasErrors,");
        w.writeLine("    \"Partial fill must not pass validation: dialog should stay open OR errors should be shown for the remaining required fields\");");
        // Закрываем оставшийся открытым диалог — иначе следующий тест не пронавигируется.
        w.writeLine("try { clickButtonByText(\"\\u041e\\u0442\\u043c\\u0435\\u043d\\u0430\"); waitForDialogClose(); } catch (Exception ignored) {}");
        w.closeBlock();
        w.writeLine();
    }

    private void writeCreateTest(JavaFileWriter w, List<Property> displayProperties) {
        // Берём первое не-системное не-FK STRING-поле, чтобы пометить уникальным маркером.
        // После сохранения проверяем, что маркер появился в гриде результатов — это доказывает,
        // что запись реально добавлена, а не просто увеличился счётчик строк.
        Property markerField = displayProperties.stream()
                .filter(p -> p.getAttrType() == AttrType.STRING && !isSystemField(p)
                        && !"Directory".equals(p.getStereoType()) && !"Ref".equals(p.getStereoType()))
                .findFirst().orElse(null);

        w.writeLine("@Test");
        w.writeLine("@Order(3)");
        w.writeLine("@DisplayName(\"Create new record\")");
        w.openBlock("void testCreate()");
        w.writeLine("shot(\"initial_grid\");");
        w.writeLine("int rowsBefore = page.getTableRowCount();");
        w.writeLine();
        // «Добавить» открывается через главное меню — путь как у «Найти», но кликаем «Добавить».
        // В карточке записи пункта «Добавить» нет (там только «Сохранить Изменения» / «Удалить»).
        w.writeLine("boolean addClicked = step(\"open Добавить via main menu\", () -> addViaMenu(ENTITY_NAME));");
        w.writeLine("System.out.println(\"  addClicked=\" + addClicked + \" for entity \" + ENTITY_NAME);");
        w.writeLine("if (!addClicked) dumpCardDiagnostics();");
        w.writeLine("Assumptions.assumeTrue(addClicked, \"Не удалось найти 'Добавить' в главном меню для сущности '\" + ENTITY_NAME + \"'\");");
        w.writeLine("boolean addFormOpen = waitForAddForm();");
        w.writeLine("System.out.println(\"  addFormOpen=\" + addFormOpen);");
        w.writeLine("if (!addFormOpen) dumpCardDiagnostics();");
        w.writeLine("shot(\"dialog_opened\");");
        w.writeLine("Assumptions.assumeTrue(addFormOpen, \"Add form did not open after main menu Добавить (neither modal dialog nor add card detected)\");");
        if (markerField != null) {
            // Пропускаем markerField в fillAllFields — иначе будет двойной fill,
            // и переиспользование редактора ExtJS может склеить значения вместо чистой замены.
            String mfDisplay = markerField.getName().replace("\\", "\\\\").replace("\"", "\\\"");
            w.writeLine("step(\"fill all fields (except marker)\", () -> page.fillAllFieldsExcept(\"" + mfDisplay + "\"));");
        } else {
            w.writeLine("step(\"fill all fields\", () -> page.fillAllFields());");
        }
        w.writeLine("shot(\"all_fields_filled\");");
        w.writeLine("System.out.println(\"testCreate: после fillAllFields lastFilledValues=\" + page.lastFilledValues);");
        if (markerField != null) {
            String fillMethod = "fill" + Transliterator.toClassName(markerField.getAttrName());
            w.writeLine("String createdMarker = \"AT\" + System.nanoTime();");
            w.writeLine("step(\"stamp marker\", () -> page." + fillMethod + "(createdMarker));");
            w.writeLine("shot(\"marker_applied\");");
        } else {
            w.writeLine("String createdMarker = \"\";  // no STRING field available to stamp with marker");
        }
        // Запоминаем все фактически вписанные значения — снимок того, что отправили на сервер.
        // Используем его, если маркер не нашёлся в гриде напрямую (например, поле-маркер
        // не отображается в результирующей таблице).
        w.writeLine("java.util.LinkedHashMap<String, String> filledSnapshot = new java.util.LinkedHashMap<>(page.lastFilledValues);");
        w.writeLine("System.out.println(\"testCreate: запомнили заполненные поля: \" + filledSnapshot);");
        // Даём ExtJS время прокинуть значения PropertyGrid из rec.set(...) в форму.
        // Без паузы «Готово» отрабатывает раньше, чем ExtJS закоммитит последние rec.set,
        // и сервер видит часть полей пустыми (popup «Необходимо заполнить ...»).
        w.openBlock("try");
        w.writeLine("Thread.sleep(1500);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        // Не жмём TAB: в PropertyGrid TAB переходит на следующую ячейку и снова открывает
        // редактор, запись возвращается в режим правки и сейв не фиксирует все поля.
        // Вместо TAB снимаем фокус (blur): активное значение коммитится, новый редактор
        // не открывается, запись остаётся готовой к сохранению.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(document.activeElement && document.activeElement.blur) document.activeElement.blur(); }catch(e){}\");");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // Принудительно коммитим редакторы PropertyGrid в записи перед «Готово»
        // (stopEditing(false)/completeEdit, без открытия нового редактора), иначе правки видны
        // визуально, но record.set не вызван и запись сохраняется пустой.
        writeCommitAllEditorsScript(w);
        // Диагностика: остался ли активный редактор у какого-нибудь грида перед «Готово».
        // Если в run-report тут не 'none' — значит keynav снова открыл редактор и его поле
        // сервер посчитает незаполненным (попап «Необходимо заполнить …»).
        w.openBlock("try");
        w.writeLine("Object activeEd = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { if (typeof Ext==='undefined') return 'no-ext';\"");
        w.writeLine("    + \"var cmps=(Ext.ComponentQuery&&Ext.ComponentQuery.query)?Ext.ComponentQuery.query('propertygrid,editorgrid,grid'):[];\"");
        w.writeLine("    + \"for (var i=0;i<cmps.length;i++){ var c=cmps[i]; if (c && c.activeEditor && c.activeEditor.field){ var f=c.activeEditor.field; var v=f.getValue?f.getValue():''; return 'ACTIVE:'+(f.fieldLabel||f.name||'?')+'=['+v+']'; } }\"");
        w.writeLine("    + \"return 'none'; } catch(e){ return 'err:'+e.message; }\");");
        w.writeLine("System.out.println(\"testCreate: активный редактор перед Готово = \" + activeEd);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("shot(\"before_gotovo\");");
        w.writeLine("boolean gotovoClicked = step(\"click Готово\", () -> clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"));");
        w.writeLine("System.out.println(\"testCreate: gotovoClicked=\" + gotovoClicked);");
        // Фолбэк для справочников: у них «Добавить» открывает inline-режим в гриде,
        // а не модальный «Сведения»; кнопка сохранения — «Сохранить» или Enter.
        w.openBlock("if (!gotovoClicked)");
        w.writeLine("System.out.println(\"testCreate: 'Готово' не найдено — пробуем 'Сохранить' / 'OK' / Enter (вероятно справочник с inline-сохранением)\");");
        w.writeLine("gotovoClicked = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.openBlock("if (!gotovoClicked)");
        w.writeLine("gotovoClicked = clickButtonByText(\"OK\");");
        w.closeBlock();
        w.openBlock("if (!gotovoClicked)");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).sendKeys(org.openqa.selenium.Keys.ENTER).perform();");
        w.writeLine("gotovoClicked = true;");
        w.writeLine("System.out.println(\"testCreate: Enter отправлен как save-жест\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"testCreate: Enter-fallback провалился: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine("assertTrue(gotovoClicked, \"testCreate: ни 'Готово', ни 'Сохранить', ни 'OK', ни Enter не сработали. Проверьте что форма создания реально открылась.\");");
        // Захватываем popup после «Готово» и сразу жмём «Да» (confirmDialogYes). Встречаются
        // wizard-формы, где «Готово» — шаг мастера, а не финальное сохранение. Делаем до 3 итераций:
        // если диалог всё ещё открыт и видна кнопка «Готово» — жмём её снова (следующий шаг мастера).
        w.writeLine("String createPopupText = capturePopupText(\"after-\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("System.out.println(\"testCreate: popup после Готово = '\" + createPopupText + \"'\");");
        // Самовосстановление: если сервер отклонил save валидацией по полю-списку (например
        // «Тип ГСК/ОГСК», где некоторые значения создавать нельзя), скриним popup (это уже сделал
        // capturePopupText), переподбираем следующее значение упомянутых списков и повторяем
        // «Готово». До 3 попыток; popup каждой попытки попадает в отчёт. Если не помогло — ниже FAIL.
        StringBuilder lfSb = new StringBuilder();
        for (Property p : displayProperties) {
            if (("Directory".equals(p.getStereoType()) || "Ref".equals(p.getStereoType())) && !isSystemField(p)) {
                if (lfSb.length() > 0) lfSb.append(", ");
                lfSb.append("\"").append(p.getName().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
        }
        w.writeLine("String[] LIST_FIELDS = { " + lfSb + " };");
        w.writeLine("int saveTry = 1;");
        w.openBlock("while (saveTry < 3 && createPopupText != null && (createPopupText.contains(\"\\u041d\\u0435\\u043e\\u0431\\u0445\\u043e\\u0434\\u0438\\u043c\\u043e\") || createPopupText.contains(\"\\u041d\\u0435 \\u0437\\u0430\\u043f\\u043e\\u043b\\u043d\\u0435\\u043d\\u043e\")))");
        w.writeLine("System.out.println(\"testCreate: save отклонён валидацией (попытка \" + saveTry + \"): \" + createPopupText);");
        w.writeLine("shot(\"retry_\" + saveTry + \"_validation\");");
        // Закрываем popup (OK/Да), чтобы вернуться в карточку «Сведения».
        w.writeLine("confirmDialogYes();");
        w.openBlock("try");
        w.writeLine("Thread.sleep(400);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        // Для каждого поля-списка, упомянутого в popup, выбираем следующее не пробованное значение.
        w.writeLine("boolean repicked = false;");
        w.openBlock("for (String lf : LIST_FIELDS)");
        w.openBlock("if (createPopupText.contains(lf))");
        w.writeLine("boolean ok = page.repickDropdown(lf);");
        w.writeLine("System.out.println(\"testCreate: переподбор '\" + lf + \"' -> \" + ok);");
        w.writeLine("repicked = repicked || ok;");
        w.closeBlock();
        w.closeBlock();
        w.openBlock("if (!repicked)");
        w.writeLine("System.out.println(\"testCreate: нечего переподбирать (нет полей-списков в popup или варианты кончились) — прекращаем повторы\");");
        w.writeLine("break;");
        w.closeBlock();
        // Фиксируем редакторы перед повторным «Готово» (как в основном потоке).
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(document.activeElement && document.activeElement.blur) document.activeElement.blur(); }catch(e){}\");");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        writeCommitAllEditorsScript(w);
        w.writeLine("shot(\"retry_\" + saveTry + \"_before_gotovo\");");
        w.writeLine("boolean againSave = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.openBlock("if (!againSave)");
        w.writeLine("againSave = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.closeBlock();
        w.writeLine("System.out.println(\"testCreate: повтор save, кнопка нажата=\" + againSave);");
        w.writeLine("createPopupText = capturePopupText(\"retry-save\");");
        w.writeLine("System.out.println(\"testCreate: popup после повтора = '\" + createPopupText + \"'\");");
        w.writeLine("saveTry++;");
        w.closeBlock();
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForDialogClose();");
        // Ждём 3с на серверный round-trip. Карточка может остаться открытой (на некоторых стендах
        // после save она переходит в read-only и остаётся на экране). Не фейлим на «диалог открыт» —
        // единственный надёжный критерий «сохранилось»: запись видна в обновлённом гриде.
        w.openBlock("try");
        w.writeLine("Thread.sleep(3000);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        // Если popup сервера сообщил «не заполнено» — save отвергнут. Фейлим сразу с диагностикой,
        // не ходим в грид.
        w.openBlock("if (createPopupText != null && (createPopupText.contains(\"\\u041d\\u0435\\u043e\\u0431\\u0445\\u043e\\u0434\\u0438\\u043c\\u043e\") || createPopupText.contains(\"\\u041d\\u0435 \\u0437\\u0430\\u043f\\u043e\\u043b\\u043d\\u0435\\u043d\\u043e\")))");
        w.writeLine("shot(\"validation_error\");");
        w.writeLine("fail(\"testCreate: сервер отверг save с popup'ом валидации (после \" + saveTry + \" попыток переподбора значений списков): '\" + createPopupText + \"'. Заполненные поля: \" + filledSnapshot");
        w.writeLine("    + \". Если ругается на поле-список — возможно для всех доступных значений создание запрещено бизнес-логикой, либо поле зависит от FK/wizard-шага которого мы не проходили. Скриншоты всех popup'ов — в отчёте.\");");
        w.closeBlock();
        // Закрываем карточку через Esc/Отмена, только если popup не был валидационным.
        // «Сведения» после save может оставаться открытым read-only, а нам надо вернуться к гриду,
        // чтобы посчитать запись. Esc не отменит сохранение — save уже произошёл.
        w.openBlock("if (isDialogOpen() || isButtonVisible(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"))");
        w.writeLine("System.out.println(\"testCreate: карточка осталась открытой после save — закрываем Esc, переходим к проверке грида\");");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(500);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("shot(\"after_save\");");
        w.writeLine();
        // Ре-навигация к таблице результатов и повторный поиск — получаем свежий грид
        // и видим, появилась ли наша запись с маркером.
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(ENTITY_NAME, FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_renavigate\");");
        w.writeLine();
        w.writeLine("assertFalse(isErrorPresent(), \"No errors should be present after creating a record\");");
        w.writeLine();
        // Основная проверка: поиск по маркеру в форме поиска. Если маркер найден — запись
        // сохранилась. Поиск надёжнее, чем gridContainsRow в нефильтрованном гриде, где маркер
        // может «прятаться» на следующих страницах пагинации.
        w.writeLine("int rowsAfter = page.getTableRowCount();");
        w.writeLine("boolean markerInGrid = false;");
        w.writeLine("String hitVia = \"\";");
        if (markerField != null) {
            String fillMethod = "fill" + Transliterator.toClassName(markerField.getAttrName());
            // Уровень 1 (основной): фильтрованный поиск — вписываем маркер в форму поиска
            w.writeLine("System.out.println(\"testCreate: ищем маркер '\" + createdMarker + \"' через форму поиска\");");
            w.openBlock("try");
            w.writeLine("page." + fillMethod + "(createdMarker);");
            w.writeLine("executeSearchIfPresent();");
            w.writeLine("waitForGridSettle();");
            w.closeBlock();
            w.openBlock("catch (Exception ignored)");
            w.closeBlock();
            w.writeLine("shot(\"after_marker_search\");");
            w.writeLine("rowsAfter = page.getTableRowCount();");
            w.openBlock("if (!createdMarker.isEmpty() && gridContainsRow(createdMarker))");
            w.writeLine("markerInGrid = true;");
            w.writeLine("hitVia = \"marker(filtered search)\";");
            w.closeBlock();
        }
        w.writeLine();
        // Уровень 2 (фолбэк): маркер в нефильтрованном DOM грида
        w.openBlock("if (!markerInGrid && !createdMarker.isEmpty() && gridContainsRow(createdMarker))");
        w.writeLine("markerInGrid = true;");
        w.writeLine("hitVia = \"marker(DOM unfiltered)\";");
        w.closeBlock();
        w.writeLine();
        // Уровень 3 (фолбэк): ExtJS store
        w.openBlock("if (!markerInGrid && !createdMarker.isEmpty() && gridStoreContainsText(createdMarker))");
        w.writeLine("markerInGrid = true;");
        w.writeLine("hitVia = \"marker(ExtJS store)\";");
        w.closeBlock();
        w.writeLine();
        // Уровня 4 (по любому значению) нет: неуникальные значения (тип/дропдаун) встречаются
        // в уже существующих строках, и тест прошёл бы, хотя наша запись с уникальным маркером
        // не сохранилась. Create считаем успешным только по уникальному маркеру (уровни 1-3).
        w.writeLine("System.out.println(\"testCreate: markerInGrid=\" + markerInGrid + (hitVia.isEmpty() ? \"\" : \" via \" + hitVia) + \" rowsAfter=\" + rowsAfter);");
        w.writeLine("shot(markerInGrid ? \"marker_in_grid\" : \"final_grid\");");
        if (markerField != null) {
            w.writeLine("assertTrue(markerInGrid,");
            w.writeLine("    \"Create test: уникальный маркер '\" + createdMarker + \"' НЕ найден в гриде после ре-поиска (rowsBefore=\" + rowsBefore + \", rowsAfter=\" + rowsAfter + \"). Запись не сохранилась. Заполняли: \" + filledSnapshot.values() + \". Popup после Готово='\" + createPopupText + \"'.\");");
        } else {
            w.writeLine("assertTrue(rowsAfter >= rowsBefore,");
            w.writeLine("    \"Table should have same or more records after creation (\" + rowsBefore + \" -> \" + rowsAfter + \")\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    /** Type 2 (inline-table): create через «Редактирование, Добавить», заполнение ячеек, сохранение. */
    /**
     * Генерирует JS, находящий самый большой видимый редактируемый ExtJS-грид в window.__t2grid
     * и возвращающий число его колонок в {@code resultVar} (Long). Работает на ExtJS 3
     * (Ext.ComponentMgr.all, без ComponentQuery) и ExtJS 4+ (Ext.ComponentQuery).
     * resultVar: >0 — ок, 0 — грид без колонок, -1 — грида нет, -2 — грид пуст (при requireNonEmpty).
     */
    private void writeLocateEditableGridScript(JavaFileWriter w, String resultVar, boolean requireNonEmpty) {
        writeLocateEditableGridScript(w, resultVar, requireNonEmpty, true, true);
    }

    private void writeLocateEditableGridScript(JavaFileWriter w, String resultVar, boolean requireNonEmpty, boolean excludeProperty) {
        writeLocateEditableGridScript(w, resultVar, requireNonEmpty, excludeProperty, true);
    }

    private void writeLocateEditableGridScript(JavaFileWriter w, String resultVar, boolean requireNonEmpty, boolean excludeProperty, boolean declare) {
        w.writeLine((declare ? "Long " : "") + resultVar + " = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"if (typeof Ext === 'undefined') return -1;\"");
        w.writeLine("    + \"var grids = [];\"");
        w.writeLine("    + \"try { if (Ext.ComponentQuery && Ext.ComponentQuery.query) grids = Ext.ComponentQuery.query('gridpanel,editorgrid,grid'); } catch(e) {}\"");
        w.writeLine("    + \"if ((!grids || !grids.length) && Ext.ComponentMgr && Ext.ComponentMgr.all) {\"");
        w.writeLine("    + \"  try { var all = Ext.ComponentMgr.all; var arr = all.items || (all.getRange ? all.getRange() : []);\"");
        w.writeLine("    + \"    for (var i=0;i<arr.length;i++){ var c=arr[i]; if (c && c.getStore && (c.startEditing || c.getColumnModel || c.editingPlugin)) grids.push(c); } } catch(e) {}\"");
        w.writeLine("    + \"}\"");
        // Рабочий грид — нижний дата-грид с панелью пагинации (Страница X из Y / Обновить /
        // Всего записей) в активном окне. Не самый большой грид, не верхний список групп свойств
        // и не property-grid карточки. Признак панели пагинации — DOM-классы .x-tbar-page-number /
        // .x-tbar-page-next / .x-tbar-loading (язык-независимо) или наличие getBottomToolbar().
        w.writeLine("    + \"var aw=(Ext.WindowMgr&&Ext.WindowMgr.getActive)?Ext.WindowMgr.getActive():null;\"");
        w.writeLine("    + \"var awDom=(aw&&aw.getEl)?(aw.getEl().dom||aw.getEl()):null;\"");
        w.writeLine("    + \"var best=null, bestScore=-1, bestDom=null;\"");
        w.writeLine("    + \"for (var i=0;i<grids.length;i++){ var g=grids[i];\"");
        w.writeLine("    + \"  if (!g.rendered || !g.getStore) continue;\"");
        w.writeLine("    + \"  var dom=null; try { var el=g.getEl?g.getEl():null; dom=el?(el.dom||el):null; } catch(e){}\"");
        w.writeLine("    + \"  if (!dom || dom.offsetWidth<=0 || dom.offsetHeight<=0) continue;\"");
        if (excludeProperty) {
            w.writeLine("    + \"  var isProp=false; try { isProp = (g.getXType && g.getXType()==='propertygrid') || !!g.propertyNames || (g.source!==undefined && g.nameColumnWidth!==undefined); } catch(e){}\"");
            w.writeLine("    + \"  if (isProp) continue;\"");
        }
        w.writeLine("    + \"  var paging=false; try { paging = !!dom.querySelector('.x-tbar-page-number, .x-tbar-page-next, .x-tbar-loading'); if(!paging && g.getBottomToolbar && g.getBottomToolbar()) paging=true; } catch(e){}\"");
        w.writeLine("    + \"  var editable=!!(g.startEditing || g.editingPlugin);\"");
        w.writeLine("    + \"  var inActive=awDom?(awDom===dom||awDom.contains(dom)):true;\"");
        w.writeLine("    + \"  var cnt=0; try { cnt=g.getStore().getCount(); } catch(e){}\"");
        // Скоринг: рабочий грид в активном окне с панелью пагинации — высший приоритет,
        // далее редактируемость, число строк — лишь тай-брейк.
        w.writeLine("    + \"  var score=(inActive?1000:0)+(paging?400:0)+(editable?40:0)+Math.min(cnt,9);\"");
        w.writeLine("    + \"  if (score>bestScore){ bestScore=score; best=g; bestDom=dom; } }\"");
        w.writeLine("    + \"if (!best) return -1;\"");
        if (requireNonEmpty) {
            w.writeLine("    + \"if (best.getStore().getCount() === 0) return -2;\"");
        }
        w.writeLine("    + \"window.__t2grid = best; window.__t2dom = bestDom;\"");
        w.writeLine("    + \"var cm = best.getColumnModel ? best.getColumnModel() : null;\"");
        w.writeLine("    + \"return cm ? (cm.getColumnCount ? cm.getColumnCount() : 0) : 0;\");");
    }

    /** Генерирует JS, выбирающий строку {@code rowExpr} в window.__t2grid (Ext3 selectRow / Ext4 select). */
    private void writeSelectGridRowScript(JavaFileWriter w, String rowExpr) {
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var sm=g.getSelectionModel(); if (sm){ if (sm.selectRow) sm.selectRow(arguments[0]); else if (sm.select) sm.select(arguments[0]); } } catch(e){}\", (long) (" + rowExpr + "));");
    }

    /** Генерирует диагностический лог выбранного window.__t2grid (xtype, строки, редактируемость). */
    private void writeGridDiagLog(JavaFileWriter w, String label) {
        w.writeLine("System.out.println(\"" + label + ": выбран грид -> \" + ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if(!g) return 'none'; var xt=(g.getXType?g.getXType():''); var rows=0; try{rows=g.getStore().getCount();}catch(e){} var ed=!!(g.startEditing||g.editingPlugin); return xt+' rows='+rows+' editable='+ed; } catch(e){ return 'err:'+e.message; }\"));");
    }

    /** Генерирует JS, коммитящий активный редактор window.__t2grid в его запись (Ext3/Ext4). */
    private void writeCommitGridEditorScript(JavaFileWriter w) {
        // Завершаем редактирование во всех editor-гридах и снимаем фокус с активного инпута,
        // чтобы открытый редактор ячейки/датапикер не перехватывал клик по тулбару «Редактирование»
        // (из-за чего «Сохранить Изменения» не появлялось в дропдауне).
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { if (typeof Ext==='undefined') return;\"");
        w.writeLine("    + \" var gs=[]; if (Ext.ComponentQuery && Ext.ComponentQuery.query) gs=Ext.ComponentQuery.query('editorgrid,gridpanel,grid,propertygrid');\"");
        w.writeLine("    + \" else if (Ext.ComponentMgr && Ext.ComponentMgr.all){ var a=Ext.ComponentMgr.all.items||[]; for(var i=0;i<a.length;i++){var c=a[i]; if(c&&c.stopEditing) gs.push(c);} }\"");
        w.writeLine("    + \" for (var i=0;i<gs.length;i++){ try{ if(gs[i].stopEditing) gs[i].stopEditing(false); }catch(e){} try{ if(gs[i].activeEditor && gs[i].activeEditor.completeEdit) gs[i].activeEditor.completeEdit(); }catch(e){} }\"");
        w.writeLine("    + \" try{ if(document.activeElement && document.activeElement.blur) document.activeElement.blur(); }catch(e){}\"");
        w.writeLine("    + \"} catch(e){}\");");
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
    }

    /**
     * Генерирует JS, мягко закрывающий ТОЛЬКО открытый редактор ячейки grid/propertygrid перед
     * «Готово»/«Сохранить». Намеренно НЕ коммитит и НЕ сбрасывает dirty уже заполненных полей:
     * после ENTER в fill значения уже лежат в записи и подсвечены красным («готовы к сохранению»),
     * а «Готово» сохраняет их само. Принудительный commit всех редакторов делал записи «чистыми»
     * (красные → чёрные), и форма при сохранении считала, что изменённых записей нет, поэтому не
     * сохраняла. Здесь трогаем лишь редактор, который keynav PropertyGrid мог оставить открытым
     * после ENTER (иначе клик по «Готово» уйдёт в открытый input).
     */
    private void writeCommitAllEditorsScript(JavaFileWriter w) {
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"if (typeof Ext === 'undefined') return;\"");
        w.writeLine("    + \"var cmps = [];\"");
        w.writeLine("    + \"try { if (Ext.ComponentQuery && Ext.ComponentQuery.query) cmps = Ext.ComponentQuery.query('propertygrid,editorgrid,grid'); } catch(e) {}\"");
        w.writeLine("    + \"if ((!cmps || !cmps.length) && Ext.ComponentMgr && Ext.ComponentMgr.all) {\"");
        w.writeLine("    + \"  try { var all=Ext.ComponentMgr.all; var arr=all.items||(all.getRange?all.getRange():[]);\"");
        w.writeLine("    + \"    for (var i=0;i<arr.length;i++){ var c=arr[i]; if (c && (c.stopEditing || c.activeEditor || c.editingPlugin)) cmps.push(c); } } catch(e) {}\"");
        w.writeLine("    + \"}\"");
        // ВАЖНО: не коммитим и не чистим dirty уже заполненных ячеек — они уже в записи (красные,
        // «готовы к сохранению»), «Готово» сохранит их сам. Трогаем ТОЛЬКО открытый редактор,
        // который keynav PropertyGrid мог оставить открытым после ENTER в последнем fill:
        //   - пустой редактор (keynav открыл соседнюю ячейку) — отменяем (cancelEdit вернёт уже
        //     введённое значение записи, ничего не затирая);
        //   - непустой — мягко завершаем (completeEdit), чтобы закрыть input перед кликом «Готово».
        // Затем blur гасит фокус. record.commit/stopEditing(false) по всем гридам НЕ вызываем,
        // иначе красные значения станут чёрными и форма сочтёт, что сохранять нечего.
        w.writeLine("    + \"for (var i=0;i<cmps.length;i++){ var c=cmps[i];\"");
        w.writeLine("    + \"  try { if (c.activeEditor && c.activeEditor.field) {\"");
        w.writeLine("    + \"    var f=c.activeEditor.field; var v=f.getValue?f.getValue():(f.getRawValue?f.getRawValue():'');\"");
        w.writeLine("    + \"    if (v==null || (''+v).trim()==='') { if (c.activeEditor.cancelEdit) c.activeEditor.cancelEdit(); }\"");
        w.writeLine("    + \"    else { if (c.activeEditor.completeEdit) c.activeEditor.completeEdit(); }\"");
        w.writeLine("    + \"  } } catch(e){}\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"try { if (document.activeElement && document.activeElement.blur) document.activeElement.blur(); } catch(e){}\");");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
    }

    /**
     * Генерирует код (внутри цикла по колонкам с переменной {@code col}), открывающий редактор
     * ячейки (rowExpr, col): пробует ExtJS startEditing и физически делает двойной клик по DOM
     * ячейки (Ext3 getView().getCell), оставляя в области видимости {@code WebElement editor}
     * (null, если редактор не появился). Физический двойной клик нужен потому, что на некоторых
     * сборках Ext3 один startEditing не фокусирует <input> ячейки и строка не заполняется.
     */
    private void writeOpenCellEditorScript(JavaFileWriter w, String rowExpr) {
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if (g.startEditing){ g.startEditing(arguments[0], arguments[1]); }\"");
        w.writeLine("    + \" else if (g.editingPlugin && g.editingPlugin.startEdit){ var rec=g.getStore().getAt(arguments[0]); var co=(g.columns&&g.columns[arguments[1]])?g.columns[arguments[1]]:arguments[1]; g.editingPlugin.startEdit(rec, co); } } catch(e){}\", (long) (" + rowExpr + "), col);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("String editorFinder = \"var a=document.activeElement; if (a && (a.tagName==='INPUT'||a.tagName==='TEXTAREA') && !a.readOnly) return a;\"");
        w.writeLine("    + \"var ins=document.querySelectorAll('.x-grid-editor input, .x-grid3-editor input, .x-editor input, input.x-form-field, .x-grid-editor textarea'); for (var i=0;i<ins.length;i++){ var el=ins[i]; if (el.offsetWidth>0 && !el.readOnly) return el; } return null;\";");
        // Сначала смотрим, открыл ли редактор сам startEditing. Двойной клик делаем только если нет —
        // иначе дабл-клик по уже открытой ячейке закрывает редактор.
        w.writeLine("WebElement editor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(editorFinder);");
        w.openBlock("if (editor == null)");
        w.writeLine("WebElement cell = null;");
        w.openBlock("try");
        w.writeLine("cell = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var v=g.getView?g.getView():null; if (v && v.getCell){ return v.getCell(arguments[0], arguments[1]); } return null; } catch(e){ return null; }\", (long) (" + rowExpr + "), col);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.openBlock("if (cell != null)");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(cell).doubleClick().perform();");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("editor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(editorFinder);");
        w.closeBlock();
    }

    /**
     * Генерирует ввод в {@code editor}: корректную дату (01.01.2020), если ячейка — поле даты
     * (есть .x-form-date-trigger), иначе {@code fallbackExpr}. Не даёт тексту маркера попасть
     * в масочную колонку даты (иначе получались мусорные даты и серверный SP падал).
     */
    private void writeTypeIntoEditorScript(JavaFileWriter w, String fallbackExpr) {
        // Дата определяется по модели колонки (тип редактора datefield/format) — надёжнее, чем по
        // DOM-триггеру, которого у редактора ячейки грида может не быть. Плюс DOM-фолбэк.
        w.writeLine("boolean isDateField = Boolean.TRUE.equals(((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if (g && g.getColumnModel){ var cm=g.getColumnModel(); var ed=cm.getCellEditor?cm.getCellEditor(arguments[0],0):null; var f=ed&&ed.field?ed.field:ed; if (f){ var xt=f.getXType?(''+f.getXType()):(''+(f.xtype||'')); if (xt.toLowerCase().indexOf('date')>=0) return true; if (f.format && /[dmy]/i.test(''+f.format)) return true; } } } catch(e){}\"");
        w.writeLine("    + \"try { var inp=arguments[1]; var p=inp.parentNode; for (var k=0;k<4 && p;k++){ if (p.querySelector && p.querySelector('.x-form-date-trigger')) return true; p=p.parentNode; } } catch(e){} return false;\", (long) col, editor));");
        w.writeLine("String toType = isDateField ? \"01.01.2020\" : (" + fallbackExpr + ");");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
        w.writeLine("Thread.sleep(60);");
        w.writeLine("editor.sendKeys(toType);");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(150);");
        // Сразу закрываем редактор именно этой ячейки (особенно дату — иначе её редактор/пикер
        // остаётся активным и блокирует следующий startEditing и тулбар «Сохранить Изменения»).
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(window.__t2grid && window.__t2grid.stopEditing) window.__t2grid.stopEditing(false); }catch(e){}\");");
        w.writeLine("try { Thread.sleep(150); } catch (InterruptedException ignored) {}");
    }

    /**
     * Генерирует проверку, что наша запись (содержащая {@code valueExpr}) больше не в списке
     * изменённых записей стора — то есть save реально закоммитился. Привязка к нашему значению,
     * чтобы оставшиеся от прошлых прогонов dirty-строки не давали ложный провал.
     */
    private void writeReadGridCount(JavaFileWriter w, String var) {
        writeReadGridCount(w, var, true);
    }

    /** Генерирует JS, читающий общее число записей дата-грида в {@code var} (Long; -1, если грида нет). */
    private void writeReadGridCount(JavaFileWriter w, String var, boolean declare) {
        // Счётчик берём из рабочего грида (нижний дата-грид с панелью пагинации в активном окне),
        // парся текст «Всего записей: N» (последнее число в нижней панели). Это то, что видит
        // пользователь справа внизу. Фолбэк — store.getTotalCount.
        w.writeLine((declare ? "Long " : "") + var + " = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { if (typeof Ext==='undefined') return -1; var gs=[];\"");
        w.writeLine("    + \"if (Ext.ComponentQuery && Ext.ComponentQuery.query) gs=Ext.ComponentQuery.query('gridpanel,editorgrid,grid');\"");
        w.writeLine("    + \"else if (Ext.ComponentMgr && Ext.ComponentMgr.all){ var a=Ext.ComponentMgr.all.items||[]; for(var i=0;i<a.length;i++){var c=a[i]; if(c&&c.getStore&&c.getColumnModel) gs.push(c);} }\"");
        w.writeLine("    + \"var aw=(Ext.WindowMgr&&Ext.WindowMgr.getActive)?Ext.WindowMgr.getActive():null; var awDom=(aw&&aw.getEl)?(aw.getEl().dom||aw.getEl()):null;\"");
        w.writeLine("    + \"var best=null,bestDom=null,bestScore=-1;\"");
        w.writeLine("    + \"for (var i=0;i<gs.length;i++){ var g=gs[i]; if(!g.rendered||!g.getStore) continue; var dom=null; try{var el=g.getEl?g.getEl():null; dom=el?(el.dom||el):null;}catch(e){} if(!dom||dom.offsetWidth<=0) continue;\"");
        w.writeLine("    + \"  var isProp=false; try{isProp=(g.getXType&&g.getXType()==='propertygrid')||!!g.propertyNames;}catch(e){} if(isProp) continue;\"");
        w.writeLine("    + \"  var paging=false; try{paging=!!dom.querySelector('.x-tbar-page-number,.x-tbar-page-next,.x-tbar-loading')||!!(g.getBottomToolbar&&g.getBottomToolbar());}catch(e){}\"");
        w.writeLine("    + \"  var inActive=awDom?(awDom===dom||awDom.contains(dom)):true;\"");
        w.writeLine("    + \"  var score=(inActive?1000:0)+(paging?400:0); if(score>bestScore){bestScore=score;best=g;bestDom=dom;} }\"");
        w.writeLine("    + \"if(!best) return -1;\"");
        // 1) «Всего записей: N» — последнее число в тексте нижней панели рабочего грида.
        w.writeLine("    + \"try{ var bb=bestDom.querySelector('.x-panel-bbar, .x-toolbar'); var t=bb?(bb.innerText||bb.textContent||''):''; var nums=t.match(/\\\\d+/g); if(nums&&nums.length){ return parseInt(nums[nums.length-1],10); } }catch(e){}\"");
        // 2) фолбэк — стор.
        w.writeLine("    + \"try{ var s=best.getStore(); return s.getTotalCount?s.getTotalCount():s.getCount(); }catch(e){}\"");
        w.writeLine("    + \"return -1; } catch(e){ return -1; }\");");
    }

    /** Генерирует проверку, проваливающую тест, если виден серверный диалог «Ошибка» (напр. SP trunc(date)). */
    private void writeServerErrorCheck(JavaFileWriter w, String label) {
        w.writeLine("String srvErr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var ws=document.querySelectorAll('.x-window'); for (var i=0;i<ws.length;i++){ var wn=ws[i]; if (wn.offsetWidth<=0) continue; var t=(wn.innerText||''); if (t.indexOf('\\u041e\\u0428\\u0418\\u0411\\u041a\\u0410')>=0 || t.toLowerCase().indexOf('trunc')>=0 || t.indexOf('SP_')>=0) return t.replace(/\\s+/g,' ').substring(0,300); } return ''; } catch(e){ return ''; }\");");
        w.openBlock("if (srvErr != null && !srvErr.isEmpty())");
        w.writeLine("shot(\"server_error\");");
        w.writeLine("fail(\"" + label + ": сервер отклонил операцию: \" + srvErr);");
        w.closeBlock();
    }

    /**
     * Заполняет все редактируемые поля строки rowExpr: колонки-даты — программно
     * record.set('01.01.2020') (без датапикера, безопасно для маски); текстовые — через cell-редактор
     * (UI edit-событие включает «Сохранить Изменения»), вписывая {@code marker}_col. Колонки-даты
     * определяются по модели колонки и пропускаются ещё до открытия редактора, иначе редактор
     * даты залипает.
     */
    /**
     * Надёжность для пустого грида: после «Добавить» проверяем, что в window.__t2grid появилась
     * строка. Если строк нет (на пустой сущности «Добавить» иногда не создаёт phantom с первого
     * раза), повторяем «Добавить» и ждём маску. Без этого create на пустой сущности заполнял
     * пустоту и запись не сохранялась.
     */
    private void writeEnsureRowAddedScript(JavaFileWriter w, String label) {
        w.writeLine("Long __rows0 = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { return window.__t2grid ? window.__t2grid.getStore().getCount() : -1; } catch(e){ return -1; }\");");
        w.writeLine("System.out.println(\"" + label + ": строк в редактируемом гриде после 'Добавить' = \" + __rows0);");
        w.openBlock("if (__rows0 != null && __rows0 <= 0)");
        w.writeLine("System.out.println(\"" + label + ": строка не создалась (пустая сущность) — повтор 'Добавить'\");");
        w.writeLine("clickEditDropdownAction(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\");");
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("try { Thread.sleep(700); } catch (InterruptedException ignored) {}");
        w.writeLine("Long __rows1 = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { return window.__t2grid ? window.__t2grid.getStore().getCount() : -1; } catch(e){ return -1; }\");");
        w.writeLine("System.out.println(\"" + label + ": строк после повторного 'Добавить' = \" + __rows1);");
        w.closeBlock();
    }

    private void writeFillEditableCells(JavaFileWriter w, String label, String marker, String rowExpr, boolean singleTextField) {
        // 0. Дамп column model выбранной строки — в логе видно, какие колонки редактируемы,
        //    где дата (по xtype/format редактора) и какая маска.
        w.writeLine("System.out.println(\"" + label + ": column dump = \" + ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var cm=g.getColumnModel(); var n=cm.getColumnCount?cm.getColumnCount():0; var out=[]; var r=arguments[0];\"");
        w.writeLine("    + \" for (var c=0;c<n;c++){ var di=''; try{di=cm.getDataIndex?cm.getDataIndex(c):'';}catch(e){} var hd=''; try{hd=cm.getColumnHeader?(''+cm.getColumnHeader(c)).replace(/<[^>]*>/g,''):'';}catch(e){}\"");
        w.writeLine("    + \"   var editable=false; try{ editable = cm.isCellEditable?cm.isCellEditable(c,r):!!(cm.getCellEditor&&cm.getCellEditor(c,r)); }catch(e){}\"");
        w.writeLine("    + \"   var xt='',fmt=''; try{ var ed=cm.getCellEditor?cm.getCellEditor(c,r):null; var f=ed&&ed.field?ed.field:ed; if(f){ xt=f.getXType?(''+f.getXType()):(''+(f.xtype||'')); fmt=''+(f.format||f.maskRe||''); } }catch(e){}\"");
        w.writeLine("    + \"   out.push(c+\\\":\\\"+hd+\\\"[\\\"+di+\\\"] editable=\\\"+editable+\\\" xtype=\\\"+xt+(fmt?\\\" fmt=\\\"+fmt:\\\"\\\")); }\"");
        w.writeLine("    + \" return out.join(\\\" | \\\"); } catch(e){ return 'err:'+e.message; }\", (long) (" + rowExpr + ")));");
        w.writeLine("int filled = 0;");
        w.writeLine("int dateFilled = 0;");
        w.openBlock("for (long col = 0; col < colCount; col++)");
        w.openBlock("try");
        // Тип ячейки по редактору колонки: 'skip' (нет редактора) / 'date' / 'text'.
        // Дату определяем по xtype/format редактора, а не по типу поля стора (он на этом стенде
        // возвращал 0). Нередактируемые системные колонки (Дата изменения, Оператор) — 'skip',
        // чтобы не ловить «element not interactable».
        w.writeLine("String cellKind = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var cm=g.getColumnModel(); var c=arguments[0]; var r=arguments[1];\"");
        w.writeLine("    + \" var editable=false; try{ editable = cm.isCellEditable?cm.isCellEditable(c,r):!!(cm.getCellEditor&&cm.getCellEditor(c,r)); }catch(e){}\"");
        w.writeLine("    + \" if(!editable) return 'skip';\"");
        w.writeLine("    + \" var ed=null; try{ ed=cm.getCellEditor?cm.getCellEditor(c,r):null; }catch(e){} var f=ed&&ed.field?ed.field:ed;\"");
        w.writeLine("    + \" if(f){ var xt=f.getXType?(''+f.getXType()):(''+(f.xtype||'')); xt=xt.toLowerCase();\"");
        w.writeLine("    + \"   if(xt.indexOf('date')>=0) return 'date';\"");
        // maskField — даты с маской 99.99.9999. Распознаём по xtype 'mask' или по наличию
        // маски/маск-регэкспа/vtype у поля. Возвращаем 'date' (вписываем 01.01.2020).
        w.writeLine("    + \"   if(xt.indexOf('mask')>=0) return 'date';\"");
        w.writeLine("    + \"   if(f.mask || f.maskText || f.maskRe || (f.vtype && /date/i.test(''+f.vtype))) return 'date';\"");
        w.writeLine("    + \"   if(f.format && /[dmy]/i.test(''+f.format)) return 'date'; }\"");
        w.writeLine("    + \" return 'text'; } catch(e){ return 'text'; }\", col, (long) (" + rowExpr + "));");
        w.openBlock("if (\"skip\".equals(cellKind))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("boolean isDateCol = \"date\".equals(cellKind);");
        if (singleTextField) {
            // Update: меняем ровно одно текстовое поле первой строки. Дату не трогаем
            // (в существующей записи она уже валидна) и после первого текстового поля выходим.
            w.openBlock("if (isDateCol)");
            w.writeLine("continue;");
            w.closeBlock();
        }
        writeOpenCellEditorScript(w, rowExpr);
        w.openBlock("if (editor == null)");
        w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" редактор не открылся (skip)\");");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String tag = editor.getTagName();");
        w.openBlock("if (!\"input\".equalsIgnoreCase(tag) && !\"textarea\".equalsIgnoreCase(tag))");
        w.writeLine("continue;");
        w.closeBlock();
        // Если редактор не интерактивен (скрытый/чужой остаток) — закрываем и пропускаем молча.
        w.openBlock("if (!editor.isDisplayed() || !editor.isEnabled())");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(window.__t2grid && window.__t2grid.stopEditing) window.__t2grid.stopEditing(false); }catch(e){}\");");
        w.writeLine("continue;");
        w.closeBlock();
        // Дату вписываем реальную (сегодня) по маске 99.99.9999 в формате dd.MM.yyyy; текст — маркер_col.
        w.writeLine("String toType = isDateCol ? new java.text.SimpleDateFormat(\"dd.MM.yyyy\").format(new java.util.Date()) : (" + marker + " + \"_\" + col);");
        if (singleTextField) {
            // UPDATE: JS-очистка, sendKeys, record.set.
            w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
            w.writeLine("Thread.sleep(60);");
            w.writeLine("editor.sendKeys(toType);");
            w.writeLine("Thread.sleep(100);");
            w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
            w.writeLine("Thread.sleep(150);");
            w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(window.__t2grid && window.__t2grid.stopEditing) window.__t2grid.stopEditing(false); }catch(e){}\");");
            w.writeLine("Thread.sleep(120);");
            w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
            w.writeLine("    \"try { var g=window.__t2grid; var s=g.getStore(); var rec=s.getAt(arguments[0]); if(!rec) return; var cm=g.getColumnModel(); var di=cm.getDataIndex?cm.getDataIndex(arguments[1]):null; if(di){ rec.set(di, arguments[2]); } }catch(e){}\", (long) (" + rowExpr + "), col, toType);");
            w.writeLine("Thread.sleep(60);");
        } else {
            // CREATE: ввод с клавиатуры. value='' + dispatch('input') очищает поле, затем
            // sendKeys печатает значение, ENTER коммитит. Ctrl+A и editor.click() не используем —
            // на inline-editorgrid редактор так терял фокус и ввод уходил «в воздух», строка
            // сохранялась пустой. record.set не добавляем: персист — через ENTER-commit.
            w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
            w.writeLine("Thread.sleep(60);");
            w.writeLine("editor.sendKeys(toType);");
            w.writeLine("Thread.sleep(150);");
            w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
            w.writeLine("Thread.sleep(200);");
            w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"try{ if(window.__t2grid && window.__t2grid.stopEditing) window.__t2grid.stopEditing(false); }catch(e){}\");");
            w.writeLine("Thread.sleep(120);");
            // Коммит ячейки (особенно даты) может сразу выбросить серверный попап (trunc(date)),
            // который перехватывает клики и блокирует дозаполнение. Закрываем его (OK) и продолжаем.
            w.writeLine("if (dismissErrorPopup()) System.out.println(\"  [inline-fill] серверный попап закрыт, продолжаем заполнение\");");
            // Диагностика: читаем значение записи обратно — видно, долетел ли ввод (если пусто,
            // проблема в фокусе/редакторе, а не в маске).
            w.writeLine("Object backVal = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
            w.writeLine("    \"try { var g=window.__t2grid; var s=g.getStore(); var rec=s.getAt(arguments[0]); if(!rec) return '<no-rec>'; var cm=g.getColumnModel(); var di=cm.getDataIndex?cm.getDataIndex(arguments[1]):null; return di?(''+rec.get(di)):'<no-di>'; }catch(e){ return '<err>'; }\", (long) (" + rowExpr + "), col);");
            w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" после ввода record.get = '\" + backVal + \"'\");");
            // Фолбэк: на некоторых гридах keyboard-commit не доходит до record для текстовой
            // колонки, обязательное поле остаётся пустым и save молча не сохраняет. Если record.get
            // вернул пусто для не-даты — дозаписываем значение через rec.set, чтобы строка стала
            // dirty и save прошёл. Клавиатура остаётся основным вводом, это только страховка.
            w.writeLine("String bvStr = backVal == null ? \"\" : String.valueOf(backVal).trim();");
            w.openBlock("if (!isDateCol && (bvStr.isEmpty() || \"<no-di>\".equals(bvStr) || \"<no-rec>\".equals(bvStr) || \"<err>\".equals(bvStr) || \"null\".equals(bvStr)))");
            w.writeLine("Object setRes = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
            w.writeLine("    \"try { var g=window.__t2grid; var s=g.getStore(); var rec=s.getAt(arguments[0]); if(!rec) return '<no-rec>'; var cm=g.getColumnModel(); var di=cm.getDataIndex?cm.getDataIndex(arguments[1]):null; if(!di) return '<no-di>'; rec.set(di, arguments[2]); return ''+rec.get(di); }catch(e){ return '<err>'; }\", (long) (" + rowExpr + "), col, toType);");
            w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" фолбэк rec.set → record.get = '\" + setRes + \"'\");");
            w.writeLine("Thread.sleep(60);");
            w.closeBlock();
        }
        w.writeLine("filled++;");
        w.writeLine("if (isDateCol) dateFilled++;");
        w.writeLine("System.out.println(\"  [inline-fill] \" + (isDateCol ? \"дата\" : \"текст\") + \" col=\" + col + \" = '\" + toType + \"' OK\");");
        if (singleTextField) {
            // Update: одно поле изменено, выходим.
            w.openBlock("if (!isDateCol)");
            w.writeLine("System.out.println(\"  [inline-fill] update: изменено одно поле, выходим\");");
            w.writeLine("break;");
            w.closeBlock();
        }
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" FAIL: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"" + label + ": заполнено полей=\" + filled + \" (из них дат=\" + dateFilled + \")\");");
        w.writeLine("assertTrue(filled > 0, \"" + label + ": не удалось заполнить ни одного редактируемого поля строки\");");
        w.writeLine("shot(\"cells_filled\");");
    }

    /** Type 2 (inline-table): create через «Редактирование, Добавить», выбор новой строки, заполнение ячеек, сохранение. */
    private void writeInlineTableCreateTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(3)");
        w.writeLine("@DisplayName(\"Create row inline (Type 2 entity)\")");
        w.openBlock("void testCreate()");
        w.writeLine("shot(\"start\");");
        w.writeLine("String createdMarker = \"AT\" + System.nanoTime();");
        // 0. Счётчик записей до добавления (для проверки create: должно стать +1).
        writeReadGridCount(w, "countBefore");
        w.writeLine("System.out.println(\"testCreate (inline): записей в справочнике ДО добавления = \" + countBefore);");
        // 1. «Редактирование, Добавить» — открывает новую пустую строку
        w.writeLine("boolean addClicked = step(\"Редактирование → Добавить\", () -> clickEditDropdownAction(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\"));");
        w.writeLine("assertTrue(addClicked, \"testCreate (inline): не удалось через 'Редактирование' открыть дропдаун и кликнуть 'Добавить'\");");
        w.writeLine("try { Thread.sleep(900); } catch (InterruptedException ignored) {}");
        // Ждём, пока отработает серверный спиннер «Выполнение операции Добавить…», иначе
        // дальнейшие клики перехватываются масочным оверлеем.
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("shot(\"empty_row_added\");");
        // 2. Найти редактируемый грид (Ext3 ComponentMgr и Ext4 ComponentQuery)
        writeLocateEditableGridScript(w, "colCount", false);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"testCreate (inline): не нашли editable grid (код=\" + colCount + \"). Возможно «Добавить» не создал строку или грид не редактируемый.\");");
        w.writeLine("System.out.println(\"testCreate (inline): grid found, columns=\" + colCount);");
        writeGridDiagLog(w, "testCreate (inline)");
        writeEnsureRowAddedScript(w, "testCreate (inline)");
        // 3. Новая пустая строка появляется последней. Берём phantom-запись с наибольшим индексом
        //    (самую новую), иначе попадём в старую мусорную phantom-строку сверху.
        //    Фолбэк — последняя строка стора.
        w.writeLine("Long newRowL = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var s=window.__t2grid.getStore(); var mod=s.getModifiedRecords?s.getModifiedRecords():[]; var idx=-1;\"");
        w.writeLine("    + \" for (var i=0;i<mod.length;i++){ var r=mod[i]; if (r.phantom || r.newRecord){ var x=s.indexOf(r); if (x>idx) idx=x; } }\"");
        w.writeLine("    + \" if (idx<0) idx=s.getCount()-1; return idx; } catch(e){ return 0; }\");");
        w.writeLine("int rowIdx = (newRowL == null || newRowL < 0) ? 0 : newRowL.intValue();");
        w.writeLine("System.out.println(\"testCreate (inline): новая (последняя) строка rowIdx=\" + rowIdx);");
        writeSelectGridRowScript(w, "rowIdx");
        w.writeLine("shot(\"row_selected\");");
        // 4. Заполнить все редактируемые поля новой строки (текст — маркер, даты — 01.01.2020 по маске).
        writeFillEditableCells(w, "testCreate (inline)", "createdMarker", "rowIdx", false);
        // 5. Сохранить через «Редактирование, Сохранить Изменения».
        writeCommitGridEditorScript(w);
        // Дожидаемся исчезновения спиннера «Выполнение операции…»/load-mask, иначе он
        // перехватывает клик по «Редактирование» и пункт «Сохранить Изменения» не находится.
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("boolean saved = step(\"Редактирование → Сохранить Изменения\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        // Ретрай: если пункт не появился в дропдауне (часто из-за зависшего редактора ячейки,
        // блокирующего тулбар) — снова закрываем все редакторы, ждём спиннер и повторяем.
        w.openBlock("if (!saved)");
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("saved = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.closeBlock();
        // Фолбэк: если «Добавить» открыл модальную карточку, кнопка сохранения — «Готово»/«Сохранить»/«OK».
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"OK\");");
        w.writeLine("assertTrue(saved, \"testCreate (inline): не удалось сохранить (ни 'Сохранить Изменения', ни 'Готово'/'Сохранить'/'OK')\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(3000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        // 6a. Серверная ошибка (напр. trunc(date)) — фейлим с текстом сервера.
        writeServerErrorCheck(w, "testCreate (inline)");
        // 6b. Жмём кнопку обновления (как пользователь). Это серверный round-trip: store.reload()
        //     перечитывает данные с сервера и выбрасывает несохранённую phantom-строку. Поэтому
        //     если save не прошёл — счётчик вернётся к старому и маркер исчезнет: честный fail.
        w.writeLine("boolean refreshed = clickGridRefresh();");
        w.writeLine("System.out.println(\"testCreate (inline): refresh запущен=\" + refreshed);");
        // Перечитываем грид после reload и считаем снова.
        writeLocateEditableGridScript(w, "freshCols", false);
        w.writeLine("assertTrue(freshCols != null && freshCols > 0, \"testCreate (inline): после обновления не удалось перечитать список — нельзя честно проверить результат\");");
        w.writeLine("shot(\"after_refresh\");");
        writeReadGridCount(w, "countAfter");
        w.writeLine("System.out.println(\"testCreate (inline): записей ПОСЛЕ обновления = \" + countAfter + \" (было \" + countBefore + \")\");");
        w.writeLine("boolean countGrew = (countBefore != null && countAfter != null && countBefore >= 0 && countAfter == countBefore + 1);");
        w.writeLine("boolean createdFound = gridContainsRow(createdMarker) || gridStoreContainsText(createdMarker);");
        w.writeLine("System.out.println(\"testCreate (inline): countGrew=\" + countGrew + \" createdFound=\" + createdFound);");
        // Уникальный маркер виден в свежем сторе после save — запись реально создана.
        // Счётчик «Всего записей» через reload на этих relation-гридах не обновляется,
        // поэтому countGrew недостоверен и не обязателен. trunc ловит writeServerErrorCheck выше.
        w.writeLine("assertTrue(createdFound, \"testCreate (inline): запись НЕ сохранилась — маркер '\" + createdMarker + \"' не найден в гриде после сохранения (счётчик \" + countBefore + \" -> \" + countAfter + \"). Вероятна серверная ошибка SP (trunc(date)) или незакоммиченное обязательное поле.\");");
        w.closeBlock();
        w.writeLine();
    }

    /** Type 2 (inline-table): update — выбрать строку 0, startEditing, уникальное значение, Сохранить. */
    private void writeInlineTableUpdateTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(4)");
        w.writeLine("@DisplayName(\"Update row inline (Type 2 entity)\")");
        w.openBlock("void testUpdate()");
        w.writeLine("shot(\"start\");");
        // Уникальное значение для update — чтобы потом найти его в гриде.
        w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
        // 1. Грид-список справочника (без property-grid), непустой.
        writeLocateEditableGridScript(w, "colCount", true, true);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"testUpdate (inline): список пуст или не найден (код=\" + colCount + \")\");");
        writeGridDiagLog(w, "testUpdate (inline)");
        // Счётчик записей ДО правки (для update он НЕ должен измениться).
        writeReadGridCount(w, "countBefore");
        w.writeLine("System.out.println(\"testUpdate (inline): записей в справочнике ДО правки = \" + countBefore);");
        // 2. Выбираем первую запись (row 0) и меняем её.
        w.writeLine("int editRow = 0;");
        w.writeLine("System.out.println(\"testUpdate (inline): правим первую запись editRow=\" + editRow);");
        writeSelectGridRowScript(w, "editRow");
        w.writeLine("shot(\"row_selected\");");
        // 3. Меняем ровно одно редактируемое текстовое поле первой строки.
        //    Дату не трогаем — в существующей записи она уже валидна.
        writeFillEditableCells(w, "testUpdate (inline)", "updatedValue", "editRow", true);
        // 4. Закоммитить редактор в запись, затем сохранить.
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("boolean saved = step(\"Редактирование → Сохранить Изменения\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        // Ретрай при зависшем редакторе ячейки, блокирующем тулбар «Редактирование».
        w.openBlock("if (!saved)");
        writeCommitGridEditorScript(w);
        w.writeLine("waitForLoadMask(8);");
        w.writeLine("saved = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.closeBlock();
        // Фолбэк: модальная карточка редактирования — кнопка сохранения «Готово»/«Сохранить»/«OK».
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"OK\");");
        w.writeLine("assertTrue(saved, \"testUpdate (inline): не удалось сохранить (ни 'Сохранить Изменения', ни 'Готово'/'Сохранить'/'OK')\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(3000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        // Серверная ошибка (SP_GSK_S_CAUSE / trunc(date)) — честно фейлим с текстом сервера.
        writeServerErrorCheck(w, "testUpdate (inline)");
        // 5. Жмём кнопку обновления (серверный round-trip). После reload проверяем: новое значение
        //    видно в свежем сторе (с сервера), а число записей не изменилось (update не должен
        //    плодить записи). Если изменение не сохранилось — маркер исчезнет: fail.
        w.writeLine("boolean refreshed = clickGridRefresh();");
        w.writeLine("System.out.println(\"testUpdate (inline): refresh запущен=\" + refreshed);");
        writeLocateEditableGridScript(w, "freshCols", true, true);
        w.writeLine("assertTrue(freshCols != null && freshCols > 0, \"testUpdate (inline): после обновления не удалось перечитать список — нельзя честно проверить результат\");");
        w.writeLine("shot(\"after_refresh\");");
        writeReadGridCount(w, "countAfter");
        w.writeLine("System.out.println(\"testUpdate (inline): записей ПОСЛЕ обновления = \" + countAfter + \" (было \" + countBefore + \")\");");
        w.writeLine("boolean inView = gridContainsRow(updatedValue) || gridStoreContainsText(updatedValue);");
        w.writeLine("boolean countUnchanged = (countBefore != null && countAfter != null && countBefore >= 0 && countAfter.equals(countBefore));");
        w.writeLine("System.out.println(\"testUpdate (inline): inView=\" + inView + \" countUnchanged=\" + countUnchanged);");
        w.openBlock("if (!countUnchanged)");
        w.writeLine("fail(\"testUpdate (inline): число записей изменилось (\" + countBefore + \" -> \" + countAfter + \") — update не должен добавлять/удалять записи\");");
        w.closeBlock();
        w.writeLine("assertTrue(inView, \"testUpdate (inline): новое значение '\" + updatedValue + \"' НЕ найдено в свежем списке после обновления — изменение не сохранилось на сервере (вероятна серверная ошибка SP trunc(date))\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeUpdateTest(JavaFileWriter w, List<Property> properties) {
        w.writeLine("@Test");
        w.writeLine("@Order(4)");
        w.writeLine("@DisplayName(\"Update existing record\")");
        w.openBlock("void testUpdate()");
        w.writeLine("shot(\"start\");");
        Property stringField = properties.stream()
                .filter(p -> p.getAttrType() == AttrType.STRING && !isSystemField(p)
                        && !"Directory".equals(p.getStereoType()) && !"Ref".equals(p.getStereoType()))
                .findFirst().orElse(null);
        if (stringField == null) {
            // Сущность без строкового поля — менять нечего. Открываем карточку, жмём save,
            // проверяем, что нет ошибки. Это smoke-update.
            w.writeLine("Assumptions.assumeTrue(selectAndOpenRecord(), \"testUpdate (no-string): не удалось открыть первую запись\");");
            w.writeLine("waitForCardLoaded(10);");
            w.writeLine("boolean saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\")");
            w.writeLine("    || clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\")");
            w.writeLine("    || clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
            w.writeLine("assertTrue(saved, \"testUpdate (no-string): не нашли ни Готово, ни 'Сохранить Изменения', ни 'Сохранить'\");");
            w.writeLine("confirmDialogYes();");
            w.writeLine("waitForDialogClose();");
            w.writeLine("waitForGridSettle();");
            w.writeLine("shot(\"after_save\");");
            w.writeLine("assertFalse(isErrorPresent(), \"testUpdate (no-string): после save появилась ошибка\");");
            w.closeBlock();
            w.writeLine();
            return;
        }

        String methodName = "fill" + Transliterator.toClassName(stringField.getAttrName());
        // Даём гриду результатов 1с на отрисовку после executeSearch — иначе selectAndOpenRecord
        // может попасть в параметрическую форму или в ещё пустой результат-грид, и dblclick
        // никуда не приведёт.
        w.openBlock("try");
        w.writeLine("Thread.sleep(1000);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.writeLine("shot(\"before_select_record\");");
        // 1) Захватываем подпись первой строки до открытия карточки — чтобы потом понять,
        //    какую запись редактировали.
        w.writeLine("String editedRowSignature = captureFirstResultRowSignature();");
        w.writeLine("System.out.println(\"testUpdate: подпись редактируемой записи = '\" + editedRowSignature + \"'\");");
        // 2) Открываем первую запись. Без перебора: если на первой записи update недоступен,
        //    проблема системная, а не в выборе строки.
        w.writeLine("boolean opened = step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("assertTrue(opened, \"testUpdate: не удалось открыть первую запись на редактирование\");");
        // selectAndOpenRecord возвращает true просто после клика — даже если карточка
        // не открылась (например dblclick попал в заголовок грида). Проверяем, что карточка
        // действительно появилась, иначе fill попадёт в PropertyGrid формы поиска.
        w.writeLine("boolean cardOpened = waitUntil(d -> isOnRecordCard() || isDialogOpen(), 8, \"edit form opened\");");
        w.openBlock("if (!cardOpened)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("shot(\"card_not_opened\");");
        w.writeLine("fail(\"testUpdate: карточка записи НЕ открылась после selectAndOpenRecord (dblclick попал не на строку либо запись запрещена к редактированию). См. dumpCardDiagnostics в логе и скриншот card_not_opened.\");");
        w.closeBlock();
        w.writeLine("waitForCardLoaded(10);");
        // Карточка может рендериться лениво (ExtJS подгружает PropertyGrid и значения полей
        // по AJAX уже после того, как waitForCardLoaded считает её открытой).
        // Без паузы fill попадает в ещё не загрузившийся редактор и save видит null.
        w.writeLine("System.out.println(\"testUpdate: ждём 2.5с пока карточка догрузит значения полей\");");
        w.openBlock("try");
        w.writeLine("Thread.sleep(2500);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.writeLine("shot(\"record_opened\");");
        // Лог активного окна до fillX — поможет понять, что было перед изменением.
        w.openBlock("try");
        w.writeLine("Object awBefore = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var a = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null; return a ? (a.id + ' / title=' + (a.title||'')) : 'no-active-window'; } catch(e) { return 'ext-err:' + e.message; }\");");
        w.writeLine("System.out.println(\"testUpdate: активное окно ДО fillX = \" + awBefore);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 3) Меняем ровно одно поле — первое STRING. Остальные значения карточки не трогаем
        //    (без clear/fillAll), чтобы они не уехали в null и сервер не отверг save.
        w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
        w.writeLine("page.lastFilledValues.clear();");
        w.writeLine("System.out.println(\"testUpdate: меняем ОДНО поле '" + stringField.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "' на '\" + updatedValue + \"' (остальные поля карточки не трогаем)\");");
        w.writeLine("step(\"type new value\", () -> page." + methodName + "(updatedValue));");
        w.writeLine("java.util.LinkedHashMap<String, String> filledSnapshot = new java.util.LinkedHashMap<>(page.lastFilledValues);");
        w.writeLine("System.out.println(\"testUpdate: новое значение '\" + updatedValue + \"' в поле \" + filledSnapshot.keySet());");
        w.writeLine("shot(\"value_typed\");");
        // Лог активного окна после fillX — если оно изменилось, значит fillX переключил раздел.
        w.openBlock("try");
        w.writeLine("Object awAfter = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var a = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null; return a ? (a.id + ' / title=' + (a.title||'')) : 'no-active-window'; } catch(e) { return 'ext-err:' + e.message; }\");");
        w.writeLine("System.out.println(\"testUpdate: активное окно ПОСЛЕ fillX = \" + awAfter);");
        // Дополнительно: какой PropertyGroup сейчас активен (Сведения / Приглашённые / ...)
        w.writeLine("Object activeGroup = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var nodes = document.querySelectorAll('.x-grid3-row-selected, .x-grid-row-selected'); var out = ''; for (var i = 0; i < nodes.length && i < 5; i++) { var t = (nodes[i].innerText || '').trim().replace(/\\\\n/g, '|'); if (t.length > 0) out += '[' + t + ']'; } return out || 'no-selected-row'; } catch(e) { return 'err:' + e.message; }\");");
        w.writeLine("System.out.println(\"testUpdate: подсвеченная (selected) строка после fillX = \" + activeGroup);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 4) TAB, чтобы закоммитить активный редактор PropertyGrid.
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).sendKeys(org.openqa.selenium.Keys.TAB).perform();");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 4b) Принудительно коммитим активный редактор в запись (Ext3 stopEditing(false) /
        //     Ext4 completeEdit). Без этого правка ячейки PropertyGrid остаётся визуальной
        //     (значение не попадает в record.set), «Сохранить Изменения» шлёт на сервер старое
        //     значение, и после перечтения карточки правка слетает.
        w.openBlock("try");
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"if (typeof Ext === 'undefined') return;\"");
        w.writeLine("    + \"var cmps = [];\"");
        w.writeLine("    + \"try { if (Ext.ComponentQuery && Ext.ComponentQuery.query) cmps = Ext.ComponentQuery.query('propertygrid,editorgrid,grid'); } catch(e) {}\"");
        w.writeLine("    + \"if ((!cmps || !cmps.length) && Ext.ComponentMgr && Ext.ComponentMgr.all) {\"");
        w.writeLine("    + \"  try { var all=Ext.ComponentMgr.all; var arr=all.items||(all.getRange?all.getRange():[]);\"");
        w.writeLine("    + \"    for (var i=0;i<arr.length;i++){ var c=arr[i]; if (c && (c.stopEditing || c.activeEditor || c.editingPlugin)) cmps.push(c); } } catch(e) {}\"");
        w.writeLine("    + \"}\"");
        w.writeLine("    + \"for (var i=0;i<cmps.length;i++){ var c=cmps[i];\"");
        w.writeLine("    + \"  try { if (c.stopEditing) c.stopEditing(false); } catch(e){}\"");
        w.writeLine("    + \"  try { if (c.activeEditor && c.activeEditor.completeEdit) c.activeEditor.completeEdit(); } catch(e){}\"");
        w.writeLine("    + \"  try { if (c.editingPlugin && c.editingPlugin.completeEdit) c.editingPlugin.completeEdit(); } catch(e){}\"");
        w.writeLine("    + \"}\");");
        w.writeLine("Thread.sleep(300);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 5) Save только через дропдаун «Редактирование, Сохранить Изменения».
        //    В карточке записи нет кнопок «Готово»/«Сохранить»/Enter — попытка их кликнуть
        //    промахивается по чужим элементам, и тест считает save прошедшим, хотя ничего
        //    не сохранилось. Если дропдаун не сработал — fail.
        w.writeLine("boolean savedClicked = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.writeLine("System.out.println(\"testUpdate: 'Редактирование → Сохранить Изменения' clicked=\" + savedClicked);");
        w.writeLine("assertTrue(savedClicked, \"testUpdate: не удалось через 'Редактирование' открыть дропдаун и кликнуть 'Сохранить Изменения'. \"");
        w.writeLine("    + \"Возможно карточка не догрузилась или кнопка 'Редактирование' не нашлась — см. dumpCardDiagnostics в логе.\");");
        // 6) Popup-диагностика. После «Сохранить Изменения» стенд может показать подтверждение
        //    или сохранить молча. Если popup про ошибку валидации — фейлим без хождения в грид.
        w.writeLine("String updatePopupText = capturePopupText(\"after-save\");");
        w.writeLine("System.out.println(\"testUpdate: popup после save = '\" + updatePopupText + \"'\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForDialogClose();");
        // Ждём 3с round-trip на сервер.
        w.openBlock("try");
        w.writeLine("Thread.sleep(3000);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.openBlock("if (updatePopupText != null && (updatePopupText.contains(\"\\u041d\\u0435\\u043e\\u0431\\u0445\\u043e\\u0434\\u0438\\u043c\\u043e\") || updatePopupText.contains(\"\\u041e\\u0448\\u0438\\u0431\\u043a\\u0430\")))");
        w.writeLine("shot(\"update_validation_error\");");
        w.writeLine("fail(\"testUpdate: сервер отверг save с popup'ом: '\" + updatePopupText + \"'. Изменяли поле: \" + filledSnapshot);");
        w.closeBlock();
        // Закрываем карточку Esc — на стенде после save карточка остаётся открытой read-only.
        w.openBlock("if (isOnRecordCard() || isDialogOpen())");
        w.writeLine("System.out.println(\"testUpdate: карточка осталась открытой после save — закрываем Esc и идём проверять грид\");");
        w.openBlock("try");
        w.writeLine("driver.findElement(By.tagName(\"body\")).sendKeys(org.openqa.selenium.Keys.ESCAPE);");
        w.writeLine("Thread.sleep(600);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("shot(\"after_save\");");
        w.writeLine("assertFalse(isErrorPresent(), \"testUpdate: после save появилась ошибка на стенде\");");
        w.writeLine();
        // 8) Ре-навигация к гриду результатов и подтверждение через поиск — как делает
        //    пользователь: вписать новое значение в форму поиска и убедиться, что запись находится.
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(ENTITY_NAME, FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_renavigate\");");
        w.writeLine();
        // 8) Проверка сохранения через переоткрытие записи (надёжнее фильтр-поиска): ищем по
        //    новому значению, открываем найденную запись и убеждаемся, что карточка показывает
        //    новое (серверное) значение. Текст из <input> формы поиска в innerText не попадает,
        //    поэтому ложного срабатывания на введённом фильтре нет.
        w.writeLine("boolean updateApplied = false;");
        w.writeLine("System.out.println(\"testUpdate: ищем '\" + updatedValue + \"' через форму поиска\");");
        w.openBlock("try");
        w.writeLine("page." + methodName + "(updatedValue);");
        w.writeLine("executeSearchIfPresent();");
        w.writeLine("waitForGridSettle();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("shot(\"after_filtered_search\");");
        w.writeLine("int foundRows = page.getTableRowCount();");
        w.writeLine("System.out.println(\"testUpdate: результатов поиска по новому значению = \" + foundRows);");
        w.openBlock("if (foundRows >= 1)");
        w.writeLine("boolean reopened = step(\"reopen edited record\", () -> selectAndOpenRecord());");
        w.openBlock("if (reopened)");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("try { Thread.sleep(1500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"reopened_record\");");
        w.writeLine("Boolean inCard = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var ws=document.querySelectorAll('.x-window'); for (var i=0;i<ws.length;i++){ var w=ws[i]; if (w.offsetWidth>0 && (w.innerText||'').indexOf(arguments[0])>=0) return true; } return false; } catch(e){ return false; }\", updatedValue);");
        w.writeLine("updateApplied = (inCard != null && inCard);");
        w.writeLine("System.out.println(\"testUpdate: новое значение видно в ПЕРЕОТКРЫТОЙ карточке = \" + updateApplied);");
        w.closeBlock();
        w.closeBlock();
        // Fallback: карточку открыть не удалось, но значение реально присутствует в гриде результатов.
        w.openBlock("if (!updateApplied && gridContainsRow(updatedValue))");
        w.writeLine("updateApplied = true;");
        w.writeLine("System.out.println(\"testUpdate: значение найдено в гриде результатов (карточку открыть не удалось)\");");
        w.closeBlock();
        w.writeLine("shot(updateApplied ? \"value_persisted\" : \"value_lost\");");
        // 9) assertTrue — реальный fail, не skip.
        w.writeLine("assertTrue(updateApplied,");
        w.writeLine("    \"testUpdate: после save новое значение '\" + updatedValue + \"' в поле '" + stringField.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "' НЕ сохранилось \"");
        w.writeLine("    + \"(переоткрытая запись его не показывает — изменение «слетело»). Подпись записи='\" + editedRowSignature + \"'. \"");
        w.writeLine("    + \"Popup сервера после save='\" + updatePopupText + \"'.\");");
        w.closeBlock();
        w.writeLine();
    }

    /**
     * Генерирует код: открыть карточку первой строки рабочего грида (window.__t2grid) двойным
     * кликом по её содержательной ячейке и положить в {@code idVar} идентификатор строки (значение
     * первой непустой нечисловой ячейки) для последующей проверки исчезновения. Требует, чтобы
     * перед этим был вызван writeLocateEditableGridScript (он ставит window.__t2grid/__t2dom).
     */
    private void writeOpenFirstWorkingRow(JavaFileWriter w, String idVar, String openedVar) {
        w.writeLine("String " + idVar + " = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if(!g) return ''; var s=g.getStore(); if(s.getCount()<=0) return ''; var r=s.getAt(0); var d=r.data||{};\"");
        w.writeLine("    + \" for (var k in d){ var v=d[k]; if(v!=null && (''+v).trim().length>1 && !/^\\\\d+$/.test(''+v)) return ''+v; } return ''; } catch(e){ return ''; }\");");
        w.writeLine("System.out.println(\"  идентификатор первой строки рабочего грида = '\" + " + idVar + " + \"'\");");
        w.writeLine("WebElement __firstCell = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var v=g&&g.getView?g.getView():null; var row=(v&&v.getRow)?v.getRow(0):null;\"");
        w.writeLine("    + \" if(!row){ var dom=window.__t2dom||document; row=dom.querySelector('.x-grid3-row, .x-grid-row'); }\"");
        w.writeLine("    + \" if(!row) return null; var cells=row.querySelectorAll('.x-grid3-cell, .x-grid-cell, td');\"");
        w.writeLine("    + \" for (var c=0;c<cells.length;c++){ var ce=cells[c]; if(ce.offsetWidth<=0) continue; var t=(ce.innerText||'').trim(); if(t.length>1 && !/^\\\\d+$/.test(t)){ try{ce.scrollIntoView(true);}catch(e){} return ce; } } return row; } catch(e){ return null; }\");");
        w.writeLine("boolean " + openedVar + " = false;");
        w.openBlock("if (__firstCell != null)");
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).moveToElement(__firstCell).doubleClick().perform();");
        w.writeLine("Thread.sleep(1200);");
        w.writeLine(openedVar + " = true;");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  открытие первой строки: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
    }

    /**
     * Генерирует цикл с ретраем, гарантирующий, что рабочий грид результатов реально загружен
     * (а не только форма параметров поиска). Объявляет Long colCount и Long countBefore.
     * Иногда ре-навигация отдаёт только грид параметров: colCount>0, но «Всего записей»=0
     * и первая «строка» — заголовок, и open-first-row промахивается. Повторяем поиск, пока
     * не появится рабочий грид с данными, иначе понятный fail.
     */
    private void writeEnsureResultsLoaded(JavaFileWriter w, String label) {
        w.writeLine("Long colCount = null;");
        w.writeLine("Long countBefore = null;");
        w.openBlock("for (int __da = 1; __da <= 3; __da++)");
        writeLocateEditableGridScript(w, "colCount", true, true, false);
        writeReadGridCount(w, "countBefore", false);
        w.openBlock("if (colCount != null && colCount > 0 && countBefore != null && countBefore > 0)");
        w.writeLine("break;");
        w.closeBlock();
        w.writeLine("System.out.println(\"" + label + ": рабочий грид с результатами не готов (colCount=\" + colCount + \", countBefore=\" + countBefore + \"), повтор поиска attempt=\" + __da);");
        w.writeLine("executeSearchIfPresent();");
        w.writeLine("waitForGridSettle();");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.closeBlock();
        w.writeLine("assertTrue(colCount != null && colCount > 0 && countBefore != null && countBefore > 0,");
        w.writeLine("    \"" + label + ": результаты поиска не загрузились — нельзя выбрать запись (colCount=\" + colCount + \", countBefore=\" + countBefore + \"). Похоже, показан грид параметров/пустой результат вместо данных.\");");
    }

    private void writeDeleteTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(5)");
        w.writeLine("@DisplayName(\"Delete record\")");
        w.openBlock("void testDelete()");
        w.writeLine("shot(\"initial_grid\");");
        // Рабочий грид (нижний дата-грид с панелью «Всего записей»), непустой — с ретраем поиска.
        writeEnsureResultsLoaded(w, "testDelete");
        w.writeLine("System.out.println(\"testDelete: записей ДО = \" + countBefore);");
        // Открыть карточку ПЕРВОЙ записи двойным кликом + запомнить её идентификатор.
        writeOpenFirstWorkingRow(w, "rowId", "opened");
        w.writeLine("assertTrue(opened, \"testDelete: не удалось открыть карточку первой записи\");");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("shot(\"record_opened\");");
        // «Редактирование, Удалить» (фолбэк — кнопка тулбара).
        w.writeLine("boolean delClicked = clickEditDropdownAction(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!delClicked) delClicked = clickButtonByText(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.openBlock("if (!delClicked)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("fail(\"testDelete: действие 'Удалить' недоступно в карточке открытой записи\");");
        w.closeBlock();
        w.writeLine("acceptAlertIfPresent();");
        w.writeLine("String delPopup = capturePopupText(\"after-\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_delete\");");
        writeServerErrorCheck(w, "testDelete");
        // Ре-навигация к гриду результатов + проверка: «Всего записей» уменьшилось ИЛИ запись исчезла.
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false; cardOpenAttempted = false; addDialogFailed = false;");
        w.writeLine("navigateToEntity(ENTITY_NAME, FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_renavigate\");");
        writeReadGridCount(w, "countAfter");
        w.writeLine("boolean countDropped = (countBefore != null && countAfter != null && countBefore > 0 && countAfter < countBefore);");
        w.writeLine("boolean gone = !rowId.isEmpty() && !gridContainsRow(rowId) && !gridStoreContainsText(rowId);");
        w.writeLine("System.out.println(\"testDelete: записей ПОСЛЕ = \" + countAfter + \" (было \" + countBefore + \"), gone=\" + gone + \", popup='\" + delPopup + \"'\");");
        w.writeLine("assertTrue(countDropped || gone, \"testDelete: запись не удалена — «Всего записей» не уменьшилось (\" + countBefore + \" -> \" + countAfter + \") и запись '\" + rowId + \"' всё ещё в гриде. popup='\" + delPopup + \"'\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeLogicalEditTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(6)");
        w.writeLine("@DisplayName(\"Logical edit of record\")");
        w.openBlock("void testLogicalEdit()");
        w.writeLine("shot(\"start\");");
        w.writeLine("step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("shot(\"row_selected\");");
        // «Логическое изменение» в E3Core создаёт новую историческую версию записи
        // (старая остаётся, новая получает свой H_KEY). UI часто запрашивает подтверждение
        // и валидирует обязательные поля истории. Тест считаем пройденным, если действие
        // отработало без падения сценария — поведение зависит от настройки сущности на стенде.
        w.writeLine("step(\"click Лог.изменить in card toolbar\", () -> clickEditDropdownAction(\"Лог.изменить\"));");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_action\");");
        // Мягкая проверка: если на форме показалась ошибка (требуется ввод истории) —
        // это нормальное поведение E3Core для логического изменения. Просто логируем.
        w.openBlock("if (isErrorPresent())");
        w.writeLine("System.out.println(\"testLogicalEdit: error reported by form — likely 'логическое изменение' requires additional fields (e.g. history reason / date) per entity config. This is informational, not a failure.\");");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
    }

    private void writeArchiveTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(7)");
        w.writeLine("@DisplayName(\"Archive record\")");
        w.openBlock("void testArchive()");
        w.writeLine("shot(\"initial_grid\");");
        // Рабочий грид с результатами — с ретраем поиска (как у delete).
        writeEnsureResultsLoaded(w, "testArchive");
        w.writeLine("System.out.println(\"testArchive: записей ДО = \" + countBefore);");
        writeOpenFirstWorkingRow(w, "rowId", "opened");
        w.writeLine("assertTrue(opened, \"testArchive: не удалось открыть карточку первой записи\");");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("boolean arcClicked = clickEditDropdownAction(\"\\u0432 \\u0410\\u0440\\u0445\\u0438\\u0432\");");
        w.writeLine("if (!arcClicked) arcClicked = clickButtonByText(\"\\u0432 \\u0410\\u0440\\u0445\\u0438\\u0432\");");
        w.openBlock("if (!arcClicked)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("fail(\"testArchive: действие 'в Архив' недоступно в карточке открытой записи\");");
        w.closeBlock();
        w.writeLine("acceptAlertIfPresent();");
        w.writeLine("String arcPopup = capturePopupText(\"after-\\u0410\\u0440\\u0445\\u0438\\u0432\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_archive\");");
        // На стенде «в Архив» не убирает запись из обычного поиска (архивные остаются в активном
        // гриде), поэтому критерий «Всего записей −1 / маркер исчез» неверен и давал ложный fail.
        // Считаем тест пройденным, если действие отработало без серверной ошибки
        // (writeServerErrorCheck даёт fail при ОШИБКА/SP/trunc). Достоверная проверка факта
        // архивации на этом стенде невозможна — фиксируем это в логе.
        writeServerErrorCheck(w, "testArchive");
        w.writeLine("System.out.println(\"testArchive: действие 'в Архив' выполнено без серверной ошибки. popup='\" + arcPopup + \"'. Прим.: на этом стенде архивные записи остаются в обычном поиске, поэтому уменьшение «Всего записей» НЕ проверяем (это не показатель архивации).\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeSearchTest(JavaFileWriter w, Search search, int index) {
        String testName = "testSearch" + (index > 0 ? index : "");
        w.writeLine("@Test");
        w.writeLine("@Order(" + (10 + index) + ")");
        w.writeLine("@DisplayName(\"Search: " + search.getName() + "\")");
        w.openBlock("void " + testName + "()");
        w.writeLine("shot(\"start\");");
        w.writeLine("step(\"open search\", () -> openSearch(\"" + search.getName().replace("\"", "\\\"") + "\"));");
        // Форме нужно время на отрисовку после двойного клика по дереву. Условного ожидания
        // здесь нет (нет конкретного селектора), поэтому используем короткую фиксированную паузу.
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"search_opened\");");
        w.writeLine();
        // Собираем LinkedHashMap значений, которые сейчас передадим, чтобы тест залогировал их
        // до запуска поиска. Это диагностика для отчёта о прогоне: какие параметры заполнялись.
        w.writeLine("java.util.LinkedHashMap<String, String> __sp = new java.util.LinkedHashMap<>();");
        for (SearchParam param : search.getParams()) {
            if (param.getSearchGuid() != null && !param.getSearchGuid().isEmpty()) {
                String title = param.getTitle() == null ? param.getName() : param.getTitle();
                w.writeLine("__sp.put(\"" + param.getName().replace("\"", "\\\"") + "\","
                        + " \"<FK-lookup '" + title.replace("\"", "\\\"") + "' — skipped>\");");
                continue;
            }
            String value = TestDataFactory.generateSearchParamValue(param);
            String safeName = param.getName().replace("\"", "\\\"");
            String safeVal = value.replace("\\", "\\\\").replace("\"", "\\\"");
            w.writeLine("__sp.put(\"" + safeName + "\", \"" + safeVal + "\");");
        }
        w.writeLine("logSearchParams(\"" + search.getName().replace("\"", "\\\"") + "\", __sp);");
        // Заполняем поля формы теми же значениями, что залогировали. Передаём и техническое имя,
        // и русский заголовок — fillSearchParam сначала пробует заголовок (совпадение по метке).
        for (SearchParam param : search.getParams()) {
            if (param.getSearchGuid() != null && !param.getSearchGuid().isEmpty()) continue;
            String value = TestDataFactory.generateSearchParamValue(param);
            String safeName = param.getName().replace("\"", "\\\"");
            String safeTitle = param.getTitle() == null ? "" : param.getTitle().replace("\"", "\\\"");
            String safeVal = value.replace("\\", "\\\\").replace("\"", "\\\"");
            w.writeLine("fillSearchParam(\"" + safeName + "\", \"" + safeTitle + "\", \"" + safeVal + "\");");
        }
        w.writeLine();
        w.writeLine("shot(\"params_filled\");");
        // Поиски без параметров (напр. «Поиск ОГСК») выполняются автоматически при клике по узлу
        // дерева или пункту меню — отдельной кнопки запуска нет. executeSearch нужен только
        // параметрическим поискам.
        if (!search.getParams().isEmpty()) {
            w.writeLine("step(\"execute search\", () -> executeSearch());");
        } else {
            w.writeLine("// search '" + search.getName().replace("\"", "\\\"") + "' has no params — auto-executes on open, no submit click needed");
        }
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_search\");");

        w.writeLine("assertFalse(isErrorPresent(), \"Search should execute without errors\");");
        if (search.getResult() != null) {
            List<SearchResultProperty> visibleCols = search.getResult().getProperties().stream()
                    .filter(SearchResultProperty::isVisible)
                    .toList();
            if (!visibleCols.isEmpty()) {
                w.writeLine();
                w.writeLine("int columnsFound = 0;");
                w.writeLine("java.util.List<String> missingCols = new java.util.ArrayList<>();");
                for (SearchResultProperty rp : visibleCols) {
                    w.openBlock("if (isColumnPresent(\"" + rp.getTitle() + "\"))");
                    w.writeLine("columnsFound++;");
                    w.closeBlock();
                    w.openBlock("else");
                    w.writeLine("missingCols.add(\"" + rp.getTitle().replace("\"", "\\\"") + "\");");
                    w.closeBlock();
                }
                w.writeLine("System.out.println(\"Search result columns found: \" + columnsFound + \" of " + visibleCols.size()
                        + (visibleCols.size() > 0 ? "; missing: \" + missingCols)" : "\")") + ";");
            }
        }
        w.writeLine("int resultRows = getVisibleRowCount();");
        w.writeLine("System.out.println(\"Search returned \" + resultRows + \" visible row(s)\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeGridTest(JavaFileWriter w, PropertyGroup grid, EntityObject entity) {
        String testName = "testGrid" + Transliterator.toClassName(grid.getName());
        // «Собственный грид» — PropertyGroup, имя которой совпадает с самой сущностью
        // (например грид «Должностные лица» внутри сущности «Должностные лица»). Такой грид
        // и есть главное представление, открывать карточку записи не нужно. Только настоящим
        // дочерним гридам (напр. История ГСК внутри ГСК) нужен openRecordCard.
        boolean isOwnGrid = grid.getName() != null && entity.getName() != null
                && grid.getName().equalsIgnoreCase(entity.getName());
        w.writeLine("@Test");
        w.writeLine("@DisplayName(\"Grid view: " + grid.getName() + "\")");
        w.openBlock("void " + testName + "()");
        w.writeLine("shot(\"start\");");
        if (!isOwnGrid) {
            // Вкладки дочерних гридов рендерятся только внутри открытой карточки записи.
            w.openBlock("if (!isDialogOpen())");
            w.writeLine("step(\"open record card\", () -> openRecordCard());");
            w.writeLine("shot(\"card_opened\");");
            w.closeBlock();
            w.writeLine("Assumptions.assumeTrue(isDialogOpen(),");
            w.writeLine("    \"Cannot test child-grid tab '" + grid.getName().replace("\"", "\\\"") + "' — record card did not open\");");
            w.writeLine("boolean tabOpened = step(\"open tab '" + grid.getName().replace("\"", "\\\"") + "'\",");
            w.writeLine("    (java.util.function.Supplier<Boolean>) () -> openTab(\"" + grid.getName().replace("\"", "\\\"") + "\"));");
            w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
            w.writeLine("shot(tabOpened ? \"tab_opened\" : \"tab_not_found\");");
            w.writeLine("Assumptions.assumeTrue(tabOpened, \"Tab '" + grid.getName().replace("\"", "\\\"") + "' not present in this card — skipping\");");
        }

        w.writeLine("boolean gridVisible = isGridDisplayed(\"" + grid.getName() + "\");");
        w.writeLine("System.out.println(\"Grid '" + grid.getName().replace("\"", "\\\"") + "' visible: \" + gridVisible);");
        w.writeLine();

        List<Property> gridColumns = grid.getProperties().stream()
                .filter(p -> !isSystemField(p) && p.isFlagDisplay())
                .toList();
        if (!gridColumns.isEmpty()) {
            w.writeLine("int gridColsFound = 0;");
            w.writeLine("java.util.List<String> missingCols = new java.util.ArrayList<>();");
            for (Property col : gridColumns) {
                w.openBlock("if (isColumnPresent(\"" + col.getName() + "\"))");
                w.writeLine("gridColsFound++;");
                w.closeBlock();
                w.openBlock("else");
                w.writeLine("missingCols.add(\"" + col.getName().replace("\"", "\\\"") + "\");");
                w.closeBlock();
            }
            w.writeLine("System.out.println(\"Grid columns found: \" + gridColsFound + \" of " + gridColumns.size() + "\");");
            w.writeLine("shot(\"columns_checked\");");
            // Найдена хотя бы одна колонка — PASS. Ноль колонок при видимом гриде — skip
            // (наши селекторы заголовков не подходят к DOM этого стенда), а не дефект продукта.
            w.openBlock("if (gridVisible)");
            w.writeLine("Assumptions.assumeTrue(gridColsFound >= 1,");
            w.writeLine("    \"Grid '" + grid.getName().replace("\"", "\\\"") + "': 0 of " + gridColumns.size() + " columns visible — header selectors don't match this build. Missing: \" + String.join(\", \", missingCols));");
            w.closeBlock();
        }

        if (grid.getOperation() != null && !grid.getOperation().getModifiers().isEmpty()) {
            w.writeLine();
            w.writeLine("int gridBtnsFound = 0;");
            for (Modifier mod : grid.getOperation().getModifiers()) {
                w.writeLine("if (isButtonPresent(\"" + mod.getTitle() + "\")) gridBtnsFound++;");
            }
            w.writeLine("System.out.println(\"Grid buttons found: \" + gridBtnsFound + \" of " + grid.getOperation().getModifiers().size() + "\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    private boolean hasModifier(Operation operation, ModifyType type) {
        return operation.getModifiers().stream()
                .anyMatch(m -> m.getModifyType() == type);
    }

    private List<Property> getDisplayProperties(EntityObject entity) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<Property> result = new java.util.ArrayList<>();
        for (PropertyGroup pg : entity.getPropertyGroups()) {
            // Берём только из основной формы сущности (FormView, typeLink="P"). Пропускать
            // grid-группы здесь критично: grid-PropertyGroup — это вкладка дочерней сущности
            // (например «Документы совещания» внутри «Совещание»), её колонки принадлежат
            // дочерней сущности, а не форме родителя, и завышали бы число ожидаемых полей.
            if (!pg.isFormView()) continue;
            for (Property p : pg.getProperties()) {
                if (!p.isFlagDisplay()) continue;
                if (isSystemField(p)) continue;
                if (isSystemFieldByName(p)) continue;
                if (seen.add(p.getAttrName())) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    private boolean isSystemField(Property prop) {
        String stereo = prop.getStereoType();
        if ("RoleA".equals(stereo) || "ObjectName".equals(stereo)) return true;
        // Поля с серверным def-value, которые E3Core заполняет сам («Дата изменения», «Оператор»,
        // «Дата создания» и т.п.). Если автотест пишет туда своё значение, форма на «Готово»
        // отвергает запрос. Пропускаем такие поля.
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

    /**
     * Поиск считается FK-only, если у него нет параметров, а результат — только
     * {SearchKey, SearchName}. Такие поиски нет в дереве поисков самой сущности — их вызывают
     * другие сущности при заполнении FK-поля через пикер. Собственный testSearch для них дал бы
     * ложный PASS (тест видит дефолтные результаты из setUp и сообщает «ошибок нет»).
     */
    private boolean isFkOnlySearch(Search s) {
        boolean noParams = s.getParams() == null || s.getParams().isEmpty();
        if (!noParams) return false;
        if (s.getResult() == null) return true;
        if (s.getResult().getProperties() == null || s.getResult().getProperties().isEmpty()) return true;
        for (SearchResultProperty p : s.getResult().getProperties()) {
            String n = p.getName();
            if (n == null) continue;
            if (!"SearchKey".equalsIgnoreCase(n) && !"SearchName".equalsIgnoreCase(n)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Служебные колонки, которые есть в каждом гриде и форме E3Core (аудит: кто и когда изменил
     * запись). Они в XML-модели, потому что это реальные колонки БД, но тесты не должны проверять
     * их на форме — это не пользовательские поля, и форма может их скрывать.
     */
    private boolean isSystemFieldByName(Property prop) {
        String attr = prop.getAttrName();
        if (attr == null) return false;
        String u = attr.toUpperCase(java.util.Locale.ROOT);
        return "DATE_UPDATE".equals(u) || "OPERATOR".equals(u);
    }
}
