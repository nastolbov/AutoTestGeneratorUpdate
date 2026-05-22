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

    // Input fields
    @FXML private TextField xmlPathField;
    @FXML private TextField urlField;
    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private TextField outputDirField;

    @FXML private ComboBox<String> testLevelCombo;
    @FXML private CheckBox smokeAllSubsystemsCheck;

    private final ComboBox<String> siteTypeCombo = new ComboBox<>();
    private final TextField subsystemField = new TextField();

    // Buttons
    @FXML private Button btnSelectXml;
    @FXML private Button btnSelectOutputDir;
    @FXML private Button btnParse;
    @FXML private Button btnGenerate;
    @FXML private Button btnRunTests;
    @FXML private Button btnRunSelected;
    @FXML private Button btnShowHistory;

    /** Test-type categories: {label, Surefire method-name pattern}. One row per real test method. */
    private static final String[][] TEST_CATEGORIES = {
        {"Поля формы",                       "testFieldsPresent"},
        {"Создание (полное)",                "testCreate"},
        {"Создание (только обязательные)",   "testCreateOnlyRequired"},
        {"Изменение",                        "testUpdate"},
        {"Удаление",                         "testDelete"},
        {"Лог. удаление",                    "testLogicalEdit"},
        {"Архивирование",                    "testArchive"},
        {"Валидация (все обязательные)",     "testRequiredFieldValidation"},
        {"Валидация (частичная)",            "testPartialRequiredFieldValidation"},
        {"Маски полей",                      "testMaskedFieldInput"},
        {"Поиск (все варианты)",             "testSearch*"},
        {"Гриды (все варианты)",             "testGrid*"},
    };

    // Entity list
    @FXML private ListView<String> entityListView;

    // Results table
    @FXML private TableView<TestCaseRow> resultsTable;
    @FXML private TableColumn<TestCaseRow, String> colClass;
    @FXML private TableColumn<TestCaseRow, String> colMethod;
    @FXML private TableColumn<TestCaseRow, String> colStatus;
    @FXML private TableColumn<TestCaseRow, String> colDuration;
    @FXML private TableColumn<TestCaseRow, String> colMessage;

    // Status
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea logArea;

    // Summary labels
    @FXML private Label totalLabel;
    @FXML private Label passedLabel;
    @FXML private Label failedLabel;

    private AppModel currentModel;
    private final ReportDao reportDao = new ReportDao();

    @FXML
    public void initialize() {
        // Setup table columns
        colClass.setCellValueFactory(new PropertyValueFactory<>("className"));
        colMethod.setCellValueFactory(new PropertyValueFactory<>("methodName"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDuration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        colMessage.setCellValueFactory(new PropertyValueFactory<>("message"));

        btnGenerate.setDisable(true);
        btnRunTests.setDisable(true);
        btnRunSelected.setDisable(true);
        progressBar.setVisible(false);

        // Test level selector
        testLevelCombo.getItems().addAll("SMOKE", "BASIC", "FULL");
        testLevelCombo.getSelectionModel().select(1); // BASIC by default

        // Site type selector
        siteTypeCombo.getItems().addAll("E3Core (ExtJS)", "Обычный HTML-сайт", "Свой (Custom)");
        siteTypeCombo.getSelectionModel().selectFirst();

        // Default values
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

            // Fill entity list
            ObservableList<String> entityNames = FXCollections.observableArrayList();
            for (EntityObject entity : currentModel.getEntities()) {
                String info = entity.getName();
                if (entity.hasCrudOperations()) info += " [CRUD]";
                int propCount = (int) entity.getPropertyGroups().stream()
                    .flatMap(pg -> pg.getProperties().stream())
                    .filter(p -> p.isFlagDisplay())
                    .count();
                info += " (" + propCount + " полей)";
                entityNames.add(info);
            }
            entityListView.setItems(entityNames);

            // Take subsystem name from XML CategoryName ("Logical View::<name>") when present.
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
            // Site type
            int siteIdx = siteTypeCombo.getSelectionModel().getSelectedIndex();
            config.setSiteType(siteIdx == 0 ? "e3core" : siteIdx == 1 ? "generic" : "custom");
            config.setSubsystemName(subsystemField.getText());
            // Test level from ComboBox
            String selectedLevel = testLevelCombo.getSelectionModel().getSelectedItem();
            config.setTestLevel(selectedLevel != null ? selectedLevel.toLowerCase() : "basic");
            // Smoke-all-subsystems toggle (defaults to checkbox value or true if checkbox not bound)
            config.setSmokeAllSubsystems(smokeAllSubsystemsCheck == null || smokeAllSubsystemsCheck.isSelected());

            TestGenerator generator = new TestGenerator(config);
            generator.generate(currentModel);

            log("Уровень тестов: " + config.getTestLevel().toUpperCase());
            log("Сгенерировано тестовых классов: " + currentModel.getEntities().size());
            log("Тесты сгенерированы в: " + outputPath);
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
     * Opens a dialog to pick entities and test types, then runs only the selected subset.
     * Empty test-type selection = run all tests of the chosen entities.
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

        // Entity checkboxes
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

        // Test-type checkboxes
        List<CheckBox> typeChecks = new ArrayList<>();
        VBox typeBox = new VBox(4);
        for (String[] cat : TEST_CATEGORIES) {
            CheckBox cb = new CheckBox(cat[0]);
            cb.setUserData(cat[1]);
            typeChecks.add(cb);
            typeBox.getChildren().add(cb);
        }

        // Live preview of the resulting -Dtest filter.
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
     * Runs the generated tests. {@code testFilter} null/blank = run everything;
     * otherwise it is passed to Surefire as {@code -Dtest=<filter>}.
     */
    private void launchRun(String testFilter) {
        String outputPath = outputDirField.getText();
        if (outputPath == null || outputPath.isEmpty()) {
            showAlert("Ошибка", "Укажите каталог с тестами.");
            return;
        }

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
                return runner.run(Path.of(outputPath), getXmlFileName(), urlField.getText(),
                        null, testFilter);
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
            log("HTML-отчёт (v5, с фотолетописью): " + outputPath + "/target/run-report.html");
            log("HTML-отчёт (стандартный surefire): " + outputPath + "/target/site/surefire-report.html");
            log("CSV-отчёт: " + outputPath + "/target/run-report.csv");
            log("Скриншоты: " + outputPath + "/target/screenshots/");
            if (result.getMavenOutput() != null && !result.getMavenOutput().isEmpty()) {
                log("=== Вывод Maven ===");
                // Show last 100 lines
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

        // Also show the most recent run details in the table
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
     * Row model for the results TableView.
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
