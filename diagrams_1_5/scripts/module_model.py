# -*- coding: utf-8 -*-
"""Модель модульной структуры для раздела 1.5.3: функциональные модули, их иерархия
вызовов с куплетами (связи по данным/управлению) и особыми условиями, классификация
(модуль/библиотека/область данных), состав диаграммы компонентов, оценки связности и
сцепления. Построено по реальному коду (api.json) и фактическому потоку вызовов."""

# модули: (ключ, имя, пакет, вид, входные данные, выходные данные, описание)
# вид: "module" | "library" (повторно используемая библиотека без собственного управления)
MODULES = [
    ("Launcher", "Launcher", "ui", "module",
     "Аргументы командной строки", "Запущенное приложение",
     "Точка входа; запуск приложения в обход модульности JavaFX"),
    ("App", "App", "ui", "module",
     "Вызов из Launcher", "Главное окно (JavaFX)",
     "JavaFX-приложение; загрузка главного окна из FXML"),
    ("MainController", "MainController", "ui", "module",
     "Действия пользователя в GUI", "Команды модулям, ход и история прогонов",
     "Контроллер главного окна; координирует разбор, генерацию, прогон и отчёты"),
    ("XmlModelParser", "XmlModelParser", "parser", "module",
     "XML-файл метамодели", "Модель AppModel",
     "Фасад потокового разбора XML-метамодели через StAX"),
    ("EntityParser", "EntityParser", "parser", "module",
     "XMLStreamReader, элемент <Entity>", "EntityObject",
     "Разбор сущности и её атрибутов"),
    ("PropertyGroupParser", "PropertyGroupParser", "parser", "module",
     "XMLStreamReader, блок <Properties>", "PropertyGroup",
     "Разбор групп свойств и операций по пространству имён"),
    ("SearchParser", "SearchParser", "parser", "module",
     "XMLStreamReader, блок <Search>", "Список Search",
     "Разбор поисковых блоков (фильтров) и параметров поиска"),
    ("StaxUtils", "StaxUtils", "parser", "library",
     "XMLStreamReader, имя атрибута", "Строка / число",
     "Библиотека утилит потокового разбора XML (StAX)"),
    ("XmlNamespaces", "XmlNamespaces", "parser", "library",
     "—", "Константы NS_E, NS_E3, NS_MD",
     "Библиотека констант пространств имён XML"),
    ("EntityClassifier", "EntityClassifier", "model", "module",
     "AppModel, EntityObject", "Вид: PRIMARY / CHILD / REFERENCE",
     "Классификация сущностей по правилам предметной области"),
    ("TestConfig", "TestConfig", "generator", "module",
     "Параметры от пользователя", "Объект конфигурации",
     "Хранение параметров генерации и прогона (уровень, fastMode, фильтры)"),
    ("TestGenerator", "TestGenerator", "generator", "module",
     "AppModel, TestConfig", "Maven-проект автотестов (файлы)",
     "Оркестратор генерации проекта автотестов"),
    ("PageObjectWriter", "PageObjectWriter", "generator", "module",
     "EntityObject, AppModel", "Java-файл Page Object",
     "Генерация объекта страницы (Page Object) для основной сущности"),
    ("TestClassWriter", "TestClassWriter", "generator", "module",
     "EntityObject, AppModel, уровень", "Java-файл теста (JUnit 5)",
     "Генерация тест-класса в зависимости от уровня тестирования"),
    ("TestDataFactory", "TestDataFactory", "generator", "module",
     "Тип поля, маска, зерно", "Сгенерированное значение",
     "Формирование детерминированных тестовых данных по типам и маскам"),
    ("TestRunner", "TestRunner", "generator", "module",
     "Каталог проекта, фильтр, fastMode", "RunResult, поток вывода",
     "Запуск автотестов (mvn test), потоковый разбор Surefire-отчётов"),
    ("RunReportWriter", "RunReportWriter", "generator", "module",
     "RunResult, каталог отчётов", "Отчёты HTML и CSV",
     "Формирование отчётов о результатах прогона"),
    ("ReportDao", "ReportDao", "data", "module",
     "RunResult / запрос истории", "id прогона / список прогонов",
     "Фасад сохранения и чтения истории прогонов из БД"),
    ("SchemaInitializer", "SchemaInitializer", "data", "module",
     "Соединение с БД", "Созданные таблицы",
     "Создание схемы БД отчётов при первом запуске"),
    ("DatabaseConnection", "DatabaseConnection", "data", "library",
     "Путь к файлу БД", "Соединение JDBC (Connection)",
     "Библиотека управления соединением с БД SQLite"),
    ("TestRunDao", "TestRunDao", "data", "module",
     "Connection, RunResult", "id прогона / список прогонов",
     "Доступ к данным прогонов (таблица run)"),
    ("TestCaseDao", "TestCaseDao", "data", "module",
     "Connection, id прогона, кейсы", "Записи кейсов / список",
     "Доступ к данным отдельных тестов (таблица test_case)"),
    ("JavaFileWriter", "JavaFileWriter", "common", "library",
     "Строки Java-кода, путь", "Java-файл на диске",
     "Библиотека форматированной записи Java-кода с отступами"),
    ("Transliterator", "Transliterator", "common", "library",
     "Русское наименование", "Корректное Java-имя",
     "Библиотека транслитерации кириллицы в Java-идентификаторы"),
]

# области данных: (ключ, подпись, назначение)
DATA_AREAS = [
    ("model_data", "модель метаданных\\n(AppModel)", "Объектная модель метаданных в памяти"),
    ("gen_tests", "generated-tests/\\n(Maven-проект)", "Сгенерированный проект автотестов на диске"),
    ("db", "autotestgen.db\\n(SQLite)", "База данных отчётов о прогонах"),
]

# рёбра иерархии: (src, dst, [данные-вниз], [данные-вверх], [управление], условие)
# условие: "" | "once" (1) | "cyclic" (↺ для каждой сущности) | "cond" (◇ по виду)
EDGES = [
    ("Launcher", "App", ["args"], [], [], "once"),
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
    ("PropertyGroupParser", "StaxUtils", ["reader", "имя"], ["значение"], [], ""),
    ("SearchParser", "StaxUtils", ["reader", "имя"], ["значение"], [], ""),
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
    ("TestRunner", "RunReportWriter", ["RunResult"], ["отчёты"], [], ""),
    ("ReportDao", "SchemaInitializer", [], ["схема готова"], [], ""),
    ("ReportDao", "TestRunDao", ["Connection", "RunResult"], ["runId"], [], ""),
    ("ReportDao", "TestCaseDao", ["Connection", "runId", "кейсы"], [], [], ""),
    ("SchemaInitializer", "DatabaseConnection", [], ["Connection"], [], ""),
    ("TestRunDao", "DatabaseConnection", [], ["Connection"], [], ""),
    ("TestCaseDao", "DatabaseConnection", [], ["Connection"], [], ""),
]

# связи модулей с областями данных (для карты Константайна): (src, data_area, подпись)
# сплошные рёбра; подпись в нотации куплетов (↓○ запись в область, ↑○ чтение из области)
DATA_EDGES = [
    ("XmlModelParser", "model_data", "↓○ AppModel"),
    ("TestGenerator", "gen_tests", "↓○ файлы проекта"),
    ("TestRunner", "gen_tests", "↑○ mvn test"),
    ("SchemaInitializer", "db", "↓○ CREATE TABLE"),
    ("TestRunDao", "db", "↓○ INSERT  ↑○ SELECT"),
    ("TestCaseDao", "db", "↓○ INSERT"),
]

# связность модулей: ключ -> (тип, обоснование)
COHESION = {
    "Launcher": ("функциональная", "единственная задача — запуск приложения"),
    "App": ("функциональная", "инициализация и показ главного окна"),
    "MainController": ("коммуникационная", "координирует разбор, генерацию, прогон над общими данными"),
    "XmlModelParser": ("функциональная", "построение модели из XML"),
    "EntityParser": ("функциональная", "разбор одной сущности"),
    "PropertyGroupParser": ("функциональная", "разбор групп свойств"),
    "SearchParser": ("функциональная", "разбор поисков"),
    "StaxUtils": ("логическая", "набор разнородных утилит StAX (чтение атрибута, парс int, перемотка), выбираемых вызовом"),
    "XmlNamespaces": ("информационная", "набор связанных констант"),
    "EntityClassifier": ("функциональная", "определение вида сущности"),
    "TestConfig": ("информационная", "хранение связанных параметров"),
    "TestGenerator": ("последовательная", "конвейер: классификация → Page Object → тест-классы → служебные файлы"),
    "PageObjectWriter": ("функциональная", "генерация Page Object"),
    "TestClassWriter": ("функциональная", "генерация тест-класса"),
    "TestDataFactory": ("функциональная", "формирование значения по типу/маске"),
    "TestRunner": ("последовательная", "конвейер: запуск Maven → разбор Surefire → привязка скриншотов"),
    "RunReportWriter": ("функциональная", "формирование отчётов"),
    "ReportDao": ("информационная", "выход TestRunDao (runId) — вход TestCaseDao при сборке истории прогонов"),
    "SchemaInitializer": ("функциональная", "создание схемы"),
    "DatabaseConnection": ("функциональная", "выдача соединения"),
    "TestRunDao": ("функциональная", "операции с таблицей прогонов"),
    "TestCaseDao": ("функциональная", "операции с таблицей тестов"),
    "JavaFileWriter": ("информационная", "операции над общим буфером кода (накопление строк, отступы, запись)"),
    "Transliterator": ("логическая", "родственные методы преобразования имён (класс/метод/поле), выбираются вызовом"),
}

# сцепление по ключевым связям: (src, dst, тип, передаваемые данные)
COUPLING = [
    ("MainController", "XmlModelParser", "по данным", "путь к файлу → AppModel"),
    ("MainController", "TestGenerator", "по образцу", "структуры AppModel, TestConfig"),
    ("MainController", "TestRunner", "по данным", "каталог, фильтр → RunResult"),
    ("MainController", "ReportDao", "по образцу", "структура RunResult"),
    ("XmlModelParser", "EntityParser", "по данным", "reader → EntityObject"),
    ("EntityParser", "PropertyGroupParser", "по данным", "reader → PropertyGroup"),
    ("EntityParser", "StaxUtils", "по данным", "reader, имя → значение"),
    ("TestGenerator", "EntityClassifier", "по управлению", "вид сущности (PRIMARY/CHILD/REFERENCE)"),
    ("TestGenerator", "PageObjectWriter", "по образцу", "EntityObject, AppModel"),
    ("PageObjectWriter", "TestDataFactory", "по данным", "Property → значение"),
    ("PageObjectWriter", "JavaFileWriter", "по данным", "строки кода"),
    ("PageObjectWriter", "Transliterator", "по данным", "рус. имя → лат. имя"),
    ("TestRunner", "RunReportWriter", "по образцу", "RunResult"),
    ("ReportDao", "TestRunDao", "по данным", "Connection, RunResult → runId"),
    ("ReportDao", "TestCaseDao", "по данным", "Connection, runId, кейсы"),
    ("TestRunDao", "DatabaseConnection", "по данным", "Connection"),
]

# компоненты (диаграмма компонентов): (ключ, имя, тип, версия, назначение)
# тип: "app" | "lib" | "ext" | "file" | "db" | "folder"
COMPONENTS = [
    ("app", "autotestgenerator.jar", "app", "1.0.0", "Основное приложение информационной системы"),
    ("fxctl", "javafx-controls.jar", "lib", "24.0.1", "Библиотека элементов управления JavaFX"),
    ("fxfxml", "javafx-fxml.jar", "lib", "24.0.1", "Загрузка интерфейса из FXML"),
    ("sqlite", "sqlite-jdbc.jar", "lib", "3.42.0.0", "JDBC-драйвер СУБД SQLite"),
    ("stax", "java.xml (StAX)", "ext", "JDK 17", "Потоковый разбор XML (встроен в JDK)"),
    ("maven", "Apache Maven", "ext", "—", "Сборка и запуск сгенерированных автотестов"),
    ("xml", "XML-метамодель", "file", "—", "Входной файл метаданных предметной области"),
    ("gentests", "generated-tests/", "folder", "—", "Сгенерированный Maven-проект автотестов"),
    ("surefire", "surefire-reports/*.xml", "file", "—", "Отчёты JUnit о выполнении тестов"),
    ("db", "autotestgen.db", "db", "—", "База данных отчётов о прогонах (SQLite)"),
    ("reports", "Отчёты HTML/CSV", "file", "—", "Итоговые отчёты о прогоне"),
]

# связи компонентов: (src, dst, вид) — dep(зависимость,пунктир) | input | create | run | read
COMP_EDGES = [
    ("app", "fxctl", "dep"), ("app", "fxfxml", "dep"), ("app", "sqlite", "dep"),
    ("app", "stax", "dep"), ("app", "maven", "dep"),
    ("xml", "app", "input"), ("app", "gentests", "create"),
    ("maven", "gentests", "run"), ("gentests", "surefire", "create"),
    ("surefire", "app", "read"), ("app", "db", "create"), ("app", "reports", "create"),
]

LIBRARY = {m[0] for m in MODULES if m[3] == "library"}


def module_keys():
    return [m[0] for m in MODULES]


def is_library(k):
    return k in LIBRARY
