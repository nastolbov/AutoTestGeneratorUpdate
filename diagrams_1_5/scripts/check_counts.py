# -*- coding: utf-8 -*-
"""Жёсткая трёхсторонняя сверка: ДИАГРАММА == ТАБЛИЦА == КОД.
- gen_class_diagrams.py пишет /tmp/cls_counts.json (что отрисовано на диаграммах).
- сверяем: поля/методы каждого класса в диаграмме == строкам таблиц == коду
  (методы — без тривиальных геттеров/сеттеров);
- число классов на диаграмме (боксов) == строкам таблицы классов пакета;
- сценарии: «Пользователя» нет, активации сбалансированы (закрываются возвратом),
  внешние классы встречаются ТОЛЬКО в common.
Аргумент — имя пакета: проверяется образец Образец_1.5_<pkg>.docx; без аргумента — весь раздел."""
import os, re, sys, json, subprocess
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


def ok(c, m):
    if not c:
        fails.append(m)


# диаграммы + сайдкар счётчиков
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "gen_class_diagrams.py")],
               check=True, stdout=subprocess.DEVNULL)
cls_counts = json.load(open("/tmp/cls_counts.json", encoding="utf-8"))

# ДИАГРАММА == КОД
for pkg in pkgs:
    for q, r in [(r["qualified"], r) for r in api[pkg]]:
        dc = cls_counts[pkg].get(q, {})
        ok(dc.get("fields") == len(r["fields"]),
           f"{pkg}/{q}: полей на диаграмме {dc.get('fields')}, в коде {len(r['fields'])}")
        ok(dc.get("methods") == len(M.visible_methods(r)),
           f"{pkg}/{q}: методов на диаграмме {dc.get('methods')}, в коде(без get/set) {len(M.visible_methods(r))}")

# узлы .dot == числу элементов на диаграмме (свои + внешние)
for pkg in pkgs:
    n_expected = len(cls_counts[pkg])
    for kind in ("source", "refined", "detailed"):
        dot = open(f"/tmp/cls_{pkg}_{kind}.dot", encoding="utf-8").read()
        nn = len(re.findall(r"\[label=", dot))
        ok(nn == n_expected, f"диаграмма cls_{pkg}_{kind}: боксов {nn}, ожидалось {n_expected}")

# ТАБЛИЦЫ docx == ДИАГРАММА/КОД
d = Document(DOCX)
last = ""; seen_f, seen_m = set(), set(); classlist = {}
for ch in d.element.body.iterchildren():
    tg = ch.tag.split("}")[-1]
    if tg == "p":
        t = Paragraph(ch, d).text.strip()
        if t.startswith("Таблица"):
            last = t
    elif tg == "tbl":
        nd = len(Table(ch, d).rows) - 1
        m = re.search(r"Поля класса (\S+)", last)
        if m:
            q = m.group(1); seen_f.add(q)
            ok(q in code and nd == len(code[q]["fields"]),
               f"таблица ПОЛЯ {q}: {nd}, код {len(code.get(q, {}).get('fields', []))}")
        m = re.search(r"Методы класса (\S+)", last)
        if m:
            q = m.group(1); seen_m.add(q)
            exp = len(M.visible_methods(code[q])) if q in code else -1
            ok(nd == exp, f"таблица МЕТОДЫ {q}: {nd}, код(без get/set) {exp}")
        m = re.search(r"Классы пакета «(\w+)»", last)
        if m:
            classlist[m.group(1).lower()] = nd
        last = ""

for pkg in pkgs:
    for r in api[pkg]:
        q = r["qualified"]
        if r["fields"]:
            ok(q in seen_f, f"нет таблицы полей для {q}")
        if M.visible_methods(r):
            ok(q in seen_m, f"нет таблицы методов для {q}")
    title = M.PKG_META[pkg][1].lower()
    ok(classlist.get(title) == len(cls_counts[pkg]),
       f"таблица классов {title}: {classlist.get(title)}, на диаграмме {len(cls_counts[pkg])}")

# сценарии
for pkg in pkgs:
    objkeys = {k for k, _ in S.SC[pkg]["objects"]}
    coop = set(S.used_object_keys(pkg)); seq = set()
    for fk, flow in S.SC[pkg]["flows"].items():
        depth = 0
        for frm, to, text, kind in flow:
            ok("ользовател" not in text, f"{pkg}/{fk}: «Пользователь» в подписи «{text}»")
            for k in (frm, to):
                ok(k in objkeys or k == S.EDGE, f"{pkg}/{fk}: неизвестный объект {k}")
                if k != S.EDGE and "::" in S.label_of(pkg, k):
                    ok(pkg == "common", f"{pkg}/{fk}: внешний класс {S.label_of(pkg, k)} вне common")
            if frm != S.EDGE: seq.add(frm)
            if to != S.EDGE: seq.add(to)
            if kind in ("call", "create"): depth += 1
            elif kind == "ret": depth -= 1
        ok(depth == 0, f"{pkg}/{fk}: несбалансированы вызовы (открытых активаций {depth})")
    ok(coop == seq, f"{pkg}: объекты кооперации != последовательностей")

print("=" * 60)
if fails:
    print(f"❌ ПРОВАЛЕНО: {len(fails)}")
    for f in fails[:40]:
        print("  -", f)
    sys.exit(1)
print(f"✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ ({'пакет ' + ONLY if ONLY else 'весь раздел'})")
print(f"  диаграмма==таблица==код для {len(code)} классов; внешние только в common")
