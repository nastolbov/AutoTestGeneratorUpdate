# -*- coding: utf-8 -*-
"""Сверка раздела 1.5.3: число модулей на дереве == строкам таблицы спецификации ==
модели; узлы карты Константайна == модули+области данных; узлы диаграммы компонентов
== строкам таблицы компонентов; таблицы связности/сцепления заполнены."""
import os, re, sys, subprocess
from docx import Document
from docx.text.paragraph import Paragraph
from docx.table import Table
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DOCX = os.path.join(DIA, "..", "Раздел_1.5.3_исправленный.docx")
fails = []
def ok(c, m): (fails.append(m) if not c else None)

# перегенерировать диаграммы
for g in ("gen_module_struct.py", "gen_constantine.py", "gen_component.py"):
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), g)], check=True, stdout=subprocess.DEVNULL)

def dot_nodes(path):
    s = open(path, encoding="utf-8").read()
    return len(re.findall(r'^\s+\w+\s*\[label=', s, re.M))

nmod = len(MM.MODULES)
n_lib = sum(1 for m in MM.MODULES if m[3] == "library")
ms = dot_nodes("/tmp/mod_struct.dot")
ok(ms == nmod, f"дерево модулей: узлов {ms}, ожидалось {nmod}")
# на схеме модульной структуры присутствуют все модули, включая библиотеки
msdot = open("/tmp/mod_struct.dot", encoding="utf-8").read()
for k in MM.module_keys():
    ok(re.search(rf'^\s+{k}\s*\[label=', msdot, re.M) is not None,
       f"модульная структура: нет узла {k}")
cdot = open("/tmp/constantine.dot", encoding="utf-8").read()
for k in MM.module_keys() + [a[0] for a in MM.DATA_AREAS]:
    ok(re.search(rf'^\s+{k}\s*\[label=', cdot, re.M) is not None,
       f"Константайн: нет узла {k}")
cn = dot_nodes("/tmp/constantine.dot")
cc = dot_nodes("/tmp/components.dot")
ok(cc == len(MM.COMPONENTS), f"компоненты: узлов {cc}, в модели {len(MM.COMPONENTS)}")

# таблицы docx (сопоставление по подписи без учёта регистра)
KEYS = {"спецификация модулей": "Спецификация модулей", "связность модулей": "Связность модулей",
        "сцепление модулей": "Сцепление модулей", "описание компонентов": "Описание компонентов"}
d = Document(DOCX); last = ""; tabs = {}
for ch in d.element.body.iterchildren():
    tg = ch.tag.split("}")[-1]
    if tg == "p":
        t = Paragraph(ch, d).text.strip()
        if t: last = t
    elif tg == "tbl":
        nd = len(Table(ch, d).rows) - 1
        low = last.lower()
        for key, canon in KEYS.items():
            if key in low: tabs[canon] = nd
        last = ""
ok(tabs.get("Спецификация модулей") == nmod, f"табл. спецификации: {tabs.get('Спецификация модулей')} ≠ {nmod}")
ok(tabs.get("Связность модулей") == nmod, f"табл. связности: {tabs.get('Связность модулей')} ≠ {nmod}")
ok(tabs.get("Сцепление модулей") == len(MM.COUPLING), f"табл. сцепления: {tabs.get('Сцепление модулей')} ≠ {len(MM.COUPLING)}")
ok(tabs.get("Описание компонентов") == len(MM.COMPONENTS), f"табл. компонентов: {tabs.get('Описание компонентов')} ≠ {len(MM.COMPONENTS)}")

# все модули в EDGES существуют
keys = set(MM.module_keys())
for s, d2, *_ in MM.EDGES:
    ok(s in keys and d2 in keys, f"ребро {s}->{d2}: неизвестный модуль")

print("=" * 56)
if fails:
    print("❌ ПРОВАЛЕНО:", len(fails))
    for f in fails: print("  -", f)
    sys.exit(1)
print(f"✅ 1.5.3 OK: модулей {nmod} (таблица спецификации == модель == схема модульной структуры, "
      f"включая {n_lib} библиотек); карта Константайна содержит все {nmod} "
      f"модуля + {len(MM.DATA_AREAS)} области данных; компонентов {len(MM.COMPONENTS)} (диаграмма==таблица); "
      f"связей в сцеплении {len(MM.COUPLING)}")
