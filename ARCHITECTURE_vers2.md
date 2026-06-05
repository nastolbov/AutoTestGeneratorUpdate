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

## 3. Пакет `ru.autotestgen.ui` — GUI и CLI

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

### 5.1. Классы

| Класс             | Роль                                                                |
| ----------------- | ------------------------------------------------------------------- |
| `XmlModelParser`  | StAX-парсер: `File → AppModel`. Узнаёт элементы по namespace (NS_E, NS_E3, NS_MD). |

### 5.2. Связи

```
XmlModelParser      -->  AppModel              // создаёт корень модели
XmlModelParser      -->  EntityObject          // создаёт и заполняет
XmlModelParser      -->  Association
XmlModelParser      -->  PropertyGroup
XmlModelParser      -->  Property
XmlModelParser      -->  AttrType              // fromXml()
XmlModelParser      -->  Operation
XmlModelParser      -->  OperationParam
XmlModelParser      -->  Modifier, ModifyType
XmlModelParser      -->  Search, SearchParam, SearchResult, SearchResultProperty
XmlModelParser      -->  ParserException       // оборачивает IOException/XMLStreamException
```

### 5.3. Особенности

- **Поточный (StAX)**, а не DOM: не загружает весь XML в память — критично для больших моделей.
- **Namespace-aware**: одни и те же локальные имена тегов могут быть в разных URI; парсер фильтрует по NS.
- **Граница уровней** через локальный `depth`-счётчик — стандартный паттерн для StAX.

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

## 10. Замечания для проектирования новых диаграмм

- **Диаграмма пакетов**: 6 пакетов, направление зависимостей — сверху вниз (`ui → generator/parser/data → model/common`).
- **Class diagram domain-слоя**: центр — `AppModel`, ниже композиции `EntityObject → PropertyGroup → Property`, отдельной веткой `Search → SearchParam/SearchResult`.
- **Class diagram UI-слоя**: `App ⇒ MainController` (loaded by FXML), `MainController` агрегирует `AppModel`, `ReportDao`, использует фасады `XmlModelParser`, `TestGenerator`, `TestRunner`.
- **Sequence diagram «Полный цикл»**: GUI → Parser → Generator → mvn → Runner → DAO → GUI (5 коробок, 8 стрелок).
- **Component diagram**: видны два «конечных продукта» — окно JavaFX и сгенерированный Maven-проект (внешний артефакт-каталог `generated-tests/`).
- **State diagram GUI**: состояния кнопок (`btnGenerate/btnRunTests/btnRunSelected` disabled/enabled) переключаются по событиям onParse → onGenerate → onRun*.

---

## 11. Точки расширения

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
