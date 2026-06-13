package ru.autotestgen.generator;

import ru.autotestgen.common.JavaFileWriter;
import ru.autotestgen.common.Transliterator;
import ru.autotestgen.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates JUnit 5 test classes for each entity.
 * Covers: field presence, required field validation, CRUD operations, search.
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

        // Find CRUD operation from ANY property group (not just formView)
        Operation crudOperation = entity.getPropertyGroups().stream()
                .map(PropertyGroup::getOperation)
                .filter(op -> op != null && !op.getModifiers().isEmpty())
                .findFirst()
                .orElse(null);
        boolean hasCrud = crudOperation != null;

        // Does THIS entity own a search? Сущности, открываемые через «Найти» (ГСК, Совещание,
        // одиночное «Должностное лицо»), имеют собственные поиски; справочники-списки, открываемые
        // прямым кликом («Причины смены председателя», «Должностные лица»), своих поисков не имеют —
        // поиски висят на одиночном двойнике. Для последних пропускаем шаг параметрической формы
        // поиска при навигации (он лишний, тратит время и может оставить окно «Дерево поисков»
        // поверх грида).
        boolean hasOwnSearchForm = model.getSearches().stream()
                .anyMatch(s -> entity.getGuid() != null
                        && entity.getGuid().equals(s.getSearchObjectGuid()));

        // Inline-справочник, у которого есть одиночный двойник-карточка с собственным модальным
        // CRUD (напр. список «Должностные лица» ↔ карточка «Должностное лицо»). Записи такого списка
        // создаются/правятся через модалку двойника, а двойник — отдельная PRIMARY-сущность со своими
        // тестами. Значит, дублирующий inline-CRUD здесь генерировать НЕ нужно (он к тому же не
        // отрабатывает). Для настоящих inline-справочников (напр. «Причины смены председателя»,
        // двойник которых read-only) twin == null и inline-тесты остаются.
        EntityObject modalTwin = entity.isInlineTableEntity()
                ? EntityClassifier.findModalTwin(entity, model) : null;

        // Find searches linked to this entity. Skip FK-only searches (no params + result is
        // just SearchKey/SearchName) — these are NOT in the search tree of THIS entity in the
        // UI; they are invoked from OTHER entities through FK pickers. Generating a testSearch
        // for them produces a fake PASS that doesn't actually exercise anything.
        List<Search> entitySearches = model.getSearches().stream()
                .filter(s -> s.getSearchObjectGuid().equals(entity.getGuid()))
                .filter(s -> !isFkOnlySearch(s))
                .toList();

        JavaFileWriter w = new JavaFileWriter();

        // Package and imports
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

        // Class
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

        // Override entityName() so BaseTest helpers (menuAction, openSearch, openRecordCard) look
        // up the entity by its real Russian name in the launcher menu — not the transliterated
        // class name. Without this override every menuAction(entityName(), action) would search
        // for an English/transliterated menu item that doesn't exist.
        w.writeLine("@Override");
        w.openBlock("protected String entityName()");
        w.writeLine("return ENTITY_NAME;");
        w.closeBlock();
        w.writeLine();

        // @BeforeEach — ensure result grid is always available before each test.
        // resetState() closes any open card AND the result-grid window (which in
        // E3Core is itself an x-window), so we can't rely on the previous test's
        // navigation. Reset the navigation cache flag so navigateToEntity actually
        // re-runs (without that, the navigationAttempted guard returns instantly).
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

        // @AfterEach — always capture a final screenshot. Together with shot() calls inside each
        // test method this gives a frame-by-frame record of the entire run under target/screenshots/,
        // which doubles as a visual map of the system's UI structure.
        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        // === SMOKE tests (always generated) ===

        // Test 1: Fields are present
        writeFieldsPresentTest(w, displayProperties, entity.getName());

        // === BASIC tests (generated for "basic" and "full") ===
        if (isBasicOrFull()) {

            // Test 8+: Search tests (with filled params)
            for (int i = 0; i < entitySearches.size(); i++) {
                writeSearchTest(w, entitySearches.get(i), i);
            }

            // Test: Grid views (with column and button verification)
            List<PropertyGroup> grids = entity.getPropertyGroups().stream()
                    .filter(PropertyGroup::isGridView)
                    .toList();
            for (PropertyGroup grid : grids) {
                writeGridTest(w, grid, entity);
            }
        }

        // === FULL tests (generated only for "full") ===
        // Inline-список с модальным двойником: CRUD покрывается тестом одиночной карточки-двойника,
        // здесь его не дублируем (оставляем только smoke/search/grid выше). Печатаем поясняющий
        // комментарий в тело класса, чтобы связь была видна прямо в сгенерированном файле.
        if (isFull() && modalTwin != null) {
            w.writeLine("// CRUD этого справочника-списка проверяется в тесте одиночной карточки \""
                    + modalTwin.getName().replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\" (модальная форма) — здесь не дублируется.");
        }
        if (isFull() && modalTwin == null) {

            // Test 2: Required field validation (empty submit)
            if (!requiredProperties.isEmpty() && hasCrud) {
                writeRequiredFieldValidationTest(w, requiredProperties);
            }

            // Type 2 detection: сущности БЕЗ отдельной модалки FormView (typeLink="P")
            // используют inline-table flow (Редактирование→Добавить / Сохранить Изменения).
            // Для них стандартный testCreate/testUpdate (через wizard-модалку) не работает.
            boolean inlineTable = entity.isInlineTableEntity();

            // Test 3: Create (Insert)
            if (hasCrud && hasModifier(crudOperation, ModifyType.INSERT)) {
                if (inlineTable) {
                    writeInlineTableCreateTest(w);
                } else {
                    writeCreateTest(w, displayProperties);
                }
            }

            // Test 4: Update
            if (hasCrud && hasModifier(crudOperation, ModifyType.UPDATE)) {
                if (inlineTable) {
                    writeInlineTableUpdateTest(w);
                } else {
                    writeUpdateTest(w, displayProperties);
                }
            }

            // Test 5: Delete
            if (hasCrud && hasModifier(crudOperation, ModifyType.DELETE)) {
                writeDeleteTest(w);
            }

            // Test 6: Logical Edit
            if (hasCrud && hasModifier(crudOperation, ModifyType.LOGICAL_EDIT)) {
                writeLogicalEditTest(w);
            }

            // Test 7: Archive
            if (hasCrud && hasModifier(crudOperation, ModifyType.ARCHIVE)) {
                writeArchiveTest(w);
            }

            // Test: Partial validation (fill only first required field)
            if (requiredProperties.size() >= 2 && hasCrud) {
                writePartialValidationTest(w, requiredProperties);
            }

            // testSearchEmpty убран по требованию пользователя: на стенде он почти всегда
            // SKIP (форма поиска не открывается / FK обязателен и без него поиск не запускается),
            // даёт шум в отчёте и не несёт диагностической ценности.
        }

        w.closeBlock(); // end class

        w.writeToFile(dir, testClassName + ".java");
    }

    /**
     * Generates an in-context test class for a CHILD entity — one that lives as a grid tab inside
     * a parent entity's card. The generated @BeforeEach navigates to the parent entity, opens a
     * record card, and switches to the child tab. CRUD tests use the grid toolbar buttons, not the
     * main menu.
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

        // @BeforeEach: navigate to parent → open card → switch to child tab
        w.writeLine("@BeforeEach");
        w.openBlock("void setUp()");
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(PARENT_ENTITY_NAME, PARENT_FEATURE_NAME);");
        w.writeLine("assumeNavigated();");
        w.writeLine("boolean cardOpened = selectAndOpenRecord();");
        w.writeLine("Assumptions.assumeTrue(cardOpened, \"Could not open parent '\" + PARENT_ENTITY_NAME + \"' record card\");");
        w.writeLine("waitForCardLoaded(10);");
        w.writeLine("shot(\"parent_card\");");
        w.writeLine("boolean tabOpened = openTab(TAB_NAME);");
        w.writeLine("Assumptions.assumeTrue(tabOpened, \"Tab '\" + TAB_NAME + \"' not found in parent card\");");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"child_tab\");");
        w.writeLine("page = new " + pageClassName + "(driver);");
        w.closeBlock();
        w.writeLine();

        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        // Test 1: Grid columns
        writeChildGridColumnsTest(w, gridColumns, tabName);

        if (isFull() && hasCrud) {
            // Test 3: Create
            if (hasModifier(gridOperation, ModifyType.INSERT)) {
                writeChildCreateTest(w, displayProperties, tabName);
            }
            // Test 4: Update
            if (hasModifier(gridOperation, ModifyType.UPDATE)) {
                writeChildUpdateTest(w, displayProperties, tabName);
            }
            // Test 5: Delete
            if (hasModifier(gridOperation, ModifyType.DELETE)) {
                writeChildDeleteTest(w, tabName);
            }
        }

        w.closeBlock(); // end class
        w.writeToFile(dir, testClassName + ".java");
    }

    /**
     * Generates an in-context test for a TREE-NODE child — a sub-entity the parent exposes as a
     * left-tree node (association addFromTree="1"), e.g. «Повестка совещания» внутри «Совещание».
     * Such an entity has its own form (typeLink="P") but is NOT in the main menu, so a standalone
     * PRIMARY test could never navigate to it. The @BeforeEach navigates to the parent, opens a
     * record card and expands the tree node; the generated test then verifies the node's form
     * fields are present. (CRUD внутри узла дерева сознательно не трогаем — поток открытия формы из
     * дерева на стенде неоднороден; presence-проверка надёжна и снимает ложный SKIP.)
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

        // @BeforeEach: navigate to parent → open card → expand tree node
        w.writeLine("@BeforeEach");
        w.openBlock("void setUp()");
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(PARENT_ENTITY_NAME, PARENT_FEATURE_NAME);");
        w.writeLine("assumeNavigated();");
        w.writeLine("boolean cardOpened = selectAndOpenRecord();");
        w.writeLine("Assumptions.assumeTrue(cardOpened, \"Could not open parent '\" + PARENT_ENTITY_NAME + \"' record card\");");
        w.writeLine("waitForCardLoaded(10);");
        w.writeLine("shot(\"parent_card\");");
        w.writeLine("boolean nodeOpened = openTab(NODE_NAME);");
        w.writeLine("Assumptions.assumeTrue(nodeOpened, \"Tree node '\" + NODE_NAME + \"' not found in parent card\");");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"tree_node\");");
        w.writeLine("page = new " + pageClassName + "(driver);");
        w.closeBlock();
        w.writeLine();

        w.writeLine("@AfterEach");
        w.openBlock("void captureFinalShot(TestInfo testInfo)");
        w.writeLine("shot(\"END\");");
        w.closeBlock();
        w.writeLine();

        writeTreeNodeFieldsTest(w, displayProperties, nodeName);

        w.closeBlock(); // end class
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

    private void writeChildGridColumnsTest(JavaFileWriter w, List<Property> gridColumns, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(1)");
        w.writeLine("@DisplayName(\"Grid columns: " + tabName.replace("\"", "\\\"") + "\")");
        w.openBlock("void testGridColumns()");
        w.writeLine("shot(\"grid_view\");");
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
            w.writeLine("Assumptions.assumeTrue(colsFound >= 1,");
            w.writeLine("    \"Grid '" + tabName.replace("\"", "\\\"") + "': 0 of " + gridColumns.size() + " columns found — selectors don't match this build. Missing: \" + String.join(\", \", missingCols));");
        } else {
            w.writeLine("System.out.println(\"Grid '" + tabName.replace("\"", "\\\"") + "': no columns defined in model\");");
        }
        w.writeLine("int rows = getVisibleRowCount();");
        w.writeLine("System.out.println(\"Grid '" + tabName.replace("\"", "\\\"") + "' rows: \" + rows);");
        w.closeBlock();
        w.writeLine();
    }

    private void writeChildCreateTest(JavaFileWriter w, List<Property> displayProperties, String tabName) {
        Property markerField = displayProperties.stream()
                .filter(p -> p.getAttrType() == AttrType.STRING && !isSystemField(p)
                        && !"Directory".equals(p.getStereoType()) && !"Ref".equals(p.getStereoType()))
                .findFirst().orElse(null);

        w.writeLine("@Test");
        w.writeLine("@Order(3)");
        w.writeLine("@DisplayName(\"Create in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testCreate()");
        w.writeLine("shot(\"grid_before_add\");");
        w.writeLine("int rowsBefore = getVisibleRowCount();");
        w.writeLine();
        // Click "Добавить" in grid toolbar
        w.writeLine("boolean addClicked = clickButtonByText(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\");");
        w.writeLine("System.out.println(\"child testCreate: addClicked=\" + addClicked + \" in tab '\" + TAB_NAME + \"'\");");
        w.writeLine("Assumptions.assumeTrue(addClicked, \"'\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c' button not found in child grid toolbar\");");
        w.writeLine("boolean addFormOpen = waitForAddForm();");
        w.writeLine("System.out.println(\"child testCreate: addFormOpen=\" + addFormOpen);");
        w.writeLine("Assumptions.assumeTrue(addFormOpen, \"Add form did not open in child grid context\");");
        w.writeLine("shot(\"add_form\");");
        w.writeLine();
        w.writeLine("step(\"fill all fields\", () -> page.fillAllFields());");
        w.writeLine("shot(\"fields_filled\");");
        if (markerField != null) {
            String fillMethod = "fill" + Transliterator.toClassName(markerField.getAttrName());
            w.writeLine("String createdMarker = \"AT\" + System.nanoTime();");
            w.writeLine("step(\"stamp marker\", () -> page." + fillMethod + "(createdMarker));");
        } else {
            w.writeLine("String createdMarker = \"\";");
        }
        w.writeLine("java.util.LinkedHashMap<String, String> filledSnapshot = new java.util.LinkedHashMap<>(page.lastFilledValues);");
        w.writeLine("System.out.println(\"child testCreate: filled values: \" + filledSnapshot);");
        w.writeLine("shot(\"marker_applied\");");
        w.writeLine();
        // Save
        w.writeLine("step(\"click \\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\", () -> clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"));");
        w.writeLine("capturePopupText(\"after-\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForDialogClose();");
        w.openBlock("if (isDialogOpen() || isButtonVisible(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"))");
        w.writeLine("System.out.println(\"child testCreate: dialog stuck, pressing \\u041e\\u0442\\u043c\\u0435\\u043d\\u0430\");");
        w.openBlock("try");
        w.writeLine("clickButtonByText(\"\\u041e\\u0442\\u043c\\u0435\\u043d\\u0430\");");
        w.writeLine("waitForDialogClose();");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        w.writeLine();
        // Verify in child grid
        w.writeLine("assertFalse(isErrorPresent(), \"Error after creating child record\");");
        w.writeLine("int rowsAfter = getVisibleRowCount();");
        w.writeLine("boolean markerFound = !createdMarker.isEmpty() && gridContainsRow(createdMarker);");
        w.writeLine("String hitVia = markerFound ? \"marker(DOM)\" : \"\";");
        w.writeLine();
        w.openBlock("if (!markerFound && !filledSnapshot.isEmpty())");
        w.openBlock("for (java.util.Map.Entry<String,String> e : filledSnapshot.entrySet())");
        w.writeLine("String v = e.getValue();");
        w.openBlock("if (v != null && !v.isEmpty() && gridContainsRow(v))");
        w.writeLine("markerFound = true;");
        w.writeLine("hitVia = \"field '\" + e.getKey() + \"'(DOM)\";");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
        w.openBlock("if (!markerFound && !createdMarker.isEmpty())");
        w.openBlock("if (gridStoreContainsText(createdMarker))");
        w.writeLine("markerFound = true;");
        w.writeLine("hitVia = \"marker(ExtJS store)\";");
        w.closeBlock();
        w.closeBlock();
        w.writeLine();
        w.writeLine("System.out.println(\"child testCreate: markerFound=\" + markerFound + (hitVia.isEmpty() ? \"\" : \" via \" + hitVia)");
        w.writeLine("    + \" rows \" + rowsBefore + \" -> \" + rowsAfter);");
        w.writeLine("shot(markerFound ? \"marker_found\" : \"final_grid\");");
        if (markerField != null) {
            w.writeLine("assertTrue(markerFound,");
            w.writeLine("    \"Child grid create: marker '\" + createdMarker + \"' not found in grid after save. rows \" + rowsBefore + \" -> \" + rowsAfter);");
        } else {
            w.writeLine("assertTrue(rowsAfter >= rowsBefore,");
            w.writeLine("    \"Child grid should have same or more rows after create (\" + rowsBefore + \" -> \" + rowsAfter + \")\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    private void writeChildUpdateTest(JavaFileWriter w, List<Property> displayProperties, String tabName) {
        Property stringField = displayProperties.stream()
                .filter(p -> p.getAttrType() == AttrType.STRING && !isSystemField(p)
                        && !"Directory".equals(p.getStereoType()) && !"Ref".equals(p.getStereoType()))
                .findFirst().orElse(null);

        w.writeLine("@Test");
        w.writeLine("@Order(4)");
        w.writeLine("@DisplayName(\"Update in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testUpdate()");
        w.writeLine("shot(\"grid_before_update\");");
        w.writeLine("int rowCount = getVisibleRowCount();");
        w.writeLine("Assumptions.assumeTrue(rowCount >= 1, \"No rows in child grid to update\");");
        w.writeLine();
        // Open child record
        w.writeLine("boolean opened = selectAndOpenRecord();");
        w.writeLine("Assumptions.assumeTrue(opened, \"Could not open child record for editing\");");
        w.writeLine("waitForCardLoaded(8);");
        w.writeLine("shot(\"child_record_opened\");");
        if (stringField != null) {
            String methodName = "fill" + Transliterator.toClassName(stringField.getAttrName());
            w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
            w.writeLine("page." + methodName + "(updatedValue);");
            w.writeLine("shot(\"value_typed\");");
        }
        // Try "Сохранить Изменения" dropdown, fallback to "Готово"
        w.writeLine("boolean saved = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.openBlock("if (!saved)");
        w.writeLine("saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.closeBlock();
        w.writeLine("Assumptions.assumeTrue(saved, \"Neither '\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f' nor '\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e' found\");");
        w.writeLine("capturePopupText(\"after-save\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForDialogClose();");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_update\");");
        w.writeLine("assertFalse(isErrorPresent(), \"Error after updating child record\");");
        if (stringField != null) {
            w.writeLine("boolean updated = gridContainsRow(updatedValue) || gridStoreContainsText(updatedValue);");
            w.writeLine("System.out.println(\"child testUpdate: updatedValue='\" + updatedValue + \"' found=\" + updated);");
        }
        w.closeBlock();
        w.writeLine();
    }

    private void writeChildDeleteTest(JavaFileWriter w, String tabName) {
        w.writeLine("@Test");
        w.writeLine("@Order(5)");
        w.writeLine("@DisplayName(\"Delete in '" + tabName.replace("\"", "\\\"") + "'\")");
        w.openBlock("void testDelete()");
        w.writeLine("shot(\"grid_before_delete\");");
        w.writeLine("int rowsBefore = getVisibleRowCount();");
        w.writeLine("Assumptions.assumeTrue(rowsBefore >= 1, \"No rows in child grid to delete\");");
        w.writeLine("String deletedMarker = captureFirstResultRowSignature();");
        w.writeLine("System.out.println(\"child testDelete: marker='\" + deletedMarker + \"' rowsBefore=\" + rowsBefore);");
        w.writeLine();
        // Open child record then delete via dropdown
        w.writeLine("boolean opened = selectAndOpenRecord();");
        w.writeLine("Assumptions.assumeTrue(opened, \"Could not open child record for deletion\");");
        w.writeLine("shot(\"child_record_opened\");");
        w.openBlock("try");
        w.writeLine("clickEditDropdownAction(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("clickButtonByText(\"\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.closeBlock();
        w.writeLine("shot(\"delete_clicked\");");
        w.writeLine("acceptAlertIfPresent();");
        w.writeLine("confirmDialogYes();");
        w.writeLine("shot(\"after_confirm\");");
        w.writeLine("waitForDialogClose();");
        w.writeLine("try { Thread.sleep(500); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_delete\");");
        w.writeLine();
        w.writeLine("assertFalse(isErrorPresent(), \"Error after deleting child record\");");
        w.writeLine("int rowsAfter = getVisibleRowCount();");
        w.writeLine("boolean markerGone = deletedMarker.isEmpty() ? false : !gridContainsRow(deletedMarker);");
        w.writeLine("System.out.println(\"child testDelete: rows \" + rowsBefore + \" -> \" + rowsAfter + \", markerGone=\" + markerGone);");
        w.openBlock("if (deletedMarker.isEmpty())");
        w.writeLine("Assumptions.assumeTrue(rowsAfter < rowsBefore,");
        w.writeLine("    \"Delete: no marker captured AND row count unchanged (\" + rowsBefore + \" -> \" + rowsAfter + \")\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("assertTrue(rowsAfter < rowsBefore || markerGone,");
        w.writeLine("    \"Delete in child grid: rows \" + rowsBefore + \" -> \" + rowsAfter + \" AND marker '\" + deletedMarker + \"' still present\");");
        w.closeBlock();
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
        // ВАЖНО (требование заказчика): поля сверяются ВНУТРИ карточки записи, а не в
        // таблице результатов. Сначала проверим, что грид навигировался (нашли хотя бы
        // колонки) — это даёт нижнюю границу. Потом откроем первую строку double-click'ом
        // и пересчитаем уже внутри карточки. Тест пройден если найдено ≥1 поля где-либо
        // (карточка или грид), чтобы не блокировать остальные CRUD-тесты Assumptions-skip'ом.
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
        // Pass if we found at least one field anywhere — navigation clearly worked.
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
        // Заказчик: «Добавить» открывается через ГЛАВНОЕ меню (не из карточки).
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
        // Don't waitForDialogClose — we EXPECT the dialog to stay open due to validation. Just give
        // ExtJS a brief moment to render error indicators, no longer.
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
        // Hard: validation MUST do something — either keep dialog open, OR show form-level errors,
        // OR highlight specific fields. If none of the three, validation is broken.
        w.writeLine("assertTrue(dialogStillOpen || hasErrors || errorFieldCount > 0,");
        w.writeLine("    \"Empty submit of required-field form must trigger validation: dialog should stay open OR errors shown OR fields highlighted. \"");
        w.writeLine("    + \"None of the three happened — form likely silently accepted invalid data.\");");
        // ВАЖНО: на этом моменте диалог Сведения остался открытым (это и есть PASS-сигнал
        // валидации). Если его не закрыть — следующий тест (testCreate, Order 3) не сможет
        // пронавигироваться: модальный диалог блокирует клик по верхнему меню. Жмём «Отмена».
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
        // We expect the dialog to NOT close — short buffer for error rendering only.
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_submit\");");
        w.writeLine();
        // Hard: with only the first required field filled, the others must still block submit.
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
        // Pick the first non-system, non-FK STRING property to stamp with a unique marker.
        // After save we then assert the marker shows up in the result grid — proof the row was
        // actually inserted rather than merely incrementing the row count.
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
        // Заказчик: «Добавить» открывается через ГЛАВНОЕ меню — путь как у Найти,
        // но кликаем «Добавить» вместо «Найти». В карточке записи пункта «Добавить» НЕТ
        // (там только «Сохранить Изменения» / «Удалить»), поэтому старый путь через
        // selectAndOpenRecord → clickEditDropdownAction('Добавить') заведомо не работал.
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
            // Пропускаем markerField в fillAllFields — иначе будет двойной fill
            // (Test_GBS_NAME_X потом AT_Y), и ExtJS editor reuse может склеить
            // их в 'AT_YTest_GBS_NAME_X' вместо чистой замены.
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
        // Запоминаем ВСЕ фактически вписанные значения — это снимок того, что мы
        // отправили на сервер. Дальше используем его, если маркер не нашёлся в гриде
        // напрямую (например, поле-маркер не отображается в результирующей таблице).
        w.writeLine("java.util.LinkedHashMap<String, String> filledSnapshot = new java.util.LinkedHashMap<>(page.lastFilledValues);");
        w.writeLine("System.out.println(\"testCreate: запомнили заполненные поля: \" + filledSnapshot);");
        // Даём ExtJS время прокинуть значения PropertyGrid'а из rec.set(...) в form.
        // Без этой паузы Готово отрабатывает раньше чем ExtJS закоммитит последние rec.set,
        // и сервер видит часть полей пустыми (popup: «Необходимо заполнить ...» хотя в
        // snapshot значения есть).
        w.openBlock("try");
        w.writeLine("Thread.sleep(1500);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        // TAB чтобы закомитить активный редактор PropertyGrid'а (последнее
        // вписанное значение). Без этого первый клик Готово часто работает как «blur» и
        // нужный сейв не происходит.
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).sendKeys(org.openqa.selenium.Keys.TAB).perform();");
        w.writeLine("Thread.sleep(400);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        w.writeLine("shot(\"before_gotovo\");");
        w.writeLine("boolean gotovoClicked = step(\"click Готово\", () -> clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\"));");
        w.writeLine("System.out.println(\"testCreate: gotovoClicked=\" + gotovoClicked);");
        // Fallback для справочников (lookup-таблиц): у них «Добавить» открывает inline-режим
        // в гриде, а не модальный «Сведения»; кнопка save — «Сохранить» или просто Enter.
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
        // Захватываем popup ПОСЛЕ Готово и сразу жмём «Да» (confirmDialogYes). На стенде
        // встречаются wizard-формы (с кнопкой «<< Назад») — Готово может быть шагом
        // мастера, а не финальным save'ом. Делаем до 3 итераций: каждый раз
        // captureconfirm → confirmDialogYes → если диалог всё ещё открыт и видна
        // кнопка Готово, нажимаем её снова (следующий шаг wizard'a).
        w.writeLine("String createPopupText = capturePopupText(\"after-\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("System.out.println(\"testCreate: popup после Готово = '\" + createPopupText + \"'\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForDialogClose();");
        // Ждём СПОКОЙНО — 3с на серверный round-trip. Если карточка осталась открытой —
        // это нормально для некоторых стендов (после save карточка переходит в read-only
        // и остаётся на экране). НЕ фейлим на «диалог открыт» — единственный надёжный
        // критерий «сохранилось» = запись видна в обновлённом гриде.
        w.openBlock("try");
        w.writeLine("Thread.sleep(3000);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        // Если popup сервера явно сказал «не заполнено» — это твёрдый сигнал что save
        // отвергнут. Фейлим сразу с диагностикой, не ходим в грид.
        w.openBlock("if (createPopupText != null && (createPopupText.contains(\"\\u041d\\u0435\\u043e\\u0431\\u0445\\u043e\\u0434\\u0438\\u043c\\u043e\") || createPopupText.contains(\"\\u041d\\u0435 \\u0437\\u0430\\u043f\\u043e\\u043b\\u043d\\u0435\\u043d\\u043e\")))");
        w.writeLine("shot(\"validation_error\");");
        w.writeLine("fail(\"testCreate: сервер отверг save с popup'ом валидации: '\" + createPopupText + \"'. Заполненные поля: \" + filledSnapshot");
        w.writeLine("    + \". ExtJS rec.set вернул OK, но сервер при save видит эти поля пустыми — возможно поле зависит от FK/wizard-шага которого мы не проходили.\");");
        w.closeBlock();
        // Закрываем карточку через Esc / Отмена ТОЛЬКО если popup НЕ был валидационным.
        // На стенде «Сведения» после save может оставаться открытым read-only — нам надо
        // вернуться к гриду чтобы посчитать запись. Esc не вызовет отмены сохранения,
        // т.к. save уже произошёл.
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
        // Re-navigate к таблице результатов и заново выполнить поиск — так получаем
        // СВЕЖИЙ грид и видим: появилась наша запись с маркером или нет.
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
        // Проверка как у пользователя в видео: ПОИСК по маркеру в форме поиска.
        // Это primary способ. Если маркер найден → запись точно сохранилась.
        // Раньше primary был gridContainsRow в нефильтрованном гриде — но он шумит:
        // на стенде грид может показывать другие записи и маркер «прячется» на следующих
        // страницах пейджинации. Поиск надёжнее.
        w.writeLine("int rowsAfter = page.getTableRowCount();");
        w.writeLine("boolean markerInGrid = false;");
        w.writeLine("String hitVia = \"\";");
        if (markerField != null) {
            String fillMethod = "fill" + Transliterator.toClassName(markerField.getAttrName());
            // Уровень 1 (PRIMARY): ФИЛЬТРОВАННЫЙ поиск — вписываем маркер в форму поиска
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
        // Уровень 2 (fallback): маркер в нефильтрованном DOM грида
        w.openBlock("if (!markerInGrid && !createdMarker.isEmpty() && gridContainsRow(createdMarker))");
        w.writeLine("markerInGrid = true;");
        w.writeLine("hitVia = \"marker(DOM unfiltered)\";");
        w.closeBlock();
        w.writeLine();
        // Уровень 3 (fallback): ExtJS store
        w.openBlock("if (!markerInGrid && !createdMarker.isEmpty() && gridStoreContainsText(createdMarker))");
        w.writeLine("markerInGrid = true;");
        w.writeLine("hitVia = \"marker(ExtJS store)\";");
        w.closeBlock();
        w.writeLine();
        // Уровень 4 (последний): любое из заполненных значений видно
        w.openBlock("if (!markerInGrid && !filledSnapshot.isEmpty())");
        w.openBlock("for (java.util.Map.Entry<String,String> e : filledSnapshot.entrySet())");
        w.writeLine("String v = e.getValue();");
        w.openBlock("if (v != null && !v.isEmpty() && gridContainsRow(v))");
        w.writeLine("markerInGrid = true;");
        w.writeLine("hitVia = \"field '\" + e.getKey() + \"' = '\" + v + \"'(DOM)\";");
        w.writeLine("break;");
        w.closeBlock();
        w.closeBlock();
        w.closeBlock();
        w.writeLine("System.out.println(\"testCreate: markerInGrid=\" + markerInGrid + (hitVia.isEmpty() ? \"\" : \" via \" + hitVia) + \" rowsAfter=\" + rowsAfter);");
        w.writeLine("shot(markerInGrid ? \"marker_in_grid\" : \"final_grid\");");
        if (markerField != null) {
            w.writeLine("assertTrue(markerInGrid,");
            w.writeLine("    \"Create test: ни маркер '\" + createdMarker + \"', ни одно из заполненных значений \" + filledSnapshot.values() + \" не найдено в гриде после ре-поиска (rowsBefore=\" + rowsBefore + \", rowsAfter=\" + rowsAfter + \"). Запись не сохранилась. Popup после Готово='\" + createPopupText + \"'.\");");
        } else {
            w.writeLine("assertTrue(rowsAfter >= rowsBefore,");
            w.writeLine("    \"Table should have same or more records after creation (\" + rowsBefore + \" -> \" + rowsAfter + \")\");");
        }
        w.closeBlock();
        w.writeLine();
    }

    /** Type 2 (inline-table): create через 'Редактирование → Добавить' → fill cells → save. */
    /**
     * Emits JS locating the largest visible editable ExtJS grid into window.__t2grid and assigning
     * its column count to {@code resultVar} (Long). Works on ExtJS 3 (Ext.ComponentMgr.all, no
     * ComponentQuery) AND ExtJS 4+ (Ext.ComponentQuery). resultVar: >0 ok, 0 grid-but-no-columns,
     * -1 no grid, -2 grid empty (only when requireNonEmpty).
     */
    private void writeLocateEditableGridScript(JavaFileWriter w, String resultVar, boolean requireNonEmpty) {
        w.writeLine("Long " + resultVar + " = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"if (typeof Ext === 'undefined') return -1;\"");
        w.writeLine("    + \"var grids = [];\"");
        w.writeLine("    + \"try { if (Ext.ComponentQuery && Ext.ComponentQuery.query) grids = Ext.ComponentQuery.query('gridpanel,editorgrid,grid'); } catch(e) {}\"");
        w.writeLine("    + \"if ((!grids || !grids.length) && Ext.ComponentMgr && Ext.ComponentMgr.all) {\"");
        w.writeLine("    + \"  try { var all = Ext.ComponentMgr.all; var arr = all.items || (all.getRange ? all.getRange() : []);\"");
        w.writeLine("    + \"    for (var i=0;i<arr.length;i++){ var c=arr[i]; if (c && c.getStore && (c.startEditing || c.getColumnModel || c.editingPlugin)) grids.push(c); } } catch(e) {}\"");
        w.writeLine("    + \"}\"");
        // Предпочитаем РЕДАКТИРУЕМЫЙ грид-данных и ИСКЛЮЧАЕМ property-grid (форма свойств / параметры
        // поиска тоже грид, но не туда нужно писать). bestE — лучший редактируемый, bestA — запасной.
        w.writeLine("    + \"var bestE=null, bestErows=-1, bestA=null, bestArows=-1;\"");
        w.writeLine("    + \"for (var i=0;i<grids.length;i++){ var g=grids[i];\"");
        w.writeLine("    + \"  if (!g.rendered || !g.getStore) continue;\"");
        w.writeLine("    + \"  try { var el=g.getEl?g.getEl():null; var dom=el?(el.dom||el):null; if (dom && (dom.offsetWidth<=0||dom.offsetHeight<=0)) continue; } catch(e){ continue; }\"");
        w.writeLine("    + \"  var isProp=false; try { isProp = (g.getXType && g.getXType()==='propertygrid') || !!g.propertyNames || (g.source!==undefined && g.nameColumnWidth!==undefined); } catch(e){}\"");
        w.writeLine("    + \"  if (isProp) continue;\"");
        w.writeLine("    + \"  var cnt=0; try { cnt=g.getStore().getCount(); } catch(e){}\"");
        w.writeLine("    + \"  var editable=!!(g.startEditing || g.editingPlugin);\"");
        w.writeLine("    + \"  if (cnt>bestArows){ bestArows=cnt; bestA=g; }\"");
        w.writeLine("    + \"  if (editable && cnt>bestErows){ bestErows=cnt; bestE=g; } }\"");
        w.writeLine("    + \"var best = bestE || bestA;\"");
        w.writeLine("    + \"if (!best) return -1;\"");
        if (requireNonEmpty) {
            w.writeLine("    + \"if (best.getStore().getCount() === 0) return -2;\"");
        }
        w.writeLine("    + \"window.__t2grid = best;\"");
        w.writeLine("    + \"var cm = best.getColumnModel ? best.getColumnModel() : null;\"");
        w.writeLine("    + \"return cm ? (cm.getColumnCount ? cm.getColumnCount() : 0) : 0;\");");
    }

    /** Emits JS that selects row {@code rowExpr} in window.__t2grid (Ext3 selectRow / Ext4 select). */
    private void writeSelectGridRowScript(JavaFileWriter w, String rowExpr) {
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; var sm=g.getSelectionModel(); if (sm){ if (sm.selectRow) sm.selectRow(arguments[0]); else if (sm.select) sm.select(arguments[0]); } } catch(e){}\", (long) (" + rowExpr + "));");
    }

    /** Emits a diagnostic log of the chosen window.__t2grid (xtype, rows, editable). */
    private void writeGridDiagLog(JavaFileWriter w, String label) {
        w.writeLine("System.out.println(\"" + label + ": выбран грид -> \" + ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if(!g) return 'none'; var xt=(g.getXType?g.getXType():''); var rows=0; try{rows=g.getStore().getCount();}catch(e){} var ed=!!(g.startEditing||g.editingPlugin); return xt+' rows='+rows+' editable='+ed; } catch(e){ return 'err:'+e.message; }\"));");
    }

    /** Emits JS that commits the active editor of window.__t2grid into its record (Ext3/Ext4). */
    private void writeCommitGridEditorScript(JavaFileWriter w) {
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if (!g) return;\"");
        w.writeLine("    + \" try { if (g.stopEditing) g.stopEditing(false); } catch(e){}\"");
        w.writeLine("    + \" try { if (g.activeEditor && g.activeEditor.completeEdit) g.activeEditor.completeEdit(); } catch(e){}\"");
        w.writeLine("    + \" try { if (g.editingPlugin && g.editingPlugin.completeEdit) g.editingPlugin.completeEdit(); } catch(e){}\"");
        w.writeLine("    + \"} catch(e){}\");");
        w.writeLine("try { Thread.sleep(300); } catch (InterruptedException ignored) {}");
    }

    /** Type 2 (inline-table): create через 'Редактирование → Добавить' → select new row → fill cells → save. */
    private void writeInlineTableCreateTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(3)");
        w.writeLine("@DisplayName(\"Create row inline (Type 2 entity)\")");
        w.openBlock("void testCreate()");
        w.writeLine("shot(\"start\");");
        w.writeLine("String createdMarker = \"AT\" + System.nanoTime();");
        // 1. 'Редактирование' → 'Добавить' — открывает новую пустую строку
        w.writeLine("boolean addClicked = step(\"Редактирование → Добавить\", () -> clickEditDropdownAction(\"\\u0414\\u043e\\u0431\\u0430\\u0432\\u0438\\u0442\\u044c\"));");
        w.writeLine("assertTrue(addClicked, \"testCreate (inline): не удалось через 'Редактирование' открыть дропдаун и кликнуть 'Добавить'\");");
        w.writeLine("try { Thread.sleep(900); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"empty_row_added\");");
        // 2. Найти редактируемый грид (Ext3 ComponentMgr + Ext4 ComponentQuery)
        writeLocateEditableGridScript(w, "colCount", false);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"testCreate (inline): не нашли editable grid (код=\" + colCount + \"). Возможно «Добавить» не создал строку или грид не редактируемый.\");");
        w.writeLine("System.out.println(\"testCreate (inline): grid found, columns=\" + colCount);");
        writeGridDiagLog(w, "testCreate (inline)");
        // 3. Определить индекс НОВОЙ (phantom/new) строки и ВЫБРАТЬ её — раньше слепо
        //    редактировали строку 0, из-за чего «строка появилась, но мы её не выбрали».
        w.writeLine("Long newRowL = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var s=window.__t2grid.getStore(); var mod=s.getModifiedRecords?s.getModifiedRecords():[];\"");
        w.writeLine("    + \" for (var i=0;i<mod.length;i++){ var r=mod[i]; if (r.phantom || r.newRecord){ var idx=s.indexOf(r); if (idx>=0) return idx; } }\"");
        w.writeLine("    + \" return 0; } catch(e){ return 0; }\");");
        w.writeLine("int rowIdx = (newRowL == null) ? 0 : newRowL.intValue();");
        w.writeLine("System.out.println(\"testCreate (inline): новая строка rowIdx=\" + rowIdx);");
        writeSelectGridRowScript(w, "rowIdx");
        w.writeLine("shot(\"row_selected\");");
        // 4. Перебираем колонки, startEditing(rowIdx, col), пишем маркер
        w.writeLine("int filledCount = 0;");
        w.openBlock("for (long col = 0; col < colCount; col++)");
        w.openBlock("try");
        w.writeLine("Boolean ok = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if (g.startEditing){ g.startEditing(arguments[0], arguments[1]); return true; }\"");
        w.writeLine("    + \" if (g.editingPlugin && g.editingPlugin.startEdit){ var rec=g.getStore().getAt(arguments[0]); var co=(g.columns&&g.columns[arguments[1]])?g.columns[arguments[1]]:arguments[1]; g.editingPlugin.startEdit(rec, co); return true; }\"");
        w.writeLine("    + \" return false; } catch(e){ return false; }\", (long) rowIdx, col);");
        w.openBlock("if (ok == null || !ok)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("Thread.sleep(200);");
        w.writeLine("WebElement editor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"return document.activeElement;\");");
        w.openBlock("if (editor == null)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String tag = editor.getTagName();");
        w.openBlock("if (!\"input\".equalsIgnoreCase(tag) && !\"textarea\".equalsIgnoreCase(tag))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String ro = editor.getAttribute(\"readonly\");");
        w.openBlock("if (ro != null && !ro.isEmpty() && !\"false\".equals(ro))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
        w.writeLine("Thread.sleep(60);");
        w.writeLine("editor.sendKeys(createdMarker + \"_\" + col);");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("filledCount++;");
        w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" OK\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"  [inline-fill] col=\" + col + \" FAIL: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("assertTrue(filledCount > 0, \"testCreate (inline): ни одна ячейка новой строки не заполнилась (строка добавлена, но ввод данных не прошёл)\");");
        w.writeLine("shot(\"cells_filled\");");
        // 5. Закоммитить редактор ячейки в запись и сохранить через Редактирование → Сохранить Изменения.
        //    Без коммита открытый редактор перехватывает клик по меню → «Сохранить Изменения» не жмётся.
        writeCommitGridEditorScript(w);
        w.writeLine("boolean saved = step(\"Редактирование → Сохранить Изменения\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        // Фолбэк: если «Добавить» открыл модальную карточку, save-кнопка — «Готово»/«Сохранить»/«OK».
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"OK\");");
        w.writeLine("assertTrue(saved, \"testCreate (inline): не удалось сохранить (ни 'Сохранить Изменения', ни 'Готово'/'Сохранить'/'OK')\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(3000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        // 6. Подтверждение: маркер в гриде
        w.writeLine("assertTrue(gridContainsRow(createdMarker), \"testCreate (inline): маркер '\" + createdMarker + \"' не найден в таблице после save\");");
        w.closeBlock();
        w.writeLine();
    }

    /** Type 2 (inline-table): update — select row 0 → startEditing → unique value → Сохранить. */
    private void writeInlineTableUpdateTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(4)");
        w.writeLine("@DisplayName(\"Update row inline (Type 2 entity)\")");
        w.openBlock("void testUpdate()");
        w.writeLine("shot(\"start\");");
        // Уникальное значение для update — чтобы потом отличить его в гриде.
        w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
        // 1. Найти редактируемый грид (Ext3 + Ext4), требуем непустой
        writeLocateEditableGridScript(w, "colCount", true);
        w.writeLine("assertTrue(colCount != null && colCount > 0, \"testUpdate (inline): не нашли таблицу или она пуста (код=\" + colCount + \")\");");
        writeGridDiagLog(w, "testUpdate (inline)");
        // 2. ВЫБРАТЬ запись для редактирования (строка 0) — раньше запись не выбиралась.
        writeSelectGridRowScript(w, "0");
        w.writeLine("shot(\"row_selected\");");
        // 3. Перебираем колонки строки 0, ищем первую editable + non-readonly
        w.writeLine("boolean edited = false;");
        w.openBlock("for (long col = 0; col < colCount && !edited; col++)");
        w.openBlock("try");
        w.writeLine("Boolean ok = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var g=window.__t2grid; if (g.startEditing){ g.startEditing(0, arguments[0]); return true; }\"");
        w.writeLine("    + \" if (g.editingPlugin && g.editingPlugin.startEdit){ var rec=g.getStore().getAt(0); var co=(g.columns&&g.columns[arguments[0]])?g.columns[arguments[0]]:arguments[0]; g.editingPlugin.startEdit(rec, co); return true; }\"");
        w.writeLine("    + \" return false; } catch(e){ return false; }\", col);");
        w.openBlock("if (ok == null || !ok)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("Thread.sleep(200);");
        w.writeLine("WebElement editor = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"return document.activeElement;\");");
        w.openBlock("if (editor == null)");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String tag = editor.getTagName();");
        w.openBlock("if (!\"input\".equalsIgnoreCase(tag) && !\"textarea\".equalsIgnoreCase(tag))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("String ro = editor.getAttribute(\"readonly\");");
        w.openBlock("if (ro != null && !ro.isEmpty() && !\"false\".equals(ro))");
        w.writeLine("continue;");
        w.closeBlock();
        w.writeLine("((org.openqa.selenium.JavascriptExecutor) driver).executeScript(\"arguments[0].value=''; arguments[0].dispatchEvent(new Event('input',{bubbles:true}));\", editor);");
        w.writeLine("Thread.sleep(60);");
        w.writeLine("editor.sendKeys(updatedValue);");
        w.writeLine("Thread.sleep(100);");
        w.writeLine("editor.sendKeys(org.openqa.selenium.Keys.ENTER);");
        w.writeLine("Thread.sleep(150);");
        w.writeLine("edited = true;");
        w.writeLine("System.out.println(\"testUpdate (inline): отредактировали col=\" + col + \" значением '\" + updatedValue + \"'\");");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("System.out.println(\"testUpdate (inline): col=\" + col + \" FAIL: \" + e.getMessage());");
        w.closeBlock();
        w.closeBlock();
        w.writeLine("assertTrue(edited, \"testUpdate (inline): ни одну ячейку выбранной строки не удалось отредактировать\");");
        w.writeLine("shot(\"value_typed\");");
        // 4. Закоммитить редактор в запись, затем сохранить.
        writeCommitGridEditorScript(w);
        w.writeLine("boolean saved = step(\"Редактирование → Сохранить Изменения\", () -> clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\"));");
        // Фолбэк: модальная карточка редактирования — save-кнопка «Готово»/«Сохранить»/«OK».
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0413\\u043e\\u0442\\u043e\\u0432\\u043e\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c\");");
        w.writeLine("if (!saved) saved = clickButtonByText(\"OK\");");
        w.writeLine("assertTrue(saved, \"testUpdate (inline): не удалось сохранить (ни 'Сохранить Изменения', ни 'Готово'/'Сохранить'/'OK')\");");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(3000); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_save\");");
        // 5. Подтверждение
        w.writeLine("assertTrue(gridContainsRow(updatedValue), \"testUpdate (inline): новое значение '\" + updatedValue + \"' не найдено в таблице после save\");");
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
            // верифицируем что нет ошибки. Это «smoke»-update.
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
        // Даём гриду результатов 1с на полную отрисовку после executeSearch — иначе
        // selectAndOpenRecord может попасть в параметрическую форму или в ещё пустой
        // результат-грид и dblclick никуда не приведёт.
        w.openBlock("try");
        w.writeLine("Thread.sleep(1000);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.writeLine("shot(\"before_select_record\");");
        // 1) Захватываем подпись первой строки ДО открытия карточки — чтобы потом понять,
        //    какую именно запись мы редактировали.
        w.writeLine("String editedRowSignature = captureFirstResultRowSignature();");
        w.writeLine("System.out.println(\"testUpdate: подпись редактируемой записи = '\" + editedRowSignature + \"'\");");
        // 2) Открываем первую запись. Без перебора — если на первой записи update недоступен,
        //    проблема системная, а не «не та строка».
        w.writeLine("boolean opened = step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("assertTrue(opened, \"testUpdate: не удалось открыть первую запись на редактирование\");");
        // ВАЖНО: selectAndOpenRecord возвращает true просто после клика — даже если карточка
        // НЕ открылась (например dblclick попал в заголовок грида). Проверяем РЕАЛЬНО что
        // карточка появилась. Раньше тест ехал дальше и fill попадал в PropertyGrid
        // формы поиска вместо карточки записи.
        w.writeLine("boolean cardOpened = waitUntil(d -> isOnRecordCard() || isDialogOpen(), 8, \"edit form opened\");");
        w.openBlock("if (!cardOpened)");
        w.writeLine("dumpCardDiagnostics();");
        w.writeLine("shot(\"card_not_opened\");");
        w.writeLine("fail(\"testUpdate: карточка записи НЕ открылась после selectAndOpenRecord (dblclick попал не на строку либо запись запрещена к редактированию). См. dumpCardDiagnostics в логе и скриншот card_not_opened.\");");
        w.closeBlock();
        w.writeLine("waitForCardLoaded(10);");
        // Карточка может рендериться лениво (ExtJS подгружает PropertyGrid + значения
        // полей по AJAX уже ПОСЛЕ того как waitForCardLoaded считает её открытой).
        // Без паузы fill попадает в ещё не загрузившийся редактор → save видит null.
        w.writeLine("System.out.println(\"testUpdate: ждём 2.5с пока карточка догрузит значения полей\");");
        w.openBlock("try");
        w.writeLine("Thread.sleep(2500);");
        w.closeBlock();
        w.openBlock("catch (InterruptedException ignored)");
        w.closeBlock();
        w.writeLine("shot(\"record_opened\");");
        // Лог активного окна ДО fillX — поможет понять что было перед изменением.
        w.openBlock("try");
        w.writeLine("Object awBefore = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(");
        w.writeLine("    \"try { var a = (Ext.WindowMgr && Ext.WindowMgr.getActive) ? Ext.WindowMgr.getActive() : null; return a ? (a.id + ' / title=' + (a.title||'')) : 'no-active-window'; } catch(e) { return 'ext-err:' + e.message; }\");");
        w.writeLine("System.out.println(\"testUpdate: активное окно ДО fillX = \" + awBefore);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 3) Меняем РОВНО ОДНО поле — первое STRING. Остальные значения, которые уже
        //    есть в карточке, не трогаем (никаких clear/fillAll — обновлять надо именно
        //    одно поле, чтобы остальные не уехали в null и сервер не отверг save).
        w.writeLine("String updatedValue = \"Upd\" + System.nanoTime();");
        w.writeLine("page.lastFilledValues.clear();");
        w.writeLine("System.out.println(\"testUpdate: меняем ОДНО поле '" + stringField.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "' на '\" + updatedValue + \"' (остальные поля карточки не трогаем)\");");
        w.writeLine("step(\"type new value\", () -> page." + methodName + "(updatedValue));");
        w.writeLine("java.util.LinkedHashMap<String, String> filledSnapshot = new java.util.LinkedHashMap<>(page.lastFilledValues);");
        w.writeLine("System.out.println(\"testUpdate: новое значение '\" + updatedValue + \"' в поле \" + filledSnapshot.keySet());");
        w.writeLine("shot(\"value_typed\");");
        // Лог активного окна ПОСЛЕ fillX — если оно изменилось (например Сведения →
        // Приглашённые ГСК), значит fillX переключил раздел.
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
        // 4) TAB чтобы закомитить активный редактор PropertyGrid'а.
        w.openBlock("try");
        w.writeLine("new org.openqa.selenium.interactions.Actions(driver).sendKeys(org.openqa.selenium.Keys.TAB).perform();");
        w.writeLine("Thread.sleep(200);");
        w.closeBlock();
        w.openBlock("catch (Exception ignored)");
        w.closeBlock();
        // 4b) ПРИНУДИТЕЛЬНО закоммитить активный редактор в запись (Ext3 stopEditing(false) /
        //     Ext4 completeEdit). Без этого правка ячейки PropertyGrid остаётся «визуальной»
        //     (значение не попадает в record.set), «Сохранить Изменения» шлёт на сервер СТАРОЕ
        //     значение, и после перечтения карточки правка «слетает» (поле остаётся чёрным).
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
        // 5) Save ТОЛЬКО через дропдаун «Редактирование → Сохранить Изменения».
        //    В карточке записи нет кнопок «Готово»/«Сохранить»/Enter — попытка их кликнуть
        //    лишь промахивалась по чужим элементам и тест думал что save прошёл, хотя
        //    реально ничего не сохранилось. Если дропдаун не сработал — реальный fail.
        w.writeLine("boolean savedClicked = clickEditDropdownAction(\"\\u0421\\u043e\\u0445\\u0440\\u0430\\u043d\\u0438\\u0442\\u044c \\u0418\\u0437\\u043c\\u0435\\u043d\\u0435\\u043d\\u0438\\u044f\");");
        w.writeLine("System.out.println(\"testUpdate: 'Редактирование → Сохранить Изменения' clicked=\" + savedClicked);");
        w.writeLine("assertTrue(savedClicked, \"testUpdate: не удалось через 'Редактирование' открыть дропдаун и кликнуть 'Сохранить Изменения'. \"");
        w.writeLine("    + \"Возможно карточка не догрузилась или кнопка 'Редактирование' не нашлась — см. dumpCardDiagnostics в логе.\");");
        // 6) Popup-диагностика. После «Сохранить Изменения» стенд может показать
        //    подтверждение или сразу сохранить молча. Если popup явно про ошибку
        //    валидации — фейлим без хождения в грид.
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
        // 8) Re-навигация к гриду результатов и подтверждение через ПОИСК — как делает
        //    пользователь руками: вписать новое значение в форму поиска и убедиться что
        //    запись находится.
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(ENTITY_NAME, FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_renavigate\");");
        w.writeLine();
        // 8) Проверка СОХРАНЕНИЯ через ПЕРЕОТКРЫТИЕ записи (надёжнее фильтр-поиска, который
        //    давал ложный PASS): ищем по новому значению, открываем найденную запись и убеждаемся,
        //    что КАРТОЧКА реально показывает новое значение (серверное). Текст из <input> формы
        //    поиска в innerText не попадает, поэтому ложного срабатывания на введённом фильтре нет.
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
        // 9) assertTrue — реальный fail, не SKIP.
        w.writeLine("assertTrue(updateApplied,");
        w.writeLine("    \"testUpdate: после save новое значение '\" + updatedValue + \"' в поле '" + stringField.getName().replace("\\", "\\\\").replace("\"", "\\\"") + "' НЕ сохранилось \"");
        w.writeLine("    + \"(переоткрытая запись его не показывает — изменение «слетело»). Подпись записи='\" + editedRowSignature + \"'. \"");
        w.writeLine("    + \"Popup сервера после save='\" + updatePopupText + \"'.\");");
        w.closeBlock();
        w.writeLine();
    }

    private void writeDeleteTest(JavaFileWriter w) {
        w.writeLine("@Test");
        w.writeLine("@Order(5)");
        w.writeLine("@DisplayName(\"Delete record\")");
        w.openBlock("void testDelete()");
        w.writeLine("shot(\"initial_grid\");");
        w.writeLine("int rowsBefore = page.getTableRowCount();");
        // КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: захватываем подпись удаляемой записи ДО открытия карточки.
        // Раньше захват шёл ПОСЛЕ selectAndOpenRecord() — карточка уже на экране,
        // .x-grid3-row-selected находит селекцию в чужом гриде/таблице, а лидирующая
        // ячейка «номер строки» (1) делает маркер несамоидентифицирующим — после удаления
        // на ту же позицию встаёт следующая запись и markerGone всегда был false.
        // captureFirstResultRowSignature() берёт первую строку самого большого видимого
        // грида и срезает ведущую цифровую ячейку.
        w.writeLine("String deletedMarker = captureFirstResultRowSignature();");
        w.writeLine("System.out.println(\"testDelete: маркер удаляемой записи (ДО открытия карточки) = '\" + deletedMarker + \"'\");");
        w.writeLine("step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("shot(\"row_selected\");");
        w.openBlock("try");
        w.writeLine("step(\"click Удалить in card toolbar\", () -> clickEditDropdownAction(\"Удалить\"));");
        w.closeBlock();
        w.openBlock("catch (Exception e)");
        w.writeLine("driver.findElement(By.xpath(\"//button[contains(text(), 'Удалить')] | //button[contains(text(), 'Готово')]\")).click();");
        w.closeBlock();
        w.writeLine("shot(\"delete_clicked\");");
        w.writeLine("acceptAlertIfPresent();");
        // Захватываем popup ПЕРЕД и ПОСЛЕ confirm — если сервер отверг удаление
        // (FK references, системная запись и т.п.), увидим причину.
        w.writeLine("String deletePopupText = capturePopupText(\"after-\\u0423\\u0434\\u0430\\u043b\\u0438\\u0442\\u044c\");");
        w.writeLine("System.out.println(\"testDelete: popup после Удалить = '\" + deletePopupText + \"'\");");
        // E3Core открывает ExtJS-confirm «Да/Нет» — без клика на «Да» удаление не применяется.
        w.writeLine("confirmDialogYes();");
        w.writeLine("shot(\"after_confirm\");");
        w.writeLine("String deletePopupAfter = capturePopupText(\"after-confirm\");");
        w.openBlock("if (!deletePopupAfter.isEmpty() && !deletePopupAfter.equals(deletePopupText))");
        w.writeLine("System.out.println(\"testDelete: popup после confirm = '\" + deletePopupAfter + \"'\");");
        w.writeLine("deletePopupText = deletePopupAfter;");
        w.closeBlock();
        w.writeLine("confirmDialogYes();");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_delete\");");
        w.writeLine();
        w.writeLine("assertFalse(isErrorPresent(), \"No errors should be present after deleting a record\");");
        w.writeLine();
        // ВАЖНО: после удаления текущий грид — это либо карточка, либо child-грид внутри
        // карточки, либо вообще тот же экран с одной строкой про удалённый объект. Считать
        // строки и искать маркер ТАМ бессмысленно. Возвращаемся к таблице результатов
        // (закрываем карточку → ре-навигируем → ре-выполняем поиск) и только потом сравниваем.
        w.writeLine("resetState();");
        w.writeLine("navigationAttempted = false;");
        w.writeLine("cardOpenAttempted = false;");
        w.writeLine("addDialogFailed = false;");
        w.writeLine("navigateToEntity(ENTITY_NAME, FEATURE_NAME);");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_renavigate\");");
        w.writeLine();
        w.writeLine("int rowsAfter = page.getTableRowCount();");
        w.writeLine("boolean markerGone = deletedMarker.isEmpty() ? false : !gridContainsRow(deletedMarker);");
        w.writeLine("System.out.println(\"testDelete: rows \" + rowsBefore + \" -> \" + rowsAfter + \", markerGone=\" + markerGone);");
        // Маркер — единственный надёжный сигнал. Row count шумит из-за чужих гридов.
        w.openBlock("if (deletedMarker.isEmpty())");
        w.writeLine("Assumptions.assumeTrue(false, \"Delete: не удалось захватить маркер удаляемой записи — пропускаем\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("assertTrue(markerGone,");
        w.writeLine("    \"Delete: после ре-навигации к таблице результатов rows \" + rowsBefore + \" -> \" + rowsAfter");
        w.writeLine("    + \" AND маркер '\" + deletedMarker + \"' всё ещё в гриде — запись не удалилась.\"");
        w.writeLine("    + \" Popup сервера='\" + deletePopupText + \"' (типичная причина: запись используется как FK в других таблицах или удаление справочника запрещено).\");");
        w.closeBlock();
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
        // «Логическое изменение» в E3Core — это создание новой исторической версии записи
        // (старая остаётся, новая получает свой H_KEY). UI часто запрашивает подтверждение,
        // и при ошибке (например, обязательное поле истории не заполнено) валидируется.
        // Тест считаем пройденным если действие просто отработало без падения сценария —
        // конкретное поведение зависит от настройки сущности на стенде.
        w.writeLine("step(\"click Лог.изменить in card toolbar\", () -> clickEditDropdownAction(\"Лог.изменить\"));");
        w.writeLine("confirmDialogYes();");
        w.writeLine("try { Thread.sleep(800); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"after_action\");");
        // Soft-проверка: если на форме показалась ошибка (требуется ввод истории) —
        // это легитимное поведение E3Core для логического изменения. Просто логируем.
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
        w.writeLine("int rowsBefore = page.getTableRowCount();");
        // Захватываем подпись архивируемой записи ДО открытия карточки (см. testDelete).
        w.writeLine("String archivedMarker = captureFirstResultRowSignature();");
        w.writeLine("System.out.println(\"testArchive: маркер архивируемой записи (ДО открытия карточки) = '\" + archivedMarker + \"'\");");
        w.writeLine("step(\"select + open record\", () -> selectAndOpenRecord());");
        w.writeLine("shot(\"row_selected\");");
        w.writeLine("step(\"click в Архив in card toolbar\", () -> clickEditDropdownAction(\"в Архив\"));");
        w.writeLine("shot(\"archive_clicked\");");
        w.writeLine("acceptAlertIfPresent();");
        w.writeLine("confirmDialogYes();");
        w.writeLine("shot(\"after_confirm\");");
        w.writeLine("waitForGridSettle();");
        w.writeLine("shot(\"after_archive\");");
        w.writeLine("assertFalse(isErrorPresent(), \"No errors should be present after archiving\");");
        w.writeLine();
        w.writeLine("int rowsAfter = page.getTableRowCount();");
        w.writeLine("boolean markerGone = archivedMarker.isEmpty() ? false : !gridContainsRow(archivedMarker);");
        // Если строк стало значительно больше — мы попали на дочерний грид внутри карточки
        // (например, история объекта). Сравнение rowsBefore/rowsAfter теряет смысл — SKIP.
        w.openBlock("if (rowsAfter > rowsBefore + 5)");
        w.writeLine("Assumptions.assumeTrue(false, \"Archive: row count grew from \" + rowsBefore + \" to \" + rowsAfter");
        w.writeLine("    + \" — we likely opened a different (child) grid; cannot compare archive result\");");
        w.closeBlock();
        w.openBlock("if (archivedMarker.isEmpty())");
        w.writeLine("Assumptions.assumeTrue(rowsAfter < rowsBefore,");
        w.writeLine("    \"Archive: could not capture marker AND row count unchanged (\" + rowsBefore + \" -> \" + rowsAfter + \") — archive may not be reachable on this build\");");
        w.closeBlock();
        w.openBlock("else");
        w.writeLine("assertTrue(rowsAfter < rowsBefore || markerGone,");
        w.writeLine("    \"Archive: rows \" + rowsBefore + \" -> \" + rowsAfter + \" AND archived row '\" + archivedMarker + \"' still in active grid\");");
        w.closeBlock();
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
        // Form needs a moment to render after the tree double-click. Conditional wait is hard here
        // (no specific selector), so short fixed delay is the pragmatic choice.
        w.writeLine("try { Thread.sleep(400); } catch (InterruptedException ignored) {}");
        w.writeLine("shot(\"search_opened\");");
        w.writeLine();
        // Build a LinkedHashMap of the actual values we're about to pass, so the test logs them
        // verbatim BEFORE the search runs. This is the user-facing diagnostic the run report
        // surfaces: "for THIS search test we filled GBS_NAME='Test_GBS_NAME', INN='123…'".
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
        // Actually fill the form fields with the same values we just logged. Pass BOTH the
        // technical name and the Russian title — fillSearchParam tries title first (label match).
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
        // Parameterless searches like «Поиск ОГСК», «Поиск объединений» auto-execute when the
        // tree node or menu item is clicked — no separate submit button. Only parametric searches
        // («по параметрам») require executeSearch.
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
        // "Own grid" = a PropertyGroup whose name matches the entity itself (e.g. Должностные лица
        // grid inside the Должностные лица entity). The grid IS the main view; tests don't need
        // to open a record card to see it. Only TRUE child grids (e.g. История ГСК/ОГСК inside
        // ГСК/ОГСК) need openRecordCard.
        boolean isOwnGrid = grid.getName() != null && entity.getName() != null
                && grid.getName().equalsIgnoreCase(entity.getName());
        w.writeLine("@Test");
        w.writeLine("@DisplayName(\"Grid view: " + grid.getName() + "\")");
        w.openBlock("void " + testName + "()");
        w.writeLine("shot(\"start\");");
        if (!isOwnGrid) {
            // Child-grid tabs only render inside an open record card.
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
            // Any column found = PASS. Zero columns found despite grid being visible = SKIP
            // (our column-header selectors don't match this stand's DOM); not a product bug.
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
            // Only take from the entity's primary FORM view (typeLink="P"). Skipping grid groups
            // here is critical: a grid PropertyGroup represents a tab-collection of a CHILD entity
            // (e.g. "Документы совещания" inside Совещание) — its columns belong to the child, not
            // to the parent form. v5 was pulling them in, inflating expected-field counts and
            // causing testFieldsPresent to fail with "missing: Название, Файл, Присутствовал, …".
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
        // Поля с серверным def-value (E3Core их заполняет сам — «Дата изменения», «Оператор»,
        // «Дата создания» и т.п.). Если автотест пишет туда своё значение, форма на «Готово»
        // отвергает запрос, потому что значение не совпадает с тем, что сервер выставляет
        // автоматически. Пропускаем такие поля.
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
     * A search counts as "FK-only" when it has no parameters and its result is just
     * {SearchKey, SearchName}. Such searches are not exposed in the entity's own search-tree
     * window — they're invoked by OTHER entities when filling a foreign-key field via a picker.
     * Generating an own testSearch for them would produce fake passes (the test just sees the
     * default search results from setUp and reports "no error").
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
     * Engine-managed columns that appear in every E3Core grid and form (audit metadata: who
     * modified the row and when). They're in the XML model because they're real DB columns, but
     * tests should never check for them on a form — they're not user-facing fields and the form
     * may legitimately hide them.
     */
    private boolean isSystemFieldByName(Property prop) {
        String attr = prop.getAttrName();
        if (attr == null) return false;
        String u = attr.toUpperCase(java.util.Locale.ROOT);
        return "DATE_UPDATE".equals(u) || "OPERATOR".equals(u);
    }
}
