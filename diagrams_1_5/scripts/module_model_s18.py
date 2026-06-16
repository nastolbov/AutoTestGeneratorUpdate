# -*- coding: utf-8 -*-
"""Данные 18-модульной версии (старая схема, как в дипломе Столбова) для раздела
модульной структуры в стиле ВКР Сафиуллина: краткая и подробная спецификация
модулей, связность, сцепление, связи для схемы структуры и карты Константайна.
Связность и состав методов — по фактическому коду src/main/java/ru/autotestgen."""

# (ключ, имя, входные данные, выходные данные, описание, тип связности)
MODULES18 = [
    ("App", "App",
     "Аргументы командной строки", "Главное окно (JavaFX)",
     "Точка входа JavaFX-приложения; загрузка главного окна из FXML", "Функциональная"),
    ("MainController", "MainController",
     "Действия пользователя в GUI", "Команды модулям, ход и история прогонов",
     "Контроллер главного окна; координирует разбор, генерацию, прогон и отчёты", "Коммуникационная"),
    ("XmlModelParser", "XmlModelParser",
     "XML-файл метамодели", "Модель AppModel",
     "Фасад потокового разбора XML-метамодели через StAX", "Функциональная"),
    ("EntityParser", "EntityParser",
     "XMLStreamReader, элемент <Object>", "EntityObject",
     "Разбор сущности и её атрибутов", "Функциональная"),
    ("SearchParser", "SearchParser",
     "XMLStreamReader, блок <Searches>", "Список Search",
     "Разбор поисковых блоков (фильтров) и их параметров", "Функциональная"),
    ("PropertyGroupParser", "PropertyGroupParser",
     "XMLStreamReader, блок <Properties>", "PropertyGroup",
     "Разбор групп свойств и операций по пространству имён", "Функциональная"),
    ("StaxUtils", "StaxUtils",
     "XMLStreamReader, имя атрибута", "Строка / число",
     "Библиотека утилит потокового разбора XML (StAX)", "Логическая"),
    ("XmlNamespaces", "XmlNamespaces",
     "—", "Константы NS_E, NS_E3, NS_MD",
     "Библиотека констант пространств имён XML", "Информационная"),
    ("EntityClassifier", "EntityClassifier",
     "AppModel, EntityObject", "Вид: PRIMARY / CHILD / REFERENCE",
     "Классификация сущностей по правилам предметной области", "Функциональная"),
    ("TestConfig", "TestConfig",
     "Параметры от пользователя", "Объект конфигурации",
     "Хранение параметров генерации и прогона (уровень, fastMode, фильтры)", "Информационная"),
    ("TestGenerator", "TestGenerator",
     "AppModel, TestConfig", "Maven-проект автотестов (файлы)",
     "Оркестратор генерации проекта автотестов", "Последовательная"),
    ("PageObjectWriter", "PageObjectWriter",
     "EntityObject, AppModel", "Java-файл Page Object",
     "Генерация объекта страницы (Page Object) для сущности", "Функциональная"),
    ("TestClassWriter", "TestClassWriter",
     "EntityObject, AppModel, уровень", "Java-файл теста (JUnit 5)",
     "Генерация тест-класса в зависимости от уровня тестирования", "Функциональная"),
    ("TestDataFactory", "TestDataFactory",
     "Тип поля, маска, зерно", "Сгенерированное значение",
     "Формирование детерминированных тестовых данных по типам и маскам", "Функциональная"),
    ("TestRunner", "TestRunner",
     "Каталог проекта, фильтр, fastMode", "RunResult, поток вывода",
     "Запуск автотестов (mvn test), потоковый разбор Surefire-отчётов", "Последовательная"),
    ("ReportDao", "ReportDao",
     "RunResult / запрос истории", "id прогона / список прогонов",
     "Сохранение и чтение истории прогонов в БД SQLite", "Информационная"),
    ("JavaFileWriter", "JavaFileWriter",
     "Строки Java-кода, путь", "Java-файл на диске",
     "Библиотека форматированной записи Java-кода с отступами", "Информационная"),
    ("Transliterator", "Transliterator",
     "Русское наименование", "Корректное Java-имя",
     "Библиотека транслитерации кириллицы в Java-идентификаторы", "Логическая"),
]

# сцепление по модулю (как у Сафиуллина — по строке на модуль), по факту из кода
COUPLING18 = {
    "App": "По данным",
    "MainController": "По образцу",
    "XmlModelParser": "По образцу",
    "EntityParser": "По образцу",
    "SearchParser": "По образцу",
    "PropertyGroupParser": "По образцу",
    "StaxUtils": "По данным",
    "XmlNamespaces": "По данным",
    "EntityClassifier": "По управлению",
    "TestConfig": "По данным",
    "TestGenerator": "По образцу",
    "PageObjectWriter": "По образцу",
    "TestClassWriter": "По образцу",
    "TestDataFactory": "По данным",
    "TestRunner": "По образцу",
    "ReportDao": "По образцу",
    "JavaFileWriter": "По данным",
    "Transliterator": "По данным",
}

# подробная спецификация: ключ -> [(название функции/процедуры, параметры, описание)]
METHODS = {
    "App": [
        ("start(Stage primaryStage)", "primaryStage — главное окно", "Загружает FXML, создаёт сцену и показывает главное окно"),
        ("main(String[] args)", "args — аргументы запуска", "Точка входа; запускает JavaFX-приложение"),
    ],
    "MainController": [
        ("initialize()", "—", "Инициализация элементов формы после загрузки FXML"),
        ("onSelectXml()", "—", "Выбор XML-файла метамодели через диалог"),
        ("onSelectOutputDir()", "—", "Выбор каталога для сгенерированного проекта"),
        ("onParse()", "—", "Разбор XML и построение дерева сущностей"),
        ("onGenerate()", "—", "Генерация проекта автотестов"),
        ("onRunTests()", "—", "Запуск всех сгенерированных тестов"),
        ("onRunSelected()", "—", "Запуск выбранного подмножества тестов"),
        ("onShowHistory()", "—", "Загрузка и отображение истории прогонов из БД"),
    ],
    "XmlModelParser": [
        ("parse(File xmlFile)", "xmlFile — файл метамодели", "Разбирает XML-метамодель в объект AppModel, делегируя элементы профильным парсерам"),
    ],
    "EntityParser": [
        ("parseObject(XMLStreamReader reader)", "reader — курсор XML на <Object>", "Разбирает сущность и её атрибуты в EntityObject"),
        ("parseAssociation(XMLStreamReader reader)", "reader — курсор XML", "Разбирает связь <AssociationObjectA> (приватный)"),
    ],
    "SearchParser": [
        ("parseSearches(XMLStreamReader reader)", "reader — курсор на <Searches>", "Разбирает блок поисков в список Search"),
        ("parseSingleSearch(XMLStreamReader reader)", "reader — курсор XML", "Разбирает один <Search> с параметрами и результатом (приватный)"),
    ],
    "PropertyGroupParser": [
        ("parsePropertyGroup(XMLStreamReader reader)", "reader — курсор на <Properties>", "Разбирает группу свойств сущности в PropertyGroup"),
        ("parseProperty(XMLStreamReader reader)", "reader — курсор XML", "Разбирает одно свойство (приватный)"),
        ("parseOperation(XMLStreamReader reader)", "reader — курсор XML", "Разбирает CRUD-операцию с параметрами (приватный)"),
    ],
    "StaxUtils": [
        ("attr(XMLStreamReader reader, String name)", "reader; name — имя атрибута", "Возвращает значение атрибута или пустую строку"),
        ("parseInt(String value)", "value — строка", "Безопасно преобразует строку в целое (0 при ошибке)"),
        ("skipToEnd(XMLStreamReader reader)", "reader — курсор XML", "Перематывает курсор до конца текущего элемента"),
    ],
    "XmlNamespaces": [
        ("NS_E, NS_E3, NS_MD (константы)", "—", "Идентификаторы пространств имён XML-метамодели E3Core, используемые всеми парсерами"),
    ],
    "EntityClassifier": [
        ("classify(EntityObject entity, AppModel model)", "entity; model", "Определяет вид сущности: PRIMARY / CHILD / REFERENCE_DICTIONARY"),
        ("findModalTwin(EntityObject entity, AppModel model)", "entity; model", "Находит modal-двойника встроенного списка"),
        ("findTreeParent(EntityObject entity, AppModel model)", "entity; model", "Находит родительский узел дерева для сущности"),
    ],
    "TestConfig": [
        ("getBaseUrl() / setBaseUrl(String)", "baseUrl", "Адрес тестируемого приложения"),
        ("getTestLevel() / setTestLevel(String)", "testLevel", "Уровень тестирования (smoke / basic / full)"),
        ("isSmokeAllSubsystems() / setSmokeAllSubsystems(boolean)", "флаг", "Признак smoke-прогона по всем подсистемам"),
        ("getOutputDir() / setOutputDir(Path)", "outputDir", "Каталог сгенерированного проекта"),
        ("getLogin/Password/BrowserType/… и сеттеры", "соответствующие поля", "Геттеры и сеттеры остальных параметров конфигурации"),
    ],
    "TestGenerator": [
        ("generate(AppModel model)", "model — модель метаданных", "Оркестрирует генерацию Maven-проекта автотестов"),
        ("folderNameForXml(String xmlFileName)", "xmlFileName", "Формирует имя каталога проекта по имени XML"),
        ("resolveProjectDir(Path baseOutput, String xmlFileName)", "baseOutput; xmlFileName", "Возвращает путь к каталогу проекта"),
    ],
    "PageObjectWriter": [
        ("write(EntityObject entity, Path outputDir)", "entity; outputDir", "Генерирует класс Page Object для сущности"),
    ],
    "TestClassWriter": [
        ("write(EntityObject entity, AppModel model, Path outputDir)", "entity; model; outputDir", "Генерирует тест-класс JUnit 5 для сущности"),
        ("write(…, String disabledReason)", "…; disabledReason", "Генерирует тест-класс с пометкой @Disabled"),
        ("writeChildTest(EntityObject entity, AppModel model, Path outputDir)", "entity; model; outputDir", "Генерирует тест дочерней сущности"),
        ("writeTreeChildTest(EntityObject entity, AppModel model, Path outputDir)", "entity; model; outputDir", "Генерирует тест узла дерева"),
    ],
    "TestDataFactory": [
        ("generateValue(Property property)", "property — свойство", "Генерирует тестовое значение по типу и маске свойства"),
        ("generateSearchParamValue(SearchParam param)", "param", "Генерирует значение параметра поиска"),
        ("generateFromMask(String mask)", "mask — маска", "Генерирует строку по маске"),
        ("generateValueExpression(Property property)", "property", "Формирует Java-выражение тестового значения"),
    ],
    "TestRunner": [
        ("run(Path projectDir, String xmlFileName, String baseUrl)", "projectDir; xmlFileName; baseUrl", "Запускает тесты (mvn test) и собирает результат"),
        ("run(…, Consumer<String> lineConsumer)", "…; lineConsumer", "То же с потоковой передачей вывода"),
        ("getLastMavenOutput()", "—", "Возвращает последний вывод Maven"),
    ],
    "ReportDao": [
        ("saveRun(TestRunResult result)", "result — результат прогона", "Сохраняет прогон и его тест-кейсы в БД (одной транзакцией)"),
        ("getAllRuns()", "—", "Загружает все прогоны из БД вместе с тест-кейсами"),
    ],
    "JavaFileWriter": [
        ("writeLine(String line) / writeLine()", "line — строка кода", "Добавляет строку кода в буфер с текущим отступом"),
        ("openBlock(String header) / closeBlock()", "header — заголовок блока", "Открывает/закрывает блок { } с изменением отступа"),
        ("indent() / unindent()", "—", "Увеличивает/уменьшает уровень отступа"),
        ("writeToFile(Path dir, String fileName)", "dir; fileName", "Записывает накопленный код в файл"),
    ],
    "Transliterator": [
        ("toClassName(String name)", "name — рус. наименование", "Преобразует в имя класса (PascalCase)"),
        ("toMethodName(String name)", "name — рус. наименование", "Преобразует в имя метода (camelCase)"),
        ("toFieldName(String name)", "name — рус. наименование", "Преобразует в имя поля"),
    ],
}

LIBRARY18 = {"StaxUtils", "XmlNamespaces", "JavaFileWriter", "Transliterator"}

# связи вызовов (старая схема, по табл. 130): (src, dst, [данные-вниз], [данные-вверх], [управление], условие)
EDGES18 = [
    ("App", "MainController", ["main.fxml"], [], [], "once"),
    ("MainController", "XmlModelParser", ["путь к файлу"], ["AppModel"], ["признак ошибки"], ""),
    ("MainController", "TestConfig", ["параметры"], [], [], ""),
    ("MainController", "TestGenerator", ["AppModel", "TestConfig"], ["путь к проекту"], [], ""),
    ("MainController", "TestRunner", ["каталог", "фильтр"], ["RunResult"], [], ""),
    ("MainController", "ReportDao", ["RunResult"], ["список прогонов"], [], ""),
    ("XmlModelParser", "EntityParser", ["reader"], ["EntityObject"], [], "cyclic"),
    ("XmlModelParser", "SearchParser", ["reader"], ["List<Search>"], [], ""),
    ("EntityParser", "PropertyGroupParser", ["reader"], ["PropertyGroup"], [], ""),
    ("EntityParser", "StaxUtils", ["reader", "имя"], ["значение"], [], ""),
    ("EntityParser", "XmlNamespaces", ["NS_E, NS_E3"], [], [], ""),
    ("SearchParser", "StaxUtils", ["reader", "имя"], ["значение"], [], ""),
    ("SearchParser", "XmlNamespaces", ["NS_E, NS_E3"], [], [], ""),
    ("PropertyGroupParser", "StaxUtils", ["reader", "имя"], ["значение"], [], ""),
    ("PropertyGroupParser", "XmlNamespaces", ["NS_MD"], [], [], ""),
    ("TestGenerator", "EntityClassifier", ["EntityObject", "AppModel"], [], ["вид (PRIMARY/CHILD)"], "cond"),
    ("TestGenerator", "PageObjectWriter", ["EntityObject", "AppModel"], [], [], "cyclic"),
    ("TestGenerator", "TestClassWriter", ["EntityObject", "AppModel"], [], [], "cyclic"),
    ("TestGenerator", "TestDataFactory", ["Property"], ["значение"], [], ""),
    ("PageObjectWriter", "TestDataFactory", ["Property"], ["значение"], [], ""),
    ("PageObjectWriter", "JavaFileWriter", ["строки кода"], [], [], ""),
    ("PageObjectWriter", "Transliterator", ["рус. имя"], ["лат. имя"], [], ""),
    ("TestClassWriter", "TestDataFactory", ["Property"], ["значение"], [], ""),
    ("TestClassWriter", "JavaFileWriter", ["строки кода"], [], [], ""),
    ("TestClassWriter", "Transliterator", ["рус. имя"], ["лат. имя"], [], ""),
]

# области данных для карты Константайна: (ключ, подпись)
DATA_AREAS18 = [
    ("model_data", "модель метаданных\\n(AppModel)"),
    ("gen_tests", "generated-tests/\\n(Maven-проект)"),
    ("db", "autotestgen.db\\n(SQLite)"),
]

# связи модулей с областями данных: (src, область, подпись-куплет)
DATA_EDGES18 = [
    ("XmlModelParser", "model_data", "↓○ AppModel"),
    ("TestGenerator", "gen_tests", "↓○ файлы проекта"),
    ("TestRunner", "gen_tests", "↑○ mvn test"),
    ("ReportDao", "db", "↓○ INSERT  ↑○ SELECT"),
]


def is_library(k):
    return k in LIBRARY18


def module_keys():
    return [m[0] for m in MODULES18]
