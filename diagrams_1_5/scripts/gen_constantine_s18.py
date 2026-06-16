# -*- coding: utf-8 -*-
"""Структурная карта Константайна (18 модулей, старая схема) для версии в стиле
ВКР Сафиуллина. Модуль — прямоугольник, библиотека — двойная рамка, область данных —
овал. Вызовы — сплошные стрелки, обращение к библиотекам — пунктир (common coupling).
Куплеты на рёбрах: ↓○/↑○ — данные (вход/выход), ↑● — управляющий признак.
Ч/Б, Liberation Serif. Вывод: diagrams_1_5/constantine_18.png."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model_s18 as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def edge_label(dd, du, ctrl):
    parts = [f"↓○ {x}" for x in dd] + [f"↑○ {x}" for x in du] + [f"↑● {x}" for x in ctrl]
    return "\\n".join(parts)


def main():
    nodes = []
    for k, name, *_ in MM.MODULES18:
        peri = ", peripheries=2" if MM.is_library(k) else ""
        nodes.append(f'  {k} [label="{name}", shape=box, style=filled, fillcolor=white{peri}];')
    for k, label in MM.DATA_AREAS18:
        nodes.append(f'  {k} [label="{label}", shape=ellipse, style=filled, fillcolor=white];')

    edges = []
    for s, d, dd, du, ctrl, _cond in MM.EDGES18:
        lbl = edge_label(dd, du, ctrl)
        style = ", style=dashed" if MM.is_library(d) else ""
        edges.append(f'  {s} -> {d} [label="{lbl}", fontsize=9{style}];')
    for s, d, lbl in MM.DATA_EDGES18:
        edges.append(f'  {s} -> {d} [label="{lbl}", fontsize=9];')

    legend = '''  subgraph cluster_leg {
    label="Условные обозначения (по Л. Константайну)"; labelloc=b; fontsize=11;
    style=dashed; color=black;
    l1 [label="модуль", shape=box];
    l2 [label="библиотека", shape=box, peripheries=2];
    l3 [label="область\\nданных", shape=ellipse];
    l4 [label="↓○ ↑○  связь по данным (вход / выход)\\n↓● ↑●  связь по управлению (управляющий флаг)\\n- - -  common coupling (библиотека)", shape=plaintext];
    l1 -> l2 [style=invis]; l2 -> l3 [style=invis]; l3 -> l4 [style=invis];
  }'''

    dot = f'''digraph constantine18 {{
  bgcolor=white; rankdir=TB; nodesep=0.4; ranksep=0.9; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", color=black, fontcolor=black, arrowsize=0.8];
{chr(10).join(nodes)}
{chr(10).join(edges)}
{legend}
}}'''
    p = "/tmp/constantine_18.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/constantine_18.png"], check=True)
    print("OK constantine_18")


if __name__ == "__main__":
    main()
