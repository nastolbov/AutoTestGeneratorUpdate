# -*- coding: utf-8 -*-
"""Единая модель «по файлам» для модульной структуры и структурной карты Константайна.

Модуль = файл .java. Все 42 файла программы AutoTestGenerator рассматриваются как узлы:
  * module    — функциональный модуль (имеет собственное управление);
  * library   — повторно используемая библиотека/утилита;
  * pojo      — класс данных предметной области (метаданные/результаты прогона);
  * enum      — перечисление (используется как управляющий признак);
  * exception — класс исключения.

Узлы и точные куплеты вызовов активных модулей переиспользуются из module_model
(построены по реальному коду, раздел 1.5.3). Здесь добавлены классы данных пакета model
(POJO/перечисления) и common/ParserException, а также рёбра «производитель → класс данных»
(запись полей, ↓○) и «потребитель → класс данных» (чтение полей, ↑○; перечисления — ↑●).
Связи выверены по исходникам (parser/*, generator/*, ui/MainController, data/*Dao)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import const_model as CM
import module_model as MM

PKG_ORDER = CM.PKG_ORDER
PKG_RU = {"ui": "ui — интерфейс", "parser": "parser — разбор XML",
          "model": "model — модель данных", "generator": "generator — генерация тестов",
          "data": "data — хранилище отчётов", "common": "common — утилиты"}

# --------------------------------------------------------------- классификация узлов
_MODULE_KEYS = set(MM.module_keys())                 # 24 активных модуля (module|library)
_LIB = set(MM.LIBRARY)                               # из них библиотеки
# классы данных, ошибочно попавшие бы в «module» (TestConfig — чистый контейнер параметров)
_POJO_OVERRIDE = {"TestConfig"}


def _kind(cls, raw):
    if cls in _LIB:
        return "library"
    if cls == "ParserException":
        return "exception"
    if raw == "enum":
        return "enum"
    if cls in _POJO_OVERRIDE:
        return "pojo"
    if cls in _MODULE_KEYS:
        return "module"
    return "pojo"                                    # классы пакета model без поведения


# узлы: (key=имя класса, label, package, kind)
NODES = [(cls, cls, pkg, _kind(cls, raw)) for pkg, fn, cls, raw, _consts in CM.MODULES]
KIND = {n[0]: n[3] for n in NODES}
PKG = {n[0]: n[2] for n in NODES}
KEYS = [n[0] for n in NODES]

# --------------------------------------------------------------- рёбра карты Константайна
# формат: (src, dst, [данные-вниз ↓○], [данные-вверх ↑○], [управление ↑●], условие)
# условие: "" | "once" (1) | "cyclic" (↺ для каждого элемента) | "cond" (◇ по признаку)

# 1) точные куплеты вызовов активных модулей — как в разделе 1.5.3
CALL_EDGES = list(MM.EDGES)

# 2) производство модели парсерами (запись полей класса данных: ↓○)
PRODUCE_EDGES = [
    ("XmlModelParser", "AppModel", ["categoryName", "guid"], [], [], "once"),
    ("XmlModelParser", "ParserException", [], [], ["ошибка разбора XML"], ""),
    ("EntityParser", "EntityObject", ["guid", "name", "featureName"], [], [], "cyclic"),
    ("EntityParser", "Association", ["roleA", "roleB", "associateItemGuid"], [], [], "cyclic"),
    ("PropertyGroupParser", "PropertyGroup", ["name", "stereoType", "typeLink"], [], [], "cyclic"),
    ("PropertyGroupParser", "Property", ["attrName", "mask", "required"], [], [], "cyclic"),
    ("PropertyGroupParser", "Operation", ["operationMethod"], [], [], "cyclic"),
    ("PropertyGroupParser", "OperationParam", ["name", "paramType"], [], [], "cyclic"),
    ("PropertyGroupParser", "Modifier", ["title"], [], [], "cyclic"),
    ("PropertyGroupParser", "AttrType", ["attrType (XML)"], [], ["STRING/DECIMAL/DATE"], "cyclic"),
    ("PropertyGroupParser", "ModifyType", ["modifyType (XML)"], [], ["I/U/D/E/A"], "cyclic"),
    ("SearchParser", "Search", ["name", "searchObjectGuid"], [], [], "cyclic"),
    ("SearchParser", "SearchParam", ["name", "valueType", "mask"], [], [], "cyclic"),
    ("SearchParser", "SearchResult", ["idObjectName"], [], [], "cyclic"),
    ("SearchParser", "SearchResultProperty", ["name", "title", "visible"], [], [], "cyclic"),
]

# 3) потребление модели (чтение полей: ↑○; перечисления как управляющий признак: ↑●)
CONSUME_EDGES = [
    ("EntityClassifier", "AppModel", [], ["entities", "searches"], [], ""),
    ("EntityClassifier", "EntityObject", [], ["guid", "name", "CRUD"], [], "cyclic"),
    ("EntityClassifier", "EntityKind", [], [], ["PRIMARY/CHILD/REFERENCE"], ""),
    ("TestGenerator", "AppModel", [], ["entities"], [], ""),
    ("PageObjectWriter", "EntityObject", [], ["name", "propertyGroups"], [], ""),
    ("PageObjectWriter", "PropertyGroup", [], ["stereoType", "properties"], [], "cyclic"),
    ("PageObjectWriter", "Property", [], ["attrName", "attrType", "mask"], [], "cyclic"),
    ("TestClassWriter", "EntityObject", [], ["name", "propertyGroups"], [], ""),
    ("TestClassWriter", "Property", [], ["attrType", "required", "mask"], [], "cyclic"),
    ("TestClassWriter", "Operation", [], ["modifiers"], [], "cyclic"),
    ("TestClassWriter", "Search", [], ["params", "result"], [], "cyclic"),
    ("TestClassWriter", "ModifyType", [], [], ["I/U/D"], "cond"),
    ("TestDataFactory", "Property", [], ["attrType", "mask"], [], ""),
    ("TestDataFactory", "SearchParam", [], ["valueType", "mask"], [], ""),
    ("TestDataFactory", "AttrType", [], [], ["STRING/DECIMAL/DATE/DATETIME"], "cond"),
    ("TestRunner", "TestRunResult", ["totalTests", "passed", "failed"], [], [], "once"),
    ("TestRunner", "TestCaseResult", ["className", "methodName", "passed"], [], [], "cyclic"),
    ("RunReportWriter", "TestRunResult", [], ["totalTests", "durationMs"], [], ""),
    ("RunReportWriter", "TestCaseResult", [], ["passed", "failureMessage", "screenshots"], [], "cyclic"),
    ("ReportDao", "TestRunResult", ["прогон"], ["история"], [], ""),
    ("TestRunDao", "TestRunResult", ["INSERT run"], ["SELECT"], [], ""),
    ("TestCaseDao", "TestCaseResult", ["INSERT test_case"], ["SELECT"], [], "cyclic"),
    ("MainController", "AppModel", [], ["entities", "searches"], [], ""),
    ("MainController", "EntityClassifier", ["EntityObject", "AppModel"], [], ["вид сущности"], "cyclic"),
    ("MainController", "TestRunResult", [], ["totalTests", "passed", "failed"], [], ""),
    ("MainController", "TestCaseResult", [], ["className", "passed"], [], "cyclic"),
]

EDGES = CALL_EDGES + PRODUCE_EDGES + CONSUME_EDGES


# --------------------------------------------------------------- самопроверка
def _selfcheck():
    keyset = set(KEYS)
    dangling = [(s, d) for s, d, *_ in EDGES if s not in keyset or d not in keyset]
    deg = {k: 0 for k in KEYS}
    for s, d, *_ in EDGES:
        if s in deg:
            deg[s] += 1
        if d in deg:
            deg[d] += 1
    orphans = [k for k, v in deg.items() if v == 0]
    from collections import Counter
    kinds = Counter(KIND.values())
    return dangling, orphans, kinds, deg


if __name__ == "__main__":
    dangling, orphans, kinds, deg = _selfcheck()
    print(f"Узлов (файлов): {len(KEYS)} | рёбер: {len(EDGES)} "
          f"(вызовы {len(CALL_EDGES)}, производство {len(PRODUCE_EDGES)}, потребление {len(CONSUME_EDGES)})")
    print("Виды узлов:", dict(kinds))
    print("Висячие ключи рёбер:", dangling if dangling else "нет")
    print("Узлы без рёбер:", orphans if orphans else "нет")
    print("\nFan-in/out по узлам (степень связности):")
    for pkg in PKG_ORDER:
        for k in KEYS:
            if PKG[k] == pkg:
                print(f"  [{pkg}] {k:24s} {KIND[k]:9s} степень={deg[k]}")
