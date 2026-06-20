# -*- coding: utf-8 -*-
"""Горизонтальная (альбомная) версия Рис.3 «Доменная модель сущностей АИС ГСК»
для вставки в презентацию. Содержание идентично графу `ris_domain` из
gen_graphviz.py, но раскладка альбомная (rankdir=TB, три сбалансированных ранга:
основные сущности → композиционные потомки → справочники).
Семантика связей сохранена: композиция — сплошная линия (vee), ссылка на
справочник (FK) — пунктир (open). Выход: diagrams_1_3/ris_domain_h.png."""
import subprocess
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"

domain = f'''
digraph DomainH {{
  layout=fdp; bgcolor=white; fontname="{FONT}"; fontsize=13;
  overlap=false; splines=true; sep="+18"; K=1.1; esep="+6";
  node [shape=box, style=filled, fillcolor=white, fontname="{FONT}", fontsize=13,
        color=black, fontcolor=black, penwidth=1.2, margin="0.22,0.12"];
  edge [fontname="{FONT}", fontsize=11, color=black, fontcolor=black, penwidth=1.1];

  // основные сущности (с собственным CRUD)
  GSK  [label="ГСК/ОГСК", penwidth=2];
  CONF [label="Совещание", penwidth=2];

  // композиционные потомки (вкладка-грид / узел дерева)
  HIST [label="История ГСК/ОГСК"];
  DOCG [label="Документ ГСК/ОГСК"];
  INV  [label="Приглашённый ГСК"];
  PART [label="Участник совещания"];
  AGEN [label="Повестка совещания"];
  DOCC [label="Документ совещания"];

  // справочники (FK-цель), форма «документ»
  node [shape=note];
  OFF  [label="Должностное лицо"];
  TYPE [label="Тип ГСК/ОГСК"];
  DIST [label="Район"];
  CAUSE[label="Причина смены"];

  // композиция — сплошная линия
  edge [arrowhead=vee, style=solid];
  GSK -> HIST; GSK -> DOCG;
  CONF -> INV; CONF -> PART; CONF -> AGEN; CONF -> DOCC;

  // ссылки на справочники (FK) — пунктир
  edge [arrowhead=open, style=dashed];
  GSK -> TYPE; GSK -> DIST; GSK -> CAUSE; GSK -> OFF;
  CONF -> OFF;
}}
'''

path = "/tmp/ris_domain_h.dot"
open(path, "w").write(domain)
subprocess.run(["dot", "-Tpng", "-Gdpi=200", path, "-o", f"{OUT}/ris_domain_h.png"], check=True)
print("OK ris_domain_h.png")
