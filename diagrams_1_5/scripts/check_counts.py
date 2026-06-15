# -*- coding: utf-8 -*-
"""Жёсткая проверка совпадения счётчиков: парсит готовый docx и сверяет число строк
в таблицах полей/методов/классов с кодом (api.json); считает узлы в .dot диаграмм
классов (= числу классов пакета); проверяет, что в сценариях нет «Пользователя» и
что набор объектов кооперации = объектам последовательностей."""
import os, re, sys, glob
from docx import Document
from docx.text.paragraph import Paragraph
from docx.table import Table
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apimodel as M
import scenarios as S

DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DOCX = os.path.join(DIA, "..", "Раздел_1.5_исправленный.docx")
api = M.load_api()
code = {r["qualified"]: r for p in api for r in api[p]}
fails = []


def ok(cond, msg):
    if not cond:
        fails.append(msg)


# 1) docx-таблицы
d = Document(DOCX)
last_cap = ""
seen_fields, seen_methods, seen_classlist = set(), set(), 0
for child in d.element.body.iterchildren():
    tag = child.tag.split("}")[-1]
    if tag == "p":
        t = Paragraph(child, d).text.strip()
        if t.startswith("Таблица"):
            last_cap = t
    elif tag == "tbl":
        tb = Table(child, d); ndata = len(tb.rows) - 1
        m = re.search(r"Поля класса (\S+)", last_cap)
        if m:
            q = m.group(1); seen_fields.add(q)
            ok(q in code and ndata == len(code[q]["fields"]),
               f"ПОЛЯ {q}: в таблице {ndata}, в коде {len(code.get(q, {}).get('fields', []))}")
        m = re.search(r"Методы класса (\S+)", last_cap)
        if m:
            q = m.group(1); seen_methods.add(q)
            ok(q in code and ndata == len(code[q]["methods"]),
               f"МЕТОДЫ {q}: в таблице {ndata}, в коде {len(code.get(q, {}).get('methods', []))}")
        m = re.search(r"Классы пакета «(\w+)»", last_cap)
        if m:
            seen_classlist += 1
            title = m.group(1).lower()
            pkg = {"ui": "ui", "parser": "parser", "model": "model", "generator": "generator",
                   "data": "data", "common": "common"}.get(title)
            ok(pkg and ndata == len(api[pkg]),
               f"КЛАССЫ пакета {title}: в таблице {ndata}, в коде {len(api.get(pkg, []))}")
        last_cap = ""

# все классы с полями/методами должны иметь таблицу
for q, r in code.items():
    if r["fields"]:
        ok(q in seen_fields, f"нет таблицы полей для {q}")
    if r["methods"]:
        ok(q in seen_methods, f"нет таблицы методов для {q}")
ok(seen_classlist == 6, f"таблиц-списков классов {seen_classlist}, ожидалось 6")

# 2) узлы в .dot диаграмм классов = числу классов пакета
import subprocess
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "gen_class_diagrams.py")],
               check=True, stdout=subprocess.DEVNULL)
for pkg in M.PKG_ORDER:
    nclass = len(api[pkg])
    for kind in ("source", "refined", "detailed"):
        dot = open(f"/tmp/cls_{pkg}_{kind}.dot", encoding="utf-8").read()
        nnodes = len(re.findall(r"\[label=", dot))
        ok(nnodes == nclass, f"диаграмма cls_{pkg}_{kind}: узлов {nnodes}, классов {nclass}")

# 3) сценарии: нет «Пользователя»; кооперация = объектам последовательностей
for pkg in S.SC:
    for fk, flow in S.SC[pkg]["flows"].items():
        for frm, to, text, kind in flow:
            ok("ользовател" not in text and "ользовател" not in frm and "ользовател" not in to,
               f"{pkg}/{fk}: упоминание Пользователя в «{text}»")
    coop_objs = set(S.used_object_keys(pkg))
    seq_objs = set()
    for flow in S.SC[pkg]["flows"].values():
        for frm, to, _, _ in flow:
            if frm != S.EDGE: seq_objs.add(frm)
            if to != S.EDGE: seq_objs.add(to)
    ok(coop_objs == seq_objs, f"{pkg}: объекты кооперации {coop_objs} != объектам последовательностей {seq_objs}")

# итог
print("=" * 60)
if fails:
    print(f"❌ ПРОВАЛЕНО проверок: {len(fails)}")
    for f in fails:
        print("  -", f)
    sys.exit(1)
print("✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ")
print(f"  классов задокументировано: полей-таблиц {len(seen_fields)}, методов-таблиц {len(seen_methods)}, "
      f"списков классов {seen_classlist}")
print(f"  всего классов в коде: {len(code)}; пакетов: {len(api)}")
