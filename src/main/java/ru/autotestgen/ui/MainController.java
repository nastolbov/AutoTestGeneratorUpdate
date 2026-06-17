package ru.autotestgen.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import ru.autotestgen.generator.*;
import ru.autotestgen.model.AppModel;
import ru.autotestgen.model.EntityClassifier;
import ru.autotestgen.model.EntityKind;
import ru.autotestgen.model.EntityObject;
import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;
import ru.autotestgen.common.ParserException;
import ru.autotestgen.common.Transliterator;
import ru.autotestgen.data.ReportDao;
import ru.autotestgen.parser.XmlModelParser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainController {

    // Поля ввода
    @FXML private TextField xmlPathField;
    @FXML private TextField urlField;
    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private TextField outputDirField;

    @FXML private CheckBox fastModeCheck;

    private final ComboBox<String> siteTypeCombo = new ComboBox<>();
    private final TextField subsystemField = new TextField();

    // Кнопки
    @FXML private Button btnSelectXml;
    @FXML private Button btnSelectOutputDir;
    @FXML private Button btnParse;
    @FXML private Button btnGenerate;
    @FXML private Button btnRunTests;
    @FXML private Button btnRunSelected;
    @FXML private Button btnShowHistory;

    /** Категории видов тестов: {подпись, шаблон имени метода для Surefire}. Одна строка на тестовый метод. */
    private static final String[][] TEST_CATEGORIES = {
        {"Поля формы",                       "testFieldsPresent"},
        {"Создание",                         "testCreate"},
        {"Изменение",                        "testUpdate"},
        {"Удаление",                         "testDelete"},
        {"Лог. удаление",                    "testLogicalEdit"},
        {"Архивирование",                    "testArchive"},
        {"Валидация (все обязательные)",     "testRequiredFieldValidation"},
        {"Валидация (частичная)",            "testPartialRequiredFieldValidation"},
        {"Поиск (все варианты)",             "testSearch*"},
        {"Гриды (все варианты)",             "testGrid*"},
    };

    // Entity tree (что будет протестировано / что нет)
    @FXML private TreeView<EntityNode> entityTreeView;

    // Таблица результатов
    @FXML private TableView<TestCaseRow> resultsTable;
    @FXML private TableColumn<TestCaseRow, String> colClass;
    @FXML private TableColumn<TestCaseRow, String> colMethod;
    @FXML private TableColumn<TestCaseRow, String> colStatus;
    @FXML private TableColumn<TestCaseRow, String> colDuration;
    @FXML private TableColumn<TestCaseRow, String> colMessage;

    // Статус
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea logArea;

    // Итоговые метки
    @FXML private Label totalLabel;
    @FXML private Label passedLabel;
    @FXML private Label failedLabel;

    private AppModel currentModel;
    private final ReportDao reportDao = new ReportDao();

    @FXML
    public void initialize() {
        // Настройка колонок таблицы
        colClass.setCellValueFactory(new PropertyValueFactory<>("className"));
        colMethod.setCellValueFactory(new PropertyValueFactory<>("methodName"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colMessage.setCellValueFactory(new PropertyValueFactory<>("message"));

        btnGenerate.setDisable(true);
        btnRunTests.setDisable(true);
        btnRunSelected.setDisable(true);
        progressBar.setVisible(false);

        // Уровень тестов всегда FULL (максимальный) — выбор уровня убран из UI.

        // Выбор типа сайта
        siteTypeCombo.getItems().addAll("E3Core (ExtJS)", "Обычный HTML-сайт", "Свой (Custom)");
        siteTypeCombo.getSelectionModel().selectFirst();

        // Значения по умолчанию
        urlField.setText("https://rrpo.cmirit.ru/E3Core");
        loginField.setText("test");
        passwordField.setText("b394e86609");
        outputDirField.setText(System.getProperty("user.dir") + "/generated-tests");
        subsystemField.setText("Гаражно-строительные кооперативы");

        log("Система готова к работе.");
    }

    @FXML
    private void onSelectXml() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите XML-файл метаданных");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML файлы", "*.xml"));
        File file = fc.showOpenDialog(xmlPathField.getScene().getWindow());
        if (file != null) {
            xmlPathField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    private void onSelectOutputDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Выберите каталог для генерации тестов");
        File dir = dc.showDialog(outputDirField.getScene().getWindow());
        if (dir != null) {
            outputDirField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    private void onParse() {
        String xmlPath = xmlPathField.getText();
        if (xmlPath == null || xmlPath.isEmpty()) {
            showAlert("Ошибка", "Укажите путь к XML-файлу.");
            return;
        }

        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            showAlert("Ошибка", "Файл не найден: " + xmlPath);
            return;
        }

        try {
            XmlModelParser parser = new XmlModelParser();
            currentModel = parser.parse(xmlFile);

            // Построить дерево сущностей: видно, по каким будут тесты, по каким нет.
            // Группировка зеркалит TestGenerator: PRIMARY → отдельный тест-класс (с вложенными
            // CHILD-детьми), REFERENCE_DICTIONARY/FK → пропуск (тесты не генерируются).
            buildEntityTree();

            // Берём имя подсистемы из CategoryName ("Logical View::<имя>"), если оно задано.
            String xmlSubsystem = currentModel.getSubsystemNameFromCategory();
            if (!xmlSubsystem.isEmpty()) {
                subsystemField.setText(xmlSubsystem);
                log("Подсистема из XML: " + xmlSubsystem);
            }

            log("XML разобран успешно. Найдено сущностей: " + currentModel.getEntities().size()
                    + ", поисков: " + currentModel.getSearches().size());
            statusLabel.setText("XML разобран: " + currentModel.getEntities().size() + " сущностей");
            btnGenerate.setDisable(false);

        } catch (ParserException e) {
            showAlert("Ошибка парсинга", e.getMessage());
            log("Ошибка парсинга: " + e.getMessage());
        }
    }

    /** Узел дерева сущностей: подпись + стиль (цвет) + tooltip (причина классификации). */
    private static final class EntityNode {
        final String text;
        final String style;     // стиль ячейки (цвет/жирность); пусто = по умолчанию
        final String tooltip;   // подсказка (причина классификации); null = без подсказки
        EntityNode(String text, String style, String tooltip) {
            this.text = text;
            this.style = style;
            this.tooltip = tooltip;
        }
        @Override public String toString() { return text; }
    }

    // Цвета групп: зелёный = отдельный тест-класс, янтарный = тест в составе родителя,
    // серый = тесты не генерируются. Заголовки групп — жирные.
    private static final String STYLE_GROUP   = "-fx-font-weight: bold;";
    private static final String STYLE_PRIMARY = "-fx-text-fill: #2e7d32; -fx-font-weight: bold;";
    private static final String STYLE_CHILD   = "-fx-text-fill: #f57f17;";
    private static final String STYLE_SKIP    = "-fx-text-fill: #9e9e9e;";

    /** Строит дерево из currentModel: группа «будут протестированы» (PRIMARY с вложенными CHILD)
     *  и группа «тесты не генерируются» (справочники / FK-пикеры). Источник — EntityClassifier,
     *  та же логика, по которой TestGenerator реально создаёт/пропускает тесты. */
    private void buildEntityTree() {
        TreeItem<EntityNode> root = new TreeItem<>(new EntityNode("root", "", null));

        TreeItem<EntityNode> tested = new TreeItem<>(
                new EntityNode("✓ Будут протестированы", STYLE_GROUP, null));
        TreeItem<EntityNode> skipped = new TreeItem<>(
                new EntityNode("✗ Тесты не генерируются", STYLE_GROUP, null));

        // 1) Классифицируем все сущности, заводим узлы для PRIMARY (по GUID, чтобы подвешивать детей).
        java.util.Map<String, TreeItem<EntityNode>> primaryByGuid = new java.util.LinkedHashMap<>();
        java.util.List<EntityObject> children = new java.util.ArrayList<>();
        int primaryCount = 0, childCount = 0, skipCount = 0;

        // GUID контейнеров «Справочник …», для которых генерируется отдельный CRUD-класс
        // (по их дочерней сущности-узлу с операциями). Такой контейнер — пункт меню, а не «без тестов».
        java.util.Set<String> dictHostGuids = new java.util.HashSet<>();
        for (EntityObject e : currentModel.getEntities()) {
            if (EntityClassifier.isTreeDictionaryCrud(e, currentModel)) {
                EntityObject p = EntityClassifier.classify(e, currentModel).parentEntity;
                if (p != null && p.getGuid() != null) dictHostGuids.add(p.getGuid());
            }
            EntityObject lh = EntityClassifier.listContainerMenuHost(e, currentModel);
            if (lh != null && lh.getGuid() != null) dictHostGuids.add(lh.getGuid());
        }

        for (EntityObject entity : currentModel.getEntities()) {
            EntityClassifier.Classification cls = EntityClassifier.classify(entity, currentModel);
            if (cls.kind == EntityKind.PRIMARY) {
                // Контейнер-список (например «Мероприятия») отдельным классом не тестируется — его CRUD
                // покрывает карточка-сущность («Мероприятие»), поэтому в дереве его не показываем.
                if (entity.getGuid() != null && dictHostGuids.contains(entity.getGuid())) {
                    continue;
                }
                TreeItem<EntityNode> item = new TreeItem<>(
                        new EntityNode(label(entity), STYLE_PRIMARY, "PRIMARY — отдельный тест-класс. " + cls.reason));
                if (entity.getGuid() != null) primaryByGuid.put(entity.getGuid(), item);
                tested.getChildren().add(item);
                primaryCount++;
            } else if (cls.kind == EntityKind.CHILD) {
                children.add(entity);
            } else {
                // Контейнер «Справочник …», чей справочник-CHILD тестируется отдельным классом,
                // не показываем как «без тестов» — тест для него есть (назван по контейнеру).
                if (entity.getGuid() != null && dictHostGuids.contains(entity.getGuid())) {
                    continue;
                }
                skipped.getChildren().add(new TreeItem<>(
                        new EntityNode(label(entity), STYLE_SKIP, "Тесты не генерируются. " + cls.reason)));
                skipCount++;
            }
        }

        // 2) Подвешиваем CHILD под их родителя (grid-вкладка / узел дерева). Если родитель не
        //    PRIMARY/не найден — показываем ребёнка отдельным узлом в группе «будут протестированы».
        for (EntityObject child : children) {
            EntityClassifier.Classification cls = EntityClassifier.classify(child, currentModel);
            // Справочник-в-дереве: отдельный CRUD тест-класс (create/update/delete своей записи).
            if (EntityClassifier.isTreeDictionaryCrud(child, currentModel)) {
                tested.getChildren().add(new TreeItem<>(new EntityNode(
                        label(child) + " (через дерево, CRUD)", STYLE_PRIMARY,
                        "Отдельный тест-класс. Добавление ПКМ по контейнеру «" + cls.parentEntity.getName()
                                + "» → «Добавить» (карточка с «Готово»), затем изменение/удаление своей записи. " + cls.reason)));
                primaryCount++;
                continue;
            }
            // Карточка под списком-контейнером («Мероприятие» под «Мероприятия») — обычный способ 1.
            EntityObject listHost = EntityClassifier.listContainerMenuHost(child, currentModel);
            if (listHost != null) {
                tested.getChildren().add(new TreeItem<>(new EntityNode(
                        label(child) + " (способ 1, меню «" + listHost.getName() + "»)", STYLE_PRIMARY,
                        "Отдельный тест-класс (способ 1): Добавить/Найти через пункт меню «" + listHost.getName()
                                + "». " + cls.reason)));
                primaryCount++;
                continue;
            }
            String kindNote = cls.parentGrid != null ? " (вкладка)" : " (узел дерева)";
            TreeItem<EntityNode> childItem = new TreeItem<>(
                    new EntityNode(label(child) + kindNote, STYLE_CHILD,
                            "CHILD — тест в составе родителя. " + cls.reason));
            TreeItem<EntityNode> parent = (cls.parentEntity != null && cls.parentEntity.getGuid() != null)
                    ? primaryByGuid.get(cls.parentEntity.getGuid()) : null;
            (parent != null ? parent : tested).getChildren().add(childItem);
            childCount++;
        }

        if (!tested.getChildren().isEmpty()) root.getChildren().add(tested);
        if (!skipped.getChildren().isEmpty()) root.getChildren().add(skipped);
        tested.setExpanded(true);
        skipped.setExpanded(true);
        for (TreeItem<EntityNode> p : primaryByGuid.values()) p.setExpanded(true);

        entityTreeView.setRoot(root);
        entityTreeView.setShowRoot(false);
        entityTreeView.setCellFactory(tv -> new TreeCell<EntityNode>() {
            @Override protected void updateItem(EntityNode node, boolean empty) {
                super.updateItem(node, empty);
                if (empty || node == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    setText(node.text);
                    setStyle(node.style);
                    setTooltip(node.tooltip == null ? null : new Tooltip(node.tooltip));
                }
            }
        });

        log("Дерево сущностей: PRIMARY=" + primaryCount + ", CHILD=" + childCount
                + ", без тестов=" + skipCount);
    }

    /** Подпись сущности в дереве: имя + [CRUD] + (N полей) — как было в плоском списке. */
    private static String label(EntityObject entity) {
        String info = entity.getName();
        if (entity.hasCrudOperations()) info += " [CRUD]";
        int propCount = (int) entity.getPropertyGroups().stream()
                .flatMap(pg -> pg.getProperties().stream())
                .filter(p -> p.isFlagDisplay())
                .count();
        info += " (" + propCount + " полей)";
        return info;
    }

    @FXML
    private void onGenerate() {
        if (currentModel == null) {
            showAlert("Ошибка", "Сначала разберите XML-файл.");
            return;
        }

        String outputPath = outputDirField.getText();
        if (outputPath == null || outputPath.isEmpty()) {
            showAlert("Ошибка", "Укажите каталог для генерации.");
            return;
        }

        String url = urlField.getText();
        if (url == null || url.isEmpty()) {
            showAlert("Ошибка", "Укажите URL тестируемого сайта.");
            return;
        }

        try {
            TestConfig config = new TestConfig();
            config.setBaseUrl(url);
            config.setLogin(loginField.getText());
            config.setPassword(passwordField.getText());
            config.setOutputDir(Path.of(outputPath));
            // Имя исходного XML — под него генератор создаёт отдельную подпапку, чтобы результаты
            // разных XML не смешивались. Повторная генерация того же XML пересоздаёт его подпапку.
            config.setSourceXmlName(getXmlFileName());
            // Тип сайта
            int siteIdx = siteTypeCombo.getSelectionModel().getSelectedIndex();
            config.setSiteType(siteIdx == 0 ? "e3core" : siteIdx == 1 ? "generic" : "custom");
            config.setSubsystemName(subsystemField.getText());
            // Уровень тестов всегда максимальный (FULL).
            config.setTestLevel("full");
            // SubsystemsSmokeTest не требуется — оставляем значение по умолчанию (false).

            TestGenerator generator = new TestGenerator(config);
            generator.generate(currentModel);

            java.nio.file.Path projectDir = TestGenerator.resolveProjectDir(Path.of(outputPath), getXmlFileName());
            log("Уровень тестов: " + config.getTestLevel().toUpperCase());
            log("Сгенерировано тестовых классов: " + currentModel.getEntities().size());
            log("Тесты сгенерированы в: " + projectDir);
            statusLabel.setText("Тесты сгенерированы");
            btnRunTests.setDisable(false);
            btnRunSelected.setDisable(false);

        } catch (Exception e) {
            showAlert("Ошибка генерации", e.getMessage());
            log("Ошибка генерации: " + e.getMessage());
        }
    }

    @FXML
    private void onRunTests() {
        launchRun(null);
    }

    /**
     * Открывает диалог выбора сущностей и видов тестов и запускает только выбранное подмножество.
     * Если ни один вид тестов не выбран — запускаются все тесты выбранных сущностей.
     */
    @FXML
    private void onRunSelected() {
        if (currentModel == null || currentModel.getEntities().isEmpty()) {
            showAlert("Ошибка", "Сначала разберите XML и сгенерируйте тесты.");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Выбор тестов для запуска");
        dlg.setHeaderText("Отметьте сущности и виды тестов.\n"
                + "Если ни один вид не отмечен — запускаются все тесты выбранных сущностей.");
        ButtonType runBtn = new ButtonType("Запустить", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(runBtn, ButtonType.CANCEL);

        // Флажки сущностей
        List<CheckBox> entityChecks = new ArrayList<>();
        VBox entityBox = new VBox(4);
        for (EntityObject entity : currentModel.getEntities()) {
            CheckBox cb = new CheckBox(entity.getName());
            cb.setUserData(Transliterator.toClassName(entity.getName()) + "Test");
            entityChecks.add(cb);
            entityBox.getChildren().add(cb);
        }
        CheckBox allEntities = new CheckBox("— выбрать все сущности —");
        allEntities.setOnAction(e -> entityChecks.forEach(c -> c.setSelected(allEntities.isSelected())));

        // Флажки видов тестов
        List<CheckBox> typeChecks = new ArrayList<>();
        VBox typeBox = new VBox(4);
        for (String[] cat : TEST_CATEGORIES) {
            CheckBox cb = new CheckBox(cat[0]);
            cb.setUserData(cat[1]);
            typeChecks.add(cb);
            typeBox.getChildren().add(cb);
        }

        // Превью итогового фильтра -Dtest в реальном времени.
        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(4);
        preview.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");
        Label previewLabel = new Label("Превью фильтра Surefire (-Dtest=):");
        Runnable refreshPreview = () -> preview.setText(buildTestFilter(entityChecks, typeChecks));
        for (CheckBox cb : entityChecks) cb.selectedProperty().addListener((o, a, b) -> refreshPreview.run());
        for (CheckBox cb : typeChecks)   cb.selectedProperty().addListener((o, a, b) -> refreshPreview.run());
        allEntities.selectedProperty().addListener((o, a, b) -> refreshPreview.run());
        refreshPreview.run();

        ScrollPane entityScroll = new ScrollPane(entityBox);
        entityScroll.setPrefHeight(320);
        entityScroll.setFitToWidth(true);
        VBox left = new VBox(6, new Label("Сущности:"), allEntities, entityScroll);
        left.setPrefWidth(320);
        VBox right = new VBox(6, new Label("Виды тестов:"), typeBox);
        HBox lists = new HBox(20, left, right);
        VBox content = new VBox(10, lists, previewLabel, preview);
        content.setStyle("-fx-padding: 10;");
        dlg.getDialogPane().setContent(content);

        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != runBtn) {
            return;
        }

        String filter = buildTestFilter(entityChecks, typeChecks);
        if (filter.isEmpty()) {
            showAlert("Ошибка", "Не выбрана ни одна сущность.");
            return;
        }
        log("Запуск выбранных тестов: -Dtest=" + filter);
        launchRun(filter);
    }

    private static String buildTestFilter(List<CheckBox> entityChecks, List<CheckBox> typeChecks) {
        List<String> classes = entityChecks.stream()
                .filter(CheckBox::isSelected)
                .map(c -> (String) c.getUserData())
                .toList();
        if (classes.isEmpty()) return "";
        List<String> methodPatterns = typeChecks.stream()
                .filter(CheckBox::isSelected)
                .map(c -> (String) c.getUserData())
                .toList();
        String methodSuffix = methodPatterns.isEmpty() ? "" : "#" + String.join("+", methodPatterns);
        StringBuilder filter = new StringBuilder();
        for (String cls : classes) {
            if (filter.length() > 0) filter.append(",");
            filter.append(cls).append(methodSuffix);
        }
        return filter.toString();
    }

    /**
     * Запускает сгенерированные тесты. {@code testFilter} null/пустой — запуск всех;
     * иначе значение передаётся Surefire как {@code -Dtest=<filter>}.
     */
    private void launchRun(String testFilter) {
        String outputPath = outputDirField.getText();
        if (outputPath == null || outputPath.isEmpty()) {
            showAlert("Ошибка", "Укажите каталог с тестами.");
            return;
        }
        // Прогон — в подпапке текущего XML (туда же сгенерировались тесты).
        final String projectPath = TestGenerator.resolveProjectDir(Path.of(outputPath), getXmlFileName()).toString();

        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        btnRunTests.setDisable(true);
        btnRunSelected.setDisable(true);
        statusLabel.setText("Запуск тестов...");
        log(testFilter == null || testFilter.isBlank()
                ? "Запуск всех тестов..." : "Запуск выбранных тестов...");

        Task<TestRunResult> task = new Task<>() {
            @Override
            protected TestRunResult call() throws Exception {
                TestRunner runner = new TestRunner();
                boolean fast = fastModeCheck != null && fastModeCheck.isSelected();
                return runner.run(Path.of(projectPath), getXmlFileName(), urlField.getText(),
                        null, testFilter, fast);
            }
        };

        task.setOnSucceeded(event -> {
            TestRunResult result = task.getValue();
            displayResults(result);
            reportDao.saveRun(result);
            progressBar.setVisible(false);
            btnRunTests.setDisable(false);
            btnRunSelected.setDisable(false);
            statusLabel.setText("Тесты завершены");
            log("Тесты завершены. Всего: " + result.getTotalTests()
                    + ", Успешно: " + result.getPassed()
                    + ", Ошибки: " + result.getFailed());
            log("HTML-отчёт (v5, с фотолетописью): " + projectPath + "/target/run-report.html");
            log("HTML-отчёт (стандартный surefire): " + projectPath + "/target/site/surefire-report.html");
            log("CSV-отчёт: " + projectPath + "/target/run-report.csv");
            log("Скриншоты: " + projectPath + "/target/screenshots/");
            if (result.getMavenOutput() != null && !result.getMavenOutput().isEmpty()) {
                log("=== Вывод Maven ===");
                // Показываем последние 100 строк
                String[] lines = result.getMavenOutput().split("\n");
                int start = Math.max(0, lines.length - 100);
                for (int i = start; i < lines.length; i++) {
                    log(lines[i]);
                }
                log("=== Конец вывода Maven ===");
            }
        });

        task.setOnFailed(event -> {
            progressBar.setVisible(false);
            btnRunTests.setDisable(false);
            btnRunSelected.setDisable(false);
            statusLabel.setText("Ошибка запуска тестов");
            log("Ошибка: " + task.getException().getMessage());
            showAlert("Ошибка", task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void onShowHistory() {
        var runs = reportDao.getAllRuns();
        if (runs.isEmpty()) {
            log("История прогонов пуста.");
            return;
        }

        StringBuilder sb = new StringBuilder("=== История прогонов ===\n");
        for (TestRunResult run : runs) {
            sb.append(String.format("[%s] XML: %s | URL: %s | Всего: %d | OK: %d | Ошибки: %d | Время: %d мс\n",
                    run.getRunTimestamp(), run.getXmlFileName(), run.getBaseUrl(),
                    run.getTotalTests(), run.getPassed(), run.getFailed(), run.getDurationMs()));
        }
        log(sb.toString());

        // Также показываем в таблице детали последнего прогона
        if (!runs.isEmpty()) {
            displayResults(runs.get(0));
        }
    }

    private void displayResults(TestRunResult result) {
        ObservableList<TestCaseRow> rows = FXCollections.observableArrayList();
        for (TestCaseResult tcr : result.getResults()) {
            String message;
            if (tcr.getFailureMessage() != null && !tcr.getFailureMessage().isEmpty()) {
                message = tcr.getFailureMessage();
            } else if (tcr.isPassed() && !tcr.isSkipped()) {
                message = "✓ Проверено: " + tcr.getMethodName();
            } else {
                message = "";
            }
            rows.add(new TestCaseRow(
                    tcr.getClassName(),
                    tcr.getMethodName(),
                    tcr.isSkipped() ? "SKIP" : (tcr.isPassed() ? "OK" : "FAIL"),
                    tcr.getDurationMs() + " мс",
                    message
            ));
        }
        resultsTable.setItems(rows);

        colStatus.setCellFactory(column -> new TableCell<TestCaseRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (empty || item == null) {
                    setStyle("");
                } else if ("OK".equals(item)) {
                    setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                } else if ("SKIP".equals(item)) {
                    setStyle("-fx-background-color: #fff9c4; -fx-text-fill: #f57f17; -fx-font-weight: bold;");
                } else if ("FAIL".equals(item)) {
                    setStyle("-fx-background-color: #ffcdd2; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        });

        totalLabel.setText("Всего: " + result.getTotalTests());
        passedLabel.setText("Успешно: " + result.getPassed() + " | Пропущено: " + result.getSkipped());
        failedLabel.setText("Ошибки: " + result.getFailed());
    }

    private String getXmlFileName() {
        String path = xmlPathField.getText();
        if (path == null || path.isEmpty()) return "unknown.xml";
        return new File(path).getName();
    }

    private void log(String message) {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(message + "\n");
            }
        });
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Модель строки таблицы результатов.
     */
    public static class TestCaseRow {
        private final String className;
        private final String methodName;
        private final String status;
        private final String duration;
        private final String message;

        public TestCaseRow(String className, String methodName, String status, String duration, String message) {
            this.className = className;
            this.methodName = methodName;
            this.status = status;
            this.duration = duration;
            this.message = message;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public String getStatus() { return status; }
        public String getDuration() { return duration; }
        public String getMessage() { return message; }
    }
}
