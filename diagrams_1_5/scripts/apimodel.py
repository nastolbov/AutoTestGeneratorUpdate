# -*- coding: utf-8 -*-
"""Единая модель кода для раздела 1.5: загрузка api.json, видимость UML (+/-/#/~),
связи внутри пакета (наследование/агрегация/композиция/вложенность), пометка
межпакетных типов как «Пакет::Класс», русские описания и метаданные пакетов.

И диаграммы классов, и таблицы строятся из этого модуля -> счётчики совпадают.
"""
import json, re

API = "/tmp/api.json"
PKG_ORDER = ["ui", "parser", "model", "generator", "data", "common"]

# слой / заголовок пакета / краткое назначение (1.5.1)
PKG_META = {
    "ui":        ("PresentationLayer", "UI",        "Граничные и управляющие классы графического интерфейса (JavaFX): окно, контроллер, точки запуска."),
    "parser":    ("BusinessLayer",     "Parser",    "Разбор XML-файла метаданных формата E3Core и построение объектной модели."),
    "model":     ("BusinessLayer",     "Model",     "Классы-сущности предметной области: модель метаданных и её элементы."),
    "generator": ("BusinessLayer",     "Generator", "Генерация тест-проекта, объектов страниц и тест-классов, запуск прогона, отчёты."),
    "data":      ("DataLayer",         "Data",      "Доступ к базе данных отчётов (SQLite): соединение, схема, DAO."),
    "common":    ("CrossLayer",        "Common",    "Вспомогательные классы общего назначения (глобальный пакет)."),
}
# зависимости пакетов (направление вызовов) для диаграммы пакетов
PKG_DEPS = [("ui", "parser"), ("ui", "model"), ("ui", "generator"), ("ui", "data"),
            ("parser", "model"), ("parser", "common"),
            ("generator", "model"), ("generator", "common"), ("generator", "data"),
            ("data", "model")]

CLASS_RU = {
    "ui.App": "JavaFX-приложение: инициализация и запуск главного окна.",
    "ui.Launcher": "Точка входа приложения (обход ограничений модульности JavaFX).",
    "ui.MainController": "Контроллер главного окна: связывает интерфейс с разбором, генерацией и прогоном.",
    "ui.MainController.EntityNode": "Узел дерева сущностей в интерфейсе.",
    "ui.MainController.TestCaseRow": "Строка таблицы результатов тестов в интерфейсе.",
    "parser.EntityParser": "Разбирает XML-описание сущности в объект EntityObject.",
    "parser.PropertyGroupParser": "Разбирает группы свойств и свойства сущности из XML.",
    "parser.SearchParser": "Разбирает описания поисков сущности из XML.",
    "parser.StaxUtils": "Вспомогательные методы потокового разбора XML (StAX).",
    "parser.XmlModelParser": "Точка входа разбора: строит модель AppModel из XML-файла.",
    "parser.XmlNamespaces": "Константы пространств имён XML модели метаданных.",
    "model.AppModel": "Корневая модель метаданных: сущности, поиски и сведения о подсистеме.",
    "model.Association": "Ассоциация между сущностями модели: роли, кратности, связанный элемент.",
    "model.AttrType": "Тип значения свойства: строка, число, дата, дата-время.",
    "model.EntityClassifier": "Классификатор сущностей: основная, дочерняя или справочник.",
    "model.EntityClassifier.Classification": "Результат классификации одной сущности (вид, причина, родитель).",
    "model.EntityKind": "Вид сущности: основная, дочерняя, справочник.",
    "model.EntityObject": "Сущность предметной области: группы свойств, операции, ассоциации.",
    "model.Modifier": "Модификатор операции — дополнительный признак её поведения.",
    "model.ModifyType": "Тип изменения записи (создание, редактирование, удаление и т. п.).",
    "model.Operation": "Операция сущности с параметрами и модификаторами.",
    "model.OperationParam": "Параметр операции сущности.",
    "model.Property": "Свойство (атрибут) сущности: тип, обязательность, маска и пр.",
    "model.PropertyGroup": "Группа свойств сущности — раздел формы со списком свойств.",
    "model.Search": "Описание поиска (фильтра) сущности с параметрами и результатами.",
    "model.SearchParam": "Параметр поиска — критерий фильтрации.",
    "model.SearchResult": "Результат поиска: набор отображаемых свойств.",
    "model.SearchResultProperty": "Свойство, выводимое в результатах поиска.",
    "model.TestCaseResult": "Результат одного теста: статус, длительность, шаги, ошибка.",
    "model.TestCaseResult.StepTiming": "Тайминг шага теста: имя шага и длительность.",
    "model.TestRunResult": "Результат прогона: сводные показатели и перечень тестов.",
    "generator.PageObjectWriter": "Генерирует класс объекта страницы (Page Object) для сущности.",
    "generator.RunReportWriter": "Формирует отчёты о прогоне (HTML и CSV).",
    "generator.TestClassWriter": "Генерирует тестовый класс с набором проверок для сущности.",
    "generator.TestConfig": "Параметры генерации и прогона: пути, адрес, учётные данные и др.",
    "generator.TestDataFactory": "Формирует тестовые данные по типам и маскам свойств.",
    "generator.TestGenerator": "Создаёт структуру тест-проекта и генерирует Page Object и тест-классы.",
    "generator.TestRunner": "Запускает автотесты (Maven) и собирает результаты прогона.",
    "data.DatabaseConnection": "Управляет соединением с базой данных отчётов.",
    "data.ReportDao": "Доступ к данным отчётов: чтение и запись прогонов.",
    "data.SchemaInitializer": "Создаёт схему базы отчётов (таблицы) при первом запуске.",
    "data.TestCaseDao": "Доступ к данным результатов отдельных тестов.",
    "data.TestRunDao": "Доступ к данным прогонов тестов.",
    "data.TestRunDao.RunRow": "Строка списка прогонов — запись о прогоне для отображения.",
    "common.JavaFileWriter": "Буфер генерации Java-кода с управлением отступами и записью в файл.",
    "common.ParserException": "Исключение при разборе XML-модели метаданных.",
    "common.Transliterator": "Транслитерация русских наименований в латиницу для идентификаторов.",
}

ENUM_CONST_RU = {
    "AttrType.STRING": "Строковое значение", "AttrType.DECIMAL": "Числовое значение",
    "AttrType.DATE": "Дата", "AttrType.DATETIME": "Дата и время",
    "EntityKind.PRIMARY": "Основная сущность", "EntityKind.CHILD": "Дочерняя сущность",
    "EntityKind.REFERENCE_DICTIONARY": "Справочник",
    "ModifyType.INSERT": "Создание записи", "ModifyType.UPDATE": "Изменение записи",
    "ModifyType.DELETE": "Удаление записи", "ModifyType.LOGICAL_EDIT": "Логическое редактирование",
    "ModifyType.ARCHIVE": "Архивирование (перенос в архив)",
}

FIELD_RU = {
    "name": "системное имя", "guid": "глобальный идентификатор (GUID)",
    "title": "отображаемое наименование", "caption": "подпись (заголовок)",
    "orderNumber": "порядковый номер", "flagDisplay": "признак отображения",
    "valueType": "тип значения", "type": "тип", "kind": "вид",
    "basePackage": "базовый Java-пакет генерируемого кода", "connection": "соединение с БД",
    "featureName": "имя подсистемы", "searchGuid": "идентификатор поиска (GUID)",
    "params": "список параметров", "stereoType": "стереотип", "dmodule": "модуль (dmodule)",
    "required": "признак обязательности", "mask": "маска ввода", "properties": "список свойств",
    "propertyGroups": "список групп свойств", "className": "имя класса", "methodName": "имя метода",
    "passed": "число успешных тестов", "skipped": "число пропущенных тестов",
    "failed": "число проваленных тестов", "total": "общее число тестов",
    "durationMs": "длительность, мс", "baseUrl": "базовый адрес тестируемого сайта",
    "testLevel": "уровень тестирования", "entities": "список сущностей", "searches": "список поисков",
    "operations": "список операций", "modifiers": "список модификаторов",
    "roleA": "роль A ассоциации", "roleB": "роль B ассоциации", "reason": "причина классификации",
    "parentEntity": "родительская сущность", "parentGrid": "родительская таблица",
    "status": "статус", "error": "сообщение об ошибке", "steps": "шаги теста",
    "value": "значение", "login": "логин", "password": "пароль", "siteType": "тип сайта",
    "stepName": "имя шага", "feature": "подсистема",
}

METHOD_RU = {
    "parser.XmlModelParser#parse": "Разбирает XML-файл метаданных, возвращает модель AppModel.",
    "generator.TestGenerator#generate": "Генерирует тест-проект: инфраструктуру, Page Object и тест-классы.",
    "generator.TestRunner#run": "Запускает автотесты и возвращает результат прогона.",
    "common.Transliterator#transliterate": "Транслитерирует русский текст в латиницу.",
}

VERB = {
    "write": "Генерирует код:", "parse": "Разбирает", "generate": "Генерирует", "build": "Формирует",
    "create": "Создаёт", "read": "Читает", "load": "Загружает", "save": "Сохраняет",
    "insert": "Сохраняет в БД", "select": "Выбирает из БД", "update": "Обновляет", "delete": "Удаляет",
    "remove": "Удаляет", "add": "Добавляет", "find": "Находит", "resolve": "Определяет",
    "classify": "Классифицирует", "extract": "Извлекает", "to": "Преобразует в", "from": "Создаёт из",
    "format": "Форматирует", "escape": "Экранирует", "esc": "Экранирует", "csv": "Формирует CSV:",
    "indent": "Увеличивает отступ", "unindent": "Уменьшает отступ", "transliterate": "Транслитерирует",
    "snake": "Преобразует в snake_case", "looks": "Проверяет, что", "main": "Точка входа",
    "now": "Возвращает текущие дату и время", "today": "Возвращает текущую дату", "open": "Открывает",
    "close": "Закрывает", "initialize": "Инициализирует", "init": "Инициализирует", "run": "Выполняет",
    "on": "Обработчик события:", "show": "Отображает", "link": "Связывает", "clean": "Очищает",
    "folder": "Формирует имя папки", "skip": "Пропускает", "ensure": "Гарантирует наличие",
}
UI_SUFFIX = [("ComboBox", "список"), ("CheckBox", "флажок"), ("TextField", "поле ввода"),
             ("PasswordField", "поле пароля"), ("TextArea", "область текста"), ("Button", "кнопка"),
             ("TableView", "таблица"), ("TableColumn", "столбец таблицы"), ("TreeView", "дерево"),
             ("Field", "поле"), ("Btn", "кнопка"), ("Table", "таблица"), ("Column", "столбец"),
             ("Tree", "дерево"), ("Label", "надпись"), ("Combo", "список"), ("Check", "флажок"),
             ("Area", "область"), ("Pane", "панель"), ("Box", "блок")]

ACCESS_WORDS = {"public", "protected", "private", "пакетный"}
VIS = {"public": "+", "protected": "#", "private": "-", "пакетный": "~"}


def load_api():
    return json.load(open(API, encoding="utf-8"))


def vis(access):
    tok = access.split()[0] if access else "пакетный"
    return VIS.get(tok if tok in ACCESS_WORDS else "пакетный", "~")


def is_static(access):
    return "static" in access.split()


def humanize(name):
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name).strip()


def field_ru(fname):
    if fname in FIELD_RU:
        return FIELD_RU[fname]
    for suf, ru in UI_SUFFIX:
        if fname.endswith(suf) and len(fname) > len(suf):
            base = humanize(fname[:-len(suf)]).lower()
            return f"{ru} «{base}»" if base else ru
    return humanize(fname).lower()


def accessor_field(n):
    for p in ("get", "set"):
        if n.startswith(p) and len(n) > len(p) and n[len(p)].isupper():
            return n[len(p)].lower() + n[len(p) + 1:]
    for p in ("is", "has"):
        if n.startswith(p) and len(n) > len(p) and n[len(p)].isupper():
            return n[len(p)].lower() + n[len(p) + 1:]
    return None


def gloss(name):
    m = re.match(r"[a-z]+", name)
    v = m.group(0) if m else ""
    rest = humanize(name[len(v):]).strip()
    base = VERB.get(v)
    if base is None:
        return None
    return (base + " " + rest).strip() if rest else base


def describe_field(f):
    if f.get("doc"):
        return f["doc"]
    d = field_ru(f["name"])
    return d[:1].upper() + d[1:]


def describe_method(pkg_qual, m):
    key = pkg_qual + "#" + m["name"]
    if key in METHOD_RU:
        return METHOD_RU[key]
    if m.get("doc"):
        return m["doc"]
    if m["ctor"]:
        return "Конструктор с параметрами." if m["params"] else "Конструктор по умолчанию."
    fn = accessor_field(m["name"])
    if fn is not None:
        d = field_ru(fn); d = d[:1].lower() + d[1:]
        if m["name"].startswith("set"):
            return "Устанавливает " + d + "."
        if m["name"].startswith(("is", "has")):
            return "Возвращает признак «" + d + "»."
        return "Возвращает " + d + "."
    g = gloss(m["name"])
    return g if g else humanize(m["name"]).capitalize()


# ---------- индекс классов и связи ----------
COLLECTION = ("List", "Set", "Map", "Collection", "ArrayList", "HashMap", "LinkedHashMap")
PRIMITIVE = {"void", "int", "long", "double", "boolean", "String", "char", "byte", "short",
             "float", "Object", "Integer", "Long", "Double", "Boolean", "Path", "File",
             "Connection", "T", "E", "K", "V"}


def build_index(api):
    """simple name -> package."""
    idx = {}
    for pkg, types in api.items():
        for r in types:
            idx[r["name"]] = pkg
    return idx


def type_refs(t):
    return re.findall(r"[A-Za-z_][A-Za-z0-9_]*", t or "")


def is_collection(t):
    return any(t.strip().startswith(c) for c in COLLECTION) or t.strip().endswith("[]")


def xref(typename, idx, cur_pkg):
    """Метка типа с префиксом «Пакет::» для межпакетных классов своего кода."""
    for tok in type_refs(typename):
        if tok in idx and idx[tok] != cur_pkg:
            typename = typename.replace(tok, f"{idx[tok]}::{tok}")
    return typename


def relations(pkg, api, idx):
    """Связи ВНУТРИ пакета: ('gen'|'agg'|'comp'|'nest', src, dst, mult)."""
    types = api[pkg]
    own = {r["name"] for r in types}
    rels = []
    for r in types:
        src = r["name"]
        # наследование/реализация (только к своим классам пакета)
        for base in ([r["extends"]] if r["extends"] else []) + (r["implements"] or []):
            for tok in type_refs(base):
                if tok in own and tok != src:
                    rels.append(("gen", src, tok, ""))
        # вложенность
        if "." in r["qualified"]:
            outer = r["qualified"].split(".")[0]
            if outer in own:
                rels.append(("nest", outer, src, ""))
        # агрегация/композиция по полям
        for f in r["fields"]:
            coll = is_collection(f["type"])
            for tok in type_refs(f["type"]):
                if tok in own and tok != src:
                    kind = "agg" if coll else "comp"
                    mult = "*" if coll else "1"
                    rels.append((kind, src, tok, mult))
                    break
    # уникализировать
    seen = set(); out = []
    for x in rels:
        if x not in seen:
            seen.add(x); out.append(x)
    return out


if __name__ == "__main__":
    api = load_api(); idx = build_index(api)
    for pkg in PKG_ORDER:
        print(f"\n=== {pkg}: {len(api[pkg])} классов ===")
        for r in api[pkg]:
            print(f"  {vis(r['access'])} {r['qualified']:32} поля={len(r['fields'])} методы={len(r['methods'])}")
        for rk in relations(pkg, api, idx):
            print("   связь:", rk)
