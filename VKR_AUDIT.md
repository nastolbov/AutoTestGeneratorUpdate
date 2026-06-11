# Аудит ВКР: выравнивание, литература, текст программы

Файл проанализирован: `Столбов_Диплом_v4.docx` (5052 параграфа, 1892 вне таблиц).

---

## 1. Выравнивание — что не «по ширине»

### 1.1. Багов с явным `left`/`right` в основном тексте — нет
Проверка вне таблиц: ни один длинный абзац основного текста не имеет явного `left` или `right` (кроме двух пунктов титульника «Руководитель: …» / «Исполнитель: …», что корректно).

### 1.2. Реальные баги — параграфы с **унаследованным** выравниванием (фактически `left`)

Это 157 абзацев со стилем `Normal`, у которых выравнивание не задано явно, и по `docDefaults` они получают левое. Из них действительно нужно «по ширине» выправить вот эти (всё, что не заголовок раздела и не код):

| Параграф | Текст |
|---|---|
| 561 | «На рис. 32 изображена диаграмма кооперации пакета "Model".» |
| 583 | «На рис. 33 изображена уточнённая диаграмма классов пакета "Model".» |
| (аналогичные «На рис. 39/40/46/47/53/54 изображена…») | 8 шт. подряд по тому же шаблону |
| параграф перед «1.7 Выбор стратегии…» | «В данном разделе представлены стратегия, программа и методика испытаний…» |

**Как починить руками в Word:** выделить — `Ctrl+J` (justify). Либо открыть стиль `Обычный` (Normal) → выравнивание «По ширине» — тогда все унаследованные сразу станут правильно.

### 1.3. Где `left/inherit` оставить как есть
- Титульник, форма задания, поля «Срок сдачи студентом…», «Дата выдачи задания», «Руководитель ВКР:» — это бланки, не основной текст.
- Заголовки разделов (`1.5.3`, `1.7`, `3.1`…) — заголовки.
- Листинги кода в Приложении 2 (`package Parsing;`, `public class …`) — код **не должен** выравниваться по ширине, иначе появятся уродские пробелы между токенами.
- Подписи рисунков (`Рис. NN. …`) — центр, это правильно.
- Заголовки таблиц (`Таблица NN`) — справа, это правильно.

---

## 2. Литература — ошибки расстановки `[N]`

Всего 26 ссылок. Найдено 3 явных бага.

### 2.1. Ссылка в Техническом задании (Приложение 1)
**Параграф 4400** (внутри `ПРИЛОЖЕНИЕ 1 — Техническое задание`):
> «…разбор XML-метамодели формата E3Core с использованием потокового парсера StAX **[20]** и поддержкой трёх XML-неймспейсов…»

**Действие:** удалить `[20]`. В ТЗ ссылок на литературу быть не должно.

### 2.2. Ссылка внутри самого списка литературы
**Параграф 4313**, запись №2 в списке литературы:
> «Буч Г., Рамбо Д., Якобсон И. Язык UML. Руководство пользователя. 2-е изд.: Пер. с англ. — М.: ДМК Пресс, 2006. — 496 с. **[27]**»

**Действие:** удалить `[27]` — это запись №2 в списке, не должна сама себя цитировать. Похоже на случайно оставленный маркер сноски.

### 2.3. Возможно избыточное цитирование `[7]`
Ссылка `[7]` стоит **трижды подряд** в близких абзацах раздела 1.4 / 1.5 (параграфы 784, 1441, 1477). Если это всё один и тот же источник по UML, достаточно одной ссылки при первом упоминании в разделе. Перепроверь — может быть, два из трёх лишние.

### 2.4. Что в порядке
- Введение (параграфы 436–438) — `[10]`, `[2]` — допустимо: это первое упоминание аналогов и постановка задачи.
- Заключение (параграфы 4293–4310) — **ссылок нет**. Правильно.
- Заголовки разделов — **ссылок нет**. Правильно.
- В разделах 1.1, 1.2, 1.3, 1.4, 1.5, 1.7 ссылки стоят рядом с заимствованными определениями/тезисами (Selenium, JavaFX, Maven, StAX, ГОСТ 19.301-79, метод граничных значений) — это корректное использование.

---

## 3. Раздел «Текст программы» — готовые листинги для вставки

Существующее `ПРИЛОЖЕНИЕ 2 — Текст программы` содержит **устаревшие** классы из старой архитектуры (`ParseException`, `MetadataParser`, `MetadataModelDTO`, `EntityDTO`, `PropertyDTO`, `OperationParamDTO`, `OperationModifiersDTO` из пакета `Parsing`). Эти классы в текущей кодовой базе ветки `claude/gallant-hopper-gvi512` **отсутствуют**. Их нужно заменить на актуальные.

Ниже — четыре основных класса из текущей реализации, ~10 страниц при моноширинном 10 pt. Формат подписей — как у тебя в существующем Приложении 2 (`Рис. П2.N. Текст класса …`).

**Структура вставки в .docx:**
1. Перед каждым листингом — отдельный абзац (выравнивание по ширине):
   «На рис. П2.N представлен текст класса `ИмяКласса.java`.»
2. Сам код — каждая строка в отдельном абзаце, моноширинный шрифт (Consolas 10 pt), выравнивание **по левому краю** (НЕ по ширине!), без отступа первой строки, межстрочный интервал одинарный.
3. После листинга — центрированная подпись: «Рис. П2.N. Текст класса `ИмяКласса.java`».
4. Длинные строки переноси вручную с отступом продолжения 4 пробела — как в твоём существующем Приложении 2.

---

### Рис. П2.X. Текст класса `XmlModelParser.java`

Фасад потокового разбора XML-метамодели E3Core. Распознаёт корневые элементы и делегирует разбор специализированным парсерам `EntityParser` и `SearchParser`.

```java
package ru.autotestgen.parser;

import ru.autotestgen.common.ParserException;
import ru.autotestgen.model.AppModel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static ru.autotestgen.parser.StaxUtils.attr;
import static ru.autotestgen.parser.XmlNamespaces.NS_E;
import static ru.autotestgen.parser.XmlNamespaces.NS_E3;

public class XmlModelParser {

    private final EntityParser entityParser;
    private final SearchParser searchParser;

    public XmlModelParser() {
        this(new EntityParser(), new SearchParser());
    }

    public XmlModelParser(EntityParser entityParser, SearchParser searchParser) {
        this.entityParser = entityParser;
        this.searchParser = searchParser;
    }

    public AppModel parse(File xmlFile) throws ParserException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

        try (FileInputStream fis = new FileInputStream(xmlFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(fis, "UTF-8");
            AppModel model = new AppModel();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String ns = reader.getNamespaceURI();
                    String local = reader.getLocalName();

                    if ("Category".equals(local) && NS_E3.equals(ns)) {
                        model.setCategoryName(attr(reader, "CategoryName"));
                        model.setGuid(attr(reader, "GUID"));
                    } else if ("Object".equals(local) && NS_E.equals(ns)) {
                        model.getEntities().add(entityParser.parseObject(reader));
                    } else if ("Searches".equals(local) && NS_E3.equals(ns)) {
                        model.setSearches(searchParser.parseSearches(reader));
                    }
                }
            }
            reader.close();
            return model;
        } catch (IOException | XMLStreamException e) {
            throw new ParserException(
                "Failed to parse XML model: " + e.getMessage(), e);
        }
    }
}
```

---

### Рис. П2.X. Текст класса `EntityClassifier.java`

Классифицирует сущности XML-модели на категории PRIMARY, CHILD и REFERENCE_DICTIONARY. Используется для решения, генерировать ли для сущности отдельный тестовый класс.

```java
package ru.autotestgen.model;

import java.util.List;
import java.util.Locale;

public final class EntityClassifier {

    private EntityClassifier() {}

    public static final class Classification {
        public final EntityKind kind;
        public final String reason;
        public final EntityObject parentEntity;
        public final PropertyGroup parentGrid;

        public Classification(EntityKind kind, String reason) {
            this(kind, reason, null, null);
        }

        public Classification(EntityKind kind, String reason,
                              EntityObject parentEntity,
                              PropertyGroup parentGrid) {
            this.kind = kind;
            this.reason = reason;
            this.parentEntity = parentEntity;
            this.parentGrid = parentGrid;
        }
    }

    public static Classification classify(EntityObject entity, AppModel model) {
        Classification childResult = findParentGrid(entity, model);
        if (childResult != null) {
            return childResult;
        }
        String dictReason = isReferenceDictionary(entity, model);
        if (dictReason != null) {
            return new Classification(
                EntityKind.REFERENCE_DICTIONARY, dictReason);
        }
        String fkOnlyReason = isFkTargetOnly(entity, model);
        if (fkOnlyReason != null) {
            return new Classification(
                EntityKind.REFERENCE_DICTIONARY, fkOnlyReason);
        }
        return new Classification(EntityKind.PRIMARY,
            "has menu entry, CRUD and own searches");
    }

    private static Classification findParentGrid(
            EntityObject entity, AppModel model) {
        if (entity.hasCrudOperations()) return null;
        for (EntityObject other : model.getEntities()) {
            if (other.getGuid() != null
                    && other.getGuid().equals(entity.getGuid())) continue;
            for (PropertyGroup pg : other.getPropertyGroups()) {
                if (!"Grid".equals(pg.getStereoType())) continue;
                String gridName = pg.getName();
                if (gridName == null || gridName.isEmpty()) continue;
                if (gridName.equalsIgnoreCase(other.getName())) continue;
                if (nameStemsMatch(entity.getName(), gridName)) {
                    String reason = "tab/grid '" + gridName
                        + "' inside '" + other.getName() + "'";
                    return new Classification(
                        EntityKind.CHILD, reason, other, pg);
                }
            }
        }
        return null;
    }

    private static String isFkTargetOnly(
            EntityObject entity, AppModel model) {
        String guid = entity.getGuid();
        if (guid == null) return null;
        int fkRefs = 0;
        int navRefs = 0;
        String referrer = null;
        for (EntityObject other : model.getEntities()) {
            if (other == entity) continue;
            if (other.getGuid() != null
                    && other.getGuid().equals(guid)) continue;
            if (isChildOf(other, entity)) continue;
            for (Association a : other.getAssociations()) {
                if (!guid.equals(a.getAssociateItemGuid())) continue;
                if (a.isAddFromTree() || a.isFlagDisplay()) {
                    navRefs++;
                } else {
                    fkRefs++;
                    if (referrer == null) referrer = other.getName();
                }
            }
        }
        if (fkRefs > 0 && navRefs == 0) {
            return "FK picker target — referenced as AssociateItem from '"
                + referrer + "'";
        }
        return null;
    }

    private static boolean isChildOf(
            EntityObject child, EntityObject parent) {
        for (PropertyGroup pg : parent.getPropertyGroups()) {
            if (!"Grid".equals(pg.getStereoType())) continue;
            String gridName = pg.getName();
            if (gridName == null || gridName.isEmpty()) continue;
            if (nameStemsMatch(child.getName(), gridName)) return true;
        }
        return false;
    }

    private static String isReferenceDictionary(
            EntityObject entity, AppModel model) {
        String feature = entity.getFeatureName();
        if (feature != null
                && feature.toUpperCase(Locale.ROOT).startsWith("V_S_")) {
            return "featureName starts with V_S_";
        }
        List<Search> searches = model.getSearches().stream()
            .filter(s -> s.getSearchObjectGuid() != null
                && s.getSearchObjectGuid().equals(entity.getGuid()))
            .toList();
        if (searches.isEmpty()) {
            return null;
        }
        boolean allEmptyParams = searches.stream()
            .allMatch(s -> s.getParams().isEmpty());
        boolean allTrivialResult = searches.stream()
            .allMatch(EntityClassifier::isTrivialResult);
        if (allEmptyParams && allTrivialResult) {
            return "all searches are pick-one";
        }
        return null;
    }

    private static boolean isTrivialResult(Search s) {
        if (s.getResult() == null) return true;
        List<SearchResultProperty> props = s.getResult().getProperties();
        if (props == null || props.isEmpty()) return true;
        if (props.size() > 2) return false;
        for (SearchResultProperty p : props) {
            String n = p.getName();
            if (n == null) continue;
            if (!"SearchKey".equalsIgnoreCase(n)
                    && !"SearchName".equalsIgnoreCase(n)) return false;
        }
        return true;
    }

    private static boolean nameStemsMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String[] aw = a.split("[\\s/]+");
        String[] bw = b.split("[\\s/]+");
        if (aw.length != bw.length) return false;
        for (int i = 0; i < aw.length; i++) {
            String wa = aw[i].toLowerCase(Locale.ROOT);
            String wb = bw[i].toLowerCase(Locale.ROOT);
            if (wa.equals(wb)) continue;
            int min = Math.min(wa.length(), wb.length());
            int prefix = Math.min(min - 1, Math.max(3, min - 2));
            if (prefix <= 0) return false;
            if (!wa.substring(0, prefix)
                    .equals(wb.substring(0, prefix))) return false;
        }
        return true;
    }
}
```

---

### Рис. П2.X. Текст класса `TestRunner.java`

Запускает сгенерированный Maven-проект через системный вызов `mvn test`, читает результаты из XML-отчётов Surefire потоковым StAX-парсером, привязывает скриншоты и формирует HTML/CSV-отчёт.

```java
package ru.autotestgen.generator;

import ru.autotestgen.model.TestCaseResult;
import ru.autotestgen.model.TestRunResult;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TestRunner {

    private String lastMavenOutput = "";

    public TestRunResult run(Path projectDir, String xmlFileName,
                             String baseUrl, Consumer<String> lineConsumer,
                             String testFilter, boolean fastMode)
            throws IOException {
        long startTime = System.currentTimeMillis();

        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            throw new IOException(
                "pom.xml не найден в " + projectDir);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("test");
        if (testFilter != null && !testFilter.isBlank()) {
            cmd.add("-Dtest=" + testFilter);
            cmd.add("-DfailIfNoTests=false");
        }
        if (fastMode) {
            cmd.add("-DforkCount=3");
            cmd.add("-Dheadless=true");
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(projectDir.toFile());

        Process process = pb.start();
        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                outputBuilder.append(line).append("\n");
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
        }
        lastMavenOutput = outputBuilder.toString();

        try {
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        long durationMs = System.currentTimeMillis() - startTime;

        TestRunResult result = new TestRunResult();
        result.setRunTimestamp(LocalDateTime.now());
        result.setDurationMs(durationMs);
        result.setXmlFileName(xmlFileName);
        result.setBaseUrl(baseUrl);
        result.setMavenOutput(lastMavenOutput);

        Path reportsDir = projectDir.resolve("target/surefire-reports");
        if (Files.exists(reportsDir)) {
            parseSurefireReports(reportsDir, result);
        } else {
            result.setTotalTests(0);
            result.setPassed(0);
            result.setFailed(0);
            result.setSkipped(0);
        }
        return result;
    }

    private void parseSurefireReports(Path reportsDir,
                                      TestRunResult runResult)
            throws IOException {
        List<TestCaseResult> allResults = new ArrayList<>();

        try (var stream = Files.list(reportsDir)) {
            List<Path> xmlFiles = stream
                .filter(p -> p.toString().endsWith(".xml"))
                .toList();

            XMLInputFactory factory = XMLInputFactory.newInstance();

            for (Path xmlFile : xmlFiles) {
                try (InputStream is = Files.newInputStream(xmlFile)) {
                    XMLStreamReader reader =
                        factory.createXMLStreamReader(is);
                    parseOneReport(reader, allResults);
                    reader.close();
                } catch (Exception e) {
                    System.err.println("Error parsing report "
                        + xmlFile + ": " + e.getMessage());
                }
            }
        }

        int passed = 0, failed = 0, skipped = 0;
        for (TestCaseResult tcr : allResults) {
            if (tcr.isSkipped()) skipped++;
            else if (tcr.isPassed()) passed++;
            else failed++;
        }
        runResult.setTotalTests(allResults.size());
        runResult.setPassed(passed);
        runResult.setFailed(failed);
        runResult.setSkipped(skipped);
        runResult.setResults(allResults);
    }

    private void parseOneReport(XMLStreamReader reader,
                                List<TestCaseResult> allResults)
            throws Exception {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            if (!"testcase".equals(reader.getLocalName())) continue;

            TestCaseResult tcr = new TestCaseResult();
            tcr.setClassName(
                reader.getAttributeValue(null, "classname"));
            tcr.setMethodName(
                reader.getAttributeValue(null, "name"));
            tcr.setDurationMs((long)
                (parseDoubleAttr(reader, "time") * 1000));
            tcr.setPassed(true);

            while (reader.hasNext()) {
                event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT
                        && "testcase".equals(reader.getLocalName())) {
                    break;
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String child = reader.getLocalName();
                    if ("failure".equals(child)
                            || "error".equals(child)) {
                        tcr.setPassed(false);
                        tcr.setFailureMessage(
                            reader.getAttributeValue(null, "message"));
                    } else if ("skipped".equals(child)) {
                        tcr.setPassed(false);
                        tcr.setSkipped(true);
                    }
                }
            }
            allResults.add(tcr);
        }
    }

    private double parseDoubleAttr(XMLStreamReader reader, String name) {
        String val = reader.getAttributeValue(null, name);
        if (val == null || val.isEmpty()) return 0;
        try { return Double.parseDouble(val); }
        catch (NumberFormatException e) { return 0; }
    }
}
```

---

### Рис. П2.X. Текст класса `ReportDao.java`

Фасад над встроенной БД SQLite. Открывает транзакцию для записи прогона и связанных тест-кейсов, инициализирует схему при первом запуске, отдаёт историю прогонов.

```java
package ru.autotestgen.data;

import ru.autotestgen.model.TestRunResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportDao {

    private final DatabaseConnection connection;
    private final TestRunDao testRunDao;
    private final TestCaseDao testCaseDao;

    public ReportDao() {
        this(new DatabaseConnection());
    }

    public ReportDao(DatabaseConnection connection) {
        this(connection, new TestRunDao(connection), new TestCaseDao());
    }

    public ReportDao(DatabaseConnection connection,
                     TestRunDao testRunDao,
                     TestCaseDao testCaseDao) {
        this.connection = connection;
        this.testRunDao = testRunDao;
        this.testCaseDao = testCaseDao;
        new SchemaInitializer(connection).initialize();
    }

    public void saveRun(TestRunResult result) {
        try (Connection conn = connection.open()) {
            conn.setAutoCommit(false);
            long runId = testRunDao.insert(conn, result);
            testCaseDao.insertBatch(conn, runId, result.getResults());
            conn.commit();
        } catch (SQLException e) {
            System.err.println(
                "Failed to save test run: " + e.getMessage());
        }
    }

    public List<TestRunResult> getAllRuns() {
        List<TestRunResult> runs = new ArrayList<>();
        try (Connection conn = connection.open()) {
            for (TestRunDao.RunRow row : testRunDao.selectAll(conn)) {
                row.run.setResults(
                    testCaseDao.selectByRunId(conn, row.runId));
                runs.add(row.run);
            }
        } catch (SQLException e) {
            System.err.println(
                "Failed to load runs: " + e.getMessage());
        }
        return runs;
    }
}
```

---

## Чек-лист правок

- [ ] Открыть стиль `Обычный` → выравнивание «По ширине» (правит сразу 157 параграфов).
- [ ] Удалить `[20]` в параграфе 4400 (Приложение 1, ТЗ).
- [ ] Удалить `[27]` в параграфе 4313 (запись №2 списка литературы).
- [ ] Перепроверить три ссылки `[7]` подряд в 1.4/1.5 — оставить только первую.
- [ ] Заменить устаревшие листинги (Приложение 2, рис. П2.1–П2.7) на четыре актуальных класса выше: `XmlModelParser`, `EntityClassifier`, `TestRunner`, `ReportDao`.
- [ ] Для каждого нового листинга: моноширинный шрифт (Consolas 10 pt), межстрочный 1.0, выравнивание по левому краю, перед — текст «На рис. П2.N представлен текст класса …», после — центрированная подпись «Рис. П2.N. Текст класса …».
