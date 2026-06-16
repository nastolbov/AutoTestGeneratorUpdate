# -*- coding: utf-8 -*-
"""Структурная карта Константайна (Graphviz): модуль (прямоугольник),
библиотека (двойная рамка), область данных (овал). Вызовы — сплошные стрелки,
обращение к библиотекам — пунктир (common coupling). Куплеты подписаны на рёбрах
с направлением (↓ — передача в вызываемый модуль, ↑ — возврат) и видом связи
(○ — по данным, ● — по управлению). Ч/Б, Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def edge_label(dd, du, ctrl):
    # ↓○ — данные, передаваемые в вызываемый модуль; ↑○ — данные, возвращаемые
    # вызывающему; ↑● — управляющий признак, возвращаемый вызывающему модулю
    parts = [f"↓○ {x}" for x in dd] + [f"↑○ {x}" for x in du] + [f"↑● {x}" for x in ctrl]
    return "\\n".join(parts)


def main():
    nodes = []
    for k, name, pkg, kind, *_ in MM.MODULES:
        peri = ", peripheries=2" if kind == "library" else ""
        nodes.append(f'  {k} [label="{name}", shape=box, style=filled, fillcolor=white{peri}];')
    for k, label, _ in MM.DATA_AREAS:
        nodes.append(f'  {k} [label="{label}", shape=ellipse, style=filled, fillcolor=white];')

    edges = []
    for s, d, dd, du, ctrl, _cond in MM.EDGES:
        lbl = edge_label(dd, du, ctrl)
        # обращение к библиотеке — пунктир (common coupling)
        style = ", style=dashed" if MM.is_library(d) else ""
        edges.append(f'  {s} -> {d} [label="{lbl}", fontsize=9{style}];')
    for s, d, lbl in MM.DATA_EDGES:
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

    dot = f'''digraph constantine {{
  bgcolor=white; rankdir=TB; nodesep=0.4; ranksep=0.9; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", color=black, fontcolor=black, arrowsize=0.8];
{chr(10).join(nodes)}
{chr(10).join(edges)}
{legend}
}}'''
    p = "/tmp/constantine.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/constantine.png"], check=True)
    print("OK constantine")


if __name__ == "__main__":
    main()
