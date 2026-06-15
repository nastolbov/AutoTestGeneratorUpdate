# -*- coding: utf-8 -*-
"""Модель модульной структуры для раздела 1.5.3: функциональные модули, их иерархия
вызовов с куплетами (связи по данным/управлению) и особыми условиями, классификация
(модуль/библиотека/область данных), состав диаграммы компонентов, оценки связности и
сцепления. Построено по реальному коду (api.json) и фактическому потоку вызовов."""

# модули: (ключ, имя, пакет, вид, назначение); вид: "module" | "library"
MODULES = [
    ("Launcher", "Launcher", "ui", "module", "Точка входа; запуск приложения в обход модульности JavaFX"),
    ("App", "App", "ui", "module", "JavaFX-приложение; загрузка главного окна (FXML)"),
    ("MainController", "MainController", "ui", "module",
     "Контроллер: оркестрация разбора, генерации, прогона и отчётов"),
    ("XmlModelParser", "XmlModelParser", "parser", "module", "Разбор XML-метамодели, построение модели AppModel"),
    ("EntityParser", "EntityParser", "parser", "module", "Разбор сущности и её атрибутов"),
    ("PropertyGroupParser", "PropertyGroupParser", "parser", "module", "Разбор групп свойств и свойств"),
    ("SearchParser", "SearchParser", "parser", "module", "Разбор поисков (фильтров) сущности"),
    ("StaxUtils", "StaxUtils", "parser", "library", "Утилиты потокового разбора XML (StAX)"),
    ("XmlNamespaces", "XmlNamespaces", "parser", "library", "Константы пространств имён XML"),
    ("EntityClassifier", "EntityClassifier", "model", "module",
     "Классификация сущностей: основная, дочерняя, справочник"),
    ("TestConfig", "TestConfig", "generator", "module", "Параметры генерации и прогона"),
    ("TestGenerator", "TestGenerator", "generator", "module", "Оркестрация генерации тест-проекта"),
    ("PageObjectWriter", "PageObjectWriter", "generator", "module", "Генерация объекта страницы (Page Object)"),
    ("TestClassWriter", "TestClassWriter", "generator", "module", "Генерация тестового класса"),
    ("TestDataFactory", "TestDataFactory", "generator", "module", "Формирование тестовых данных по типам и маскам"),
    ("TestRunner", "TestRunner", "generator", "module", "Запуск автотестов (Maven), сбор результатов"),
    ("RunReportWriter", "RunReportWriter", "generator", "module", "Формирование отчётов о прогоне (HTML, CSV)"),
    ("ReportDao", "ReportDao", "data", "module", "Доступ к БД отчётов: сохранение и чтение прогонов"),
    ("SchemaInitializer", "SchemaInitializer", "data", "module", "Создание схемы БД отчётов"),
    ("DatabaseConnection", "DatabaseConnection", "data", "library", "Управление соединением с БД (SQLite)"),
    ("TestRunDao", "TestRunDao", "data", "module", "Доступ к данным прогонов"),
    ("TestCaseDao", "TestCaseDao", "data", "module", "Доступ к данным отдельных тестов"),
    ("JavaFileWriter", "JavaFileWriter", "common", "library", "Буфер генерации Java-кода с отступами"),
    ("Transliterator", "Transliterator", "common", "library", "Транслитерация русских наименований в латиницу"),
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
DATA_EDGES = [
    ("XmlModelParser", "model_data", "создаёт"),
    ("TestGenerator", "gen_tests", "пишет"),
    ("TestRunner", "gen_tests", "mvn test"),
    ("TestRunDao", "db", "INSERT/SELECT"),
    ("TestCaseDao", "db", "INSERT"),
    ("DatabaseConnection", "db", "JDBC"),
    ("SchemaInitializer", "db", "CREATE TABLE"),
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
    "StaxUtils": ("функциональная", "элементарные операции чтения StAX"),
    "XmlNamespaces": ("информационная", "набор связанных констант"),
    "EntityClassifier": ("функциональная", "определение вида сущности"),
    "TestConfig": ("информационная", "хранение связанных параметров"),
    "TestGenerator": ("коммуникационная", "координирует генерацию над моделью и конфигом"),
    "PageObjectWriter": ("функциональная", "генерация Page Object"),
    "TestClassWriter": ("функциональная", "генерация тест-класса"),
    "TestDataFactory": ("функциональная", "формирование значения по типу/маске"),
    "TestRunner": ("функциональная", "запуск прогона и сбор результатов"),
    "RunReportWriter": ("функциональная", "формирование отчётов"),
    "ReportDao": ("коммуникационная", "координирует запись/чтение через DAO над соединением"),
    "SchemaInitializer": ("функциональная", "создание схемы"),
    "DatabaseConnection": ("функциональная", "выдача соединения"),
    "TestRunDao": ("функциональная", "операции с таблицей прогонов"),
    "TestCaseDao": ("функциональная", "операции с таблицей тестов"),
    "JavaFileWriter": ("функциональная", "накопление и запись кода"),
    "Transliterator": ("функциональная", "преобразование имени"),
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
