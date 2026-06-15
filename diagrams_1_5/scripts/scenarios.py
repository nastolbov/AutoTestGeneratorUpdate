# -*- coding: utf-8 -*-
"""Сценарии взаимодействия по пакетам (нормальный ход / прерывание пользователем /
прерывание системой). Управление входит из края («EDGE») — актёра «Пользователь»
на диаграммах нет (методичка Рис.7.8б). Межпакетные объекты помечены «Пакет::Класс».

Сообщение: (откуда, куда, подпись, вид), вид ∈ call|ret|create|self.
И диаграммы последовательности, и кооперация строятся из этих данных -> наборы
объектов совпадают.
"""

EDGE = "EDGE"

SC = {
    "ui": {
        "objects": [
            ("Launcher", "Launcher"), ("App", "App"), ("ctrl", ":MainController"),
            ("node", ":EntityNode"), ("row", ":TestCaseRow"),
            ("xmlp", "parser::XmlModelParser"), ("runner", "generator::TestRunner"),
            ("exc", "common::ParserException"),
        ],
        "flows": {
            "normal": [
                (EDGE, "Launcher", "main(args)", "call"),
                ("Launcher", "App", "launch(App.class)", "call"),
                ("App", "ctrl", "загрузка main.fxml", "create"),
                ("ctrl", "App", "контроллер готов", "ret"),
                ("App", "Launcher", "окно показано", "ret"),
                ("Launcher", EDGE, "приложение запущено", "ret"),
                (EDGE, "ctrl", "onParse()", "call"),
                ("ctrl", "xmlp", "parse(файл)", "call"),
                ("xmlp", "ctrl", "модель AppModel", "ret"),
                ("ctrl", "node", "создать узлы дерева", "create"),
                ("node", "ctrl", "узлы готовы", "ret"),
                ("ctrl", EDGE, "дерево сущностей показано", "ret"),
                (EDGE, "ctrl", "onRunTests()", "call"),
                ("ctrl", "runner", "run(конфиг, фильтр)", "call"),
                ("runner", "ctrl", "результат прогона", "ret"),
                ("ctrl", "row", "создать строки результатов", "create"),
                ("row", "ctrl", "строки готовы", "ret"),
                ("ctrl", EDGE, "таблица результатов показана", "ret"),
            ],
            "user": [
                (EDGE, "ctrl", "onSelectXml()", "call"),
                ("ctrl", "ctrl", "открыть диалог выбора файла; выбор отменён", "self"),
                ("ctrl", EDGE, "файл не выбран, разбор не выполняется", "ret"),
            ],
            "system": [
                (EDGE, "ctrl", "onParse()", "call"),
                ("ctrl", "xmlp", "parse(файл)", "call"),
                ("xmlp", "exc", "создать исключение разбора", "create"),
                ("exc", "xmlp", "ParserException", "ret"),
                ("xmlp", "ctrl", "проброс ParserException", "ret"),
                ("ctrl", "ctrl", "showAlert(сообщение об ошибке)", "self"),
                ("ctrl", EDGE, "сообщение об ошибке показано", "ret"),
            ],
        },
    },
    "parser": {
        "objects": [
            ("xmlp", ":XmlModelParser"), ("ep", ":EntityParser"), ("pgp", ":PropertyGroupParser"),
            ("sp", ":SearchParser"), ("stax", ":StaxUtils"), ("ns", ":XmlNamespaces"),
            ("model", "model::AppModel"), ("exc", "common::ParserException"),
        ],
        "flows": {
            "normal": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "model", "создать пустую модель", "create"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                ("ep", "ns", "NS_E3 (сверка namespace)", "call"),
                ("ep", "stax", "attr(reader, имя)", "call"),
                ("stax", "ep", "значение атрибута", "ret"),
                ("ep", "pgp", "parsePropertyGroup(reader)", "call"),
                ("pgp", "stax", "attr(reader, имя), parseInt(...)", "call"),
                ("pgp", "ep", "группа свойств", "ret"),
                ("ep", "xmlp", "EntityObject", "ret"),
                ("xmlp", "sp", "parseSearches(reader)", "call"),
                ("sp", "xmlp", "список поисков", "ret"),
                ("xmlp", EDGE, "модель AppModel", "ret"),
            ],
            "user": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                (EDGE, "xmlp", "Прервать", "call"),
                ("xmlp", EDGE, "разбор прерван", "ret"),
            ],
            "system": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                ("ep", "stax", "attr(reader, имя)", "call"),
                ("ep", "exc", "некорректная структура XML", "create"),
                ("ep", "xmlp", "ParserException", "ret"),
                ("xmlp", EDGE, "завершение с ошибкой", "ret"),
            ],
        },
    },
    "model": {
        "objects": [
            ("clf", ":EntityClassifier"), ("ent", ":EntityObject"), ("pg", ":PropertyGroup"),
            ("model", ":AppModel"), ("cls", ":Classification"),
        ],
        "note": "Большинство классов пакета Model — пассивные классы-данные (геттеры/сеттеры) и в обмене "
                "сообщениями не участвуют; на диаграммах взаимодействия показан сценарий классификации сущности, "
                "затрагивающий активные классы EntityClassifier, AppModel, EntityObject, PropertyGroup и Classification.",
        "flows": {
            "normal": [
                (EDGE, "clf", "classify(сущность, модель)", "call"),
                ("clf", "ent", "getPropertyGroups()", "call"),
                ("ent", "clf", "группы свойств", "ret"),
                ("clf", "pg", "getProperties()", "call"),
                ("pg", "clf", "список свойств", "ret"),
                ("clf", "model", "поиск родительской сущности", "call"),
                ("model", "clf", "сущность-родитель", "ret"),
                ("clf", "cls", "создать результат (вид, причина, родитель)", "create"),
                ("clf", EDGE, "Classification", "ret"),
            ],
            "user": [
                (EDGE, "clf", "classify(сущность, модель)", "call"),
                ("clf", "ent", "getPropertyGroups()", "call"),
                (EDGE, "clf", "Прервать", "call"),
                ("clf", EDGE, "классификация прервана", "ret"),
            ],
            "system": [
                (EDGE, "clf", "classify(сущность, модель)", "call"),
                ("clf", "ent", "getPropertyGroups()", "call"),
                ("ent", "clf", "пустой список свойств", "ret"),
                ("clf", "cls", "тривиальный результат (справочник)", "create"),
                ("clf", EDGE, "Classification (REFERENCE_DICTIONARY)", "ret"),
            ],
        },
    },
    "generator": {
        "objects": [
            ("gen", ":TestGenerator"), ("cfg", ":TestConfig"), ("pow", ":PageObjectWriter"),
            ("tcw", ":TestClassWriter"), ("tdf", ":TestDataFactory"), ("runner", ":TestRunner"),
            ("rrw", ":RunReportWriter"),
        ],
        "flows": {
            "normal": [
                (EDGE, "gen", "generate(модель)", "call"),
                ("gen", "cfg", "чтение параметров", "call"),
                ("cfg", "gen", "пути, адрес, уровень тестов", "ret"),
                ("gen", "pow", "write(сущность)", "call"),
                ("pow", "tdf", "generateValue(свойство)", "call"),
                ("tdf", "pow", "значение", "ret"),
                ("pow", "gen", "Page Object готов", "ret"),
                ("gen", "tcw", "write(сущность, модель)", "call"),
                ("tcw", "tdf", "generateValue(свойство)", "call"),
                ("tdf", "tcw", "значение", "ret"),
                ("tcw", "gen", "тест-класс готов", "ret"),
                ("gen", EDGE, "проект сгенерирован", "ret"),
                (EDGE, "runner", "run(каталог, фильтр)", "call"),
                ("runner", "rrw", "write(результат прогона)", "call"),
                ("rrw", "runner", "отчёты HTML и CSV", "ret"),
                ("runner", EDGE, "результат прогона", "ret"),
            ],
            "user": [
                (EDGE, "gen", "generate(модель)", "call"),
                ("gen", "pow", "write(сущность)", "call"),
                (EDGE, "gen", "Прервать", "call"),
                ("gen", EDGE, "генерация прервана", "ret"),
            ],
            "system": [
                (EDGE, "runner", "run(каталог, фильтр)", "call"),
                ("runner", "runner", "запуск Maven (mvn test)", "self"),
                ("runner", "runner", "ошибка выполнения / разбора отчётов", "self"),
                ("runner", EDGE, "завершение с ошибкой, доступные результаты сохранены", "ret"),
            ],
        },
    },
    "data": {
        "objects": [
            ("dao", ":ReportDao"), ("schema", ":SchemaInitializer"), ("conn", ":DatabaseConnection"),
            ("trd", ":TestRunDao"), ("tcd", ":TestCaseDao"),
        ],
        "note": "Класс-данные RunRow используется как строка списка прогонов и в обмене сообщениями не участвует; "
                "на диаграммах взаимодействия показаны активные классы пакета Data.",
        "flows": {
            "normal": [
                (EDGE, "dao", "saveRun(результат)", "call"),
                ("dao", "schema", "initialize()", "call"),
                ("schema", "conn", "open()", "call"),
                ("conn", "schema", "Connection", "ret"),
                ("schema", "dao", "схема готова", "ret"),
                ("dao", "conn", "open()", "call"),
                ("conn", "dao", "Connection", "ret"),
                ("dao", "trd", "insert(conn, результат)", "call"),
                ("trd", "dao", "идентификатор прогона runId", "ret"),
                ("dao", "tcd", "insertBatch(conn, runId, кейсы)", "call"),
                ("tcd", "dao", "записи сохранены", "ret"),
                ("dao", EDGE, "прогон сохранён", "ret"),
            ],
            "user": [
                (EDGE, "dao", "saveRun(результат)", "call"),
                ("dao", "conn", "open()", "call"),
                (EDGE, "dao", "Прервать", "call"),
                ("dao", EDGE, "сохранение прервано", "ret"),
            ],
            "system": [
                (EDGE, "dao", "saveRun(результат)", "call"),
                ("dao", "conn", "open()", "call"),
                ("conn", "dao", "Connection", "ret"),
                ("dao", "trd", "insert(conn, результат)", "call"),
                ("trd", "dao", "SQLException", "ret"),
                ("dao", EDGE, "ошибка записи в БД", "ret"),
            ],
        },
    },
    "common": {
        "objects": [
            ("pow", "generator::PageObjectWriter"), ("tr", ":Transliterator"),
            ("jfw", ":JavaFileWriter"), ("exc", ":ParserException"),
        ],
        "note": "Классы пакета Common — независимые вспомогательные утилиты; они не вызывают друг друга, а "
                "используются классами других пакетов. Для иллюстрации показан класс-потребитель "
                "generator::PageObjectWriter (с указанием пакета), обращающийся к утилитам Common.",
        "flows": {
            "normal": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "tr", "toClassName(рус. имя)", "call"),
                ("tr", "pow", "имя класса (латиница)", "ret"),
                ("pow", "jfw", "writeLine(строка)", "call"),
                ("jfw", "jfw", "openBlock()/closeBlock() — отступы", "self"),
                ("pow", "jfw", "writeToFile(путь)", "call"),
                ("jfw", "pow", "файл записан", "ret"),
            ],
            "user": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "jfw", "writeLine(строка)", "call"),
                (EDGE, "pow", "Прервать", "call"),
                ("pow", EDGE, "генерация прервана", "ret"),
            ],
            "system": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "jfw", "writeToFile(путь)", "call"),
                ("jfw", "exc", "IOException → создать исключение", "create"),
                ("jfw", "pow", "ParserException", "ret"),
                ("pow", EDGE, "завершение с ошибкой", "ret"),
            ],
        },
    },
}

FLOW_TITLE = {"normal": "нормальный ход", "user": "прерывание пользователем", "system": "прерывание системой"}
FLOW_NUM_PREFIX = {"normal": "", "user": "п", "system": "с"}


def used_object_keys(pkg):
    """Ключи объектов, реально участвующих хотя бы в одном сообщении (в порядке objects)."""
    used = set()
    for flow in SC[pkg]["flows"].values():
        for frm, to, _, _ in flow:
            if frm != EDGE:
                used.add(frm)
            if to != EDGE:
                used.add(to)
    return [k for k, _ in SC[pkg]["objects"] if k in used]


def label_of(pkg, key):
    for k, lbl in SC[pkg]["objects"]:
        if k == key:
            return lbl
    return key
