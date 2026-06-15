# -*- coding: utf-8 -*-
"""Очень внимательная трёхсторонняя сверка: КОД == ТАБЛИЦА == ДИАГРАММА.
- число классов одинаково на всех трёх диаграммах классов пакета (исходная/уточнённая/детальная);
- поля и методы каждого класса в коде == строкам таблиц == тому, что отрисовано на детальной диаграмме
  (методы — без тривиальных геттеров/сеттеров);
- сценарии: «Пользователя» нет, активации сбалансированы, внешние классы только в common.
Печатает подробный отчёт. Аргумент — пакет (образец); без аргумента — весь раздел."""
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


# диаграммы + сайдкар
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "gen_class_diagrams.py")],
               check=True, stdout=subprocess.DEVNULL)
cls_counts = json.load(open("/tmp/cls_counts.json", encoding="utf-8"))

# число боксов на .dot диаграммах (source/refined/detailed) — должно быть ОДИНАКОВО
box = {}
for pkg in pkgs:
    for kind in ("source", "refined", "detailed"):
        dot = open(f"/tmp/cls_{pkg}_{kind}.dot", encoding="utf-8").read()
        box[(pkg, kind)] = len(re.findall(r"\[label=", dot))
    s, r, dd = box[(pkg, "source")], box[(pkg, "refined")], box[(pkg, "detailed")]
    ok(s == r == dd == len(cls_counts[pkg]),
       f"{pkg}: число классов на диаграммах различается source={s} refined={r} detailed={dd} (ожид {len(cls_counts[pkg])})")

# таблицы docx
d = Document(DOCX)
last = ""; table_f, table_m, classlist = {}, {}, {}
for ch in d.element.body.iterchildren():
    tg = ch.tag.split("}")[-1]
    if tg == "p":
        t = Paragraph(ch, d).text.strip()
        if t:
            last = t
    elif tg == "tbl":
        nd = len(Table(ch, d).rows) - 1
        m = re.search(r"Поля класса (\S+)", last)
        if m:
            table_f[m.group(1)] = nd
        m = re.search(r"Методы класса (\S+)", last)
        if m:
            table_m[m.group(1)] = nd
        m = re.search(r"Классы пакета «(\w+)»", last)
        if m:
            classlist[m.group(1).lower()] = nd
        last = ""

# сверка КОД == ТАБЛИЦА == ДИАГРАММА по каждому классу
report = []
for pkg in pkgs:
    for r in api[pkg]:
        q = r["qualified"]
        cf, cm = len(r["fields"]), len(M.visible_methods(r))
        df = cls_counts[pkg].get(q, {}).get("fields")
        dm = cls_counts[pkg].get(q, {}).get("methods")
        tf = table_f.get(q, 0 if cf == 0 else None)
        tm = table_m.get(q, 0 if cm == 0 else None)
        ok(cf == df, f"{q}: поля код={cf} диаграмма={df}")
        ok(cf == tf, f"{q}: поля код={cf} таблица={tf}")
        ok(cm == dm, f"{q}: методы код={cm} диаграмма={dm}")
        ok(cm == tm, f"{q}: методы код={cm} таблица={tm}")
        report.append((pkg, q, cf, tf, df, cm, tm, dm))
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
                    ok(pkg == "common", f"{pkg}/{fk}: внешний класс вне common")
            if frm != S.EDGE: seq.add(frm)
            if to != S.EDGE: seq.add(to)
            if kind in ("call", "create"): depth += 1
            elif kind == "ret": depth -= 1
        ok(depth == 0, f"{pkg}/{fk}: несбалансированы вызовы ({depth})")
    ok(coop == seq, f"{pkg}: объекты кооперации != последовательностей")

# отчёт
print("\nКЛАСС                              код  табл диагр | код  табл диагр")
print("                                   ── поля ──       ── методы ──")
cur = None
for pkg, q, cf, tf, df, cm, tm, dm in report:
    if pkg != cur:
        print(f"--- пакет {pkg}: классов на диаграммах "
              f"S/R/D = {box[(pkg,'source')]}/{box[(pkg,'refined')]}/{box[(pkg,'detailed')]} ---")
        cur = pkg
    fmark = "OK" if cf == tf == df else "‼"
    mmark = "OK" if cm == tm == dm else "‼"
    print(f"  {q:32} {cf:3}  {str(tf):>3} {str(df):>4} {fmark} | {cm:3}  {str(tm):>3} {str(dm):>4} {mmark}")

print("=" * 64)
if fails:
    print(f"❌ ПРОВАЛЕНО: {len(fails)}")
    for f in fails[:40]:
        print("  -", f)
    sys.exit(1)
print(f"✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ ({'пакет ' + ONLY if ONLY else 'весь раздел'}): "
      f"код==таблица==диаграмма для {len(code)} классов; число классов на S/R/D диаграммах одинаково; "
      f"внешние только в common")
