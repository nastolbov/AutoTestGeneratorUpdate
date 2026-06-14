# Правки ВКР «Разработка ИС генерации автотестов для web-приложения на основе XML-файла»

Документ сверки **текста** ВКР с фактической реализацией. Для каждого пункта: **где** в ВКР,
**что написано**, **как на самом деле (по коду)**, **как исправить**. Особое внимание тексту
разделов **ТЗ (1.7)** и **БД (1.4.5 / 1.5.2.5)**, а не только схемам. Все факты сверены с
`src/main/java/ru/autotestgen/**`, DDL `SchemaInitializer` и текстом ВКР.

## A. Критические фактические расхождения (исправить обязательно)

| # | Где в ВКР | Что написано | Как на самом деле (код) | Правка |
|---|---|---|---|---|
| A1 | Табл. 36 (1.5.1), строка DataLayer/`data` | «DAO-класс **RunReportWriter** для SQLite, DTO TestRunResult и TestCaseResult» | В `data` 5 классов: `DatabaseConnection`, `SchemaInitializer`, `ReportDao` (фасад), `TestRunDao`, `TestCaseDao`. `RunReportWriter` — в пакете **generator** (пишет **HTML-отчёт + CSV**, не SQLite). DTO `TestRunResult`/`TestCaseResult` — в пакете **model** | «ReportDao (фасад `saveRun`/`getAllRuns`) + DatabaseConnection + SchemaInitializer + TestRunDao + TestCaseDao — доступ к SQLite через JDBC». Убрать отсюда RunReportWriter и DTO |
| A2 | Табл. 36, строка BusinessLayer/`generator` | «…писатели Page Object и тест-классов, TestRunner, **ReportService**» | Класса `ReportService` нет. В `generator`: `TestGenerator`, `TestClassWriter`, `PageObjectWriter`, `TestRunner`, `TestConfig`, **`TestDataFactory`**, **`RunReportWriter`** | «ReportService» → «RunReportWriter (HTML/CSV-отчёт)»; добавить `TestDataFactory` (значения по типу/маске), `TestConfig` |
| A3 | Табл. 36, строка `model` | «Доменная модель: **14** классов» | В `model` **18** классов, включая **`EntityClassifier`**, `EntityKind`, DTO `TestRunResult`/`TestCaseResult` | Исправить число на 18; отметить, что `EntityClassifier` — в `model` |
| A4 | Табл. 36 / 1.5.3, `parser` | XmlModelParser/EntityParser/SearchParser/PropertyGroupParser | В `parser` ещё `StaxUtils` (утилиты потокового чтения) и `XmlNamespaces` (константы 3 неймспейсов) | Добавить `StaxUtils`, `XmlNamespaces` |
| A5 | 1.3, абзац про именование | методы «**toTestClassName, toPageClassName, toSetterName**» | В `common.Transliterator`: **`toClassName`**, **`toMethodName`**, **`toFieldName`** | Исправить имена методов на фактические |
| A6 | ТЗ 1.7.2.1; 1.3 | «Page Object и тест… **для каждой PRIMARY-сущности**» | Тесты генерируются и для **CHILD** (grid-вкладки — inline-CRUD), и для **tree-node** детей; REFERENCE_DICTIONARY пропускаются | «…для каждой PRIMARY; для CHILD — тест в карточке родителя (inline-грид); для tree-node — из узла дерева; справочники-пикеры пропускаются» |
| A7 | 1.3 / ТЗ — правила классификации | «родительский Grid → V_S_ → FK-target → PRIMARY» | `EntityClassifier`: grid-вкладка → **tree-node (`addFromTree=1`)** → `V_S_` → **pick-one-поиск** (params пусты, результат=SearchKey+SearchName) → **FK-target-only** (`flag_display=0 & addFromTree=0`) → иначе PRIMARY; плюс **inline-table** (нет FormView) и подавление модального двойника | Привести полный список правил; подчеркнуть: решение по СТРУКТУРЕ метамодели (разд. D) |

## B. ТЗ (раздел 1.7) — правки ТЕКСТА
Подтверждено кодом (оставить): StAX + 3 неймспейса (`NS_E/NS_E3/NS_MD`), транслитерация,
3 уровня (SMOKE=`testFieldsPresent`; BASIC +`testSearch*`/`testGrid*`; FULL +CRUD), `mvn test`,
разбор Surefire-XML через StAX (`TestRunner` использует `XMLStreamReader`), SQLite-история,
HTML+CSV, graceful degradation, транзакция при сохранении, try-with-resources.

Добавить/исправить:
- **B1.** Генерация не только для PRIMARY — добавить CHILD (inline-CRUD в grid-вкладке родителя) и tree-node (из узла дерева). См. A6.
- **B2.** Явно указать **два сценария CRUD по типу группы свойств**: модальная карточка (Type 1: меню→Добавить→карточка→Готово) и inline-грид (Type 2: Редактирование→Добавить→строка→заполнение по маске→Сохранить изменения→Обновить). Сейчас не отражено.
- **B3.** Требование **достоверности тестов** (методологический результат): верификация по уникальному маркеру + счётчику записей; **seed-then-delete/-archive** (тест сам создаёт свою запись и удаляет именно её); **честный FAIL** при серверной ошибке вместо ложного PASS; отсутствие skip-заглушек.
- **B4.** Требование **заполнения по типам/маскам** (даты `99.99.9999`), автопропуск служебных полей «Оператор»/«Дата изменения», учёт обязательных по «\*».
- **B5.** В 1.7.2.2 (надёжность): мутирующие тесты прогоняются **последовательно** (forkCount=1) — параллельные сессии одного логина конфликтуют.
- **B6.** В «ограничения/допущения» (или 1.7.4): зависимость от стенда; примеры реальных серверных дефектов (напр. хранимка с `trunc(date)`), на которые тест даёт честный RED.
- **B7.** Терминологию привести к платформе E3Core (разд. D): «группа свойств» (Карточка/Грид), «дерево объектов», «дерево поисков», «Обновить», «общее число записей».

## C. БД (1.4.5, 1.5.2.5, табл. 36) — правки ТЕКСТА
**Верно (оставить):** спецификации **табл. 34 (`test_run`)** и **табл. 35 (`test_case`)** полностью
совпадают с DDL `SchemaInitializer` (поля, NOT NULL, DEFAULT, PK AUTOINCREMENT, FK
`test_case.run_id → test_run.id`, связь 1:N). Раздел классов **1.5.2.5 «Data»** перечисляет верно:
ReportDao(фасад)+DatabaseConnection+SchemaInitializer+TestRunDao+TestCaseDao.

Исправить/уточнить:
- **C1.** Устранить **внутреннее противоречие**: обзорная табл. 36 (A1) против корректного 1.5.2.5 — привести табл. 36 к фактическому составу слоя `data`.
- **C2.** **Физическая модель (рис. 11) и типы.** В SQLite нет типов «Number/String» — это affinity `INTEGER`/`TEXT`. В логической модели (табл. 34/35) можно оставить Number/String, в **физической** указать `INTEGER`/`TEXT` (как в реальном DDL). Проверить, что рис. 10/11 содержат столбцы `id, run_date, xml_file, base_url, total, passed, failed, skipped, duration_ms` и `id, run_id, class_name, method_name, passed, failure_msg, duration_ms` с FK `run_id`.
- **C3.** Уточнить: `passed` хранится как `INTEGER` 0/1 (булев флаг); `failure_msg` — nullable `TEXT`.
- **C4.** **Версия SQLite.** «SQLite 3.42» — сверить с зависимостью `pom.xml` (xerial `sqlite-jdbc`): указать версию драйвера/встроенную версию SQLite либо формулировать «встраиваемая SQLite через драйвер xerial sqlite-jdbc».
- **C5.** **Транзакция и FK (СВЕРЕНО).** Тезис «запись в одной транзакции с откатом» ПОДТВЕРЖДЁН: `ReportDao.saveRun` → `conn.setAutoCommit(false)` → `testRunDao.insert` → `testCaseDao.insertBatch` → `conn.commit()` (откат в catch). Нюанс: FK `test_case.run_id` объявлен декларативно, но **`PRAGMA foreign_keys=ON` в коде нет** → SQLite его принудительно не контролирует. В тексте: «FK задан декларативно; целостность обеспечивается логикой DAO», не утверждать про контроль FK на уровне СУБД.
- **C6.** **Путь к БД и автосоздание (СВЕРЕНО).** Файл — `autotestgen.db` в рабочем каталоге (`DatabaseConnection.DEFAULT_URL = "jdbc:sqlite:autotestgen.db"`), соединение через `DriverManager`. Схема создаётся `SchemaInitializer.initialize()` (CREATE TABLE IF NOT EXISTS) при старте — подтверждает «создаётся автоматически при первом запуске».

## D. Раздел 1.3 — содержательные дополнения (методы и алгоритмы)
- **D1.** Алгоритм **выбора сценария CRUD по типу группы свойств** (Карточка→модальный, Грид→inline); выбор по структуре метамодели (FormView/тип PropertyGroup), а не по именам.
- **D2.** Полный **алгоритм классификации** (структурный, шаблонный) — все правила из A7; вывод о переносимости на любые E3Core-модели.
- **D3.** **Навигация к дочерним**: родитель→карточка→вкладка(grid)/узел дерева; дочерние гриды редактируются inline.
- **D4.** **Достоверная верификация**: маркер+счётчик после «Обновить»; seed-then-delete; честный RED; почему row-count ненадёжен.
- **D5.** **Заполнение по маске и служебные поля** (см. B4).
- **D6.** **Поиск**: дерево поисков→параметры→Выполнить; режим «через поисковую систему / через модуль»; фильтрованный поиск по маркеру для верификации.
- **D7.** **Источники методологии**: руководство оператора E3Core (типы групп свойств, «Обновить», общее число записей) + инструкция пользователя АИС ГСК — обоснование алгоритмов предметной областью.

## E. Пакеты (1.5.1)
- **E1.** Добавить **диаграмму зависимостей пакетов** и тезис об ацикличности/слоистости: `ui → generator → {parser, model, data, common}`, `parser → model`, `generator → model, common, data`, все → `common`; обратных зависимостей нет.
- **E2.** Исправить табл. 36 (A1–A4).
- **E3.** Явно: `generator` порождает не только тест-классы, но и рантайм-инфраструктуру (BaseTest, SharedDriver, Page Object, `pom.xml`) — мостик к разд. F.

## F. Модульная структура (1.5.3)
- **F1.** Сверить «18 модулей» с фактическими классами; добавить в перечень/табл. 106: `StaxUtils`, `XmlNamespaces`, `TestDataFactory`, `RunReportWriter`, `DatabaseConnection`, `SchemaInitializer`, `TestRunDao`, `TestCaseDao`, `JavaFileWriter`, `Transliterator`.
- **F2.** Добавить **двухуровневую модульность**: система генерирует отдельный модульный артефакт — Maven-проект автотестов. Привести **диаграмму компонентов сгенерированного проекта** и таблицу его модулей: `pom.xml`, `BaseTest`, `SharedDriver`, `<Сущность>Page`, `<Сущность>Test`, `SubsystemsSmokeTest`, `TestData`.

## G. Схемы (добавить/исправить)
- Диаграмма зависимостей пакетов (E1).
- Блок-схемы 1.3: «классификация сущности», «выбор и выполнение CRUD-флоу», «достоверная верификация» (стиль уже сделанных PNG в `docs/xml-diagrams/`).
- Диаграмма компонентов сгенерированного проекта (F2).
- Сверить ER рис. 10/11 с фактической схемой (C2).

## Приложение. Фактический инвентарь (для сверки таблиц ВКР)
**Пакеты/классы (по коду):**
- `ui` (2): App, MainController.
- `parser` (6): XmlModelParser, EntityParser, PropertyGroupParser, SearchParser, StaxUtils, XmlNamespaces.
- `generator` (7): TestGenerator, TestClassWriter, PageObjectWriter, TestRunner, TestConfig, TestDataFactory, RunReportWriter.
- `model` (18): AppModel, EntityObject, PropertyGroup, Property, Operation, Modifier, ModifyType, OperationParam, Association, AttrType, Search, SearchParam, SearchResult, SearchResultProperty, EntityClassifier, EntityKind, TestRunResult, TestCaseResult.
- `data` (5): DatabaseConnection, SchemaInitializer, ReportDao, TestRunDao, TestCaseDao.
- `common` (3): JavaFileWriter, Transliterator, ParserException.

**Фактическая схема БД (DDL из `SchemaInitializer`):**
```
test_run(id INTEGER PK AUTOINCREMENT, run_date TEXT NN, xml_file TEXT NN, base_url TEXT NN,
         total INT NN d0, passed INT NN d0, failed INT NN d0, skipped INT NN d0, duration_ms INT NN d0)
test_case(id INTEGER PK AUTOINCREMENT, run_id INT NN REFERENCES test_run(id),
          class_name TEXT NN, method_name TEXT NN, passed INT NN d1, failure_msg TEXT, duration_ms INT NN d0)
```
БД-файл: `jdbc:sqlite:autotestgen.db` (рабочий каталог). Транзакция: `setAutoCommit(false)…commit()`.
Транслитерация: `Transliterator.toClassName / toMethodName / toFieldName`.
Неймспейсы (`XmlNamespaces`): `NS_E`, `NS_E3`, `NS_MD`.
