# AutoTestGenerator — архитектура vers2

База для проектирования новых диаграмм. Цель документа — зафиксировать **все классы, их роль и связи между ними** с учётом трёх видов связей UML:

| Обозначение | Тип связи           | Что значит на практике                                                                         |
| ----------- | ------------------- | ---------------------------------------------------------------------------------------------- |
| `A --> B`   | **Ассоциация / зависимость** | A использует B, но не владеет им (вызов метода, параметр, локальная переменная)        |
| `A ◇--> B`  | **Агрегация**       | A «имеет» B, но B живёт независимо: можно подменить, передать снаружи, переиспользовать         |
| `A ♦--> B`  | **Композиция**      | A «состоит из» B: B создаётся внутри A, умирает вместе с A, не существует без владельца         |

> Для UML-диаграмм:
> агрегация — пустой ромб со стороны владельца (◇),
> композиция — закрашенный ромб со стороны владельца (♦).

---

## 1. Общее назначение программы

**AutoTestGenerator** — информационная система автоматической генерации UI-автотестов веб-приложений (E3Core / ExtJS) из XML-модели предметной области.

Поток данных:

```
XML-метамодель ──► [Парсер] ──► AppModel ──► [Генератор] ──► Maven-проект автотестов
                                                                       │
                                                                       ▼
                                                              [Runner: mvn test]
                                                                       │
                                                                       ▼
                                                       TestRunResult ──► [GUI/CLI отображение]
                                                                       │
                                                                       ▼
                                                                  SQLite (история)
```

Запуск: **GUI-режим** — `App` (`mvn javafx:run`).

---

## 2. Структура пакетов

```
ru.autotestgen
├── ui          — JavaFX-контроллер и CLI-обвязка (точки входа)
├── parser      — Stax-парсер XML-метамодели
├── model       — POJO предметной области (anemic domain model)
├── common      — Утилиты: транслитерация, форматтер Java-кода, исключение
├── generator   — Генерация тест-проекта + запуск Maven Surefire + отчёт
└── data        — JDBC-DAO для SQLite (история прогонов)
```

Зависимости между пакетами (на уровне `import`):

```
ui      ──► generator, parser, model, common, data
generator ──► model, common
parser    ──► model, common
data      ──► model
common    ──► (нет внутренних зависимостей)
model     ──► (нет внутренних зависимостей)
```

> `model` и `common` — листовые пакеты, ни от кого не зависят. Это корректная многослойная архитектура: UI-слой выше всех, доменные POJO ниже всех.

---

## 3. Пакет `ru.autotestgen.ui` — GUI

### 3.1. Классы

| Класс             | Роль                                                                                       |
| ----------------- | ------------------------------------------------------------------------------------------ |
| `App`             | Точка входа (`main`). Запускает JavaFX (`launch`).                                           |
| `MainController`  | JavaFX-контроллер `main.fxml`. Связан с XML через `fx:controller="..."`.                    |
| `MainController.TestCaseRow` | Внутренний static-класс — модель строки `TableView` с результатами.              |

### 3.2. Связи внутри пакета `ui`

```
App                 -->  MainController       // косвенно: FXMLLoader инстанцирует через main.fxml
MainController      ♦--> TestCaseRow          // вложенный static-класс, модель строки таблицы
```

### 3.3. Связи `MainController` с другими пакетами

```
MainController      ◇--> AppModel             // поле currentModel, заполняется после parse
MainController      ◇--> ReportDao            // final-поле, переиспользуется на каждый запуск
MainController      -->  XmlModelParser       // создаётся локально в onParse()
MainController      -->  TestGenerator        // создаётся локально в onGenerate()
MainController      -->  TestConfig           // создаётся и настраивается в onGenerate()
MainController      -->  TestRunner           // создаётся в Task call() при запуске тестов
MainController      -->  TestRunResult        // получает из TestRunner, передаёт в displayResults
MainController      -->  TestCaseResult       // итерация по результатам
MainController      -->  EntityObject         // итерация по сущностям для UI-списка
MainController      -->  Transliterator       // static: имя класса теста из имени сущности
MainController      -->  ParserException      // ловит при парсинге

```

### 3.4. Особенности GUI-части

- **Декларативный layout**: вся разметка в `src/main/resources/fxml/main.fxml` (BorderPane, SplitPane, FlowPane).
- **FXML-связывание**: `@FXML`-аннотированные поля заполняются `FXMLLoader` по `fx:id`.
- **Асинхронность**: запуск тестов выполняется в фоне через `javafx.concurrent.Task` (отдельный поток), результат прокидывается обратно в JavaFX-thread через `setOnSucceeded` / `Platform.runLater`.
- **Прогресс / лог**: `ProgressBar` в режиме INDETERMINATE + `TextArea` для журнала, обновляются через `Platform.runLater`.
- **Раскраска статусов**: кастомный `TableCell` в `colStatus` подсвечивает OK / FAIL / SKIP.
- **Двухрежимный диалог запуска**: `onRunSelected` собирает чекбоксы сущностей × чекбоксы видов тестов и формирует `-Dtest=ClassA,ClassB#m1+m2` (Surefire-фильтр) с live-превью.
- **Уровни тестов**: SMOKE / BASIC / FULL — пробрасываются в `TestConfig` и далее в генератор.
- **Сохранение истории**: каждый прогон через `reportDao.saveRun(result)`.

---

## 4. Пакет `ru.autotestgen.model` — доменные POJO

### 4.1. Классы и их роль

| Класс / enum                  | Описание                                                                          |
| ----------------------------- | --------------------------------------------------------------------------------- |
| `AppModel`                    | Корень модели: список сущностей + список поисков + категория + GUID.              |
| `EntityObject`                | Бизнес-сущность (карточка, справочник).                                            |
| `PropertyGroup`               | Группа свойств (вкладка карточки или Grid внутри карточки).                       |
| `Property`                    | Одно поле (атрибут БД + UI-метаданные: тип, маска, обязательность, порядок).      |
| `Operation`                   | Серверная операция (Insert/Update/Delete...).                                     |
| `OperationParam`              | Параметр серверной операции.                                                      |
| `Modifier`                    | Кнопка-модификатор в UI (например, «Создать»).                                    |
| `ModifyType` *(enum)*         | I/U/D/E/A — тип модификации.                                                      |
| `Association`                 | Связь сущности с другой (FK-пикер или дочерняя коллекция).                        |
| `Search`                      | Параметрический поиск (Surefire-аналог: запрос с параметрами).                   |
| `SearchParam`                 | Параметр поиска (имя, тип, обязательность, маска).                                |
| `SearchResult`                | Описание грида-результата поиска.                                                 |
| `SearchResultProperty`        | Колонка в гриде результата.                                                       |
| `AttrType` *(enum)*           | STRING / DECIMAL / DATE / DATETIME.                                               |
| `EntityKind` *(enum)*         | PRIMARY / CHILD / REFERENCE_DICTIONARY — логическая роль сущности.                 |
| `EntityClassifier`            | Static-классификатор: какой `EntityKind` у каждой сущности (используется генератором, чтобы решить, нужно ли вообще генерировать класс тестов). |
| `EntityClassifier.Classification` | Результат классификации: `kind`, `reason`, опционально parent + parentGrid.   |
| `TestRunResult`               | Результат запуска тестов: счётчики + список `TestCaseResult` + maven-output.       |
| `TestCaseResult`              | Один тест-метод: статус, время, скриншоты, шаги, search-параметры.                |
| `TestCaseResult.StepTiming`   | Имя шага + длительность.                                                          |

### 4.2. Связи внутри пакета `model`

```
AppModel            ♦--> EntityObject         // entities — список владеется AppModel
AppModel            ♦--> Search               // searches — список владеется AppModel

EntityObject        ♦--> Association          // associations
EntityObject        ♦--> PropertyGroup        // propertyGroups

PropertyGroup       ♦--> Property             // properties
PropertyGroup       ♦--> Operation            // operation (одиночное поле, инициализируется в parser-е)

Operation           ♦--> OperationParam       // params
Operation           ♦--> Modifier             // modifiers

Modifier            -->  ModifyType           // enum, ассоциация по значению
Property            -->  AttrType             // enum

Search              ♦--> SearchParam          // params
Search              ♦--> SearchResult         // result (одиночное поле)
SearchResult        ♦--> SearchResultProperty // properties

TestRunResult       ♦--> TestCaseResult       // results
TestCaseResult      ♦--> StepTiming           // steps (внутренний static-класс)

EntityClassifier    -->  AppModel             // параметр метода classify
EntityClassifier    -->  EntityObject         // параметр + итерация
EntityClassifier    -->  PropertyGroup        // итерация по PG для поиска родительского Grid
EntityClassifier    -->  Association          // итерация для FK-target анализа
EntityClassifier    -->  Search, SearchResultProperty  // используются в isReferenceDictionary
EntityClassifier    -->  EntityKind           // возвращает в Classification

EntityClassifier.Classification ◇--> EntityObject   // parentEntity — на найденный родитель ссылается, не владеет
EntityClassifier.Classification ◇--> PropertyGroup  // parentGrid    — ссылка на чужой PG
EntityClassifier.Classification -->  EntityKind     // kind
```

### 4.3. Особенности модели

- **Anemic domain model**: классы — почти чистые POJO с геттерами/сеттерами. Логика в `EntityClassifier` и `EntityObject.hasCrudOperations()` / `getFormView()`.
- **`EntityClassifier` намеренно вынесен «снаружи»** доменных классов (functional style, static), потому что классификация требует знания соседних сущностей (контекста `AppModel`), а не одного объекта.
- **Парная связь PG ↔ Operation**: в XML операции описаны как часть `Properties` — отсюда композиция.
- **`Association.associateItemGuid`** ссылается на `EntityObject.guid` — это семантическая ассоциация через GUID, **без прямой ссылки на объект**: на UML-диаграмме её можно показать как `Association --> EntityObject` с комментарием «by GUID».
- **`Property.tableName/attrName`** — координаты в БД-схеме E3Core, нужны для драйверов автозаполнения формы.

---

## 5. Пакет `ru.autotestgen.parser`

Пакет декомпозирован по принципу единственной ответственности (SRP): каждый класс отвечает за разбор своего типа XML-узлов. Раньше всё лежало в одном `XmlModelParser` (≈300 строк) — теперь это **фасад-координатор**, который делегирует работу специализированным парсерам.

### 5.1. Классы

| Класс                 | Роль                                                                                          |
| --------------------- | --------------------------------------------------------------------------------------------- |
| `XmlModelParser`      | **Фасад/координатор**. `File → AppModel`. Открывает StAX-reader, диспетчеризует `<Category>`, `<Object>`, `<Searches>`. |
| `EntityParser`        | Парсит `<Object>` → `EntityObject` + `<AssociationObjectA>` → `Association`. Делегирует `<Properties>` в `PropertyGroupParser`. |
| `PropertyGroupParser` | Парсит `<Properties>` → `PropertyGroup`, включая `<Property>` → `Property` и `<Operation>` → `Operation` + `OperationParam` + `Modifier`. |
| `SearchParser`        | Парсит `<Searches>` → `List<Search>`, включая `<SearchParam>`, `<SearchResult>`, `<SearchResultProperty>`. |
| `StaxUtils`           | Статические утилиты: `attr(reader, name)`, `parseInt(value)`, `skipToEnd(reader)`. Общие для всех парсеров. |
| `XmlNamespaces`       | Константы namespace-URI: `NS_E`, `NS_E3`, `NS_MD`. Один источник правды.                       |

### 5.2. Связи внутри пакета

```
XmlModelParser        ♦--> EntityParser              // создаёт в конструкторе, владеет
XmlModelParser        ♦--> SearchParser              // создаёт в конструкторе, владеет

EntityParser          ♦--> PropertyGroupParser       // создаёт в конструкторе, владеет

XmlModelParser        -->  StaxUtils                 // static-вызовы attr()
XmlModelParser        -->  XmlNamespaces             // константы NS_E, NS_E3
EntityParser          -->  StaxUtils                 // attr()
EntityParser          -->  XmlNamespaces             // NS_E, NS_E3
PropertyGroupParser   -->  StaxUtils                 // attr(), parseInt(), skipToEnd()
PropertyGroupParser   -->  XmlNamespaces             // NS_E, NS_E3
SearchParser          -->  StaxUtils                 // attr(), parseInt()
SearchParser          -->  XmlNamespaces             // NS_E3
```

> Конструкторы `XmlModelParser` и `EntityParser` перегружены: есть `new XmlModelParser()` (создаёт зависимости сам = композиция) и `new XmlModelParser(entityParser, searchParser)` (зависимости извне = инверсия зависимостей / агрегация). Это позволяет подменять парсеры в тестах.

### 5.3. Связи с другими пакетами

```
XmlModelParser        -->  AppModel                  // создаёт и возвращает
XmlModelParser        -->  ParserException           // оборачивает IOException/XMLStreamException

EntityParser          -->  EntityObject, Association

PropertyGroupParser   -->  PropertyGroup, Property, Operation, OperationParam, Modifier
PropertyGroupParser   -->  AttrType, ModifyType      // enums (fromXml / fromCode)

SearchParser          -->  Search, SearchParam, SearchResult, SearchResultProperty
```

### 5.4. Особенности

- **Поточный (StAX)**, а не DOM: не загружает весь XML в память — критично для больших моделей.
- **Namespace-aware**: одни и те же локальные имена тегов могут быть в разных URI; каждый парсер фильтрует по NS из `XmlNamespaces`.
- **Граница уровней** через локальный `depth`-счётчик — стандартный паттерн StAX. Каждый специализированный парсер читает ровно «своё поддерево» и возвращает курсор на закрывающий тег.
- **Точка расширения**: новый тип XML-узла → добавить ещё один класс `XxxParser`, зарегистрировать в `XmlModelParser.parse()`. Остальные парсеры менять не нужно.

---

## 6. Пакет `ru.autotestgen.common`

### 6.1. Классы

| Класс              | Роль                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------- |
| `Transliterator`   | Static-утилита: кириллица → PascalCase / camelCase / snake_camelCase для Java-имён.    |
| `JavaFileWriter`   | Билдер форматированного Java-исходника: `writeLine`, `openBlock`, `closeBlock`, `indent`. |
| `ParserException`  | Доменное checked-исключение.                                                          |

### 6.2. Связи внутри пакета

Пакет — листовой, классы между собой не связаны. Только `ParserException extends Exception`.

### 6.3. Особенности

- `Transliterator` детерминирован: одно и то же русское имя всегда даёт одно и то же Java-имя — это важно для воспроизводимости имён сгенерированных классов.
- `JavaFileWriter` — минималистичный, без AST: 60 строк, ровно столько, сколько нужно генератору.

---

## 7. Пакет `ru.autotestgen.data` — JDBC-DAO

### 7.1. Классы

| Класс       | Роль                                                                                       |
| ----------- | ------------------------------------------------------------------------------------------ |
| `ReportDao` | Доступ к SQLite-базе `autotestgen.db`. Хранит историю прогонов и тест-кейсов.              |

### 7.2. Связи

```
ReportDao           -->  TestRunResult         // принимает в saveRun, возвращает из getAllRuns
ReportDao           -->  TestCaseResult        // вложенные кейсы
ReportDao           -->  java.sql.* (JDBC)     // SQLite через DriverManager
```

### 7.3. Особенности

- **Init-on-construct**: `initDatabase()` создаёт таблицы при необходимости — DAO самодостаточен.
- **Транзакция на запись прогона**: `setAutoCommit(false)` + `commit()` — все кейсы пишутся атомарно.
- **Схема**:
  ```sql
  test_run (id PK, run_date, xml_file, base_url, total, passed, failed, skipped, duration_ms)
  test_case (id PK, run_id → test_run.id, class_name, method_name, passed, failure_msg, duration_ms)
  ```
- **N+1 при выборке истории**: `getAllRuns()` → для каждого run отдельный `getCaseResults()`. Это известное место для оптимизации (JOIN).

---

## 8. Пакет `ru.autotestgen.generator`

### 8.1. Классы

| Класс                | Роль                                                                                            |
| -------------------- | ----------------------------------------------------------------------------------------------- |
| `TestConfig`         | Контейнер настроек запуска: URL, login, password, output-dir, browser, siteType, subsystem, testLevel. |
| `TestGenerator`      | Главный «дирижёр»: пишет `pom.xml`, `BaseTest`, делегирует генерацию Page Object + тест-класса.  |
| `PageObjectWriter`   | Генерирует Page Object Java-класс по `EntityObject` + `TestConfig`.                              |
| `TestClassWriter`    | Генерирует тест-класс JUnit 5 (testCreate, testUpdate, testDelete, testCreateOnlyRequired, ...). |
| `TestDataFactory`    | Подбирает значения для полей формы по типу/маске (rand string, decimal, date).                  |
| `TestRunner`         | Запускает `mvn test` (опционально с `-Dtest=...`) и парсит Surefire XML.                         |
| `RunReportWriter`    | Генерирует HTML/CSV-отчёт по `TestRunResult` (run-report v5 с фотолетописью).                    |

### 8.2. Связи внутри пакета

```
TestGenerator       ◇--> TestConfig            // получает извне через конструктор, не создаёт
TestGenerator       -->  PageObjectWriter      // инстанцирует и зовёт write(...)
TestGenerator       -->  TestClassWriter       // инстанцирует и зовёт write(...)
TestGenerator       -->  TestDataFactory       // используется внутри писателей

PageObjectWriter    -->  TestConfig            // конфиг прокидывается параметром
TestClassWriter     -->  TestConfig
RunReportWriter     -->  TestConfig            // путь к скриншотам, base-url

TestRunner          -->  (нет генератор-зависимостей; работает с готовым проектом)
```

### 8.3. Связи с другими пакетами

```
TestGenerator       -->  AppModel, EntityObject, PropertyGroup, Property,
                         Association, Operation, Modifier, ModifyType,
                         Search, EntityClassifier, EntityKind
TestGenerator       -->  JavaFileWriter        // common — форматтер
TestGenerator       -->  Transliterator        // common — имена классов

PageObjectWriter    -->  EntityObject, PropertyGroup, Property, Association
PageObjectWriter    -->  JavaFileWriter, Transliterator

TestClassWriter     -->  EntityObject, PropertyGroup, Property, Operation, Modifier, ModifyType
TestClassWriter     -->  Search, SearchParam, SearchResult, SearchResultProperty
TestClassWriter     -->  JavaFileWriter, Transliterator

TestDataFactory     -->  Property, AttrType

TestRunner          -->  TestRunResult, TestCaseResult        // возвращает
TestRunner          -->  Surefire-XML-репорты (StAX)          // парсит target/surefire-reports/*.xml

RunReportWriter     -->  TestRunResult, TestCaseResult
```

### 8.4. Особенности генератора

- **Без шаблонизатора**: всё через `JavaFileWriter.writeLine(...)` — байт-в-байт контроль над выходом, никаких Freemarker/Velocity.
- **Page Object + Test разделены**: `PageObjectWriter` пишет POM-класс, `TestClassWriter` пишет JUnit-класс. Page Object скрывает работу с ExtJS (PropertyGrid, FK-picker, кнопки, грид, маски).
- **`TestGenerator` — единственный, кто пишет `pom.xml` и `BaseTest`** сгенерированного проекта. То есть структура целевого Maven-проекта зашита здесь.
- **`TestDataFactory`** — простая фабрика-стратегия по `AttrType` и `mask`. Можно вынести как отдельный интерфейс при росте поведения.
- **Скиппинг сущностей**: `EntityClassifier` решает, нужен ли тест-класс вообще. Если REFERENCE_DICTIONARY/CHILD без меню-входа — не генерируем.
- **`TestRunner` не использует Maven Embedder** — запускает `mvn` через `ProcessBuilder`, читает stdout построчно (`lineConsumer`). Это намеренно: меньше зависимостей и более предсказуемое поведение, чем у встроенного Maven.

---

## 9. Сквозные связи: «как одна команда GUI распадается на вызовы»

Кнопка **«Сгенерировать тесты»** в `MainController`:

```
MainController.onGenerate()
   ├── new TestConfig()                                  (composition: рождается локально)
   ├── new TestGenerator(config)                         (config становится агрегацией внутри)
   └── generator.generate(currentModel)
         ├── для каждой EntityObject:
         │     ├── EntityClassifier.classify(entity, model) → Classification
         │     ├── PageObjectWriter.write(entity, ...)
         │     │     └── JavaFileWriter → Files.write()
         │     └── TestClassWriter.write(entity, ...)
         │           └── JavaFileWriter → Files.write()
         └── записать pom.xml, BaseTest.java
```

Кнопка **«Запустить тесты»**:

```
MainController.onRunTests() / onRunSelected()
   └── Task<TestRunResult>.call()
         └── new TestRunner().run(outputDir, xml, url, filter, fast)
               ├── ProcessBuilder("mvn test ...").start()
               ├── читает stdout построчно → lineConsumer (лог в GUI)
               └── парсит target/surefire-reports/*.xml → TestRunResult
   ↳ Platform.runLater:
         ├── displayResults(result)        // обновить TableView
         └── reportDao.saveRun(result)     // сохранить в SQLite
```

Кнопка **«История прогонов»**:

```
MainController.onShowHistory()
   ├── reportDao.getAllRuns() → List<TestRunResult>
   ├── для каждого: append в logArea краткой строкой
   └── displayResults(runs.get(0))         // последний прогон в TableView
```

---

## 10. Диаграммы взаимодействия

Раздел показывает, **кто кого вызывает по времени** в трёх сценариях: нормальный поток, прерывание пользователем, прерывание системой. Стрелка `─►` — синхронный вызов, `◄─` — возврат, `╳` — гибель сценария, `⚠` — точка ошибки.

### 10.1. Нормальный сценарий: «Парсинг → Генерация → Запуск → Отчёт»

```
Пользователь  MainController     XmlModelParser      TestGenerator     TestRunner       ReportDao    SQLite
     │              │                  │                   │                │              │           │
  click            │                  │                   │                │              │           │
"Разобрать XML"     │                  │                   │                │              │           │
     ├─────────────►│                  │                   │                │              │           │
     │              │  parse(file)     │                   │                │              │           │
     │              ├─────────────────►│                   │                │              │           │
     │              │                  │ (EntityParser /                    │              │           │
     │              │                  │  SearchParser     │                │              │           │
     │              │                  │  делегирование)   │                │              │           │
     │              │   AppModel       │                   │                │              │           │
     │              │◄─────────────────┤                   │                │              │           │
     │ список       │                  │                   │                │              │           │
     │ сущностей    │                  │                   │                │              │           │
     │◄─────────────┤                  │                   │                │              │           │
     │              │                  │                   │                │              │           │
  click             │                  │                   │                │              │           │
"Сгенерировать"     │                  │                   │                │              │           │
     ├─────────────►│                  │                   │                │              │           │
     │              │  generate(model) │                   │                │              │           │
     │              ├──────────────────────────────────────►│                │              │           │
     │              │     pom.xml, BaseTest.java, *Test.java записаны        │              │           │
     │              │◄──────────────────────────────────────┤                │              │           │
     │  "Тесты      │                  │                   │                │              │           │
     │  сгенерены"  │                  │                   │                │              │           │
     │◄─────────────┤                  │                   │                │              │           │
     │              │                  │                   │                │              │           │
  click             │                  │                   │                │              │           │
"Запустить тесты"   │                  │                   │                │              │           │
     ├─────────────►│                  │                   │                │              │           │
     │              │  Task.start()    │                   │                │              │           │
     │              ├───── (fork JavaFX thread) ────────────────────────────►│              │           │
     │              │                  │                   │                │              │           │
     │              │                  │                   │   ProcessBuilder("mvn test")  │           │
     │              │                  │                   │   читает stdout построчно     │           │
     │              │                  │                   │   парсит surefire-reports/*.xml           │
     │              │                  │                   │                │              │           │
     │              │  onSucceeded(result)                 │                │              │           │
     │              │◄─── (Platform.runLater) ─────────────────────────────┤              │           │
     │              │                  │                   │                │              │           │
     │              │  saveRun(result) │                   │                │              │           │
     │              ├───────────────────────────────────────────────────────────────────►│           │
     │              │                  │                   │                │              │ INSERT    │
     │              │                  │                   │                │              ├──────────►│
     │              │                  │                   │                │              │◄──────────┤
     │              │◄──────────────────────────────────────────────────────────────────────┤           │
     │  таблица     │                  │                   │                │              │           │
     │  + лог       │                  │                   │                │              │           │
     │◄─────────────┤                  │                   │                │              │           │
```

### 10.2. Прерывание пользователем

#### 10.2.1. Отмена в диалоге выбора файла

```
Пользователь          MainController            FileChooser
     │                       │                       │
  click "Обзор..."           │                       │
     ├──────────────────────►│                       │
     │                       │  showOpenDialog()     │
     │                       ├──────────────────────►│
     │   "Отмена" / Esc      │                       │
     ├───────────────────────────────────────────────►│
     │                       │   null                │
     │                       │◄──────────────────────┤
     │                       │                       │
     │                       │  if (file != null) ╳  │   ← путь не подставляется,
     │                       │  return без действия  │     поле xmlPathField остаётся пустым
     │                       │                       │
     │  ничего не            │                       │
     │  поменялось           │                       │
     │◄──────────────────────┤                       │
```

#### 10.2.2. Отмена в диалоге «Выбрать тесты…»

```
Пользователь    MainController     Dialog<ButtonType>
     │                │                    │
  click             │                    │
"Выбрать тесты..."   │                    │
     ├───────────────►│                    │
     │                │  showAndWait()     │
     │                ├───────────────────►│
     │  чекбоксы +    │                    │
     │  превью        │                    │
     │◄───────────────┼────────────────────┤
     │                │                    │
  click "Cancel"      │                    │
     ├────────────────────────────────────►│
     │                │  Optional.of(CANCEL) (или пусто)
     │                │◄───────────────────┤
     │                │                    │
     │                │  if (res != runBtn) ╳   ← launchRun НЕ вызывается,
     │                │  return            │      ничего не запускается
     │                │                    │
     │  без изменений │                    │
     │◄───────────────┤                    │
```

#### 10.2.3. Закрытие окна программы во время прогона тестов

> Сценарий **проблемный**: окно JavaFX закрывается, но `Task` в фоновом потоке продолжает работать вместе с дочерним процессом `mvn`. На текущий момент graceful-shutdown НЕ реализован.

```
Пользователь    MainController      Task<TestRunResult>      mvn (Process)     Chrome
     │                │                      │                      │              │
  click "Запустить"  │                      │                      │              │
     ├───────────────►│                      │                      │              │
     │                │  new Thread(task).start()                   │              │
     │                ├─────────────────────►│                      │              │
     │                │                      │  ProcessBuilder.start()             │
     │                │                      ├─────────────────────►│              │
     │                │                      │                      │ launch Chrome│
     │                │                      │                      ├─────────────►│
     │                │                      │                      │  Selenium UI │
     │                │                      │                      │◄─────────────┤
     │                │                      │                      │              │
  click ✕ (закрыть)  │                      │                      │              │
     ├───────────────►│                      │                      │              │
     │                │  окно JavaFX         │                      │              │
     │                │  закрывается         │                      │              │
     │                ╳                      │                      │              │
     │                                       │                      │              │
     │                                       │  task продолжает выполняться        │
     │                                       │  mvn-процесс жив, Chrome жив        │
     │                                       │  Platform.runLater → NPE (JFX off)  │
     │                                       │                      │              │
     │  ⚠ процесс mvn остаётся в фоне        │                      │              │
     │    Chrome остаётся открытым           │                      │              │
     │    задачу надо снимать руками         │                      │              │
     │    через Диспетчер задач              │                      │              │
```

> **Точка расширения** (раздел 11): добавить `primaryStage.setOnCloseRequest(e -> { task.cancel(true); process.destroyForcibly(); })`.

#### 10.2.4. Попытка генерации без разбора XML

```
Пользователь    MainController
     │                │
  click             │
"Сгенерировать"     │
     ├───────────────►│
     │                │  if (currentModel == null) ⚠
     │                │     ↓
     │                │  showAlert("Ошибка",
     │                │   "Сначала разберите XML-файл.")
     │  Alert         │
     │◄───────────────┤
     │  click OK      │
     ├───────────────►│
     │                │  return без действия ╳
```

### 10.3. Прерывание системой

#### 10.3.1. Невалидный / повреждённый XML-файл

```
Пользователь  MainController    XmlModelParser   EntityParser   StAX reader
     │              │                  │                │              │
  click             │                  │                │              │
"Разобрать XML"     │                  │                │              │
     ├─────────────►│                  │                │              │
     │              │  parse(file)     │                │              │
     │              ├─────────────────►│                │              │
     │              │                  │  open + create XMLStreamReader│
     │              │                  ├──────────────────────────────►│
     │              │                  │  next()...                   │
     │              │                  ├──────────────────────────────►│
     │              │                  │                ⚠ XMLStreamException
     │              │                  │                  (malformed)
     │              │                  │◄──────────────────────────────┤
     │              │                  │  catch → throw ParserException
     │              │  ParserException │                │              │
     │              │◄─────────────────┤                │              │
     │              │  catch в onParse:                 │              │
     │              │  showAlert("Ошибка парсинга", e.getMessage())   │
     │  Alert       │                  │                │              │
     │◄─────────────┤                  │                │              │
     │              │  log("Ошибка парсинга: ...")     │              │
     │              │  btnGenerate остаётся disabled    │              │
```

#### 10.3.2. Файл не найден (введён вручную)

```
Пользователь  MainController       java.io.File
     │              │                    │
  click             │                    │
"Разобрать XML"     │                    │
     ├─────────────►│                    │
     │              │  new File(path)    │
     │              ├───────────────────►│
     │              │  exists()          │
     │              ├───────────────────►│
     │              │   false ⚠          │
     │              │◄───────────────────┤
     │              │  showAlert("Ошибка",
     │              │   "Файл не найден: " + path)
     │  Alert       │                    │
     │◄─────────────┤                    │
     │              │  return ╳          │
```

#### 10.3.3. Maven не установлен / нет в PATH

```
Пользователь  MainController   Task     TestRunner    ProcessBuilder
     │              │           │            │              │
  click             │           │            │              │
"Запустить тесты"   │           │            │              │
     ├─────────────►│           │            │              │
     │              │  Task.start()           │              │
     │              ├──────────►│            │              │
     │              │           │  run(...)  │              │
     │              │           ├───────────►│              │
     │              │           │            │  start("mvn",...)
     │              │           │            ├─────────────►│
     │              │           │            │  ⚠ IOException│
     │              │           │            │   "mvn not found"
     │              │           │            │◄─────────────┤
     │              │           │  throws    │              │
     │              │           │◄───────────┤              │
     │              │  onFailed(throwable)   │              │
     │              │◄──────────┤            │              │
     │              │  showAlert("Ошибка", t.getMessage()) │
     │  Alert       │                                       │
     │◄─────────────┤                                       │
     │              │  progressBar скрыт,                   │
     │              │  кнопки снова active                  │
```

#### 10.3.4. Chrome / ChromeDriver недоступен (падение в Selenium-тесте)

> Это **не** прерывание программы — это падение внутри сгенерированного теста. Программа продолжает работу, тест помечается FAIL и попадает в отчёт.

```
mvn (forked JVM)    JUnit       сгенерированный Test    WebDriverManager   Chrome
     │                │                  │                       │              │
     │  Surefire fork │                  │                       │              │
     ├───────────────►│                  │                       │              │
     │                │  @BeforeAll setup()                      │              │
     │                ├─────────────────►│                       │              │
     │                │                  │ WebDriverManager.chromedriver().setup()
     │                │                  ├──────────────────────►│              │
     │                │                  │                       │  ⚠ нет инета │
     │                │                  │                       │  или версия  │
     │                │                  │                       │  Chrome ≠    │
     │                │                  │                       │  драйвера    │
     │                │                  │  throws WebDriverException           │
     │                │                  │◄──────────────────────┤              │
     │                │  test FAILED     │                       │              │
     │                │◄─────────────────┤                       │              │
     │  XML-репорт с │                  │                       │              │
     │  <failure/>   │                  │                       │              │
     │◄───────────────┤                  │                       │              │
     │                │                  │                       │              │
     ┴ (мvn возвращает exit-code ≠ 0, но Surefire-XML создан)                  │

     ↓ далее TestRunner парсит XML и помещает FAIL в TestRunResult,
       программа отображает красную строку в таблице — НЕ падает.
```

#### 10.3.5. Сбой записи в SQLite (диск переполнен / БД залочена)

```
MainController    ReportDao        SQLite (JDBC)
     │                │                  │
     │ saveRun(result)│                  │
     ├───────────────►│                  │
     │                │  setAutoCommit(false)
     │                ├─────────────────►│
     │                │  INSERT test_run │
     │                ├─────────────────►│
     │                │   ⚠ SQLException │
     │                │   "database is locked" / "disk full"
     │                │◄─────────────────┤
     │                │  catch → log в stderr,
     │                │  НЕ пробрасывает дальше
     │                │  (graceful degradation)
     │   void         │                  │
     │◄───────────────┤                  │
     │                                   │
     │ ⚠ результаты прогона ОТОБРАЖЕНЫ в UI,
     │   но не сохранены в историю.
     │   В логе появится "Failed to save test run: ..."
```

#### 10.3.6. Каталог вывода защищён от записи (permission denied)

```
Пользователь  MainController    TestGenerator    JavaFileWriter    Files.write()
     │              │                  │                │                  │
  click             │                  │                │                  │
"Сгенерировать"     │                  │                │                  │
     ├─────────────►│                  │                │                  │
     │              │  generate(model) │                │                  │
     │              ├─────────────────►│                │                  │
     │              │                  │  writeToFile()                    │
     │              │                  ├───────────────►│                  │
     │              │                  │                │  Files.write(...)│
     │              │                  │                ├─────────────────►│
     │              │                  │                │   ⚠ AccessDeniedException
     │              │                  │                │◄─────────────────┤
     │              │                  │   IOException  │                  │
     │              │                  │◄───────────────┤                  │
     │              │   Exception      │                │                  │
     │              │◄─────────────────┤                │                  │
     │              │  showAlert("Ошибка генерации", e.getMessage())      │
     │  Alert       │                  │                │                  │
     │◄─────────────┤                  │                │                  │
     │              │  кнопки RunTests остаются disabled,                 │
     │              │  частично записанные файлы могут остаться           │
```

### 10.4. Сводная таблица обработки прерываний

| Источник                  | Где обнаруживается              | Реакция программы                                     | Восстановление |
| ------------------------- | ------------------------------- | ----------------------------------------------------- | -------------- |
| Отмена FileChooser        | `MainController.onSelectXml`    | `if (file != null)` — silent return                   | Полное          |
| Cancel в диалоге выбора тестов | `MainController.onRunSelected` | `if (res != runBtn)` — silent return                  | Полное          |
| Закрытие окна при прогоне | (не обработано)                 | Task + mvn остаются в фоне                            | **Нет** ⚠       |
| Generate без parse        | `MainController.onGenerate`     | `showAlert` + return                                  | Полное          |
| Невалидный XML            | `XmlModelParser.parse`          | `throw ParserException` → `showAlert` в `onParse`     | Полное          |
| Файл не найден            | `MainController.onParse`        | `showAlert` + return                                  | Полное          |
| Нет Maven                 | `TestRunner.run`                | `IOException` → `Task.onFailed` → `showAlert`         | Полное          |
| Падение Chrome            | внутри сгенерированного теста   | Помечается FAIL, попадает в `TestRunResult`           | Полное          |
| Сбой SQLite               | `ReportDao.saveRun`             | `catch SQLException` + `log в stderr`                 | Частичное      |
| Permission denied на запись | `TestGenerator.generate`      | `IOException` → `showAlert`                           | Частичное (могут остаться полу-файлы) |

---

## 11. Замечания для проектирования новых диаграмм

- **Диаграмма пакетов**: 6 пакетов, направление зависимостей — сверху вниз (`ui → generator/parser/data → model/common`).
- **Class diagram domain-слоя**: центр — `AppModel`, ниже композиции `EntityObject → PropertyGroup → Property`, отдельной веткой `Search → SearchParam/SearchResult`.
- **Class diagram UI-слоя**: `App ⇒ MainController` (loaded by FXML), `MainController` агрегирует `AppModel`, `ReportDao`, использует фасады `XmlModelParser`, `TestGenerator`, `TestRunner`.
- **Sequence diagram «Полный цикл»**: GUI → Parser → Generator → mvn → Runner → DAO → GUI (5 коробок, 8 стрелок). См. раздел 10.1.
- **Sequence diagram прерываний пользователем** (раздел 10.2): отмена FileChooser, отмена диалога выбора тестов, закрытие окна при прогоне, попытка генерации без парсинга.
- **Sequence diagram прерываний системой** (раздел 10.3): невалидный XML, файл не найден, отсутствие Maven, падение Chrome, сбой SQLite, permission denied.
- **Component diagram**: видны два «конечных продукта» — окно JavaFX и сгенерированный Maven-проект (внешний артефакт-каталог `generated-tests/`).
- **State diagram GUI**: состояния кнопок (`btnGenerate/btnRunTests/btnRunSelected` disabled/enabled) переключаются по событиям onParse → onGenerate → onRun*.

---

## 12. Точки расширения

| Где                          | Идея                                                                       |
| ---------------------------- | -------------------------------------------------------------------------- |
| `TestDataFactory`            | Вынести в интерфейс `ValueProvider` + реализации по `AttrType`.            |
| `ReportDao`                  | Заменить N+1 на одно SQL с JOIN; добавить индекс на `test_case.run_id`.    |
| `TestConfig`                 | Превратить в record + Builder для immutability.                            |
| `EntityClassifier`           | Перевести `Classification` в `sealed interface` (PRIMARY / CHILD / DICTIONARY) с конкретными data-records. |
| `TestRunner`                 | Вынести интерфейс `TestExecutor` чтобы можно было подменить Maven на Gradle / standalone JUnit. |
| `ui.MainController`          | Раздробить на отдельные подконтроллеры по секциям (Input / Run / Results / History) для уменьшения 530 строк. |

---

*Файл предназначен как основа для перепроектирования (vers2): здесь зафиксированы все классы, их роли и три типа связей (`->`, агрегация `◇`, композиция `♦`), необходимых для отрисовки новых UML-диаграмм.*
