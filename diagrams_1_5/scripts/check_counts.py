# -*- coding: utf-8 -*-
"""Жёсткая проверка совпадения счётчиков. Аргумент — имя пакета: проверяется образец
Образец_1.5_<pkg>.docx и только классы этого пакета; без аргумента — весь раздел.
Проверяет: число строк таблиц полей = коду; методов = код БЕЗ геттеров/сеттеров
(visible_methods) = детальной диаграмме; список классов = размеру пакета; узлы в
.dot диаграмм классов = числу классов; сценарии сбалансированы (все активации
закрываются), нет «Пользователя», объекты кооперации = объектам последовательностей."""
import os, re, sys, subprocess
from docx import Document
from docx.text.paragraph import Paragraph
from docx.table import Table
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apimodel as M
import scenarios as S

ONLY = sys.argv[1] if len(sys.argv) > 1 else None
DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DOCX = os.path.join(DIA, "..", f"Образец_1.5_{ONLY}.docx" if ONLY else "Раздел_1.5_исправленный.docx")
api = M.load_api()
pkgs = [ONLY] if ONLY else M.PKG_ORDER
code = {r["qualified"]: r for p in pkgs for r in api[p]}
fails = []


def ok(cond, msg):
    if not cond:
        fails.append(msg)


# 1) таблицы docx
d = Document(DOCX)
last_cap = ""; seen_f, seen_m, classlists = set(), set(), 0
for child in d.element.body.iterchildren():
    tag = child.tag.split("}")[-1]
    if tag == "p":
        t = Paragraph(child, d).text.strip()
        if t.startswith("Таблица"):
            last_cap = t
    elif tag == "tbl":
        nd = len(Table(child, d).rows) - 1
        m = re.search(r"Поля класса (\S+)", last_cap)
        if m:
            q = m.group(1); seen_f.add(q)
            ok(q in code and nd == len(code[q]["fields"]),
               f"ПОЛЯ {q}: таблица {nd}, код {len(code.get(q, {}).get('fields', []))}")
        m = re.search(r"Методы класса (\S+)", last_cap)
        if m:
            q = m.group(1); seen_m.add(q)
            exp = len(M.visible_methods(code[q])) if q in code else -1
            ok(nd == exp, f"МЕТОДЫ {q}: таблица {nd}, код(без get/set) {exp}")
        m = re.search(r"Классы пакета «(\w+)»", last_cap)
        if m:
            classlists += 1
            pkg = next((p for p in M.PKG_ORDER if M.PKG_META[p][1].lower() == m.group(1).lower()), None)
            ok(pkg and nd == len(api[pkg]), f"КЛАССЫ {m.group(1)}: таблица {nd}, код {len(api.get(pkg, []))}")
        last_cap = ""

for q, r in code.items():
    if r["fields"]:
        ok(q in seen_f, f"нет таблицы полей для {q}")
    if M.visible_methods(r):
        ok(q in seen_m, f"нет таблицы методов для {q}")
ok(classlists == len(pkgs), f"таблиц-списков классов {classlists}, ожидалось {len(pkgs)}")

# 2) узлы .dot = числу классов
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "gen_class_diagrams.py")],
               check=True, stdout=subprocess.DEVNULL)
for pkg in pkgs:
    for kind in ("source", "refined", "detailed"):
        dot = open(f"/tmp/cls_{pkg}_{kind}.dot", encoding="utf-8").read()
        nn = len(re.findall(r"\[label=", dot))
        ok(nn == len(api[pkg]), f"диаграмма cls_{pkg}_{kind}: узлов {nn}, классов {len(api[pkg])}")

# 3) сценарии: нет «Пользователя»; сбалансированность (активации закрыты); кооперация = объектам
for pkg in pkgs:
    objkeys = {k for k, _ in S.SC[pkg]["objects"]}
    coop = set(S.used_object_keys(pkg)); seq = set()
    for fk, flow in S.SC[pkg]["flows"].items():
        stack = 0
        for frm, to, text, kind in flow:
            ok("ользовател" not in text, f"{pkg}/{fk}: «Пользователь» в подписи «{text}»")
            ok(frm in objkeys or frm == S.EDGE, f"{pkg}/{fk}: неизвестный объект {frm}")
            ok(to in objkeys or to == S.EDGE, f"{pkg}/{fk}: неизвестный объект {to}")
            if frm != S.EDGE: seq.add(frm)
            if to != S.EDGE: seq.add(to)
            if kind in ("call", "create"): stack += 1
            elif kind == "ret": stack -= 1
        ok(stack == 0, f"{pkg}/{fk}: несбалансированные вызовы (открытых активаций {stack})")
    ok(coop == seq, f"{pkg}: объекты кооперации {coop} != последовательностей {seq}")

print("=" * 60)
if fails:
    print(f"❌ ПРОВАЛЕНО: {len(fails)}")
    for f in fails:
        print("  -", f)
    sys.exit(1)
print(f"✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ ({'пакет ' + ONLY if ONLY else 'весь раздел'})")
print(f"  таблиц полей {len(seen_f)}, методов {len(seen_m)}, списков классов {classlists}; классов {len(code)}")
