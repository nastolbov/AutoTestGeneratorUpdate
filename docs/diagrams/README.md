# Диаграммы пакетов — обновление под реальный код (01.06.2026)

Базовая ветка: `claude/wonderful-dijkstra-Kdp5i`.

Сверка проведена по тексту курсовой (`16dee7b3-______________.docx`,
извлечено в `/tmp/docx_text.txt`) и реальному коду в
`src/main/java/ru/autotestgen/`.

## Сводка по пакетам

| Пакет в курсовой | Пакет в коде | Вердикт                | Перерисован |
|------------------|--------------|------------------------|-------------|
| UI               | `ui/`        | minor drift            | да          |
| Parsing          | `parser/`    | matches doc            | —           |
| Model            | `model/`     | minor drift            | да          |
| Generator        | `generator/` | **major drift**        | да          |
| Data             | `data/`      | minor drift            | да          |
| Common           | `common/`    | matches doc            | —           |

## Файлы

PNG-рендеринг и PlantUML-источник:

- `package-overview.{puml,png}` — обновлённая Рис. 12 (диаграмма пакетов).
- `package-ui-detailed.{puml,png}` — детальная диаграмма классов «UI».
- `package-model-detailed.{puml,png}` — детальная диаграмма классов «Model».
- `package-generator-detailed.{puml,png}` — детальная диаграмма классов «Generator».
- `package-data-detailed.{puml,png}` — детальная диаграмма классов «Data».

Редактируемые drawio-схемы (отдельный файл на каждую диаграмму) —
в подпапке [`drawio/`](./drawio/):

- `drawio/01-package-overview.drawio`
- `drawio/02-ui-detailed.drawio`
- `drawio/03-parsing-detailed.drawio`
- `drawio/04-model-detailed.drawio`
- `drawio/05-generator-detailed.drawio`
- `drawio/06-data-detailed.drawio`
- `drawio/07-common-detailed.drawio`

Замечания по пакетам без drift'а:

- `package-parsing-NOT-CHANGED.md`, `package-common-NOT-CHANGED.md` —
  на эти два пакета диаграммы из курсовой остаются актуальными.
  drawio-файлы для них всё равно сделаны (для единообразия — `03-` и `07-`),
  PlantUML PNG не делался, так как изменений нет.

## Что именно изменилось

### UI
- Поле `reportService: ReportService` удалено; `MainController` обращается к
  `ReportDao` напрямую (`private final ReportDao reportDao = new ReportDao();`).
- `siteTypeCombo: ComboBox<String>` и `subsystemField: TextField` перестали
  быть `@FXML`-инжектируемыми — теперь это локальные `final new ...`.
- Добавлены поля `testLevelCombo`, `smokeAllSubsystemsCheck`, `fastModeCheck`,
  `btnRunSelected`, статический массив `TEST_CATEGORIES`.
- Добавлены методы `onRunSelected()`, `buildTestFilter(...)`, `launchRun(String)`,
  `getXmlFileName()`.

### Model
- Добавлены **`EntityClassifier`** (с вложенным `Classification`) и
  **enum `EntityKind { PRIMARY, CHILD, REFERENCE_DICTIONARY }`**.
- На `Property` добавлены `defValueSource`, `comment`.
- На `Association` добавлено `addFromTree`.
- `AppModel` получил `getSubsystemNameFromCategory()`.
- `TestRunResult` и `TestCaseResult` физически лежат под `model/`
  (в курсовой указаны в Data); на `TestCaseResult` добавлены `stdOut`,
  `searchParams`, `screenshots`, `steps` и вложенный `StepTiming`.

### Generator (major drift)
- **Удалено**: `ReportService` (всех 2 таблиц курсовой — 78 и 79).
- **Добавлено**: `RunReportWriter` (HTML и CSV отчёт о прогоне; вызывается из
  `TestRunner` после прохода Maven).
- В `TestClassWriter` добавлены writer'ы тестов:
  `writePartialValidationTest`, `writeCreateWithOnlyRequiredTest`,
  `writeLogicalEditTest`, `writeArchiveTest`, `writeSearchEmptyResultTest`.
- В `TestDataFactory` добавлены `DATE_TIME_FORMAT`, `MSK`, и хелперы
  `todayMsk()`, `nowMskMinus10()`, `looksLikeDateMask()`, `looksLikeDateTimeMask()`.
- В `TestRunner` добавлены 3 overload'а `run(...)` и хелперы `linkScreenshots`,
  `cleanSkipMessage`, `extractSearchParams`, `extractStepTimings`.
- Поле `smokeAllSubsystems: boolean` в `TestConfig`.
- Метода `generateTestData()` из курсовой в коде нет — тестовые данные
  генерируются inline через `PageObjectWriter`/`TestClassWriter`.

### Data
- В пакете `data/` остался только `ReportDao` (поля и методы — как в курсовой).
- `TestRunResult` и `TestCaseResult` физически в `model/`, но используются как DTO для DAO. На диаграмме показаны на стыке.

## Как пересобрать PNG

Из `docs/diagrams/`:

```bash
for f in *.puml; do
  java -jar plantuml.jar -tpng -charset UTF-8 "$f"
done
```

PlantUML 1.2024.7 + Graphviz `dot` достаточно.
