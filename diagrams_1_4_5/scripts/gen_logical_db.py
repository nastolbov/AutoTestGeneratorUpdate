# -*- coding: utf-8 -*-
"""Логическая модель базы данных (раздел 1.4.5) — построена по реальной схеме SQLite
программы (ru.autotestgen.data.SchemaInitializer): сущности «Прогон тестов» (test_run)
и «Результат теста» (test_case), связь 1:N (идентифицирующая).

Логический уровень: сущности, атрибуты, ключи (PK/FK) и связь с мощностью — БЕЗ
СУБД-специфичных типов и индексов (это физический уровень). Нотация — в стиле ERwin
(заголовок сущности, блок ключа сверху, остальные атрибуты ниже)."""
import subprocess, os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "DejaVu Sans"


def entity(nid, title, pk, attrs):
    rows = [f'<TR><TD ALIGN="CENTER" BGCOLOR="#E8E8E8"><B>{title}</B></TD></TR>', '<HR/>']
    for a in pk:
        rows.append(f'<TR><TD ALIGN="LEFT">{a}</TD></TR>')
    rows.append('<HR/>')
    for a in attrs:
        rows.append(f'<TR><TD ALIGN="LEFT">{a}</TD></TR>')
    tbl = ('<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="4">'
           + "".join(rows) + '</TABLE>')
    return f'{nid} [shape=none, margin=0, label=<{tbl}>];'


run = entity("RUN", "test_run", ["id  (PK)"],
             ["run_date", "xml_file", "base_url", "total", "passed", "failed", "skipped", "duration_ms"])
case = entity("CASE", "test_case", ["id  (PK)"],
              ["run_id  (FK)", "class_name", "method_name", "passed", "failure_msg", "duration_ms"])

dot = f'''
digraph LogicalDB {{
  rankdir=LR; bgcolor=white; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  nodesep=0.6; ranksep=1.4;

  {run}
  {case}

  RUN -> CASE [label="содержит", taillabel="1", headlabel="N", arrowhead=none, labeldistance=2.0];
}}
'''
path = "/tmp/ris_logical_db.dot"
open(path, "w").write(dot)
subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/ris_logical_db.png"], check=True)
print("OK ris_logical_db.png")

# ===================== Физическая модель (Рис. 11) =====================
runp = entity("RUN", "test_run", ["id : INTEGER  (PK)"],
              ["run_date : TEXT  NN", "xml_file : TEXT  NN", "base_url : TEXT  NN",
               "total : INTEGER  NN", "passed : INTEGER  NN", "failed : INTEGER  NN",
               "skipped : INTEGER  NN", "duration_ms : INTEGER  NN"])
casep = entity("CASE", "test_case", ["id : INTEGER  (PK)"],
               ["run_id : INTEGER  (FK) NN", "class_name : TEXT  NN", "method_name : TEXT  NN",
                "passed : INTEGER  NN", "failure_msg : TEXT", "duration_ms : INTEGER  NN"])
dot_phys = f'''
digraph PhysicalDB {{
  rankdir=LR; bgcolor=white; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  nodesep=0.6; ranksep=1.4;

  {runp}
  {casep}

  RUN -> CASE [label="run_id → id  (1:N)", arrowhead=vee];
}}
'''
open("/tmp/ris_physical_db.dot", "w").write(dot_phys)
subprocess.run(["dot", "-Tpng", "-Gdpi=150", "/tmp/ris_physical_db.dot", "-o", f"{OUT}/ris_physical_db.png"], check=True)
print("OK ris_physical_db.png")
