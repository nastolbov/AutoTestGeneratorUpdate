# План изменений диплома

Аудит файла `0c9d27b6-_______________1.docx` (1926 параграфов, 170 таблиц) против актуального состояния репозитория `AutoTestGeneratorUpdate` (vers2).

Эталонные источники истины:

- `/home/user/AutoTestGeneratorUpdate/ARCHITECTURE_vers2.md`
- `/home/user/AutoTestGeneratorUpdate/TABLES_vers2.md`
- Исходный код в `/home/user/AutoTestGeneratorUpdate/src/main/java/ru/autotestgen/{ui,parser,model,common,generator,data}/`
- `/home/user/AutoTestGeneratorUpdate/pom.xml`

Обозначения статуса раздела:
- **OK** — раздел не требует изменений (или допустимы только стилистические).
- **MINOR** — мелкие правки (типы данных, дописать механизм, переименование класса).
- **CRITICAL** — фактическая ошибка: упоминается несуществующий класс/метод, неверные зависимости, потеря рефакторинга. Без исправления текст диплома противоречит коду.

---

## Сводка (кратко)

| Категория | Количество |
| ----- | -----: |
| Критических несоответствий (CRITICAL) | **18** |
| Минорных правок (MINOR) | **17** |
| Разделов без правок (OK) | **8** |
| Итого затронутых таблиц | **≈ 15 из 99** |
| Итого затронутых параграфов | **≈ 70 из 1256** |

**Главные источники проблем (на чём держится больше всего правок):**

1. **CLI удалён**, но в дипломе он живёт в табл. 37, 39, 40, 92, П3.1, описании App.main, ТЗ, рук. пользователя, тест-таблицах 97. → 7 точек правки.
2. **Парсер разбит на 6 классов**, но в дипломе он остаётся одним «толстым» `XmlModelParser` (табл. 43–45, рис. 20–26, табл. 92, П3.1, тест-таблицы 97). → 6 точек правки.
3. **ReportService** придуман в дипломе и не существует в коде. Полное несоответствие. → 5 правок: рис. кооп. UI, табл. 71 (`reportService`), табл. 72–73 (вызовы `reportService.save/loadHistory`), табл. 97. Нужно везде заменить на `ReportDao`.
4. **Уровни тестов SMOKE/BASIC/FULL, fastMode, smokeAllSubsystems, EntityClassifier, HTML-отчёт v5, скриншоты, Surefire-фильтр** — реализованы в коде, но в дипломе либо не упомянуты, либо упомянуты вскользь. → 5–6 разделов нужно дополнить.
5. **Таблица 91 (компоненты)** — `Selenium.jar` и `JUnit.jar` указаны как компоненты разрабатываемого ПО, хотя являются зависимостями только сгенерированного Maven-проекта. → 1 крупная правка.

---

## Раздел «Введение» (параграфы 115–118)

**Статус: OK** ✅

Текст в целом соответствует реализации. Указана цель — «разработка информационной системы автоматической генерации Selenium-автотестов веб-приложений на платформе E3Core на основе формального XML-описания модели метаданных». Это точно отражает суть.

**Опционально (стилистика, не обязательно):**
- В пар. 118 фраза «(парсер XML-модели, генератор тестового проекта, раннер тестов, подсистема хранения истории)» — корректна и совпадает с реальной разбивкой на пакеты `parser / generator / generator (TestRunner) / data`.

---

## Раздел 1.1. Сравнительный анализ аналогов (параграфы 121–151, табл. 1)

**Статус: OK** ✅

Сравнение Selenium / Selenide / Katalon / Playwright корректно. Все четыре названных инструмента действительно являются аналогами в нише UI-автоматизации, и ни один из них не решает задачу «генерация автотестов из XML-метамодели». Вывод в пар. 148–151 обоснован.

**Правок не требуется.**

---

## Раздел 1.2. Выбор технологии, среды и языка программирования (параграфы 153–168)

**Статус: MINOR** ⚠

Что соответствует факту:
- Java 17+, Maven, Selenium WebDriver, JUnit 5, JavaFX + FXML, SQLite 3.42, VS Code — всё подтверждается `pom.xml` (`<javafx.version>24.0.1</javafx.version>`, `sqlite-jdbc 3.42.0.0`) и кодом.
- Итерационная модель ЖЦ — выбор обоснован.

Что требует уточнения:
- **Пар. 158** «Использование Java позволяет реализовать все компоненты системы (парсер XML-модели, генератор тестового проекта, **запуск тестов** и обработку результатов) в едином стеке» — фактически запуск тестов идёт через внешний `mvn test`, не через программный JUnit Platform Launcher. Это не противоречит тексту, но можно усилить: «единый стек, в т. ч. запуск `mvn test` через `ProcessBuilder` с парсингом отчётов Surefire».
- **Пар. 162** «JavaFX … MVC за счёт контроллеров» — в коде есть один `MainController` без явного M и V в отдельных классах (FXML — view, AppModel/TestRunResult — модели). Не ошибка, но честнее назвать паттерн **MVC-light** или «контроллер-фасад».

**Действия:**
1. Добавить (опционально) к пар. 158 уточнение про `mvn test` через `ProcessBuilder`.

---

## Раздел 1.3. Анализ процесса обработки информации (параграфы 170–203)

**Статус: MINOR** ⚠

Что верно:
- StAX-парсер, метод skipToEnd, потоковая обработка — всё корректно (`StaxUtils.skipToEnd`).
- Шаблонная генерация Page Object + тестовых классов, алгоритмы именования (`toClassName`, `toMethodName`) — соответствует `Transliterator`.
- Запуск через Maven + парсинг Surefire-XML — соответствует `TestRunner.parseSurefireReports`.

**Что упущено (нужно добавить, иначе диплом не отражает ключевой механизм):**

1. **Алгоритм классификации сущностей (EntityClassifier)** — отдельный механизм, фактически решающий, для какой сущности генерировать тесты. Из 19 сущностей AIS_GSK генератор обрабатывает только PRIMARY (≈ 8), CHILD генерируются как табы внутри родителя, REFERENCE_DICTIONARY пропускаются. Без упоминания этого алгоритма раздел 1.3 неполный.

   **Куда вписать:** перед «Выбор алгоритмов генерации тестов» (после пар. 187) добавить новый подраздел или абзац:
   > «Алгоритм классификации сущностей. После построения объектной модели каждая сущность пропускается через классификатор `EntityClassifier`, который относит её к одной из трёх категорий: `PRIMARY` (основная бизнес-сущность с точкой входа в меню), `CHILD` (дочерняя — существует только как Grid-вкладка внутри родителя), `REFERENCE_DICTIONARY` (справочник, доступный только через FK-пикер). Правила классификации последовательные: поиск родительского Grid → проверка префикса `V_S_` → проверка “FK-target only” → иначе PRIMARY. Для не-PRIMARY сущностей standalone-тесты не генерируются; CHILD получают специализированный тест внутри карточки родителя.»

2. **Уровни тестов SMOKE / BASIC / FULL** — упоминаются в коде (`TestConfig.testLevel`, `MainController.testLevelCombo`), но в дипломе нет ни строчки.

   **Куда вписать:** в «Алгоритм покрытия» (пар. 196) дополнить:
   > «Глубина покрытия выбирается параметром `testLevel`: `SMOKE` — только проверка отображения форм; `BASIC` (по умолчанию) — CRUD + поиск; `FULL` — все методы, включая валидацию обязательных полей, частичную валидацию, операции с гридами.»

3. **fastMode (3× headless Chrome)** — реализован в `TestConfig`/`TestRunner.run(...fastMode)` и `MainController.fastModeCheck`. Стоит упомянуть в разделе про запуск тестов.

   **Куда вписать:** пар. 197–198 дополнить:
   > «Для ускорения прогона предусмотрен “быстрый режим” (`fastMode`): Surefire запускается с `forkCount=3` и Chrome в режиме `--headless=new`, что даёт ускорение прогона в 2–3 раза за счёт параллельного исполнения тест-классов.»

**Что НЕ нужно трогать (формулировки верны):**
- Алгоритм разбора с `skipToEnd` (пар. 184–187).
- Структура входных/выходных данных (пар. 173–180).
- Алгоритм анализа результатов (пар. 199–202).

**Действия:**
1. **CRITICAL:** дополнить алгоритм классификации (`EntityClassifier`).
2. **MINOR:** добавить уровни тестов.
3. **MINOR:** добавить упоминание `fastMode`.

---

## Раздел 1.4.1. Диаграмма вариантов использования (параграфы 208–288, табл. 2–11)

**Статус: OK** ✅

Use-case-диаграмма с 5 use-case'ами («Подготовить параметры», «Разобрать метаданные», «Сгенерировать автотесты», «Запустить автотесты», «Сохранить отчёты в БД») и одним актором «Пользователь» соответствует реальной структуре приложения. Все альтернативы корректны.

**Правок не требуется.** Use-case'ы независимы от того, есть CLI или нет — все они доступны из GUI.

---

## Раздел 1.4.2. Контекстная диаграмма классов (параграфы 289–310, табл. 12)

**Статус: OK** ✅

Контекстная (концептуальная) диаграмма правильно содержит абстрактные сущности: «Файл метаданных (XML)», «Парсер», «Модель метаданных», «Генератор автотестов», «Автотест», «СессияТестирования», «Отчёт», «База данных отчётов». На концептуальном уровне разбиение парсера на 6 классов не показывается — это конкретика реализации.

**Правок не требуется.**

---

## Раздел 1.4.3. Диаграммы последовательности системы (параграфы 311–425, табл. 13–33)

**Статус: OK** ✅

5 sequence-диаграмм по 5 use-case'ам, описания операций (открыть приложение, указать путь, ввести URL/логин/пароль/параметры БД, нажать «Разобрать/Сгенерировать/Запустить», просмотреть результаты, сохранить в БД) — всё реально присутствует в `MainController`.

**Правок не требуется.**

---

## Раздел 1.4.4. Диаграмма деятельностей (параграфы 426–437)

**Статус: OK** ✅

Activity-диаграмма для use-case'а «Сгенерировать автотесты». Раздел концептуальный, прямого соответствия с кодом не требует.

**Правок не требуется.**

---

## Раздел 1.4.5. ER-диаграмма БД (параграфы 438–450, табл. 34–35)

**Статус: OK** ✅

Две таблицы `test_run` и `test_case` со связью 1:N через `test_case.run_id`, СУБД SQLite 3.42, инструмент ERwin Data Modeler. Полностью соответствует `ReportDao.initDatabase()`.

**Правок не требуется.**

---

## Раздел 1.4.6. Диаграмма состояний (параграфы 452–462)

**Статус: OK** ✅

State-machine GUI: `S0 (начальное) → S1 (введены параметры) → S2 (разобран XML) → S3 (сгенерированы тесты) → S4 (тесты запущены)`. Соответствует логике `MainController` (флаги `btnGenerate.setDisable / btnRunTests.setDisable`).

**Правок не требуется.**

> Опечатка: в пар. 459 написано «показана на рис. 10» — должно быть «рис. 11». В пар. 461 уже корректно «Рис. 11». Это **опечатка**, которую полезно поправить.

**Действия:** поправить опечатку «рис. 10» → «рис. 11» в пар. 459.

---

## Раздел 1.5.1. Диаграмма пакетов (параграфы 467–477, табл. 36)

**Статус: CRITICAL** ❌

В табл. 36 указано:

| Слой | Пакет | Назначение |
| ---- | ----- | ---------- |
| PresentationLayer | ui | JavaFX-приложение, FXML-контроллер, **CLI-раннер**, точки входа. |

**Ошибка:** CLI-раннер из кода удалён (`CliRunner.java` отсутствует, `App.java` имеет только `launch(args)` без проверки `--cli`). Упоминание «CLI-раннер» в назначении пакета `ui` некорректно.

**Что заменить:**
> ~«JavaFX-приложение, FXML-контроллер, CLI-раннер, точки входа.»~
> → «JavaFX-приложение, FXML-контроллер главного окна, точка входа `App`.»

**Остальные строки таблицы 36 корректны** (parser — StAX, generator — оркестратор + writer-ы + TestRunner + ReportService → должно быть **ReportDao**, а не ReportService, см. ниже).

> ⚠ Также в строке `DataLayer` написано «DAO-класс ReportDao для SQLite, DTO TestRunResult и TestCaseResult» — это корректно (соответствует `data/ReportDao.java` и `model/TestRunResult.java`, `model/TestCaseResult.java`).

> ⚠ В строке `generator` написано «ReportService» — должно быть **RunReportWriter** (или вообще убрать; ReportService не существует, а класс, отвечающий за HTML/CSV отчёт, — это `RunReportWriter`).

**Действия:**
1. **CRITICAL:** убрать упоминание «CLI-раннер» из строки `ui`.
2. **CRITICAL:** заменить «ReportService» в строке `generator` на «RunReportWriter». В коде *никакого* `ReportService` нет; есть `ReportDao` (data) и `RunReportWriter` (generator).

---

## Раздел 1.5.2. Проектирование классов в пакетах

### 1.5.2.1–1.5.2.5. Пакет «UI» (параграфы 484–556, табл. 37–42)

**Статус: CRITICAL** ❌

#### Таблица 37 — Описание классов пакета «UI»

Реально в коде существуют **2 класса + 1 вложенный**: `App`, `MainController`, `MainController.TestCaseRow`.

Сейчас в табл. 37 (TBL_IDX 65) перечислены: `App`, **`CliMain`**, **`CliRunner`**. Нет `MainController` и `TestCaseRow`. Это полное несоответствие.

**Полная замена табл. 37 (взять из `TABLES_vers2.md` §1.1):**

| Класс | Описание |
| ----- | -------- |
| `App` | Главный класс JavaFX-приложения, наследник `javafx.application.Application`. Загружает FXML-разметку главного окна (`main.fxml`), создаёт сцену и запускает GUI. |
| `MainController` | FXML-контроллер главного окна. Связывает элементы интерфейса (поля, кнопки, таблицы) с обработчиками и координирует вызовы парсера, генератора, раннера и DAO. |
| `MainController.TestCaseRow` | Вложенный статический класс — строка таблицы результатов тестов (5 неизменяемых полей: class/method/status/duration/message). |

В описании `App` уже есть слова «которая также проверяет флаг `--cli` и в этом случае делегирует запуск консольному раннеру» — **это надо удалить**. Реальный код `App.main` (строка 22 в `App.java`):

```java
public static void main(String[] args) {
    launch(args);
}
```

— ни флага, ни делегации в CLI нет.

#### Таблица 38 — Описание методов класса «App»

В коде:

| Метод | Параметры | Возвращает | Описание |
| ----- | --------- | ---------- | -------- |
| `start` | `primaryStage: Stage` | `void` | Загружает `/fxml/main.fxml`, создаёт `Scene(root, 1000, 700)`, ставит заголовок «AutoTestGenerator - Генерация автотестов из XML-модели», показывает окно. |
| `main` | `args: String[]` | `void` | Делегирует `launch(args)`. |

В дипломе (TBL_IDX 67) описание `main` сейчас: «Анализирует аргументы командной строки на наличие флага `--cli`. При его наличии делегирует выполнение классу `CliRunner`, иначе вызывает `Application.launch(args)`.» — **это неверно**. Заменить на: «Статическая точка входа. Делегирует управление `launch(args)`, запускающему JavaFX-runtime.»

#### Таблицы 39 и 40 — `CliMain` и `CliRunner`

**Эти таблицы полностью лишние** — классов `CliMain.java` и `CliRunner.java` в репозитории нет (директория `src/main/java/ru/autotestgen/ui/` содержит только `App.java` и `MainController.java`).

**Что сделать:** удалить таблицы 39 и 40 (и все ссылки на них в подпунктах 1.5.2.5 «Описание классов пакета «UI» показано в табл. 38–42»). Перенумеровать оставшиеся таблицы:
- была табл. 41 (поля MainController) → станет табл. 39
- была табл. 42 (методы MainController) → станет табл. 40

Заодно надо пересмотреть нумерацию во всём документе после табл. 40 (текущие 43–99 сдвинутся на –2). Это самая трудоёмкая мелкая работа. **Альтернатива:** оставить нумерацию как есть и просто пометить табл. 39 и 40 как «зарезервированы» — но это плохо смотрится.

> Рекомендация: пересчитать всю нумерацию. Это «трудно, но один раз».

#### Таблица 41 — Описание полей класса «MainController» (TBL_IDX 70–71)

Сейчас в дипломе список полей частично соответствует, но:

1. **Нет полей:** `testLevelCombo`, `smokeAllSubsystemsCheck`, `fastModeCheck`, `btnRunSelected`, `TEST_CATEGORIES`. Эти поля **есть** в коде (`MainController.java`, строки 39–68).
2. **Лишнее поле:** `reportService: ReportService` (TBL_IDX 71, последняя строка) — в коде такого поля **нет**. Есть `reportDao: final ReportDao` (строка 92 в `MainController.java`).

**Полный список полей `MainController` — взять из `TABLES_vers2.md` §2.1.**

#### Таблица 42 — Описание методов класса «MainController» (TBL_IDX 72–73)

Сейчас в дипломе описаны методы: `initialize, onSelectXml, onSelectOutputDir, onParse, onGenerate, onRunTests, onShowHistory, displayResults, log, showAlert`. Из них:

- `initialize` — есть, но описание «блокирует кнопки до успешного парсинга» — корректно.
- `onSelectXml`, `onSelectOutputDir`, `onParse`, `onGenerate` — есть, описания корректны.
- `onRunTests` — есть. **Но** в описании сейчас: «сохраняет результат через `reportService.save()`» — **должно быть** «через `reportDao.saveRun(result)`».
- `onShowHistory` — есть. **Описание:** «`reportService.loadHistory()`» — **должно быть** «`reportDao.getAllRuns()`».
- `displayResults`, `log`, `showAlert` — есть, описания корректны.

**Что отсутствует:** `onRunSelected`, `buildTestFilter`, `launchRun`, `getXmlFileName`. Все 4 метода реально присутствуют в `MainController.java`. **Эти 4 метода нужно добавить в табл. 42** — иначе таблица неполна.

**Готовый список из `TABLES_vers2.md` §3.2:**

| Метод | Параметры | Возвращает | Описание |
| ----- | --------- | ---------- | -------- |
| `onRunSelected` | — | `void` | Открывает модальный диалог с чекбоксами сущностей и видов тестов и live-превью Surefire-фильтра; собирает строку `-Dtest=Class1Test,Class2Test#m1+m2` и вызывает `launchRun(filter)`. |
| `buildTestFilter` | `entityChecks: List<CheckBox>`, `typeChecks: List<CheckBox>` | `String` | Static. Из отмеченных чекбоксов формирует строку Surefire-фильтра. |
| `launchRun` | `testFilter: String` | `void` | Запускает фоновую задачу `Task<TestRunResult>` с `TestRunner.run`. По завершении — `displayResults` + `reportDao.saveRun`. |
| `getXmlFileName` | — | `String` | Имя файла из `xmlPathField` (`File.getName()`) либо `"unknown.xml"`. |

#### Параграф 522

Сейчас: «Описание классов пакета «UI» показано в табл. 38–42.» — должно быть «в табл. 38–40» (после удаления табл. 39 и 40 и переименования).

#### Параграф 953

«Модульная структура системы включает **32** класса, распределённых по шести пакетам.» — это число надо пересчитать (см. ниже раздел 1.5.3). В коде сейчас:
- ui: 2
- model: 18 (включая enum'ы и вложенные классы Classification, StepTiming)
- parser: 6
- common: 3
- data: 1
- generator: 7
- **Итого: 37 классов** (с учётом enum'ов и static-inner).

Если не считать вложенные/enum: ≈ 32, согласуется с текстом, но проверьте.

#### Действия для пакета «UI»

1. **CRITICAL:** Полностью переписать табл. 37 без `CliMain`/`CliRunner` (см. готовый текст выше).
2. **CRITICAL:** Удалить из табл. 38 описание `main` с упоминанием `--cli` и `CliRunner`.
3. **CRITICAL:** Удалить табл. 39 (`CliMain`) и табл. 40 (`CliRunner`) — нет таких классов. Перенумеровать.
4. **CRITICAL:** В табл. 41 (поля MainController) добавить: `testLevelCombo`, `smokeAllSubsystemsCheck`, `fastModeCheck`, `btnRunSelected`, `TEST_CATEGORIES`. Удалить строку `reportService: ReportService`. Добавить строку `reportDao: final ReportDao`.
5. **CRITICAL:** В табл. 42 (методы MainController) добавить: `onRunSelected`, `buildTestFilter`, `launchRun`, `getXmlFileName`. В описаниях `onRunTests` и `onShowHistory` заменить `reportService.save/loadHistory()` на `reportDao.saveRun(result)` / `reportDao.getAllRuns()`.
6. **MINOR:** Перенумеровать рис. 14–19 в подпунктах 1.5.2.2–1.5.2.5 — на них в коде ничего не меняется, но если уйдут CLI-методы, исходная/уточнённая/детальная диаграмма должна показывать только `App + MainController + TestCaseRow` (см. ARCHITECTURE_vers2.md §3.6–3.7 — там готовые описания).

---

### 1.5.2.6–1.5.2.10. Пакет «Parsing» (параграфы 558–608, табл. 43–45)

**Статус: CRITICAL** ❌

#### Таблица 43 — Описание классов пакета «Parsing»

Сейчас (TBL_IDX 74): один класс `XmlModelParser` с описанием «Потоковый парсер XML-модели метаданных формата E3Core. Построен на стандартной Java-библиотеке `javax.xml.stream` (StAX), … рекурсивно обходит XML-дерево, распознавая теги `Object`, `Properties`, `Property`, `Operation`, `Modifier`, `Search`, `SearchParam`, `AssociationObjectA`, и наполняет объектную модель `AppModel`…»

**Реально в пакете 6 классов** (`/src/main/java/ru/autotestgen/parser/`):
- `XmlModelParser.java` — **фасад**, диспетчеризует top-level элементы
- `EntityParser.java` — парсит `<Object>` и `<AssociationObjectA>`
- `PropertyGroupParser.java` — парсит `<Properties>` со всеми `<Property>` и `<Operation>` + `<OperationParam>` + `<Modifier>`
- `SearchParser.java` — парсит `<Searches>` со всеми `<SearchParam>`, `<SearchResult>`, `<SearchResultProperty>`
- `StaxUtils.java` — static-утилиты (`attr`, `parseInt`, `skipToEnd`)
- `XmlNamespaces.java` — константы NS_E, NS_E3, NS_MD

**Замена табл. 43 — взять из `TABLES_vers2.md` §1.3.**

> Заметьте, что в дипломе пакет называется «Parsing», а в коде каталог — `parser` (single). Это **допустимо** (разные стили именования: русский UML vs физический пакет), но можно отметить сноской в первом упоминании раздела 1.5.2.6: «*В исходном коде пакет именуется `ru.autotestgen.parser` (англоязычная конвенция Java-пакетов).*»

#### Таблицы 44 и 45 — Поля и методы класса `XmlModelParser`

Сейчас содержат:
- Табл. 44 (поля XmlModelParser) (TBL_IDX 75) — 3 поля `NS_E, NS_E3, NS_MD`. **Эти поля принадлежат классу `XmlNamespaces`, а не `XmlModelParser`.** В реальном `XmlModelParser` поля: `entityParser: final EntityParser`, `searchParser: final SearchParser`.
- Табл. 45 (методы XmlModelParser) (TBL_IDX 76–78) — 10 методов: `parse, parseObject, parseAssociation, parsePropertyGroup, parseProperty, parseOperation, parseSearches, parseSingleSearch, skipToEnd, attr, parseInt`. **Только один из них** (`parse`) реально живёт в `XmlModelParser`. Остальные переехали в `EntityParser`, `PropertyGroupParser`, `SearchParser`, `StaxUtils`.

**Что сделать:** разделить табл. 44 и 45 на серию небольших таблиц по 6 классам:

- **Табл. 44.** Поля класса `XmlModelParser` — 2 поля: `entityParser, searchParser`.
- **Табл. 45.** Методы класса `XmlModelParser` — 2 метода: `parse(File)` + конструктор с DI.
- **Табл. 46 (новая).** Методы класса `EntityParser` — `parseObject`, `parseAssociation` + конструктор.
- **Табл. 47 (новая).** Методы класса `PropertyGroupParser` — `parsePropertyGroup`, `parseProperty`, `parseOperation`.
- **Табл. 48 (новая).** Методы класса `SearchParser` — `parseSearches`, `parseSingleSearch`.
- **Табл. 49 (новая).** Поля класса `XmlNamespaces` — `NS_E`, `NS_E3`, `NS_MD`.
- **Табл. 50 (новая).** Методы класса `StaxUtils` — `attr`, `parseInt`, `skipToEnd`.

**Готовый текст для всех 7 таблиц — `TABLES_vers2.md` §3.14–3.18 и §2.17.**

Альтернатива (если ломать нумерацию во всём диплом слишком трудоёмко): оставить нумерацию табл. 44–45, но в табл. 45 завести шапку «Методы парсеров (распределены по 6 классам пакета)» и в первом столбце дополнительно указывать класс, в котором метод живёт.

#### Рис. 20, 25, 26 (Исходная / Уточнённая / Детальная диаграмма классов пакета «Parsing»)

Сейчас рисуется **один класс XmlModelParser**. Перерисовать с 6 классами, показав:

- `XmlModelParser ♦──► EntityParser` (композиция)
- `XmlModelParser ♦──► SearchParser`
- `EntityParser ♦──► PropertyGroupParser`
- Все 3 парсера → `StaxUtils` (зависимость, static)
- Все 3 парсера → `XmlNamespaces` (зависимость, константы)

Готовые описания — `ARCHITECTURE_vers2.md` §5.2, §5.6, §5.7.

#### Рис. 21, 22, 23 (диаграммы последовательностей для нормального/прерывания пользователем/прерывания системой)

Сейчас показывают одного «парсера»; должны показывать делегирование от `XmlModelParser` → `EntityParser` → `PropertyGroupParser`. См. `ARCHITECTURE_vers2.md` §5.4.

#### Рис. 24 (Диаграмма кооперации пакета «Parsing»)

Должна показывать центральную роль `XmlModelParser` (фасад) и нумерованные сообщения к 3 другим парсерам. См. `ARCHITECTURE_vers2.md` §5.5.

#### Действия для пакета «Parsing»

1. **CRITICAL:** Полностью переписать табл. 43 (6 классов вместо 1).
2. **CRITICAL:** Разделить табл. 44 и 45 на 7 таблиц по классам (см. список выше).
3. **CRITICAL:** Перерисовать рис. 20, 25, 26 (исходная/уточнённая/детальная диаграммы) с 6 классами.
4. **CRITICAL:** Перерисовать рис. 21–24 (sequence + collaboration), показав фасад-делегирование.
5. **MINOR:** Опционально добавить сноску про несоответствие имени пакета «Parsing» (диплом) и `parser` (код).

---

### 1.5.2.11–1.5.2.15. Пакет «Model» (параграфы 609–714, табл. 46–65)

**Статус: MINOR / частично OK** ⚠

#### Таблица 46 — Описание классов пакета «Model»

В коде 18 классов в `model/`:
`AppModel, EntityObject, PropertyGroup, Property, Operation, OperationParam, Modifier, ModifyType, Association, Search, SearchParam, SearchResult, SearchResultProperty, AttrType, EntityKind, EntityClassifier, TestRunResult, TestCaseResult`.

**Что упущено** в табл. 46 (нужно добавить):
1. `EntityKind` (enum) — `PRIMARY / CHILD / REFERENCE_DICTIONARY`.
2. `EntityClassifier` (static utility class) — классификатор сущностей.
3. `EntityClassifier.Classification` (inner static class) — результат классификации.

Эти три класса критичны: без них не объясняется, почему генерируется только 8 тестов из 19 сущностей. **Готовое описание — `TABLES_vers2.md` §1.2.**

Также в табл. 49 (поля EntityObject) уже корректно перечислены поля (соответствуют `EntityObject.java`).

#### Таблицы 47–65 — Поля и методы классов модели

Большинство таблиц корректны (POJO, геттеры/сеттеры). Что нужно проверить:

- **Табл. 47–48 (`AppModel`):** В коде есть метод `getSubsystemNameFromCategory()` (см. `MainController.onParse()` строка 180 — вызывается). Если в табл. 48 этого метода нет — добавить.
- **Табл. 49–50 (`EntityObject`):** В коде методы `hasCrudOperations()` и `getFormView()`. Если их нет — добавить.
- **Табл. 51–52 (`PropertyGroup`):** В коде методы `isFormView()` и `isGridView()`. Если их нет — добавить.
- **Табл. 53 (`Property`):** все поля Property — POJO, методов кроме геттеров нет.
- **Табл. 62–63 (`AttrType`):** содержит метод `fromXml(String)` (статический парсинг). Проверить.
- **Табл. 64–65 (`ModifyType`):** содержит `getCode()` и `fromCode(String)`. Проверить.

#### Новые таблицы для добавления

Минимум 3 новые таблицы:

- **Поля + методы класса `EntityClassifier`** (см. `TABLES_vers2.md` §3.11)
- **Поля класса `EntityClassifier.Classification`** (см. `TABLES_vers2.md` §2.14)
- **Перечисление `EntityKind`** (3 значения: PRIMARY, CHILD, REFERENCE_DICTIONARY) — можно одной строкой в табл. 46 или отдельным мини-разделом.

#### TestRunResult / TestCaseResult

Эти классы фактически живут в пакете `model` (см. `/src/main/java/ru/autotestgen/model/TestRunResult.java` и `TestCaseResult.java`). В дипломе они описаны (табл. 121, 122 по TBL_IDX) в разделе про DAO/Data — это нелогично, лучше переместить их описания в раздел пакета `model`. Но **это косметическая правка** — формально классы используются на стыке model+data, и оба варианта приемлемы.

#### Рис. 27, 32, 33 (Исходная / Уточнённая / Детальная диаграммы классов пакета «Model»)

Сейчас в рис. 27 и далее **не отражены** `EntityClassifier`, `EntityKind`, `Classification`. На диаграмме пакета их нужно показать. Готовые описания — `ARCHITECTURE_vers2.md` §4.1, §4.5, §4.6.

#### Действия для пакета «Model»

1. **CRITICAL:** Добавить в табл. 46 строки `EntityKind`, `EntityClassifier`, `EntityClassifier.Classification`.
2. **CRITICAL:** Добавить новые таблицы с полями и методами `EntityClassifier` + `Classification`. Без них раздел не описывает ключевой механизм системы.
3. **CRITICAL:** Перерисовать рис. 27 / 32 / 33 — добавить EntityClassifier и его связи (см. ARCHITECTURE_vers2.md §4.2, §4.5, §4.6).
4. **MINOR:** Проверить методы `getSubsystemNameFromCategory`, `hasCrudOperations`, `getFormView`, `isFormView/isGridView`, `AttrType.fromXml`, `ModifyType.fromCode/getCode` в табл. 48/50/52/63/65 — добавить, если отсутствуют.

---

### 1.5.2.16–1.5.2.20. Пакет «Generator» (параграфы 715–820, табл. 66–79)

**Статус: CRITICAL** ❌

#### Таблица 66 — Описание классов пакета «Generator»

Проверить, что упомянуты **все 7 классов**: `TestConfig, TestGenerator, PageObjectWriter, TestClassWriter, TestDataFactory, TestRunner, RunReportWriter`. По табл. 67–79 видно, что:

- `TestConfig` (67), `TestGenerator` (68, 70), `PageObjectWriter` (69, 71), `TestClassWriter` (72, 73), `TestDataFactory` (74, 75), `TestRunner` (76, 77) — есть.
- Таблица 78–79 — `ReportService` — **этого класса в коде нет**.

В реальности 7-й класс — это **`RunReportWriter`** (`/src/main/java/ru/autotestgen/generator/RunReportWriter.java`), он пишет HTML-отчёт v5 и CSV.

#### Таблицы 78 и 79 — `ReportService` (NOT EXIST)

**Класс `ReportService` отсутствует в коде**. Реально есть:
- `ReportDao` (пакет `data`) — отвечает за JDBC-доступ к SQLite.
- `RunReportWriter` (пакет `generator`) — пишет HTML/CSV отчёты.

В разных местах диплома (табл. 66, 71, 72, 73, 78, 79, 91, 97) фигурирует «ReportService» с разными ролями (то фасад над DAO, то фасад над DAO+DB, то компонент). **Это вымышленный класс.** Надо везде заменить:
- Когда речь о сохранении в БД / истории прогонов → `ReportDao`.
- Когда речь об HTML/CSV отчёте → `RunReportWriter`.

**Полная замена табл. 78–79:** взять методы `RunReportWriter` из `TABLES_vers2.md` §3.29:

| Метод | Параметры | Возвращает | Описание |
| ----- | --------- | ---------- | -------- |
| `write` | `htmlPath: Path, result: TestRunResult` | `void` | Пишет HTML-отчёт v5 с фотолетописью, шагами и подсветкой ошибок. |
| `writeCsv` | `csvPath: Path, result: TestRunResult` | `void` | Пишет CSV-выгрузку для Excel/BI. |
| `csv` | `v: String` | `static String` | Приватный. Экранирование значения для CSV. |
| `esc` | `s: String` | `static String` | Приватный. HTML-экранирование. |
| `shortName` | `cls: String` | `static String` | Приватный. Из `pkg.Sub.Class` оставляет только `Class`. |
| `extractStepLabel` | `filename: String` | `static String` | Приватный. По имени файла скриншота восстанавливает имя шага. |
| `formatDuration` | `ms: long` | `static String` | Приватный. Формат `1 м 23 с` / `420 мс`. |

#### Таблица 67 — Поля `TestConfig`

В коде 10 полей (`TestConfig.java`):
`baseUrl, login, password, outputDir, browserType, basePackage, siteType, subsystemName, testLevel, smokeAllSubsystems`.

**Что нужно проверить в табл. 67:**
- Есть ли `siteType`, `subsystemName`, `testLevel`, `smokeAllSubsystems`? Это 4 новых поля по сравнению с первой версией. Если нет — добавить.

Готовый список — `TABLES_vers2.md` §2.20.

#### Таблицы 70, 73, 77 — Методы TestGenerator / TestClassWriter / TestRunner

- **Методы `TestGenerator`** (`TestGenerator.java`): `generate(model)` плюс приватные `generatePom, generateJUnitConfig, generateSharedDriver, generateBaseTest, generateTestData, generateSubsystemsSmokeTest`. В дипломе нужно как минимум упомянуть, что **классификация EntityClassifier применяется внутри generate()** и **только PRIMARY-сущности получают standalone Page Object + Test**, а CHILD пишутся через `writeChildTest`. Если этого нет в текущем описании — добавить.

- **Методы `TestClassWriter`** (`TestClassWriter.java`): из кода видно ≥ 25 ссылок на различные `testCreate`, `testCreateOnlyRequired`, `testUpdate`, `testDelete`, `testLogicalEdit`, `testArchive`, `testFieldsPresent`, `testRequiredFieldValidation`, `testPartialRequiredFieldValidation`, `testSearch*`, `testGrid*`. **И тут отсутствует то, что в первой версии был метод `testMaskedFieldInput` — он удалён.** Если в табл. 73 этот метод упоминается — **удалить**.

- **Методы `TestRunner`** (`TestRunner.java`, проверено grep'ом): **4 перегрузки** `run(...)`:
  - `run(Path projectDir, String xmlFileName, String baseUrl)`
  - `run(..., Consumer<String> lineConsumer)`
  - `run(..., lineConsumer, String testFilter)`
  - `run(..., lineConsumer, testFilter, boolean fastMode)`

  Плюс `getLastMavenOutput(): String`. В дипломе **должны быть все 4 перегрузки**, иначе теряется описание Surefire-фильтра (`testFilter`) и fastMode.

#### Действия для пакета «Generator»

1. **CRITICAL:** В табл. 66 заменить `ReportService` на `RunReportWriter` (если он там фигурирует).
2. **CRITICAL:** Заменить табл. 78–79 (`ReportService`) на описание `RunReportWriter`.
3. **CRITICAL:** В табл. 67 (поля `TestConfig`) дописать: `siteType`, `subsystemName`, `testLevel`, `smokeAllSubsystems`.
4. **CRITICAL:** В табл. 70 (методы `TestGenerator`) упомянуть классификацию через `EntityClassifier` и спец-метод `writeChildTest` (или зафиксировать, что для CHILD генерация идёт особым путём).
5. **CRITICAL:** В табл. 73 (методы `TestClassWriter`) удалить упоминание `testMaskedFieldInput`, если присутствует; добавить (или подтвердить наличие) `testFieldsPresent, testRequiredFieldValidation, testPartialRequiredFieldValidation, testSearch*, testGrid*`.
6. **CRITICAL:** В табл. 77 (методы `TestRunner`) показать все 4 перегрузки `run(...)` (а не только базовый вариант).
7. **MINOR:** В описании `PageObjectWriter.fillPropertyGridField` (если есть) подчеркнуть Strategy A (`rec.set('value', v)`) + Fallback на DOM-редактор. См. `PageObjectWriter.java` строка 71+ и комментарии в коде.
8. **MINOR:** В описании `pom.xml` (`generatePom`) показать, что зависимости генерируемого проекта: Selenium 4.15, JUnit 5.10.1, WebDriverManager 5.6.2, Surefire 3.2.2.

---

### 1.5.2.21–1.5.2.25. Пакет «Data» (параграфы 822–878, табл. 80–84)

**Статус: MINOR** ⚠

В коде пакет `data` содержит **только 1 класс** — `ReportDao.java`. Классы `TestRunResult` и `TestCaseResult` живут в пакете `model`, а не `data`.

#### Что нужно проверить

- **Табл. 80** (классы пакета «Data») — **УСТАРЕЛО**. После декомпозиции в пакете **5 классов**: `ReportDao` (фасад), `DatabaseConnection`, `SchemaInitializer`, `TestRunDao`, `TestCaseDao`. Если в дипломе перечислены `ReportService`, `ReportDao` и т.п. — полностью заменить. DTO `TestRunResult` / `TestCaseResult` остаются в пакете `model` (см. табл. 46). См. актуальную таблицу классов в `TABLES_vers2.md` §1.5.
- **Табл. 81–82** (поля/методы `ReportDao`) — **УСТАРЕЛО**. После рефакторинга на 5 классов нужно переписать как **отдельный набор таблиц на каждый из 5 классов**: поля (DEFAULT_URL/url, connection и т.д.) — формат «Название \| Тип \| Описание», методы — формат «Название \| Параметры \| Возвращаемое значение \| Описание». Готовые таблицы см. в `TABLES_vers2.md` §2.19 (5 таблиц полей) и §3.22 (5 таблиц методов).
- **Табл. 83–84** (`TestRunResult` / `TestCaseResult`) — можно оставить здесь же (как DTO для пакета data), но **лучше пометить, что физически класс лежит в `model`**.

#### Действия для пакета «Data»

1. **CRITICAL:** Если в табл. 80 фигурирует `ReportService` — удалить, оставить только `ReportDao`.
2. **MINOR:** Указать сноской, что DTO `TestRunResult` / `TestCaseResult` физически лежат в пакете `model`.
3. **MINOR:** Проверить, что в табл. 82 (методы ReportDao) есть `initDatabase` и `getConnection` (приватные) — это важно для понимания graceful degradation при сбое SQLite.

---

### 1.5.2.26–1.5.2.30. Пакет «Common» (параграфы 880–939, табл. 85–90)

**Статус: OK** ✅

В пакете `common/` 3 класса: `JavaFileWriter, Transliterator, ParserException`. Соответствует описанию.

**Что верифицировано** (по табл. 85–90 в дипломе):
- `JavaFileWriter`: поле `sb: StringBuilder`, `indentLevel: int`, `INDENT` — соответствует.
- `JavaFileWriter` методы: `writeLine, openBlock, closeBlock, indent/unindent, writeToFile, toString` — соответствует. Не забыть, что они fluent (возвращают `JavaFileWriter`).
- `Transliterator`: поле `MAPPING: Map<Character, String>` — соответствует.
- `Transliterator` методы: `toClassName, toMethodName, toFieldName, transliterate, snakeToCamel` — соответствует.
- `ParserException`: extends Exception, 2 конструктора — соответствует.

**Правок не требуется.**

> Если в табл. 89 (методы Transliterator) указан только 1 метод — добавить `toFieldName(attrName)`, `snakeToCamel(snake)` (см. `TABLES_vers2.md` §3.19).

---

## Раздел 1.5.3. Диаграмма компонентов и модульная структура (параграфы 940–960, табл. 91–92)

**Статус: CRITICAL** ❌❌❌ (самый «больной» раздел)

### Таблица 91 — Описание компонентов системы (TBL_IDX 129)

**Текущее содержимое:**

| Наименование | Назначение | Входные данные | Выходные данные |
| ------------ | ---------- | --------------- | --------------- |
| `AutotestGeneratorApp.exe` | Основное приложение… | XML-файл, параметры | Сгенерированные файлы, результаты тестов, записи в БД |
| `JavaFX.jar` | Библиотеки UI | — | Классы UI-фреймворка |
| **`Selenium.jar`** | **Библиотеки для автоматизации браузера при выполнении UI-тестов** | — | Классы для управления браузером |
| **`JUnit.jar`** | **Тестовый фреймворк для выполнения автотестов** | — | Механизмы запуска тестов |
| `SQLiteJDBC.jar` | Драйвер SQLite | — | Возможность выполнения SQL-запросов |

**Ошибка:** `Selenium.jar` и `JUnit.jar` **не являются зависимостями нашей программы** — они зависимости **сгенерированного Maven-проекта автотестов**. В нашем `pom.xml` (вы можете увидеть это в `/home/user/AutoTestGeneratorUpdate/pom.xml`) есть только: JavaFX (`javafx-controls`, `javafx-fxml`) и `sqlite-jdbc`. Никаких Selenium и JUnit в нашем проекте нет.

**Что сделать:**
1. **Удалить** строки `Selenium.jar` и `JUnit.jar` из табл. 91.
2. Если нужно — добавить отдельный «компонент» с пометкой «**Внешние артефакты (создаются программой):**» и перечислить там зависимости генерируемого проекта (Selenium 4.15, JUnit 5.10.1, WebDriverManager 5.6.2, Surefire 3.2.2 — поскольку это они выезжают в сгенерированный `pom.xml`).

**Также:** упоминание `AutotestGeneratorApp.exe` — наша программа не `.exe`, а `.jar`+JavaFX (запускается через `mvn javafx:run` или собирается в shaded-jar). Лучше: `autotestgenerator.jar` (это `artifactId` из `pom.xml`).

Дополнительно: рекомендуется добавить компонент **`autotestgen.db`** (SQLite-файл с историей), который реально создаётся в рабочем каталоге `ReportDao` при первом запуске.

### Таблица 92 — Спецификация модулей программы (TBL_IDX 131, 132)

**Текущее содержимое** включает 35+ модулей, из которых:
- ✅ Корректно перечислены: `App.java, MainController.java, TestCaseRow.java, AppModel.java, EntityObject.java, EntityKind.java, EntityClassifier.java, PropertyGroup.java, Property.java, Operation.java, OperationParam.java, Modifier.java, ModifyType.java, Association.java, Search.java, SearchParam.java, SearchResult.java, SearchResultProperty.java, AttrType.java, TestConfig.java, TestGenerator.java, PageObjectWriter.java, TestClassWriter.java, TestDataFactory.java, TestRunner.java, RunReportWriter.java, ReportDao.java, TestRunResult.java, TestCaseResult.java, JavaFileWriter.java, Transliterator.java, ParserException.java`.
- ❌ **Лишние** (классы удалены или не существуют): `CliMain.java`, `CliRunner.java`. Удалить.
- ❌ **Лишний модуль `XmlModelParser.java` без указания других парсеров**. На самом деле сейчас 6 модулей в пакете `parser`. Нужно добавить: `EntityParser.java, PropertyGroupParser.java, SearchParser.java, StaxUtils.java, XmlNamespaces.java`.

**Правка:**

Удалить:
- `CliMain.java` (нет такого файла)
- `CliRunner.java` (нет такого файла)

Заменить строку `XmlModelParser.java` тремя строками (или добавить ещё 5 новых строк):
- `XmlModelParser.java` | XML-файл (путь) | AppModel | Фасад StAX-парсера; диспетчеризует Category/Object/Searches.
- **`EntityParser.java`** | `XMLStreamReader` | `EntityObject` или `Association` | Парсер `<Object>` и `<AssociationObjectA>`.
- **`PropertyGroupParser.java`** | `XMLStreamReader` | `PropertyGroup` | Парсер `<Properties>` со всеми `<Property>` и `<Operation>`.
- **`SearchParser.java`** | `XMLStreamReader` | `List<Search>` | Парсер `<Searches>`.
- **`StaxUtils.java`** | `XMLStreamReader`, имя атрибута | значение / int / `void` | Утилиты StAX: `attr, parseInt, skipToEnd`.
- **`XmlNamespaces.java`** | — | константы строк | Константы NS-URI формата E3Core.

Также добавить (если нет):
- **`SharedDriver`** — это **не модуль нашей программы**, это генерируемый класс. Не нужно его упоминать в табл. 92, потому что табл. 92 — про наши модули.
- **`BaseTest`** / **`TestData`** — тоже генерируемые, не наши модули.

#### Параграф 953

«Модульная структура системы включает **32** класса, распределённых по шести пакетам.»

Реально сейчас:
- ui: 2 (App, MainController) + вложенный TestCaseRow = 3
- model: 16 классов + 2 enum + 2 inner (Classification, StepTiming) = 20
- parser: 6
- common: 3
- data: 1
- generator: 7
- **Итого: 40 (с inner classes и enum)** или **≈ 32 (только основные)**.

Если считать без вложенных и enum — действительно ≈ 32. Это число **примерно совпадает**, но нужно скоординировать с табл. 92 (модули) и явно указать, что считается.

### Действия для раздела 1.5.3

1. **CRITICAL:** Удалить `Selenium.jar` и `JUnit.jar` из табл. 91 (или перенести в раздел «зависимости генерируемого проекта»).
2. **CRITICAL:** Переименовать `AutotestGeneratorApp.exe` → `autotestgenerator.jar` (или просто «AutoTestGenerator (JavaFX)»).
3. **CRITICAL:** Добавить в табл. 91 компонент `autotestgen.db` (SQLite-файл истории).
4. **CRITICAL:** Удалить из табл. 92 строки `CliMain.java` и `CliRunner.java`.
5. **CRITICAL:** Добавить в табл. 92 пять новых строк: `EntityParser.java, PropertyGroupParser.java, SearchParser.java, StaxUtils.java, XmlNamespaces.java`.
6. **MINOR:** Сверить число «32 класса» в пар. 953 с реальным числом из обновлённой табл. 92.
7. **MINOR:** Возможно стоит перерисовать рис. 55 (диаграмма компонентов) — убрать Selenium/JUnit и добавить SQLite-файл.
8. **MINOR:** Возможно стоит перерисовать рис. 56 (модульная структура) — показать 6 классов парсера вместо 1.

---

## Раздел 1.5.4. Диаграмма размещения (параграфы 962–971)

**Статус: OK** ✅

Описание корректно: «приложение размещается на сервере тестирования», XML-файл, WebDriver, Chrome, SQLite — всё локально, тестируемое веб-приложение — через Интернет.

**Опционально:** упомянуть, что в режиме `fastMode` поднимаются 3 параллельных Chrome-инстанса.

---

## Раздел 1.6. Проектирование интерфейса пользователя (параграфы 972–1010, табл. 93–96)

**Статус: MINOR** ⚠

#### Табл. 93 (состояния интерфейса)

Состояния `S0..S4` — корректны.

#### Табл. 95 (элементы формы главного окна, TBL_IDX 136)

Сейчас в табл. 95 19 элементов. Реально в `MainController.java` элементов больше:
- 5 ввод (xmlPathField, urlField, loginField, passwordField, outputDirField) — есть
- ComboBox-ы: `testLevelCombo`, `siteTypeCombo` — должны быть, проверить
- CheckBox-ы: `smokeAllSubsystemsCheck`, `fastModeCheck` — должны быть, проверить
- Поле `subsystemField` — должно быть
- 7 кнопок: `btnSelectXml, btnSelectOutputDir, btnParse, btnGenerate, btnRunTests, btnRunSelected, btnShowHistory` — должны быть все 7. **Проверьте, что есть `btnRunSelected` — раньше его не было.**

#### Действия для раздела 1.6

1. **CRITICAL:** В табл. 95 добавить (если отсутствуют): `testLevelCombo, smokeAllSubsystemsCheck, fastModeCheck, btnRunSelected, siteTypeCombo, subsystemField`.
2. **MINOR:** В рис. 58 (макет окна) показать `btnRunSelected` («Выбрать тесты…») и выпадающий список уровня тестов.
3. **MINOR:** Описать новую функцию «диалог выбора подмножества тестов с live-превью Surefire-фильтра».

---

## Раздел 1.7. Стратегия тестирования (параграфы 1012–1106, табл. 97–99)

**Статус: CRITICAL** ❌

### Раздел 1.7.2.1 (функциональные характеристики) — параграф 1033

Сейчас: «**консольный режим работы без графического интерфейса**».

**Удалить эту строку**, так как CLI больше нет (`CliMain` и `CliRunner` удалены).

### Таблица 97 — Тестирование модулей (TBL_IDX 138–141)

Содержит 22 строки. Из них:
- ✅ Корректно тестируется: `XmlModelParser, Transliterator, JavaFileWriter, TestDataFactory, PageObjectWriter, TestClassWriter, TestGenerator, TestRunner, ReportDao, MainController`.
- ❌ **Тестируется несуществующий класс `ReportService.java`** (последняя строка в TBL_IDX 141): «`ReportService.java | Тестировщик | Функциональное | Успех, фасад корректно инкапсулирует работу с ReportDao`». Этого класса нет — **удалить строку или переписать как тест на `RunReportWriter`** («Успех, HTML-отчёт v5 корректно генерируется по результатам прогона»).
- ❌ **Тестируется несуществующий класс `CliRunner.java`** (последняя строка в TBL_IDX 141): «`CliRunner.java | Тестировщик | Функциональное | Успех, консольный режим выполняет полный цикл без GUI`». **Удалить строку.**
- 🟡 **Только один модуль `XmlModelParser.java` тестируется.** Нужно добавить тестирование выделенных классов (хотя бы по одной строке):
  - `EntityParser.java`
  - `PropertyGroupParser.java`
  - `SearchParser.java`
  - `EntityClassifier.java` (особенно важно — это новый алгоритм)

### Таблица 98 — Тестирование функциональных требований (TBL_IDX 142, 143)

Проверить, что:
- ✅ «Загрузка XML-файла через GUI или **CLI**» — **убрать «или CLI»**, так как CLI больше нет.
- ✅ «Классификация сущностей по категориям» и «Формирование отчёта entity-classification.csv» — корректно (EntityClassifier).
- ❌ «Конфигурирование параметров работы системы … как через графический интерфейс, так и **через интерфейс командной строки**» — **убрать про CLI**.

### Таблица 99 — Тестирование требований к надёжности (TBL_IDX 144, 145)

В целом корректна. Проверьте, что:
- «Защита от одновременного запуска с одинаковым каталогом» — это требование заявлено в ТЗ, но в коде явной блокировки нет (генератор просто перезаписывает файлы — см. `TestGenerator.generate()`: «Wipe previously-generated test classes»). Если хотите остаться в правде — переписать как «Защита от потери ранее сгенерированных файлов: при повторной генерации старые файлы удаляются перед записью новых».

### Действия для раздела 1.7

1. **CRITICAL:** Пар. 1033 — удалить пункт про консольный режим.
2. **CRITICAL:** Табл. 97 — удалить строки про `CliRunner.java` и `ReportService.java`.
3. **CRITICAL:** Табл. 97 — добавить хотя бы 4 строки для новых классов парсера и `EntityClassifier`.
4. **CRITICAL:** Табл. 98 — убрать упоминания CLI в формулировках требований.
5. **MINOR:** Табл. 99 — уточнить формулировку про «защиту от одновременного запуска» (либо удалить, либо переформулировать как «перезапись при повторной генерации»).

---

## Раздел 2. ТЭО (параграфы 1107–1298)

**Статус: OK** ✅

ТЭО полностью самодостаточно: организация работ, ресурсы, диаграмма Ганта, критический путь, расчёт цены ПП и экономической эффективности. Формулы и числа не зависят от технических деталей реализации.

**Замечание:** время разработки 768 часов × 2 (программист + руководитель) — это допустимое предположение, не проверяемое из кода. Цифра 19 сущностей / 40+ классов / Pascal-кейс-конвенции совпадает с реальностью.

**Правок не требуется.**

---

## Раздел «Заключение» (параграфы 1299–1313)

**Статус: OK** ✅

Корректно перечислены: JavaFX 24, Selenium 4.15, JUnit 5.10, SQLite 3.42. Все компетенции по программе обучения упомянуты. Никаких следов CLI.

**Опционально (стилистика):**
- В пар. 1302 написано «**встраиваемой СУБД SQLite 3.42**» — в `pom.xml` версия `3.42.0.0`. Корректно.
- Можно добавить упоминание новых функций: классификация сущностей, HTML-отчёт v5, fastMode, уровни покрытия.

---

## Раздел «Список литературы» (параграфы 1315–1342)

**Статус: OK** ✅

27 источников включая UML (Бабич, Буч, Леоненков, Фаулер), тестирование (Бек, Месарош, Канер), технологии (Maven, JavaFX, JUnit 5, Selenium, SQLite, StAX, Page Object, Java SE 17), ГОСТы ЕСПД. Достаточно для ВКР.

**Правок не требуется.**

> Опционально можно добавить:
> - Constantine L. Structured Design (книга, по которой строится модульная структура — раздел 1.5.3).
> - WebDriverManager Boni García (Selenium-related утилита, используется в SharedDriver).

---

## Приложение 1. Техническое задание (параграфы 1343–1506)

**Статус: CRITICAL** ❌

Несколько мест прямо противоречат тому, что реализовано:

#### Пар. 1407

«Загрузка XML-файла, описывающего модель тестируемого веб-приложения, **через графический интерфейс приложения или через интерфейс командной строки**.»

**Удалить «или через интерфейс командной строки»**, оставив только GUI.

#### Пар. 1410

«Автоматическая классификация сущностей XML-модели по трём логическим категориям: основные бизнес-сущности с собственной точкой входа в меню; дочерние сущности, существующие как вкладки внутри карточки родительской записи; справочники-перечисления, доступные только через выбор значения внешнего ключа.»

**Корректно** — это точное описание `EntityKind.PRIMARY / CHILD / REFERENCE_DICTIONARY`. ✅

#### Пар. 1411

«Формирование отчётного файла с описанием результата классификации каждой сущности и обоснованием отнесения её к соответствующей категории.»

**Корректно** — это `entity-classification.csv` (см. `TestGenerator.generate()`, который пишет `csvPath = outputDir.resolve("entity-classification.csv")`). ✅

#### Пар. 1420

«Конфигурирование основных параметров работы системы (адрес тестового стенда, учётные данные доступа, имя обходимой подсистемы, уровень покрытия тестов, каталог выходных артефактов) **как через графический интерфейс, так и через интерфейс командной строки**.»

**Убрать «так и через интерфейс командной строки»**.

#### Пар. 1429

«параметр типа сайта должен принимать значение **e3core**.»

**Корректно?** — В коде `TestConfig.setSiteType("e3core" / "generic" / "custom")` (см. `MainController.onGenerate` строка 224). То есть допустимы три значения. **Дополнить:**
> «параметр типа сайта должен принимать одно из значений: `e3core`, `generic`, `custom`.»

#### Пар. 1437–1444 (требования к выходным данным)

Все корректны. Особенно:
- Пар. 1444: «артефакты, образуемые при выполнении сгенерированных тестов (скриншоты, surefire-отчёты), должны сохраняться в каталог `target/` Maven-проекта: скриншоты — в `target/screenshots/` в формате PNG, surefire-отчёты — в `target/surefire-reports/` в форматах XML и TXT.» ✅ — соответствует коду.
- Пар. 1445: «код возврата программы-генератора должен принимать значение 0 при успешном завершении и ненулевое значение при ошибке…» — **в GUI этот тезис не имеет смысла**. Можно сохранить как наследие, но текущая программа возвращает 0 всегда (нет CLI).

#### Пар. 1481

«Программное обеспечение должно быть разработано с использованием: Java 17+, **Maven, JUnit 5, Selenium**, формат входных — XML, выходных — Java-классы, отчёты XML/HTML.»

**Корректно**, но **JUnit 5 и Selenium — это зависимости генерируемого проекта**, а не нашего. Можно либо переформулировать как:
> «генерируемый проект автотестов должен использовать JUnit 5, Selenium WebDriver и Maven как стандартный инструментарий».

Либо оставить как есть (там перечислены технологии, относящиеся к экосистеме разработки).

### Действия для Приложения 1

1. **CRITICAL:** Удалить упоминания CLI в пар. 1407, 1420.
2. **MINOR:** В пар. 1429 дописать `generic, custom` к значениям `siteType`.
3. **MINOR:** В пар. 1445 либо удалить, либо переформулировать (про exit-код).
4. **MINOR:** В пар. 1481 уточнить, что Selenium и JUnit относятся к генерируемому проекту.

---

## Приложение 2. Текст программы (параграфы 1507–1827)

**Статус: CRITICAL** ❌ (если приложение содержит исходные тексты)

Приложение «Текст программы» обычно содержит **полный листинг всех Java-классов**. Если оно действительно содержит листинги:

1. **Полностью удалить** листинги `CliMain.java` и `CliRunner.java` (если присутствуют).
2. **Заменить** старый листинг «толстого» `XmlModelParser.java` на 6 новых файлов:
   - `XmlModelParser.java` (фасад, ~67 строк)
   - `EntityParser.java`
   - `PropertyGroupParser.java`
   - `SearchParser.java`
   - `StaxUtils.java`
   - `XmlNamespaces.java`
3. **Обновить листинг `App.java`** до текущего короткого варианта (без флага `--cli`).
4. **Обновить листинг `MainController.java`** — добавить недавно появившиеся поля и методы (`testLevelCombo, smokeAllSubsystemsCheck, fastModeCheck, btnRunSelected, TEST_CATEGORIES, onRunSelected, buildTestFilter, launchRun`).
5. **Удалить любые упоминания `ReportService.java`** в листингах — нет такого класса.
6. **Обновить листинг `pom.xml`** — убрать `exec-maven-plugin` (если есть) и подтвердить, что зависимости только `javafx-controls, javafx-fxml, sqlite-jdbc`.
7. **Добавить листинги** новых классов: `EntityClassifier.java`, `EntityKind.java`, `RunReportWriter.java` (если их раньше не было).

> Без полного просмотра содержимого Приложения 2 точный список замен не дать — но как минимум проверьте присутствие/отсутствие классов по списку выше.

### Действия для Приложения 2

1. **CRITICAL:** Сверить состав листингов со списком из 37 файлов в `src/main/java/ru/autotestgen/`.
2. **CRITICAL:** Удалить листинги `CliMain.java`, `CliRunner.java`, `ReportService.java`.
3. **CRITICAL:** Добавить листинги `EntityClassifier.java`, `EntityKind.java`, `RunReportWriter.java`, `EntityParser.java`, `PropertyGroupParser.java`, `SearchParser.java`, `StaxUtils.java`, `XmlNamespaces.java` (если их там нет).
4. **CRITICAL:** Обновить листинг `App.java` (короткий вариант, без `--cli`).
5. **CRITICAL:** Обновить листинг `MainController.java` (добавить новые поля/методы).

---

## Приложение 3. Спецификация (параграфы 1828–1834, табл. П3.1)

**Статус: CRITICAL** ❌

Таблица П3.1 (TBL_IDX 168, 169) — спецификация на разработанную программную документацию и ПО.

В блоке «Компоненты» (TBL_IDX 168):
- ✅ `App.java`, `MainController.java`, `main.fxml`, `XmlModelParser.java`, `AppModel.java` — корректно.
- ❌ **`CliMain.java`**, **`CliRunner.java`** — удалить, классов нет.

В блоке «Компоненты» (TBL_IDX 169):
- ✅ `EntityObject.java, EntityKind.java, EntityClassifier.java, Association.java, PropertyGroup.java, Property.java, AttrType.java, Operation.java, OperationParam.java, Modifier.java, ModifyType.java, Search.java, SearchParam.java, SearchResult.java, SearchResultProperty.java, TestRunResult.java, TestCaseResult.java, TestGenerator.java, TestClassWriter.java, PageObjectWriter.java, TestDataFactory.java, TestConfig.java, TestRunner.java, RunReportWriter.java, ReportDao.java, JavaFileWriter.java, Transliterator.java, ParserException.java` — корректно.

❌ **Отсутствуют 5 классов парсера**: `EntityParser.java, PropertyGroupParser.java, SearchParser.java, StaxUtils.java, XmlNamespaces.java`. Добавить.

### Действия для Приложения 3

1. **CRITICAL:** Удалить `CliMain.java` и `CliRunner.java` из табл. П3.1.
2. **CRITICAL:** Добавить в табл. П3.1: `EntityParser.java, PropertyGroupParser.java, SearchParser.java, StaxUtils.java, XmlNamespaces.java`.

---

## Приложение 4. Руководство пользователя (параграфы 1835–1925)

**Статус: MINOR / частично OK** ⚠

#### Раздел 4. Запуск программы (пар. 1868–1871)

«Для запуска приложения с графическим интерфейсом JavaFX необходимо выполнить команду: `mvn javafx:run`»

**Корректно.** Никаких упоминаний `mvn exec:java --cli ...` или подобного быть не должно.

#### Раздел 5. Работа с программой (пар. 1873+)

Описание главного окна — соответствует реальности.

«После заполнения поля «XML-файл» нажмите кнопку «Разобрать XML» (зелёная кнопка в верхней панели). Система выполнит потоковый разбор XML-файла **в фоновом потоке**.»

⚠ В коде `MainController.onParse()` парсинг выполняется **синхронно**, не в фоне (метод не использует `Task`). Это **расхождение между документацией и кодом**. Либо переписать руководство («выполняет потоковый разбор без блокировки окна»), либо доработать `onParse` и вынести в `Task`. **Рекомендация для дипломной работы:** написать в руководстве «синхронно, обычно занимает менее секунды» — это правда.

#### Раздел 5.4. Генерация (пар. 1898–1905)

Корректно: упомянуты Selenium WebDriver 4.15, JUnit 5.10, WebDriverManager 5.6, SharedDriver, BaseTest, TestData.

#### Раздел 5.5. Запуск тестов (пар. 1907–1915)

Корректно. Упоминается тайм-аут 10 минут — соответствует коду (`TestRunner` использует `process.waitFor(10, MINUTES)`).

⚠ **Не упомянуты ключевые новые функции:**
1. **Выбор уровня тестов** (SMOKE / BASIC / FULL) через `testLevelCombo`.
2. **Кнопка «Выбрать тесты…»** (`btnRunSelected`) — открывает диалог с чекбоксами сущностей и видов тестов.
3. **Чекбокс fastMode** — параллельный headless Chrome ×3.
4. **Чекбокс smokeAllSubsystems** — прогон smoke по всем подсистемам перед основными тестами.
5. **HTML-отчёт v5** в `target/run-report.html` и CSV в `target/run-report.csv`.
6. **Скриншоты** в `target/screenshots/` (особенно при падениях навигации).

### Действия для Приложения 4

1. **CRITICAL:** Добавить раздел 5.5.1 (или новый 5.6) про выбор уровня тестов SMOKE/BASIC/FULL.
2. **CRITICAL:** Добавить раздел 5.5.2 про диалог «Выбрать тесты…» (с скриншотом, если возможно).
3. **CRITICAL:** Описать чекбоксы fastMode и smokeAllSubsystems.
4. **MINOR:** Добавить раздел про артефакты прогона: HTML-отчёт v5, CSV, скриншоты.
5. **MINOR:** В пар. 1891 заменить «выполнит потоковый разбор XML-файла в фоновом потоке» на «выполнит потоковый разбор XML-файла (синхронно, обычно <1 с)».

---

## Сводная таблица обязательных правок

★★★ — критическая правка (без неё текст диплома противоречит коду)
★★  — важная (теряется ключевой функционал в описании)
★   — косметическая / стилистическая

| # | Раздел | Что в дипломе | Что должно быть | Приоритет |
| -- | ----- | ------------- | --------------- | --------- |
| 1 | Табл. 36 (1.5.1) | «`ui` … CLI-раннер, точки входа» | «`ui` … точка входа `App`» (без CLI) | ★★★ |
| 2 | Табл. 36 (1.5.1) | «`generator` … TestRunner, **ReportService**» | «`generator` … TestRunner, **RunReportWriter**» | ★★★ |
| 3 | Табл. 37 (1.5.2.1) | 3 строки: App, CliMain, CliRunner | 3 строки: App, MainController, MainController.TestCaseRow | ★★★ |
| 4 | Табл. 38 (1.5.2.5) | `main` проверяет флаг `--cli` и делегирует CliRunner | `main` вызывает `launch(args)` | ★★★ |
| 5 | Табл. 39, 40 | Описание методов CliMain и CliRunner | Удалить таблицы (классов нет) | ★★★ |
| 6 | Табл. 41 (1.5.2.5) | поле `reportService: ReportService` | поле `reportDao: final ReportDao` | ★★★ |
| 7 | Табл. 41 (1.5.2.5) | Нет полей: testLevelCombo, smokeAllSubsystemsCheck, fastModeCheck, btnRunSelected, TEST_CATEGORIES | Добавить 5 полей | ★★★ |
| 8 | Табл. 42 (1.5.2.5) | `onRunTests` сохраняет через `reportService.save()`; `onShowHistory` использует `reportService.loadHistory()` | через `reportDao.saveRun()` / `reportDao.getAllRuns()` | ★★★ |
| 9 | Табл. 42 (1.5.2.5) | Нет методов: onRunSelected, buildTestFilter, launchRun, getXmlFileName | Добавить 4 метода | ★★★ |
| 10 | Табл. 43 (1.5.2.6) | 1 класс XmlModelParser (толстый) | 6 классов: XmlModelParser, EntityParser, PropertyGroupParser, SearchParser, StaxUtils, XmlNamespaces | ★★★ |
| 11 | Табл. 44, 45 (1.5.2.10) | Все парсер-методы и NS-константы внутри XmlModelParser | Разделить на 7 таблиц по 6 классам | ★★★ |
| 12 | Рис. 20, 25, 26 (1.5.2.6, 1.5.2.9, 1.5.2.10) | Один класс XmlModelParser | 6 классов с композицией ♦ и зависимостями | ★★★ |
| 13 | Рис. 21–24 (1.5.2.7, 1.5.2.8) | Один объект «парсер» | Делегирование от XmlModelParser к EntityParser/SearchParser/PropertyGroupParser | ★★★ |
| 14 | Табл. 46 (1.5.2.11) | Нет EntityKind, EntityClassifier, Classification | Добавить 3 класса/enum | ★★★ |
| 15 | Новая таблица в 1.5.2.15 | (нет) | Поля и методы EntityClassifier + Classification | ★★★ |
| 16 | Раздел 1.3 (анализ алгоритмов) | Не упомянут EntityClassifier | Добавить алгоритм классификации сущностей | ★★★ |
| 17 | Раздел 1.3, пар. 196 | testLevel не упомянут | Добавить SMOKE / BASIC / FULL | ★★ |
| 18 | Раздел 1.3, пар. 197 | fastMode не упомянут | Добавить про 3×headless Chrome | ★★ |
| 19 | Табл. 66 (1.5.2.16) | 7-й класс — ReportService | 7-й класс — RunReportWriter | ★★★ |
| 20 | Табл. 67 (1.5.2.20) | Нет полей siteType, subsystemName, testLevel, smokeAllSubsystems | Добавить 4 поля TestConfig | ★★★ |
| 21 | Табл. 70 (методы TestGenerator) | Не упомянута классификация и writeChildTest | Добавить упоминания EntityClassifier и спец-метода для CHILD | ★★ |
| 22 | Табл. 73 (методы TestClassWriter) | Может упоминаться testMaskedFieldInput; не все актуальные тесты | Удалить testMaskedFieldInput; добавить testFieldsPresent, testRequiredFieldValidation, testPartialRequiredFieldValidation, testSearch*, testGrid* | ★★★ |
| 23 | Табл. 77 (методы TestRunner) | 1 метод run(...) | 4 перегрузки run(...) + getLastMavenOutput() | ★★★ |
| 24 | Табл. 78–79 (1.5.2.20) | Поля и методы ReportService | Удалить → заменить на RunReportWriter | ★★★ |
| 25 | Раздел 1.5.2.20 (опционально) | fillPropertyGridField описан старо | Подчеркнуть Strategy A (`rec.set('value', v)`) + DOM-fallback | ★ |
| 26 | Табл. 91 (1.5.3) | Selenium.jar, JUnit.jar как зависимости нашей программы | Удалить (это зависимости генерируемого проекта) | ★★★ |
| 27 | Табл. 91 (1.5.3) | AutotestGeneratorApp.exe | autotestgenerator.jar | ★★ |
| 28 | Табл. 91 (1.5.3) | (нет) | Добавить autotestgen.db (SQLite-файл истории) | ★★ |
| 29 | Табл. 92 (1.5.3) | CliMain.java, CliRunner.java | Удалить (нет таких классов) | ★★★ |
| 30 | Табл. 92 (1.5.3) | Только XmlModelParser.java | Добавить 5 новых: EntityParser, PropertyGroupParser, SearchParser, StaxUtils, XmlNamespaces | ★★★ |
| 31 | Пар. 953 (1.5.3) | «32 класса» | Пересчитать после обновления табл. 92 | ★ |
| 32 | Табл. 95 (1.6.2) | Не все элементы | Добавить testLevelCombo, smokeAllSubsystemsCheck, fastModeCheck, btnRunSelected, siteTypeCombo, subsystemField | ★★★ |
| 33 | Пар. 1033 (1.7.2.1) | «консольный режим работы без графического интерфейса» | Удалить пункт | ★★★ |
| 34 | Табл. 97 (1.7.4) | Тесты CliRunner.java и ReportService.java | Удалить | ★★★ |
| 35 | Табл. 97 (1.7.4) | Только XmlModelParser в тестах парсера | Добавить тесты EntityParser, PropertyGroupParser, SearchParser, EntityClassifier | ★★★ |
| 36 | Табл. 98 (1.7.4) | «через GUI или CLI» | «через GUI» | ★★★ |
| 37 | Табл. 99 (1.7.4) | «Защита от одновременного запуска с одинаковым каталогом» | Переформулировать («перезапись при повторной генерации») | ★ |
| 38 | Прил. 1 (ТЗ), пар. 1407 | «через GUI или CLI» | «через GUI» | ★★★ |
| 39 | Прил. 1 (ТЗ), пар. 1420 | «через GUI и CLI» | «через GUI» | ★★★ |
| 40 | Прил. 1 (ТЗ), пар. 1429 | «параметр типа сайта — e3core» | «e3core, generic, custom» | ★★ |
| 41 | Прил. 1 (ТЗ), пар. 1445 | про exit-код | Удалить или переформулировать (GUI exit-кодов не возвращает) | ★ |
| 42 | Прил. 2 (текст программы) | Старые листинги XmlModelParser, App с CLI; листинги CliMain, CliRunner, ReportService | Удалить старые; добавить новые: 6 парсер-классов, App без CLI, EntityClassifier, EntityKind, RunReportWriter | ★★★ |
| 43 | Прил. 3 (спецификация), табл. П3.1 | CliMain.java, CliRunner.java | Удалить | ★★★ |
| 44 | Прил. 3 (спецификация), табл. П3.1 | Нет 5 классов парсера | Добавить EntityParser, PropertyGroupParser, SearchParser, StaxUtils, XmlNamespaces | ★★★ |
| 45 | Прил. 4 (руководство), раздел 5.5 | Нет упоминания SMOKE/BASIC/FULL, fastMode, smokeAllSubsystems, btnRunSelected, HTML-отчёт v5, скриншоты | Добавить новые разделы 5.5.1, 5.5.2, 5.6 | ★★★ |
| 46 | Пар. 1891 (Прил. 4) | «парсинг в фоновом потоке» | «парсинг синхронно (<1 с)» | ★ |
| 47 | Пар. 459 (1.4.6) | «рис. 10» | «рис. 11» | ★ |
| 48 | Раздел 1.2, пар. 162 | «MVC за счёт контроллеров» | «MVC-light: FXML-разметка + контроллер» | ★ |
| 49 | Раздел 1.2, пар. 158 | «единый стек» | «единый стек, в т.ч. запуск mvn test через ProcessBuilder» | ★ |

---

## Порядок выполнения правок

Чтобы не сломать связность повествования и не пришлось переделывать дважды, рекомендуется такой порядок:

### Шаг 1. Глобальные находки/замены (полчаса, страховка от пропусков)

1. **«ReportService» → «ReportDao»** (5 мест: табл. 36, 71, 72, 73, 97). После этого нигде в дипломе не должно остаться слова «ReportService».
2. **«CliMain», «CliRunner», «CLI», «--cli», «mvn exec:java», «консольный режим»** — пройти по всему документу через поиск и удалить/перефразировать (≥ 8 мест: пар. 1033, 1407, 1420, табл. 37–40, 91, 92, 97, 98, П3.1, листинги в Прил. 2).
3. **«testMaskedFieldInput»** — поиск, если есть упоминание (в табл. 73 или тест-таблицах) → удалить.
4. **«AutotestGeneratorApp.exe»** → «autotestgenerator.jar» (или просто «AutoTestGenerator»).
5. **«Selenium.jar»** и **«JUnit.jar»** в табл. 91 — удалить (или перенести в отдельный блок).

### Шаг 2. Структурные правки в разделе 1.5.2 (полдня)

6. Переписать **табл. 37, 41, 42** (UI-пакет): новые классы и поля/методы MainController.
7. Удалить **табл. 39 и 40** (CliMain, CliRunner) → перенумеровать остальные. Это самая болезненная правка по нумерации.
8. Переписать **табл. 43, 44, 45** (Parsing-пакет): 6 классов вместо 1, разделить на ≥ 7 таблиц.
9. Перерисовать **рис. 13, 18, 19** (UI) — убрать CLI-классы, оставить App + MainController + TestCaseRow.
10. Перерисовать **рис. 20, 21, 22, 23, 24, 25, 26** (Parsing) — показать 6 классов и делегирование.

### Шаг 3. Дополнения в раздел 1.5.2 (модель и generator) (полдня)

11. В **табл. 46** (Model) добавить EntityKind, EntityClassifier, Classification.
12. **Создать новые таблицы** с полями и методами EntityClassifier + Classification.
13. **Перерисовать рис. 27, 32, 33** (Model) — добавить EntityClassifier.
14. **Табл. 66, 67, 70, 73, 77, 78, 79** (Generator) — обновить состав классов и методов: TestConfig (+4 поля), TestGenerator (+EntityClassifier), TestClassWriter (новые методы тестов), TestRunner (4 перегрузки), RunReportWriter вместо ReportService.

### Шаг 4. Структурно-композиционные правки (полдня)

15. **Табл. 91 (компоненты)** — убрать лишние jar-зависимости, добавить SQLite-файл.
16. **Табл. 92 (модули)** — удалить CLI, добавить 5 классов парсера.
17. **Рис. 55 (диаграмма компонентов)** и **рис. 56 (модульная структура)** — перерисовать.
18. **Табл. 95 (элементы формы)** — добавить новые контролы.
19. **Рис. 58 (макет окна)** — обновить, показав testLevelCombo, btnRunSelected, чекбоксы.

### Шаг 5. Дополнения функциональных описаний (час-два)

20. В **раздел 1.3** добавить алгоритм классификации (EntityClassifier), уровни тестов, fastMode.
21. В **табл. 97, 98, 99 (тестирование)** удалить ссылки на удалённые классы, добавить новые модули.

### Шаг 6. ТЗ и руководство пользователя (час)

22. В **Приложение 1 (ТЗ)** убрать упоминания CLI (пар. 1407, 1420), уточнить siteType.
23. В **Приложение 4 (руководство)** добавить разделы про уровни тестов, диалог выбора тестов, fastMode, HTML-отчёт v5.

### Шаг 7. Приложения 2 и 3 (полдня)

24. **Приложение 2** — синхронизировать листинги Java-кода с реальным состоянием репозитория. Это **самая трудоёмкая** часть: все 37 файлов должны быть актуальны.
25. **Приложение 3 (спецификация)** — убрать CLI-файлы, добавить 5 классов парсера.

### Шаг 8. Косметика (15 минут)

26. Пар. 459 (рис. 10 → 11), пар. 158, пар. 162 — мелкие текстовые правки.
27. Сквозная проверка ссылок «см. табл. N» после перенумерации.
28. Финальная вычитка на пунктуацию и согласование.

---

## Контрольные точки самопроверки

После каждого шага рекомендуется сверить:

- [ ] **Поиск `CliMain`, `CliRunner`, `--cli`, `mvn exec:java`** → 0 совпадений
- [ ] **Поиск `ReportService`** → 0 совпадений
- [ ] **Поиск `testMaskedFieldInput`** → 0 совпадений
- [ ] **Поиск `XmlModelParser`** даёт ≈ 6 контекстов (фасад + табл. + диаграммы), везде упомянуты как минимум `EntityParser`/`SearchParser` рядом
- [ ] **Поиск `EntityClassifier`** даёт ≥ 4 совпадений (раздел 1.3, табл. 46+новая, табл. 92, П3.1, рис. модели)
- [ ] **Поиск `fastMode`, `SMOKE`, `BASIC`, `FULL`, `smokeAllSubsystems`** даёт ≥ 1 совпадение каждое (раздел 1.3 + руководство пользователя)
- [ ] **Поиск `Selenium.jar`, `JUnit.jar`** в табл. 91 → 0 совпадений (или перенесено в отдельный блок «зависимости генерируемого проекта»)
- [ ] **Число модулей в табл. 92** ≈ 37 (по `find src/main/java -name '*.java' | wc -l`)
- [ ] **Список полей `MainController`** в табл. 41 включает все 30+ полей из `MainController.java`

---

## Что НЕ нужно трогать

Чтобы дипломник не «перестрахрвался» и не переделывал лишнего:

- ✅ **Введение** — формулировки целей и задач корректны.
- ✅ **Раздел 1.1** (сравнительный анализ) — Selenium / Selenide / Katalon / Playwright описаны верно.
- ✅ **Раздел 1.2** (выбор технологий) — стек указан корректно; только мелкие косметические правки.
- ✅ **Раздел 1.4.1** (use-case) — 5 use-case'ов соответствуют GUI.
- ✅ **Раздел 1.4.2** (контекстная диаграмма классов) — на концептуальном уровне разбивка парсера не показывается.
- ✅ **Раздел 1.4.3** (sequence-диаграммы) — соответствуют коду.
- ✅ **Раздел 1.4.4** (диаграмма деятельностей) — концептуальная.
- ✅ **Раздел 1.4.5** (ER-диаграмма) — соответствует `ReportDao`.
- ✅ **Раздел 1.4.6** (state-диаграмма) — корректна (кроме опечатки «рис. 10» → «рис. 11»).
- ✅ **Раздел 1.5.4** (диаграмма размещения) — соответствует.
- ✅ **Пакет «Common»** (1.5.2.26–1.5.2.30) — 3 класса без изменений.
- ✅ **Раздел 2 (ТЭО)** — самодостаточен.
- ✅ **Заключение** — корректно.
- ✅ **Список литературы** — соответствует.

---

## Финальная статистика правок

| Тип | Количество | Время |
| --- | ---------: | ----- |
| Поисково-заменные правки (Шаг 1) | ≈ 25 точек | 30 мин |
| Переписать таблицы (Шаги 2–4) | ≈ 15 таблиц | 5–6 ч |
| Перерисовать диаграммы (Шаги 2–3) | ≈ 12 рисунков | 8–10 ч |
| Дополнения разделов (Шаги 5–6) | ≈ 5 разделов | 2–3 ч |
| Синхронизация Прил. 2 (листинги) | 37 файлов | 4–6 ч |
| Косметика и пересчёт ссылок | ≈ 10 правок | 1 ч |
| **Итого** | | **20–26 часов работы** |

После всех правок диплом будет:
- Согласован с актуальным состоянием кода (vers2).
- Полно отражать все новые механизмы (EntityClassifier, уровни тестов, fastMode, HTML-отчёт v5, Surefire-фильтр, диалог выбора тестов).
- Не содержать упоминаний удалённых сущностей (CliMain, CliRunner, ReportService, testMaskedFieldInput, exec-maven-plugin).
- Корректно описывать декомпозицию парсера на 6 классов (SRP-рефакторинг).
- Корректно перечислять зависимости (без Selenium.jar/JUnit.jar в собственных компонентах).
