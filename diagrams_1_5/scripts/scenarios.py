# -*- coding: utf-8 -*-
"""Сценарии взаимодействия по пакетам (нормальный ход / прерывание пользователем /
прерывание системой). Управление и межпакетные вызовы идут через КРАЙ (EDGE) —
актёра «Пользователь» и внешних объектов на диаграммах нет. Исключение — пакет
common: ему разрешён внешний класс-потребитель (generator::PageObjectWriter),
помеченный «Пакет::Класс».

Сообщение: (откуда, куда, подпись, вид), вид ∈ call|ret|create|self.
Сценарии СБАЛАНСИРОВАНЫ: каждый call/create имеет парный ret -> все активации
закрываются возвратной стрелкой.
"""

EDGE = "EDGE"

SC = {
    "ui": {
        "objects": [("Launcher", "Launcher"), ("App", "App"), ("ctrl", ":MainController"),
                    ("node", ":EntityNode"), ("row", ":TestCaseRow")],
        "flows": {
            "normal": [
                (EDGE, "Launcher", "main(args)", "call"),
                ("Launcher", "App", "launch(App.class)", "call"),
                ("App", "ctrl", "загрузка main.fxml", "create"),
                ("ctrl", "App", "контроллер готов", "ret"),
                ("App", "Launcher", "окно показано", "ret"),
                ("Launcher", EDGE, "приложение запущено", "ret"),
                (EDGE, "ctrl", "onParse()", "call"),
                ("ctrl", EDGE, "разобрать метаданные: подсистема Parser", "call"),
                (EDGE, "ctrl", "модель метаданных", "ret"),
                ("ctrl", "node", "создать узлы дерева", "create"),
                ("node", "ctrl", "узлы готовы", "ret"),
                ("ctrl", EDGE, "дерево сущностей показано", "ret"),
                (EDGE, "ctrl", "onRunTests()", "call"),
                ("ctrl", EDGE, "генерация и прогон: подсистемы Generator, Data", "call"),
                (EDGE, "ctrl", "результат прогона", "ret"),
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
                ("ctrl", EDGE, "разобрать метаданные: подсистема Parser", "call"),
                (EDGE, "ctrl", "ошибка разбора (исключение)", "ret"),
                ("ctrl", "ctrl", "showAlert(сообщение об ошибке)", "self"),
                ("ctrl", EDGE, "сообщение об ошибке показано", "ret"),
            ],
        },
    },
    "parser": {
        "objects": [("xmlp", ":XmlModelParser"), ("ep", ":EntityParser"),
                    ("pgp", ":PropertyGroupParser"), ("sp", ":SearchParser"),
                    ("stax", ":StaxUtils"), ("ns", ":XmlNamespaces")],
        "flows": {
            "normal": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                ("ep", "ns", "сверка namespace (NS_E3)", "call"),
                ("ns", "ep", "пространство имён", "ret"),
                ("ep", "stax", "attr(reader, имя)", "call"),
                ("stax", "ep", "значение атрибута", "ret"),
                ("ep", "pgp", "parsePropertyGroup(reader)", "call"),
                ("pgp", "stax", "attr(reader, имя), parseInt(...)", "call"),
                ("stax", "pgp", "значения атрибутов", "ret"),
                ("pgp", "ep", "группа свойств", "ret"),
                ("ep", "xmlp", "объект сущности", "ret"),
                ("xmlp", "sp", "parseSearches(reader)", "call"),
                ("sp", "xmlp", "список поисков", "ret"),
                ("xmlp", EDGE, "модель метаданных: подсистема Model", "ret"),
            ],
            "user": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                ("ep", "xmlp", "разбор прерван", "ret"),
                ("xmlp", EDGE, "разбор прерван", "ret"),
            ],
            "system": [
                (EDGE, "xmlp", "parse(файл)", "call"),
                ("xmlp", "ep", "parseObject(reader)", "call"),
                ("ep", "stax", "attr(reader, имя)", "call"),
                ("stax", "ep", "некорректная структура XML", "ret"),
                ("ep", "xmlp", "исключение разбора (ParserException)", "ret"),
                ("xmlp", EDGE, "завершение с ошибкой", "ret"),
            ],
        },
    },
    "model": {
        "objects": [("clf", ":EntityClassifier"), ("ent", ":EntityObject"),
                    ("pg", ":PropertyGroup"), ("model", ":AppModel"), ("cls", ":Classification")],
        "note": "Большинство классов пакета Model — пассивные классы-данные (свойства с геттерами/сеттерами) и "
                "в обмене сообщениями не участвуют; на диаграммах взаимодействия показан сценарий классификации "
                "сущности, затрагивающий активные классы EntityClassifier, AppModel, EntityObject, PropertyGroup "
                "и Classification. Третий сценарий — вырожденный случай (сущность без свойств распознаётся как "
                "справочник).",
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
                ("cls", "clf", "результат готов", "ret"),
                ("clf", EDGE, "результат классификации", "ret"),
            ],
            "user": [
                (EDGE, "clf", "classify(сущность, модель)", "call"),
                ("clf", "ent", "getPropertyGroups()", "call"),
                ("ent", "clf", "группы свойств", "ret"),
                ("clf", EDGE, "классификация прервана", "ret"),
            ],
            "system": [
                (EDGE, "clf", "classify(сущность, модель)", "call"),
                ("clf", "ent", "getPropertyGroups()", "call"),
                ("ent", "clf", "пустой список свойств", "ret"),
                ("clf", "cls", "тривиальный результат (справочник)", "create"),
                ("cls", "clf", "результат готов", "ret"),
                ("clf", EDGE, "результат: справочник", "ret"),
            ],
        },
    },
    "generator": {
        "objects": [("gen", ":TestGenerator"), ("cfg", ":TestConfig"), ("pow", ":PageObjectWriter"),
                    ("tcw", ":TestClassWriter"), ("tdf", ":TestDataFactory"),
                    ("runner", ":TestRunner"), ("rrw", ":RunReportWriter")],
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
                ("runner", "runner", "запуск Maven (mvn test)", "self"),
                ("runner", "rrw", "write(результат прогона)", "call"),
                ("rrw", "runner", "отчёты HTML и CSV", "ret"),
                ("runner", EDGE, "результат прогона", "ret"),
            ],
            "user": [
                (EDGE, "gen", "generate(модель)", "call"),
                ("gen", "pow", "write(сущность)", "call"),
                ("pow", "gen", "генерация прервана", "ret"),
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
        "objects": [("dao", ":ReportDao"), ("schema", ":SchemaInitializer"),
                    ("conn", ":DatabaseConnection"), ("trd", ":TestRunDao"), ("tcd", ":TestCaseDao")],
        "note": "Класс-данные RunRow используется как строка списка прогонов и в обмене сообщениями не "
                "участвует; на диаграммах взаимодействия показаны активные классы пакета Data.",
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
                ("conn", "dao", "Connection", "ret"),
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
        "objects": [("pow", "generator::PageObjectWriter"), ("tr", ":Transliterator"),
                    ("jfw", ":JavaFileWriter"), ("exc", ":ParserException")],
        "note": "Классы пакета Common — независимые вспомогательные утилиты; они не вызывают друг друга, а "
                "используются классами других пакетов. Для иллюстрации показан класс-потребитель "
                "generator::PageObjectWriter (с указанием пакета), обращающийся к утилитам Common; на диаграммах "
                "классов он показан без состава (его поля и методы описаны в пакете Generator).",
        "flows": {
            "normal": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "tr", "toClassName(рус. имя)", "call"),
                ("tr", "pow", "имя класса (латиница)", "ret"),
                ("pow", "jfw", "writeLine(строка)", "call"),
                ("jfw", "jfw", "openBlock()/closeBlock() — отступы", "self"),
                ("jfw", "pow", "строка добавлена", "ret"),
                ("pow", "jfw", "writeToFile(путь)", "call"),
                ("jfw", "pow", "файл записан", "ret"),
                ("pow", EDGE, "класс сгенерирован", "ret"),
            ],
            "user": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "jfw", "writeLine(строка)", "call"),
                ("jfw", "pow", "запись прервана", "ret"),
                ("pow", EDGE, "генерация прервана", "ret"),
            ],
            "system": [
                (EDGE, "pow", "генерация класса", "call"),
                ("pow", "jfw", "writeToFile(путь)", "call"),
                ("jfw", "exc", "создать исключение (IOException)", "create"),
                ("exc", "jfw", "ParserException", "ret"),
                ("jfw", "pow", "проброс исключения", "ret"),
                ("pow", EDGE, "завершение с ошибкой", "ret"),
            ],
        },
    },
}

FLOW_TITLE = {"normal": "нормальный ход", "user": "прерывание пользователем", "system": "прерывание системой"}
FLOW_NUM_PREFIX = {"normal": "", "user": "п", "system": "с"}


def used_object_keys(pkg):
    """Ключи объектов, участвующих хотя бы в одном сообщении (в порядке objects)."""
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


def external_labels(pkg):
    """Метки внешних классов («Пакет::Класс»), участвующих в сценариях пакета."""
    seen = []
    for flow in SC[pkg]["flows"].values():
        for frm, to, _, _ in flow:
            for k in (frm, to):
                if k != EDGE:
                    lbl = label_of(pkg, k)
                    if "::" in lbl and lbl not in seen:
                        seen.append(lbl)
    return seen
