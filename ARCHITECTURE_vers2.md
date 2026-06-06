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

**Таблица 3.1. Описание классов пакета «UI»**

| Класс                          | Описание                                                                                                                                                              |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `App`                          | Главный класс JavaFX-приложения, наследник `javafx.application.Application`. Загружает FXML-разметку главного окна (`main.fxml`), создаёт сцену и запускает интерфейс. |
| `MainController`               | FXML-контроллер главного окна JavaFX-приложения. Связывает элементы интерфейса (кнопки, поля, таблицы) с обработчиками событий. Координирует вызовы парсера, генератора, раннера и DAO. |
| `MainController.TestCaseRow`   | Вложенный статический класс внутри `MainController`. Представляет одну строку таблицы результатов тестов: класс, метод, статус, длительность, сообщение.              |

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

### 3.4. Диаграммы последовательностей пакета `ui`

#### 3.4.1. Нормальный ход событий

```
Пользователь   Stage        FXMLLoader     MainController   FileChooser    TableView
     │           │              │               │                │             │
   запуск        │              │               │                │             │
   программы     │              │               │                │             │
     ├──────────►│  start()    │               │                │             │
     │           ├──load("main.fxml")          │                │             │
     │           │  ────────────►│              │                │             │
     │           │              │  new()        │                │             │
     │           │              ├──────────────►│                │             │
     │           │              │  @FXML inject │                │             │
     │           │              ├──────────────►│                │             │
     │           │              │  initialize() │                │             │
     │           │              ├──────────────►│                │             │
     │  окно открыто           │               │                │             │
     │◄──────────┤              │               │                │             │
     │                                          │                │             │
   click "Обзор..."                             │                │             │
     ├────────────────────────────────────────►│                │             │
     │                                          │ showOpenDialog()             │
     │                                          ├───────────────►│             │
     │           выбор файла .xml               │                │             │
     ├────────────────────────────────────────────────────────►│             │
     │                                          │  File          │             │
     │                                          │◄───────────────┤             │
     │                                          │ setText(path)  │             │
     │                                          │◄┐              │             │
     │  путь в поле                              │ │             │             │
     │◄─────────────────────────────────────────┤              │             │
     │                                          │                │             │
   click "Разобрать XML"                        │                │             │
     ├────────────────────────────────────────►│                │             │
     │                                          │ onParse() заполняет          │
     │                                          │ entityListView                │
     │  список сущностей слева                  │                │             │
     │◄─────────────────────────────────────────┤                │             │
     │                                          │                │             │
   click "Запустить"                            │                │             │
     ├────────────────────────────────────────►│                │             │
     │                                          │ launchRun(null)              │
     │                                          │ Task в фоне →                │
     │                                          │ onSucceeded → displayResults │
     │                                          ├──────────────────────────────►│
     │                                          │ setItems(rows)               │
     │  таблица заполнена                       │                │             │
     │◄─────────────────────────────────────────┤                │             │
```

#### 3.4.2. Прерывание пользователем

```
Пользователь    MainController     FileChooser
     │                │                  │
   click "Обзор..."   │                  │
     ├───────────────►│                  │
     │                │ showOpenDialog() │
     │                ├─────────────────►│
     │  Esc / Cancel  │                  │
     ├──────────────────────────────────►│
     │                │  null            │
     │                │◄─────────────────┤
     │                │ if (file != null) ╳   ← путь не подставлен,
     │                │ return           │      состояние UI не меняется
     │  без изменений │                  │
     │◄───────────────┤                  │

─── другой сценарий: закрытие окна во время прогона тестов ───

Пользователь    Stage     MainController       Task<TestRunResult>
     │            │              │                       │
  click "Запустить"             │                       │
     ├──────────►│               │                       │
     │            │ Task.start() │                       │
     │            │              ├──────────────────────►│
     │            │              │                       │ работает
     │            │              │                       │ в фоне
   click ✕       │              │                       │
     ├──────────►│               │                       │
     │            │ окно закрыто │                       │
     │            │ Stage hidden │                       │
     │            ╳              │                       │
     │                           ⚠ Platform.runLater     │
     │                           │ из onSucceeded — NPE  │
     │                           │ JavaFX-thread мёртв   │
     │                           │ Task продолжает,      │
     │                           │ mvn-процесс жив       │
     │  ⚠ требуется ручное снятие задачи через Диспетчер │
```

#### 3.4.3. Прерывание системой

```
Пользователь   Stage     FXMLLoader     ClassLoader
     │           │            │                │
   запуск        │            │                │
     ├──────────►│ start()    │                │
     │           ├───────────►│ load("main.fxml")
     │           │            ├───────────────►│
     │           │            │  ⚠ IOException │
     │           │            │  (FXML не      │
     │           │            │   найден / битый)
     │           │            │◄───────────────┤
     │           │  Exception │                │
     │           │◄───────────┤                │
     │           ╳ start() кидает Exception    │
     │             программа не запускается    │
     │           stdout: java.lang.RuntimeException
     │
     ↓ окно НЕ открывается, в консоли стек-трейс

─── другой сценарий: NPE в displayResults после смены модели ───

MainController    Task     TableView
     │              │           │
     │  Task.onSucceeded(result)│
     │◄─────────────┤           │
     │ displayResults(result)   │
     │ result == null ⚠         │
     │ NullPointerException     │
     │ ловит JavaFX UncaughtExceptionHandler
     │ в логе stderr, окно живо
     │ progressBar остаётся видимым,
     │ кнопки disabled
```

### 3.5. Диаграмма кооперации пакета `ui`

В кооперации (collaboration diagram UML 1.x) указаны нумерованные сообщения, отправляемые между объектами в типовом сценарии «Запуск тестов».

```
              1: launch(args)              2: load("main.fxml")
   ┌────────┐ ──────────────────►  ┌─────┐ ────────────────────► ┌────────────┐
   │  main  │                      │ App │                       │ FXMLLoader │
   └────────┘                      └─────┘                       └─────┬──────┘
                                                                       │
                                                              3: new() │
                                                                       ▼
   ┌──────────┐  4: @FXML inject  ┌──────────────────┐  5: initialize()
   │ Controls │ ◄─────────────────┤ MainController   │ ◄────────┐
   │ (Button, │                   │  - xmlPathField  │          │
   │  Table,  │ 6: onParse click  │  - btnParse      │          │
   │  ...)    │ ──────────────────► onParse()        │──────────┤
   └──────────┘                   │  - urlField      │          │
                                  │  - testLevelCombo│          │
                                  │  - btnGenerate   │          │
                                  └────────┬─────────┘          │
                                           │ 7: onRunTests      │
                                           ▼                    │
                                   ┌───────────────┐            │
                                   │ Task<TestRunResult>        │
                                   │ background    ├────────────┘
                                   │ thread        │ 8: setOnSucceeded
                                   └───────────────┘
                                           │
                                           │ 9: displayResults(result)
                                           ▼
                                   ┌───────────────┐
                                   │ TestCaseRow   │
                                   │ (data row)    │
                                   └───────────────┘
```

### 3.6. Уточнённая диаграмма классов пакета `ui`

Классы пакета с атрибутами (поля), без сигнатур методов.

```
┌────────────────────────────────────────┐
│              App                       │
│  «entry point»                         │
├────────────────────────────────────────┤
│ (нет полей — точка входа)              │
└────────────────────────────────────────┘
                  │
                  │ запускает (через main.fxml)
                  ▼
┌─────────────────────────────────────────────────────────┐
│              MainController                             │
├─────────────────────────────────────────────────────────┤
│ — @FXML xmlPathField:        TextField                  │
│ — @FXML urlField:            TextField                  │
│ — @FXML loginField:          TextField                  │
│ — @FXML passwordField:       PasswordField              │
│ — @FXML outputDirField:      TextField                  │
│ — @FXML testLevelCombo:      ComboBox<String>           │
│ — @FXML smokeAllSubsystemsCheck: CheckBox               │
│ — @FXML fastModeCheck:       CheckBox                   │
│ — siteTypeCombo:             ComboBox<String> = new     │
│ — subsystemField:            TextField = new            │
│ — @FXML btnSelectXml,btnSelectOutputDir,btnParse,       │
│   btnGenerate,btnRunTests,btnRunSelected,btnShowHistory │
│ — @FXML entityListView:      ListView<String>           │
│ — @FXML resultsTable:        TableView<TestCaseRow>     │
│ — @FXML colClass,colMethod,colStatus,colDuration,       │
│   colMessage:                TableColumn<TestCaseRow,String> │
│ — @FXML statusLabel,totalLabel,passedLabel,failedLabel  │
│ — @FXML progressBar:         ProgressBar                │
│ — @FXML logArea:             TextArea                   │
│ — TEST_CATEGORIES: String[][] = { ... 11 пар ... }      │
│ — currentModel:              AppModel                   │
│ — reportDao:                 final ReportDao = new      │
└─────────────────────────────────────────────────────────┘
                  ♦ (вложенный static class)
                  ▼
┌─────────────────────────────────────────────────────────┐
│        MainController.TestCaseRow                       │
├─────────────────────────────────────────────────────────┤
│ — className:                 final String               │
│ — methodName:                final String               │
│ — status:                    final String               │
│ — duration:                  final String               │
│ — message:                   final String               │
└─────────────────────────────────────────────────────────┘
```

**Таблица 3.2. Описание полей класса «MainController»**

| Название                    | Тип                              | Описание                                                                       |
| --------------------------- | -------------------------------- | ------------------------------------------------------------------------------ |
| `xmlPathField`              | `TextField`                      | Поле ввода пути к XML-файлу метаданных.                                        |
| `urlField`                  | `TextField`                      | Поле ввода URL тестируемого веб-приложения.                                    |
| `loginField`                | `TextField`                      | Поле ввода логина пользователя.                                                |
| `passwordField`             | `PasswordField`                  | Поле ввода пароля с маскированием символов.                                    |
| `outputDirField`            | `TextField`                      | Поле ввода каталога для сгенерированного проекта.                              |
| `testLevelCombo`            | `ComboBox<String>`               | Выпадающий список выбора уровня тестов (SMOKE / BASIC / FULL).                  |
| `smokeAllSubsystemsCheck`   | `CheckBox`                       | Флаг прогона smoke-тестов по всем подсистемам.                                 |
| `fastModeCheck`             | `CheckBox`                       | Флаг быстрого режима (3 параллельных headless-Chrome).                         |
| `siteTypeCombo`             | `ComboBox<String>`               | Выпадающий список выбора типа тестируемого сайта (E3Core / generic / custom).  |
| `subsystemField`            | `TextField`                      | Поле ввода названия подсистемы внутри приложения.                              |
| `btnSelectXml`              | `Button`                         | Кнопка открытия диалога выбора XML-файла.                                      |
| `btnSelectOutputDir`        | `Button`                         | Кнопка открытия диалога выбора выходного каталога.                             |
| `btnParse`                  | `Button`                         | Кнопка запуска парсинга XML-модели.                                            |
| `btnGenerate`               | `Button`                         | Кнопка запуска генерации тестов.                                               |
| `btnRunTests`               | `Button`                         | Кнопка запуска всех сгенерированных тестов.                                    |
| `btnRunSelected`            | `Button`                         | Кнопка открытия диалога выбора подмножества тестов.                            |
| `btnShowHistory`            | `Button`                         | Кнопка показа истории прогонов из базы данных.                                 |
| `entityListView`            | `ListView<String>`               | Список найденных сущностей XML-модели (отображается слева).                    |
| `resultsTable`              | `TableView<TestCaseRow>`         | Таблица результатов прогона тестов (центральная панель).                       |
| `colClass`                  | `TableColumn<TestCaseRow,String>`| Столбец «Класс» в таблице результатов.                                         |
| `colMethod`                 | `TableColumn<TestCaseRow,String>`| Столбец «Метод».                                                               |
| `colStatus`                 | `TableColumn<TestCaseRow,String>`| Столбец «Статус» с цветовой подсветкой (OK / FAIL / SKIP).                     |
| `colDuration`               | `TableColumn<TestCaseRow,String>`| Столбец «Длительность» в миллисекундах.                                        |
| `colMessage`                | `TableColumn<TestCaseRow,String>`| Столбец «Сообщение» (текст ошибки либо подтверждение успеха).                  |
| `statusLabel`               | `Label`                          | Метка статуса в правом верхнем углу окна.                                      |
| `progressBar`               | `ProgressBar`                    | Индикатор прогресса фоновых задач.                                             |
| `logArea`                   | `TextArea`                       | Многострочное поле журнала событий.                                            |
| `totalLabel`                | `Label`                          | Метка «Всего тестов».                                                          |
| `passedLabel`               | `Label`                          | Метка «Успешно / Пропущено» (зелёная).                                         |
| `failedLabel`               | `Label`                          | Метка «Ошибки» (красная).                                                      |
| `TEST_CATEGORIES`           | `static final String[][]`        | Каталог пар (label, Surefire-фильтр) для диалога выбора видов тестов.          |
| `currentModel`              | `AppModel`                       | Загруженная и распарсенная XML-модель. `null`, пока не выполнен `onParse()`.    |
| `reportDao`                 | `final ReportDao`                | Постоянный DAO для сохранения и загрузки истории прогонов из SQLite.           |

**Таблица 3.3. Описание полей класса «MainController.TestCaseRow»**

| Название      | Тип            | Описание                                                          |
| ------------- | -------------- | ----------------------------------------------------------------- |
| `className`   | `final String` | Имя тестового класса (например, `GskOgskTest`).                   |
| `methodName`  | `final String` | Имя тест-метода (например, `testCreate`).                         |
| `status`      | `final String` | Статус: `OK`, `FAIL` или `SKIP`.                                  |
| `duration`    | `final String` | Длительность выполнения с суффиксом «мс».                          |
| `message`     | `final String` | Текст ошибки или подтверждение успешного прохождения.              |

### 3.7. Детальная диаграмма классов пакета `ui`

Классы пакета с полной сигнатурой методов.

```
┌─────────────────────────────────────────────────────────┐
│              App  extends javafx.application.Application │
├─────────────────────────────────────────────────────────┤
│ + start(primaryStage: Stage): void                      │
│ + main(args: String[]): void                            │
└─────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│              MainController                                  │
├──────────────────────────────────────────────────────────────┤
│  (все @FXML поля и сервисные — см. 3.6)                       │
├──────────────────────────────────────────────────────────────┤
│ + initialize(): void                                          │
│ — onSelectXml(): void                                         │
│ — onSelectOutputDir(): void                                   │
│ — onParse(): void                                             │
│ — onGenerate(): void                                          │
│ — onRunTests(): void                                          │
│ — onRunSelected(): void                                       │
│ + buildTestFilter(entityChecks, typeChecks):                  │
│       static String                                           │
│ — launchRun(testFilter: String): void                         │
│ — onShowHistory(): void                                       │
│ — displayResults(result: TestRunResult): void                 │
│ — getXmlFileName(): String                                    │
│ — log(message: String): void                                  │
│ — showAlert(title: String, content: String): void             │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│        MainController.TestCaseRow  «static inner»             │
├──────────────────────────────────────────────────────────────┤
│ + TestCaseRow(className, methodName, status, duration, message)│
│ + getClassName(): String                                      │
│ + getMethodName(): String                                     │
│ + getStatus(): String                                         │
│ + getDuration(): String                                       │
│ + getMessage(): String                                        │
└──────────────────────────────────────────────────────────────┘
```

**Таблица 3.4. Описание методов класса «App»**

| Название | Параметры                | Возвращаемое значение | Описание                                                                                                                                                            |
| -------- | ------------------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `start`  | `primaryStage: Stage`    | `void`                | Переопределённый метод JavaFX `Application`. Загружает FXML-разметку главного окна (`main.fxml`), создаёт сцену 1000×700, устанавливает заголовок и показывает окно. |
| `main`   | `args: String[]`         | `void`                | Статическая точка входа в программу. Делегирует управление методу `launch(args)`, запускающему JavaFX-runtime.                                                       |

**Таблица 3.5. Описание методов класса «MainController»**

| Название              | Параметры                                    | Возвращаемое значение | Описание                                                                                                                                                                                                  |
| --------------------- | -------------------------------------------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `initialize`          | —                                            | `void`                | Метод инициализации, автоматически вызываемый JavaFX после загрузки FXML и inject. Настраивает `CellValueFactory` колонок, наполняет ComboBox-ы вариантами и проставляет дефолтные значения полей.        |
| `onSelectXml`         | —                                            | `void`                | Обработчик кнопки «Обзор…» для XML-файла. Открывает диалог `FileChooser` с фильтром `*.xml`. Выбранный путь записывается в `xmlPathField`.                                                                |
| `onSelectOutputDir`   | —                                            | `void`                | Обработчик кнопки «Обзор…» для выходного каталога. Открывает диалог `DirectoryChooser`, выбранный путь — в `outputDirField`.                                                                              |
| `onParse`             | —                                            | `void`                | Обработчик кнопки «Разобрать XML». Создаёт `XmlModelParser`, парсит файл, наполняет `entityListView` именами сущностей, активирует кнопку «Сгенерировать тесты». При ошибке показывает `Alert`.            |
| `onGenerate`          | —                                            | `void`                | Обработчик кнопки «Сгенерировать тесты». Создаёт `TestConfig`, заполняет его из полей формы, передаёт в `TestGenerator.generate(model)`. Активирует кнопки «Запустить тесты» и «Выбрать тесты…».          |
| `onRunTests`          | —                                            | `void`                | Обработчик кнопки «Запустить тесты». Делегирует `launchRun(null)` — прогон всех сгенерированных тестов.                                                                                                    |
| `onRunSelected`       | —                                            | `void`                | Обработчик кнопки «Выбрать тесты…». Открывает диалог с чекбоксами сущностей и видов тестов с live-превью Surefire-фильтра, формирует строку `-Dtest=...` и вызывает `launchRun(filter)`.                  |
| `buildTestFilter`     | `entityChecks: List<CheckBox>`, `typeChecks: List<CheckBox>` | `String` | Static. Из отмеченных чекбоксов строит Surefire-фильтр вида `Class1Test,Class2Test#m1+m2`.                                                                                                                |
| `launchRun`           | `testFilter: String`                         | `void`                | Запускает фоновую задачу `Task<TestRunResult>`, внутри которой работает `TestRunner.run(...)`. По завершении — `displayResults(result)` + `reportDao.saveRun(result)`. Управляет состоянием progress-bar. |
| `onShowHistory`       | —                                            | `void`                | Обработчик кнопки «История прогонов». Вызывает `reportDao.getAllRuns()`, выводит сводку в `logArea`, последний прогон — в `resultsTable`.                                                                  |
| `displayResults`      | `result: TestRunResult`                      | `void`                | Приватный метод отображения результатов прогона в `resultsTable`. Раскрашивает статусы OK / FAIL / SKIP и обновляет метки счётчиков «Всего / Успешно / Ошибки».                                            |
| `getXmlFileName`      | —                                            | `String`              | Возвращает имя XML-файла без пути (`File.getName()`) либо строку `"unknown.xml"`, если поле пустое.                                                                                                       |
| `log`                 | `message: String`                            | `void`                | Приватный метод дозаписи строки в `logArea` через `Platform.runLater` (thread-safe).                                                                                                                       |
| `showAlert`           | `title: String`, `content: String`           | `void`                | Приватный метод показа модального диалога `Alert.AlertType.ERROR` через `Platform.runLater`.                                                                                                              |

**Таблица 3.6. Описание методов класса «MainController.TestCaseRow»**

| Название         | Параметры                                                                          | Возвращаемое значение | Описание                                                                              |
| ---------------- | ---------------------------------------------------------------------------------- | --------------------- | ------------------------------------------------------------------------------------- |
| `TestCaseRow`    | `className, methodName, status, duration, message: String`                          | (конструктор)         | Создаёт неизменяемый объект-строку, инициализируя все пять `final`-полей.             |
| `getClassName`   | —                                                                                  | `String`              | Возвращает имя тестового класса. Используется `PropertyValueFactory` для колонки.    |
| `getMethodName`  | —                                                                                  | `String`              | Возвращает имя тест-метода.                                                          |
| `getStatus`      | —                                                                                  | `String`              | Возвращает статус (`OK` / `FAIL` / `SKIP`).                                          |
| `getDuration`    | —                                                                                  | `String`              | Возвращает длительность выполнения (с суффиксом «мс»).                               |
| `getMessage`     | —                                                                                  | `String`              | Возвращает сообщение об ошибке либо подтверждение успешного прохождения.             |

### 3.8. Особенности GUI-части

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

### 4.3. Диаграммы последовательностей пакета `model`

> Пакет состоит почти полностью из POJO (anemic domain model) — у объектов почти нет поведения. Реальная динамика — это **наполнение объектов** парсером и **классификация** через `EntityClassifier`. Эти два сценария и показаны ниже.

#### 4.3.1. Нормальный ход событий — построение и классификация

```
XmlModelParser   AppModel    EntityObject   PropertyGroup    Property    EntityClassifier
      │             │              │              │              │              │
      │ new()       │              │              │              │              │
      ├────────────►│              │              │              │              │
      │ new()       │              │              │              │              │
      ├─────────────┼─────────────►│              │              │              │
      │ setName(...)│              │              │              │              │
      ├─────────────┼─────────────►│              │              │              │
      │             │              │ new()        │              │              │
      ├─────────────┼──────────────┼─────────────►│              │              │
      │             │              │              │ new()        │              │
      ├─────────────┼──────────────┼──────────────┼─────────────►│              │
      │             │              │              │ getProperties()             │
      │             │              │              │  .add(prop)  │              │
      │             │              │ getPropertyGroups()        │              │
      │             │              │  .add(pg)    │              │              │
      │             │ getEntities().add(entity)   │              │              │
      │             │              │              │              │              │
                                                                                │
─── позже, в TestGenerator ───                                                  │
                                                                                │
TestGenerator     EntityClassifier            Classification                    │
      │                  │                          │                           │
      │ classify(entity, model)                     │                           │
      ├─────────────────►│                          │                           │
      │                  │ — findParentGrid(entity, model)                      │
      │                  │ — isReferenceDictionary(entity, model)               │
      │                  │ — isFkTargetOnly(entity, model)                      │
      │                  │ new Classification(kind, reason)                     │
      │                  ├─────────────────────────►│                           │
      │ Classification   │                          │                           │
      │◄─────────────────┤                          │                           │
      │ if (kind != PRIMARY) skip — иначе генерировать                          │
```

#### 4.3.2. Прерывание пользователем

> Не применимо. POJO-классы пакета не имеют собственных точек ввода от пользователя — они наполняются автоматически парсером. Прерывание пользователем возможно только в вызывающем пакете `ui` (см. 3.4.2) либо в `parser` (см. 5.x.2).

#### 4.3.3. Прерывание системой — NPE при доступе к неполным POJO

```
TestGenerator         EntityObject       PropertyGroup       Property
      │                    │                   │                  │
      │ getFormView()      │                   │                  │
      ├───────────────────►│                   │                  │
      │ propertyGroups.stream().filter(...).findFirst().orElse(null)
      │                    │                   │                  │
      │ null ⚠ (в XML нет PG с typeLink="P")   │                  │
      │◄───────────────────┤                   │                  │
      │ formView.getProperties()  ⚠ NPE        │                  │
      │ TestGenerator оборачивает в try/catch — лог + пропуск     │
      │ сущности без падения программы         │                  │
```

### 4.4. Диаграмма кооперации пакета `model`

Кооперация показывает **иерархию владения** (композиция ♦) внутри `AppModel` и навигацию по GUID-ссылкам.

```
                    1: setEntities(List)
   ┌──────────┐ ◄─────────────────────────────  ┌────────────────┐
   │ AppModel │                                  │ XmlModelParser │
   │          │                                  └────────────────┘
   │ entities │ ♦  2: add(entity)
   │   List<> │ ───────────────────► ┌──────────────┐
   │          │                       │ EntityObject │
   │ searches │ ♦                     │   guid       │
   │   List<> │ ──────► ┌────────┐    │   name       │ ♦  3: add(pg)
   └──────────┘         │ Search │    │              │ ─────────► ┌────────────────┐
                        │  guid  │    │ associations │            │ PropertyGroup  │
                        │  params│ ♦  │   List<>     │  ♦         │  guid          │
                        │   ▼    │    │ propertyGroups│ ◄─ 4: navigate by GUID
                        │ SearchParam │  List<>      │            │ properties     │ ♦
                        │   result    │              │            │   List<>       │ ───► ┌──────────┐
                        │     │       │              │ ◄─5: getOperation()        │      │ Property │
                        │     ▼       └──────────────┘            │ operation: Op  │      └──────────┘
                        │ SearchResult                            └────────────────┘
                        │     │
                        │     ▼
                        │ SearchResultProperty
                        └──────────────────────

   ─── параллельно ───

   ┌──────────────────┐  6: classify(entity, model)
   │ EntityClassifier │ ◄─────────────────  ┌───────────────┐
   │   (static)       │                      │ TestGenerator │
   └────────┬─────────┘                      └───────────────┘
            │ 7: new
            ▼
   ┌────────────────┐
   │ Classification │
   │   kind         │ ──► EntityKind (enum)
   │   reason       │
   │   parentEntity │ ──► EntityObject   (агрегация по навигации)
   │   parentGrid   │ ──► PropertyGroup  (агрегация по навигации)
   └────────────────┘
```

### 4.5. Уточнённая диаграмма классов пакета `model`

```
┌─────────────────────────────┐       ┌─────────────────────────────┐
│        AppModel             │       │       EntityObject          │
├─────────────────────────────┤       ├─────────────────────────────┤
│ — categoryName: String      │       │ — guid:            String   │
│ — guid:         String      │  ♦──► │ — name:            String   │
│ — entities:     List<EntityObject>  │ — keyName:         String   │
│ — searches:     List<Search>│       │ — featureName:     String   │
└─────────────────────────────┘       │ — nameValueMethod: String   │
                                       │ — associations:    List<Association>
                                       │ — propertyGroups:  List<PropertyGroup>
                                       └──────────────┬──────────────┘
                                                      │ ♦
                                                      ▼
┌─────────────────────────────┐       ┌─────────────────────────────┐
│       PropertyGroup         │       │        Property             │
├─────────────────────────────┤       ├─────────────────────────────┤
│ — guid:        String       │       │ — guid:        String       │
│ — name:        String       │  ♦──► │ — name:        String       │
│ — stereoType:  String       │       │ — stereoType:  String       │
│ — dmodule:     String       │       │ — dmodule:     String       │
│ — typeLink:    String       │       │ — attrName:    String       │
│ — orderNumber: int          │       │ — tableName:   String       │
│ — flagDisplay: boolean      │       │ — attrType:    AttrType     │
│ — properties:  List<Property>       │ — required:    boolean      │
│ — operation:   Operation    │       │ — mask:        String       │
└─────────────────────────────┘       │ — orderNumber: int          │
       │ ♦                            │ — flagDisplay: boolean      │
       ▼                              │ — defValueSource: String    │
┌─────────────────────────────┐       │ — comment:     String       │
│       Operation             │       └─────────────────────────────┘
├─────────────────────────────┤
│ — guid:            String   │       ┌─────────────────────────────┐
│ — operationMethod: String   │       │       Association           │
│ — operationModule: String   │       ├─────────────────────────────┤
│ — params:    List<OperationParam>  │ — guid:        String       │
│ — modifiers: List<Modifier> │       │ — roleA, roleB: String      │
└─────────────────────────────┘       │ — roleACaption: String      │
                                       │ — featureName, associationId│
┌─────────────────────────────┐       │ — associateItemName/Guid    │
│       Search                │       │ — searchGuid:  String       │
├─────────────────────────────┤       │ — flagDisplay: boolean      │
│ — guid, name: String        │       │ — addFromTree: boolean      │
│ — searchObjectGuid: String  │       └─────────────────────────────┘
│ — query: String             │
│ — minParamCount: int        │       ┌─────────────────────────────┐
│ — params: List<SearchParam> │       │       TestRunResult         │
│ — result: SearchResult      │       ├─────────────────────────────┤
└─────────────────────────────┘       │ — totalTests, passed, failed, skipped: int │
                                       │ — runTimestamp: LocalDateTime│
┌─────────────────────────────┐       │ — durationMs: long          │
│   EntityClassifier (static) │       │ — xmlFileName, baseUrl: String│
│   ─ Classification (inner)  │       │ — results: List<TestCaseResult>│
│       kind: EntityKind      │       │ — mavenOutput: String       │
│       reason: String        │       └─────────────────────────────┘
│       parentEntity:EntityObject
│       parentGrid: PropertyGroup     ┌─────────────────────────────┐
└─────────────────────────────┘       │      TestCaseResult         │
                                       ├─────────────────────────────┤
                                       │ — className, methodName: String│
                                       │ — passed, skipped: boolean  │
                                       │ — failureMessage: String    │
                                       │ — durationMs: long          │
                                       │ — stdOut: String            │
                                       │ — searchParams: Map<String,String>│
                                       │ — screenshots: List<String> │
                                       │ — steps: List<StepTiming>   │
                                       └─────────────────────────────┘
```

### 4.6. Детальная диаграмма классов пакета `model`

Все классы — POJO с конструктором по умолчанию + парами `getX/setX`. Ниже только нетривиальные методы.

```
┌─────────────────────────────────────────────────────────┐
│              AppModel                                   │
├─────────────────────────────────────────────────────────┤
│ + getCategoryName(): String / setCategoryName(...)      │
│ + getGuid(): String          / setGuid(...)             │
│ + getEntities(): List<EntityObject> / setEntities(...)  │
│ + getSearches(): List<Search>       / setSearches(...)  │
│ + findEntityByGuid(guid: String): EntityObject          │
│ + getSubsystemNameFromCategory(): String                │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              EntityObject                               │
├─────────────────────────────────────────────────────────┤
│ + getXxx / setXxx для всех 7 полей                       │
│ + hasCrudOperations(): boolean                          │
│ + getFormView(): PropertyGroup                          │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              PropertyGroup                              │
├─────────────────────────────────────────────────────────┤
│ + getXxx / setXxx для всех 9 полей                       │
│ + isFormView(): boolean   // "P".equals(typeLink)       │
│ + isGridView(): boolean   // "Grid".equals(stereoType)  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              EntityClassifier (final, utility)          │
├─────────────────────────────────────────────────────────┤
│ + classify(entity, model): static Classification        │
│ — findParentGrid(entity, model): Classification         │
│ — isFkTargetOnly(entity, model): String                 │
│ — isChildOf(child, parent): boolean                     │
│ — isReferenceDictionary(entity, model): String          │
│ — isTrivialResult(search): boolean                      │
│ — nameStemsMatch(a, b): boolean                         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│       EntityClassifier.Classification                   │
├─────────────────────────────────────────────────────────┤
│ + kind:        final EntityKind                         │
│ + reason:      final String                             │
│ + parentEntity: final EntityObject                      │
│ + parentGrid:  final PropertyGroup                      │
│ + Classification(kind, reason)                          │
│ + Classification(kind, reason, parentEntity, parentGrid)│
└─────────────────────────────────────────────────────────┘
```

#### Описание нетривиальных методов пакета `model`

| Класс           | Метод                                  | Описание                                                                   |
| --------------- | -------------------------------------- | -------------------------------------------------------------------------- |
| `AppModel`      | `findEntityByGuid(guid)`               | Линейный поиск сущности по GUID, `null` если нет.                          |
| `AppModel`      | `getSubsystemNameFromCategory()`       | Из `categoryName` вида `"Logical View::ГСК"` возвращает `"ГСК"`.           |
| `EntityObject`  | `hasCrudOperations()`                  | `true`, если хотя бы один `PropertyGroup.operation.modifiers` непуст.       |
| `EntityObject`  | `getFormView()`                        | Первый `PropertyGroup` с `isFormView()`; `null` если нет.                  |
| `PropertyGroup` | `isFormView()` / `isGridView()`        | Проверка значения `typeLink="P"` / `stereoType="Grid"`.                    |
| `EntityClassifier` | `classify(entity, model)`           | Возвращает `Classification` с `EntityKind`. Применяет цепочку правил: парент-грид → справочник → FK-цель → иначе PRIMARY. |
| `EntityClassifier` | `findParentGrid`                    | Ищет в других сущностях `PropertyGroup` со `stereoType="Grid"`, чьё имя совпадает по корням слов. |
| `EntityClassifier` | `isFkTargetOnly`                    | Сущность считается FK-целью, если на неё ссылаются только как на пикер (нет `addFromTree` и `flag_display=0`). |
| `EntityClassifier` | `isReferenceDictionary`             | Если `featureName` начинается с `V_S_` или все поиски имеют пустые параметры и тривиальный результат. |
| `EntityClassifier` | `nameStemsMatch(a, b)`              | Сравнение слов по началу (имена «Адрес» и «Адресов» считаются одним стволом). |

### 4.7. Особенности модели

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

### 5.4. Диаграммы последовательностей пакета `parser`

#### 5.4.1. Нормальный ход событий

```
MainController   XmlModelParser   StAX Reader   EntityParser  PropertyGroupParser  SearchParser   AppModel
       │               │                │             │              │                   │             │
       │ parse(file)   │                │             │              │                   │             │
       ├──────────────►│                │             │              │                   │             │
       │               │ createXMLStreamReader(fis)  │              │                   │             │
       │               ├───────────────►│             │              │                   │             │
       │               │  reader        │             │              │                   │             │
       │               │◄───────────────┤             │              │                   │             │
       │               │ new AppModel() │             │              │                   │             │
       │               ├──────────────────────────────────────────────────────────────────────────────►│
       │               │                │             │              │                   │             │
       │               │ ─ loop reader.hasNext() ─                                                     │
       │               │   next()       │             │              │                   │             │
       │               │ if "Category"  │             │              │                   │             │
       │               │   setCategoryName/Guid       │              │                   │             │
       │               ├──────────────────────────────────────────────────────────────────────────────►│
       │               │ if "Object"    │             │              │                   │             │
       │               │   parseObject(reader)        │              │                   │             │
       │               ├────────────────┼───────────►│              │                   │             │
       │               │                │             │ ─ loop ─     │                   │             │
       │               │                │             │ pgParser.parsePropertyGroup(reader)            │
       │               │                │             ├─────────────►│                   │             │
       │               │                │             │              │  parseProperty()  │             │
       │               │                │             │              │  parseOperation() │             │
       │               │                │             │ PropertyGroup│                   │             │
       │               │                │             │◄─────────────┤                   │             │
       │               │                │             │ entity.add(pg)                   │             │
       │               │                │             │ parseAssociation(reader)         │             │
       │               │   EntityObject │             │              │                   │             │
       │               │◄───────────────┼─────────────┤              │                   │             │
       │               │ model.getEntities().add(entity)             │                   │             │
       │               ├──────────────────────────────────────────────────────────────────────────────►│
       │               │ if "Searches"  │             │              │                   │             │
       │               │   searchParser.parseSearches(reader)        │                   │             │
       │               ├────────────────┼─────────────┼──────────────┼──────────────────►│             │
       │               │                │             │              │ List<Search>      │             │
       │               │◄───────────────┼─────────────┼──────────────┼───────────────────┤             │
       │               │ model.setSearches(list)                     │                   │             │
       │               ├──────────────────────────────────────────────────────────────────────────────►│
       │               │ reader.close() │             │              │                   │             │
       │               ├───────────────►│             │              │                   │             │
       │  AppModel     │                │             │              │                   │             │
       │◄──────────────┤                │             │              │                   │             │
```

#### 5.4.2. Прерывание пользователем — «отмена ДО вызова парсера»

> Парсер синхронный и не имеет своих UI-элементов. Прерывание пользователем возможно только на уровне `ui` (отмена `FileChooser`), которая выливается в **отсутствие вызова** `parse()`.

```
Пользователь    MainController      FileChooser     XmlModelParser
     │                │                  │                  │
   click "Обзор..."   │                  │                  │
     ├───────────────►│                  │                  │
     │                │ showOpenDialog() │                  │
     │                ├─────────────────►│                  │
     │  Esc / Cancel  │                  │                  │
     ├──────────────────────────────────►│                  │
     │                │   null           │                  │
     │                │◄─────────────────┤                  │
     │                │ if (file != null) ╳                 │
     │                │ parse() НЕ ВЫЗЫВАЕТСЯ               │
     │  состояние     │                  │                  │
     │  не меняется   │                  │                  │
     │◄───────────────┤                  │                  │
```

#### 5.4.3. Прерывание системой

**Сценарий А — невалидный XML (StAX парсер бросает исключение):**

```
MainController   XmlModelParser    StAX Reader    EntityParser
       │               │                │              │
       │ parse(file)   │                │              │
       ├──────────────►│                │              │
       │               │ createXMLStreamReader        │
       │               ├───────────────►│              │
       │               │ next()         │              │
       │               ├───────────────►│              │
       │               │ if "Object" parseObject(reader)
       │               ├────────────────┼─────────────►│
       │               │                │ next()       │
       │               │                ├──────────────┤
       │               │                │ ⚠ XMLStreamException
       │               │                │  (malformed tag, unclosed element)
       │               │                │◄──────────────
       │               │   throws       │              │
       │               │◄───────────────┼──────────────┤
       │               │ catch (IOException | XMLStreamException e)
       │               │ throw new ParserException(...)│
       │  ParserException                │              │
       │◄──────────────┤                │              │
       │ catch → showAlert("Ошибка парсинга", e.getMessage())
       │ log + UI остаётся в состоянии «до парсинга»  │
```

**Сценарий Б — нечитаемый файл (`IOException`):**

```
MainController   XmlModelParser   FileInputStream
       │               │                  │
       │ parse(file)   │                  │
       ├──────────────►│                  │
       │               │ new FileInputStream(file)
       │               ├─────────────────►│
       │               │ ⚠ IOException    │
       │               │  (Permission denied, file deleted)
       │               │◄─────────────────┤
       │               │ catch → throw new ParserException(...)
       │  ParserException                 │
       │◄──────────────┤                  │
```

### 5.5. Диаграмма кооперации пакета `parser`

```
                  1: parse(file)
   ┌────────────────┐ ───────────────► ┌──────────────────┐
   │ MainController │                   │ XmlModelParser   │
   └────────────────┘                   │   (фасад)         │
                                         └──┬──────────┬────┘
                                            │          │
                                       2:parseObject(r)│ 4:parseSearches(r)
                                            ▼          ▼
                                   ┌──────────────┐  ┌──────────────┐
                                   │ EntityParser │  │ SearchParser │
                                   └──────┬───────┘  └──────┬───────┘
                                          │                  │
                                3: parsePropertyGroup(r)     │
                                          ▼                  │
                                 ┌──────────────────────┐    │
                                 │ PropertyGroupParser  │    │
                                 └──────────────────────┘    │
                                          │                  │
                       ┌──────────────────┼──────────────────┘
                       │ 5: attr(r, "name"), parseInt(...), skipToEnd(r)
                       ▼
                ┌─────────────┐
                │ StaxUtils   │
                │  (static)   │
                └─────────────┘
                       │ 6: ns equality check
                       ▼
                ┌──────────────┐
                │ XmlNamespaces│
                │ NS_E, NS_E3  │
                └──────────────┘
```

### 5.6. Уточнённая диаграмма классов пакета `parser`

```
┌───────────────────────────────────────┐
│        XmlModelParser  «facade»       │
├───────────────────────────────────────┤
│ — entityParser:  final EntityParser   │
│ — searchParser:  final SearchParser   │
└───────────────────────────────────────┘
         ♦                  ♦
         ▼                  ▼
┌──────────────────┐    ┌──────────────────┐
│ EntityParser     │    │ SearchParser     │
├──────────────────┤    ├──────────────────┤
│ — pgParser:      │    │ (нет полей)      │
│   final          │    └──────────────────┘
│   PropertyGroupParser │
└──────────────────┘
         ♦
         ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│PropertyGroupParser│   │ StaxUtils (util) │    │XmlNamespaces(const)│
├──────────────────┤    ├──────────────────┤    ├──────────────────┤
│ (нет полей)      │    │ (private ctor)   │    │ + NS_E:  String   │
└──────────────────┘    └──────────────────┘    │ + NS_E3: String   │
                                                 │ + NS_MD: String   │
                                                 └──────────────────┘
```

### 5.7. Детальная диаграмма классов пакета `parser`

```
┌─────────────────────────────────────────────────────────────────┐
│                  XmlModelParser                                 │
├─────────────────────────────────────────────────────────────────┤
│ — entityParser: final EntityParser                              │
│ — searchParser: final SearchParser                              │
├─────────────────────────────────────────────────────────────────┤
│ + XmlModelParser()                                              │
│ + XmlModelParser(EntityParser, SearchParser)                    │
│ + parse(xmlFile: File): AppModel throws ParserException         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  EntityParser                                   │
├─────────────────────────────────────────────────────────────────┤
│ — pgParser: final PropertyGroupParser                           │
├─────────────────────────────────────────────────────────────────┤
│ + EntityParser()                                                │
│ + EntityParser(PropertyGroupParser)                             │
│ + parseObject(reader: XMLStreamReader): EntityObject            │
│ — parseAssociation(reader: XMLStreamReader): Association        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  PropertyGroupParser                            │
├─────────────────────────────────────────────────────────────────┤
│ + parsePropertyGroup(reader: XMLStreamReader): PropertyGroup    │
│ — parseProperty(reader: XMLStreamReader): Property              │
│ — parseOperation(reader: XMLStreamReader): Operation            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  SearchParser                                   │
├─────────────────────────────────────────────────────────────────┤
│ + parseSearches(reader: XMLStreamReader): List<Search>          │
│ — parseSingleSearch(reader: XMLStreamReader): Search            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              StaxUtils   «final utility»                        │
├─────────────────────────────────────────────────────────────────┤
│ + attr(reader: XMLStreamReader, name: String): static String    │
│ + parseInt(value: String): static int                           │
│ + skipToEnd(reader: XMLStreamReader): static void               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│              XmlNamespaces   «final constants»                  │
├─────────────────────────────────────────────────────────────────┤
│ + NS_E:  static final String = "uuid:EDDB..."                   │
│ + NS_E3: static final String = "uuid:EF68..."                   │
│ + NS_MD: static final String = "urn:ruitsol-ru:E3"              │
└─────────────────────────────────────────────────────────────────┘
```

#### Описание методов пакета `parser`

| Класс                  | Метод                          | Описание                                                                            |
| ---------------------- | ------------------------------ | ----------------------------------------------------------------------------------- |
| `XmlModelParser`       | `parse(File)`                  | Открывает StAX-reader, в цикле диспетчеризует `<Category>`, `<Object>`, `<Searches>`, наполняет `AppModel`. Оборачивает `IOException`/`XMLStreamException` в `ParserException`. |
| `EntityParser`         | `parseObject(reader)`          | Читает `<Object>` до закрывающего тега; делегирует `<Properties>` в `PropertyGroupParser` и собирает `<AssociationObjectA>` через `parseAssociation`. |
| `EntityParser`         | `parseAssociation(reader)`     | Собирает `Association` с под-элементами `<Qualifier>` и `<AssociateItem>`.          |
| `PropertyGroupParser`  | `parsePropertyGroup(reader)`   | Читает `<Properties>`; вложенно вызывает `parseProperty` и `parseOperation`.        |
| `PropertyGroupParser`  | `parseProperty(reader)`        | Собирает `Property` из атрибутов; `skipToEnd` уводит курсор на закрывающий тег.     |
| `PropertyGroupParser`  | `parseOperation(reader)`       | Собирает `Operation` + вложенные `<OperationParam>` и `<Modifier>` (с `ModifyType.fromCode`). |
| `SearchParser`         | `parseSearches(reader)`        | Перебирает все `<Search>` под `<Searches>` и возвращает `List<Search>`.             |
| `SearchParser`         | `parseSingleSearch(reader)`    | Собирает `Search` + вложенные `<SearchQuery>`, `<SearchParams>`, `<SearchParam>`, `<SearchResult>`, `<SearchResultProperty>`. |
| `StaxUtils`            | `attr(reader, name)`           | Безопасно читает атрибут (если нет — возвращает `""`).                              |
| `StaxUtils`            | `parseInt(value)`              | Безопасно парсит int, при пустоте/ошибке — `0`.                                     |
| `StaxUtils`            | `skipToEnd(reader)`            | Прокручивает курсор до закрытия текущего элемента (счётчик depth).                  |

### 5.8. Особенности

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

### 6.3. Диаграммы последовательностей пакета `common`

#### 6.3.1. Нормальный ход событий — построение Java-файла

```
TestClassWriter   JavaFileWriter   StringBuilder   Files.write
       │                │                │              │
       │ new()          │                │              │
       ├───────────────►│                │              │
       │                │ new()          │              │
       │                ├───────────────►│              │
       │ writeLine("package ...")        │              │
       ├───────────────►│ append(indent) │              │
       │                ├───────────────►│              │
       │ openBlock("class X")            │              │
       ├───────────────►│ append + indent++              │
       │ writeLine("...")                │              │
       ├───────────────►│ append         │              │
       │ closeBlock()   │ indent-- + "}"                │
       ├───────────────►│                │              │
       │ writeToFile(dir, fileName)      │              │
       ├───────────────►│ Files.createDirectories(dir) │
       │                ├──────────────────────────────►│
       │                │ Files.newBufferedWriter      │
       │                ├──────────────────────────────►│
       │                │ pw.write(sb.toString())      │
       │                ├──────────────────────────────►│
       │  void          │                │              │
       │◄───────────────┤                │              │
```

#### 6.3.2. Прерывание пользователем

> Не применимо. Утилиты пакета не имеют UI и не получают прямых вводов от пользователя.

#### 6.3.3. Прерывание системой — `IOException` при записи файла

```
TestClassWriter   JavaFileWriter   Files.write
       │                │                  │
       │ writeToFile(dir, "FooTest.java")  │
       ├───────────────►│                  │
       │                │ Files.createDirectories(dir)
       │                ├─────────────────►│
       │                │  ⚠ AccessDeniedException
       │                │  (нет прав на запись)
       │                │◄─────────────────┤
       │  IOException   │                  │
       │◄───────────────┤                  │
       │ throws вверх в TestGenerator      │
       │ → MainController.onGenerate catch │
       │ → showAlert("Ошибка генерации", e.getMessage())
```

### 6.4. Уточнённая диаграмма классов пакета `common`

```
┌─────────────────────────────┐    ┌──────────────────────────┐    ┌─────────────────────────────┐
│ Transliterator  «utility»   │    │ JavaFileWriter           │    │ ParserException             │
├─────────────────────────────┤    ├──────────────────────────┤    │ extends Exception           │
│ — MAPPING: static final     │    │ — sb: final StringBuilder│    ├─────────────────────────────┤
│   Map<Character,String>     │    │ — indentLevel: int       │    │ (наследует поля Exception)  │
│ — private constructor       │    │ — INDENT: static "    "  │    └─────────────────────────────┘
└─────────────────────────────┘    └──────────────────────────┘
```

### 6.5. Детальная диаграмма классов пакета `common`

```
┌─────────────────────────────────────────────────────────┐
│              Transliterator (final utility)             │
├─────────────────────────────────────────────────────────┤
│ + toClassName(russianName: String): static String       │
│ + toMethodName(russianName: String): static String      │
│ + toFieldName(attrName: String): static String          │
│ — transliterate(word: String): static String            │
│ — snakeToCamel(snake: String): static String            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              JavaFileWriter                             │
├─────────────────────────────────────────────────────────┤
│ + writeLine(line: String): JavaFileWriter (fluent)      │
│ + writeLine(): JavaFileWriter                           │
│ + openBlock(header: String): JavaFileWriter             │
│ + closeBlock(): JavaFileWriter                          │
│ + indent(): JavaFileWriter                              │
│ + unindent(): JavaFileWriter                            │
│ + writeToFile(dir: Path, fileName: String): void        │
│ + toString(): String                                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│      ParserException  extends Exception                 │
├─────────────────────────────────────────────────────────┤
│ + ParserException(message: String)                      │
│ + ParserException(message: String, cause: Throwable)    │
└─────────────────────────────────────────────────────────┘
```

#### Описание методов пакета `common`

| Класс             | Метод                          | Описание                                                                          |
| ----------------- | ------------------------------ | --------------------------------------------------------------------------------- |
| `Transliterator`  | `toClassName(russianName)`     | Cyrillic → PascalCase. «ГСК/ОГСК» → `GskOgsk`.                                    |
| `Transliterator`  | `toMethodName(russianName)`    | То же, но первая буква строчная.                                                  |
| `Transliterator`  | `toFieldName(attrName)`        | Если уже Latin (`KEY_GB_SOCIETY`) — snake → camelCase, иначе `toMethodName`.      |
| `JavaFileWriter`  | `writeLine(line)`              | Дописывает строку с текущим отступом + `\n`. Fluent.                              |
| `JavaFileWriter`  | `openBlock(header)`            | `writeLine(header + " {")` и `indentLevel++`.                                      |
| `JavaFileWriter`  | `closeBlock()`                 | `indentLevel--` и `writeLine("}")`.                                                |
| `JavaFileWriter`  | `writeToFile(dir, fileName)`   | `Files.createDirectories(dir)` + запись UTF-8.                                     |
| `ParserException` | конструкторы                  | Проброс сообщения и/или причины.                                                   |

### 6.6. Особенности

- `Transliterator` детерминирован: одно и то же русское имя всегда даёт одно и то же Java-имя — это важно для воспроизводимости имён сгенерированных классов.
- `JavaFileWriter` — минималистичный, без AST: 60 строк, ровно столько, сколько нужно генератору.
- Диаграмма кооперации **не требуется**: классы пакета не взаимодействуют друг с другом.

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

### 7.3. Диаграммы последовательностей пакета `data`

#### 7.3.1. Нормальный ход событий — сохранение прогона + выборка истории

```
MainController   ReportDao    JDBC Connection    test_run    test_case
       │             │              │                │            │
       │ saveRun(result)            │                │            │
       ├────────────►│              │                │            │
       │             │ getConnection()              │            │
       │             ├─────────────►│                │            │
       │             │ setAutoCommit(false)         │            │
       │             ├─────────────►│                │            │
       │             │ INSERT test_run (run_date,...│            │
       │             │  + RETURN_GENERATED_KEYS)    │            │
       │             ├──────────────┼───────────────►│            │
       │             │ runId        │                │            │
       │             │◄─────────────┼────────────────┤            │
       │             │ for each TestCaseResult: addBatch INSERT  │
       │             ├──────────────┼────────────────┼───────────►│
       │             │ executeBatch │                │            │
       │             ├──────────────┼────────────────┼───────────►│
       │             │ commit()     │                │            │
       │             ├─────────────►│                │            │
       │  void       │              │                │            │
       │◄────────────┤              │                │            │
       │             │              │                │            │
       │ onShowHistory()            │                │            │
       │ getAllRuns()│              │                │            │
       ├────────────►│              │                │            │
       │             │ SELECT * FROM test_run ORDER BY id DESC  │
       │             ├──────────────┼───────────────►│            │
       │             │ ResultSet    │                │            │
       │             │◄─────────────┼────────────────┤            │
       │             │ for each run: getCaseResults(conn, runId) │
       │             │              │                │            │
       │             │ SELECT * FROM test_case WHERE run_id = ?  │
       │             ├──────────────┼────────────────┼───────────►│
       │             │ ResultSet    │                │            │
       │             │◄─────────────┼────────────────┼────────────┤
       │ List<TestRunResult>        │                │            │
       │◄────────────┤              │                │            │
```

#### 7.3.2. Прерывание пользователем

> Не применимо. DAO синхронный, вызывается только из background-задачи (`Task.onSucceeded`) после завершения прогона. Кнопки UI его не отменяют.

#### 7.3.3. Прерывание системой — `SQLException` (диск переполнен / БД залочена)

```
MainController   ReportDao    JDBC Connection
       │             │              │
       │ saveRun(result)            │
       ├────────────►│              │
       │             │ setAutoCommit(false)
       │             ├─────────────►│
       │             │ INSERT test_run
       │             ├─────────────►│
       │             │  ⚠ SQLException
       │             │  "database is locked"
       │             │  или "disk full"
       │             │◄─────────────┤
       │             │ catch (SQLException e)
       │             │ System.err.println("Failed to save test run: " + e.getMessage())
       │             │ commit() НЕ вызван → транзакция откатывается на close
       │  void       │              │
       │◄────────────┤              │
       │ ⚠ результаты прогона ОТОБРАЖЕНЫ в UI, но НЕ сохранены в историю
```

### 7.4. Диаграмма кооперации пакета `data`

Кооперация мини: 1 актор-DAO + JDBC. Показана типовая транзакция «saveRun».

```
   ┌────────────────┐  1: saveRun(result)
   │ MainController │ ───────────────────► ┌────────────┐
   └────────────────┘                       │ ReportDao  │
                                            └─────┬──────┘
                                                  │ 2: getConnection()
                                                  ▼
                                          ┌──────────────┐
                                          │  Connection  │
                                          │   (SQLite)   │
                                          └──────┬───────┘
                                                  │ 3: setAutoCommit(false)
                                                  │ 4: prepareStatement(INSERT test_run)
                                                  │ 5: executeUpdate()
                                                  │ 6: getGeneratedKeys() → runId
                                                  │ 7: prepareStatement(INSERT test_case)
                                                  │ 8: addBatch * N
                                                  │ 9: executeBatch()
                                                  │ 10: commit()
                                                  ▼
                                          ┌──────────────┐
                                          │  SQLite файл │
                                          │ autotestgen.db│
                                          └──────────────┘
```

### 7.5. Уточнённая диаграмма классов пакета `data`

```
┌─────────────────────────────────────────────────────────┐
│                  ReportDao                              │
├─────────────────────────────────────────────────────────┤
│ — DB_URL:   static final String = "jdbc:sqlite:autotestgen.db"
│ — DT_FORMAT: static final DateTimeFormatter             │
└─────────────────────────────────────────────────────────┘
```

### 7.6. Детальная диаграмма классов пакета `data`

```
┌─────────────────────────────────────────────────────────┐
│                  ReportDao                              │
├─────────────────────────────────────────────────────────┤
│ — DB_URL: static final String                           │
│ — DT_FORMAT: static final DateTimeFormatter             │
├─────────────────────────────────────────────────────────┤
│ + ReportDao()                                           │
│ — initDatabase(): void                                  │
│ + saveRun(result: TestRunResult): void                  │
│ + getAllRuns(): List<TestRunResult>                     │
│ — getCaseResults(conn: Connection, runId: long):        │
│           List<TestCaseResult>  throws SQLException     │
│ — getConnection(): Connection  throws SQLException      │
└─────────────────────────────────────────────────────────┘
```

#### Описание методов пакета `data`

| Метод                                  | Описание                                                                                  |
| -------------------------------------- | ----------------------------------------------------------------------------------------- |
| `ReportDao()` (конструктор)            | Вызывает `initDatabase()` — DDL создаёт таблицы, если их нет.                              |
| `initDatabase()`                       | `CREATE TABLE IF NOT EXISTS test_run` и `test_case`. Ошибки логируются в `stderr`, не пробрасываются. |
| `saveRun(result)`                      | Транзакция: INSERT в `test_run` (с `RETURN_GENERATED_KEYS`), затем batch INSERT в `test_case`. `commit()` в конце. `SQLException` ловится — пишется в `stderr`. |
| `getAllRuns()`                         | `SELECT * FROM test_run ORDER BY id DESC`, для каждого вызывает `getCaseResults` (классический N+1). |
| `getCaseResults(conn, runId)`          | `SELECT * FROM test_case WHERE run_id = ? ORDER BY id`. Маппинг в `TestCaseResult`.       |
| `getConnection()`                      | `DriverManager.getConnection(DB_URL)`. JDBC создаёт `autotestgen.db` рядом с .jar.        |

### 7.7. Особенности

- **Init-on-construct**: `initDatabase()` создаёт таблицы при необходимости — DAO самодостаточен.
- **Транзакция на запись прогона**: `setAutoCommit(false)` + `commit()` — все кейсы пишутся атомарно.
- **Graceful degradation при сбое**: `SQLException` ловится и логируется в `stderr`, программа продолжает работу. Пользователь увидит результаты, но в истории прогона не будет.
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

### 8.4. Диаграммы последовательностей пакета `generator`

#### 8.4.1. Нормальный ход событий — генерация + запуск

```
MainController  TestGenerator  PageObjectWriter  TestClassWriter  TestRunner  ProcessBuilder  Surefire
       │              │                │                │              │              │            │
       │ new TestConfig() + setters    │                │              │              │            │
       │ new TestGenerator(config)     │                │              │              │            │
       │ generate(model)               │                │              │              │            │
       ├─────────────►│                │                │              │              │            │
       │              │ writePomXml(), writeBaseTest()                │              │            │
       │              │ for each EntityObject e:                      │              │            │
       │              │   classify(e) → PRIMARY/CHILD/REFERENCE       │              │            │
       │              │   if PRIMARY:  │                │              │              │            │
       │              │     PageObjectWriter.write(e, config)         │              │            │
       │              ├───────────────►│                │              │              │            │
       │              │                │ JavaFileWriter → Files.write │              │            │
       │              │     TestClassWriter.write(e, config)          │              │            │
       │              ├────────────────┼───────────────►│              │              │            │
       │              │                │                │ JavaFileWriter → Files.write             │
       │              │                │                │              │              │            │
       │  "Тесты сгенерированы"        │                │              │              │            │
       │◄─────────────┤                │                │              │              │            │
       │                                                                │              │            │
       │ launchRun(filter) → Task в фоне                                │              │            │
       │ TestRunner.run(outputDir, xml, url, filter, fast)              │              │            │
       │                                                ├─────────────►│              │            │
       │                                                                │ ProcessBuilder("mvn test")│
       │                                                                ├─────────────►│            │
       │                                                                │              │ запуск forkов
       │                                                                │              ├───────────►│
       │                                                                │  stdout      │            │
       │                                                                │◄─────────────┤            │
       │                                                                │ lineConsumer передаёт в UI│
       │                                                                │ (Platform.runLater)       │
       │                                                                │              │ surefire-reports/*.xml
       │                                                                │              │◄───────────┤
       │                                                                │ parseSurefireReports()    │
       │                                                                │ TestRunResult             │
       │                                                                │◄─────────────┤            │
       │ Task.onSucceeded(result) → displayResults + reportDao.saveRun  │              │            │
```

#### 8.4.2. Прерывание пользователем — закрытие окна во время прогона

```
Пользователь   MainController   Task    TestRunner    Process (mvn)    Chrome
     │              │            │           │              │              │
     │ click "Запустить"        │           │              │              │
     ├─────────────►│            │           │              │              │
     │              │ Task.start()           │              │              │
     │              ├───────────►│           │              │              │
     │              │            │ run(...)  │              │              │
     │              │            ├──────────►│              │              │
     │              │            │           │ ProcessBuilder.start()      │
     │              │            │           ├─────────────►│              │
     │              │            │           │              │ запускает    │
     │              │            │           │              │ Chrome forkи│
     │              │            │           │              ├─────────────►│
     │ click ✕      │            │           │              │              │
     ├─────────────►│            │           │              │              │
     │              ╳ окно закрыто           │              │              │
     │                           │           │              │              │
     │                           │ ⚠ Task продолжает выполняться           │
     │                           │ ⚠ Process.waitFor() блокирует поток     │
     │                           │ ⚠ Chrome остаётся открытым              │
     │                           │ Platform.runLater из onSucceeded → NPE  │
     │                           │ (JavaFX-thread мёртв)                   │
     │                                                                     │
     │ ⚠ требуется снять процесс mvn и Chrome через Диспетчер задач         │
```

#### 8.4.3. Прерывание системой

**Сценарий А — Maven не установлен:**

```
MainController   Task    TestRunner    ProcessBuilder
       │            │          │              │
       │ Task.start()          │              │
       ├───────────►│          │              │
       │            │ run(...) │              │
       │            ├─────────►│              │
       │            │          │ start("mvn", "test", ...)
       │            │          ├─────────────►│
       │            │          │  ⚠ IOException
       │            │          │   "Cannot run program 'mvn': not found in PATH"
       │            │          │◄─────────────┤
       │            │ throws   │              │
       │            │◄─────────┤              │
       │ Task.onFailed(throwable)             │
       │◄───────────┤          │              │
       │ showAlert("Ошибка", throwable.getMessage())
       │ progressBar скрыт, кнопки разблокированы
```

**Сценарий Б — генерация падает с `IOException` (нет прав на запись каталога):**

```
MainController   TestGenerator   PageObjectWriter   JavaFileWriter   Files.write
       │              │                │                  │                │
       │ generate(model)               │                  │                │
       ├─────────────►│                │                  │                │
       │              │ write(entity, config)             │                │
       │              ├───────────────►│                  │                │
       │              │                │ writeToFile()    │                │
       │              │                ├─────────────────►│                │
       │              │                │                  │ Files.write(...)
       │              │                │                  ├───────────────►│
       │              │                │                  │  ⚠ AccessDeniedException
       │              │                │                  │◄───────────────┤
       │              │                │   IOException    │                │
       │              │                │◄─────────────────┤                │
       │              │  IOException   │                  │                │
       │              │◄───────────────┤                  │                │
       │  Exception   │                │                  │                │
       │◄─────────────┤                │                  │                │
       │ catch в onGenerate → showAlert("Ошибка генерации", e.getMessage())│
       │ ⚠ часть файлов может остаться записанной (частичная генерация)   │
```

### 8.5. Диаграмма кооперации пакета `generator`

```
              1: new TestConfig() / setters
                ┌──────────────┐
                │ TestConfig   │
                └──────┬───────┘
                       │ 2: new TestGenerator(config)
                       ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │                  TestGenerator   (дирижёр)                       │
   └──┬──────────┬──────────────────────┬──────────────┬──────────────┘
      │ 3:write  │ 4:write              │ 5:write      │ 6:writeToFile
      │ pom.xml  │ BaseTest.java        │ *Test.java   │
      ▼          ▼                      ▼              ▼
   ┌──────┐  ┌──────────┐         ┌────────────┐ ┌──────────────┐
   │ pom  │  │ BaseTest │         │TestClassWriter│ │ PageObjectWriter│
   │ .xml │  │  .java   │         └──────┬─────┘ └──────┬───────┘
   └──────┘  └──────────┘                │              │
                                    7: helper          │
                                          ▼              ▼
                                  ┌─────────────────────────┐
                                  │ TestDataFactory  +      │
                                  │ JavaFileWriter (common) │
                                  └─────────────────────────┘
                                          │
                                          │ 8: render bytes
                                          ▼
                                  ┌─────────────────────────┐
                                  │  generated-tests/...    │
                                  │  *.java files           │
                                  └─────────────────────────┘

   ─── позже ───

   ┌─────────────────────────┐   9: run(outputDir, xml, url, filter, fast)
   │       TestRunner        │ ◄──────────────────────────  ┌────────────────┐
   │                         │                              │ MainController │
   └───┬─────────────────────┘                              └────────────────┘
       │ 10: ProcessBuilder("mvn test")
       ▼
   ┌────────────────────┐
   │  mvn (child JVM)   │ ──► Surefire ──► JUnit ──► Selenium ──► Chrome
   └────────┬───────────┘
            │ 11: stdout (lineConsumer → UI лог)
            │ 12: surefire-reports/*.xml
            ▼
   ┌────────────────────┐  13: TestRunResult
   │  TestRunner.parse  │ ──────────────────► ┌─────────────────┐
   │  SurefireReports() │                     │ RunReportWriter │
   └────────────────────┘                     │  (HTML / CSV)   │
                                              └─────────────────┘
```

### 8.6. Уточнённая диаграмма классов пакета `generator`

```
┌─────────────────────────────────────┐    ┌─────────────────────────────────────┐
│           TestConfig                │    │           TestGenerator             │
├─────────────────────────────────────┤    ├─────────────────────────────────────┤
│ — baseUrl:     String               │    │ — config: final TestConfig          │
│ — login:       String               │ ◄──┤                                     │
│ — password:    String               │    └─────────────────────────────────────┘
│ — outputDir:   Path                 │
│ — browserType: String = "chrome"    │    ┌─────────────────────────────────────┐
│ — basePackage: String = "generated" │    │       PageObjectWriter              │
│ — siteType:    String = "e3core"    │    ├─────────────────────────────────────┤
│ — subsystemName: String = ""        │    │ (без полей — методы принимают конфиг)│
│ — testLevel:   String = "basic"     │    └─────────────────────────────────────┘
│ — smokeAllSubsystems: boolean = true│
└─────────────────────────────────────┘    ┌─────────────────────────────────────┐
                                            │       TestClassWriter               │
┌─────────────────────────────────────┐    ├─────────────────────────────────────┤
│           TestRunner                │    │ (без полей)                          │
├─────────────────────────────────────┤    └─────────────────────────────────────┘
│ — lastMavenOutput: String = ""      │
└─────────────────────────────────────┘    ┌─────────────────────────────────────┐
                                            │       TestDataFactory               │
┌─────────────────────────────────────┐    ├─────────────────────────────────────┤
│           RunReportWriter           │    │ (static helpers)                     │
├─────────────────────────────────────┤    └─────────────────────────────────────┘
│ (методы принимают TestRunResult)    │
└─────────────────────────────────────┘
```

### 8.7. Детальная диаграмма классов пакета `generator`

```
┌─────────────────────────────────────────────────────────────────┐
│                  TestConfig                                     │
├─────────────────────────────────────────────────────────────────┤
│ (все поля из 8.6)                                                │
├─────────────────────────────────────────────────────────────────┤
│ + getXxx / setXxx для каждого из 10 полей                        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  TestGenerator                                  │
├─────────────────────────────────────────────────────────────────┤
│ — config: final TestConfig                                      │
├─────────────────────────────────────────────────────────────────┤
│ + TestGenerator(config: TestConfig)                             │
│ + generate(model: AppModel): void  throws IOException           │
│ — writePomXml(): void                                           │
│ — writeBaseTest(): void                                         │
│ (+ десятки приватных хелперов для генерации частей кода)        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  PageObjectWriter                               │
├─────────────────────────────────────────────────────────────────┤
│ + write(entity: EntityObject, config: TestConfig): void          │
│ — writeImports, writeConstructor, writeFillField,               │
│   writeFkPicker, writeClickButton, ... (много приватных)         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  TestClassWriter                                │
├─────────────────────────────────────────────────────────────────┤
│ + write(entity: EntityObject, model: AppModel, config: TestConfig): void
│ — writeCreateTest, writeCreateWithOnlyRequiredTest,             │
│   writeUpdateTest, writeDeleteTest, writeSearchTest, ...        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  TestDataFactory                                │
├─────────────────────────────────────────────────────────────────┤
│ + generateValue(prop: Property): static String                  │
│ + generateRandomString, generateDecimal, generateDate (static)  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  TestRunner                                     │
├─────────────────────────────────────────────────────────────────┤
│ — lastMavenOutput: String                                       │
├─────────────────────────────────────────────────────────────────┤
│ + run(projectDir, xmlFileName, baseUrl): TestRunResult          │
│ + run(projectDir, xmlFileName, baseUrl, lineConsumer):          │
│       TestRunResult                                             │
│ + run(projectDir, xmlFileName, baseUrl, lineConsumer,           │
│       testFilter): TestRunResult                                │
│ + run(projectDir, xmlFileName, baseUrl, lineConsumer,           │
│       testFilter, fastMode): TestRunResult                      │
│ — parseSurefireReports(reportsDir: Path): List<TestCaseResult>  │
│ — readStream(stream, consumer): void                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  RunReportWriter                                │
├─────────────────────────────────────────────────────────────────┤
│ + writeHtml(result: TestRunResult, outputDir: Path): void        │
│ + writeCsv(result: TestRunResult, outputDir: Path): void         │
└─────────────────────────────────────────────────────────────────┘
```

#### Описание ключевых методов пакета `generator`

| Класс              | Метод                                  | Описание                                                                              |
| ------------------ | -------------------------------------- | ------------------------------------------------------------------------------------- |
| `TestGenerator`    | `generate(model)`                      | Главная точка: пишет `pom.xml` + `BaseTest.java`, затем для каждой PRIMARY-сущности — Page Object и Test-класс. |
| `PageObjectWriter` | `write(entity, config)`                | Создаёт класс `XxxPage` с методами `open`, `fillField`, `clickCreate`, `clickSave` и т.д. — скрывает работу с ExtJS. |
| `TestClassWriter`  | `write(entity, model, config)`         | Создаёт класс `XxxTest` с JUnit-методами `testCreate`, `testCreateOnlyRequired`, `testUpdate`, `testDelete`, `testSearch*`, `testGrid*`, ...|
| `TestDataFactory`  | `generateValue(prop)`                  | По `AttrType` и `mask` подбирает корректное случайное значение для поля.              |
| `TestRunner`       | `run(...)` (4 перегрузки)              | `ProcessBuilder("mvn", "test", "-Dtest=...")`. Читает stdout построчно, парсит surefire-reports/*.xml через StAX. |
| `TestRunner`       | `parseSurefireReports(reportsDir)`     | StAX-парсинг XML-отчётов Surefire → список `TestCaseResult` со статусами и сообщениями ошибок. |
| `RunReportWriter`  | `writeHtml(result, outputDir)`         | Кастомный HTML-отчёт v5 с фотолетописью (включает скриншоты и шаги).                  |
| `RunReportWriter`  | `writeCsv(result, outputDir)`          | CSV-выгрузка для Excel/BI.                                                            |

### 8.8. Особенности генератора

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
