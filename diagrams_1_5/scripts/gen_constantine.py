# -*- coding: utf-8 -*-
"""Структурная карта Константайна (Graphviz) по методичке 5.3:
модуль (прямоугольник), библиотека (двойная рамка), область данных (овал);
связи по данным (○) и управлению (●) подписаны на рёбрах; особые условия —
↺ (циклический, «для каждой сущности»), ◇ (условный), 1 (однократный). Ч/Б."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"
COND = {"once": "1: ", "cyclic": "↺ для каждой сущности\\n", "cond": "◇ по виду\\n"}


def edge_label(dd, du, ctrl, cond):
    parts = []
    if cond in COND:
        parts.append(COND[cond])
    for x in dd + du:
        parts.append(f"○ {x}")
    for x in ctrl:
        parts.append(f"● {x}")
    return "\\n".join(parts)


def main():
    nodes = []
    for k, name, pkg, kind, _ in MM.MODULES:
        peri = ", peripheries=2" if kind == "library" else ""
        nodes.append(f'  {k} [label="{name}", shape=box, style=filled, fillcolor=white{peri}];')
    for k, label, _ in MM.DATA_AREAS:
        nodes.append(f'  {k} [label="{label}", shape=ellipse, style=filled, fillcolor=white];')

    edges = []
    for s, d, dd, du, ctrl, cond in MM.EDGES:
        lbl = edge_label(dd, du, ctrl, cond)
        edges.append(f'  {s} -> {d} [label="{lbl}", fontsize=9];')
    for s, d, lbl in MM.DATA_EDGES:
        edges.append(f'  {s} -> {d} [label="{lbl}", style=dashed, fontsize=9];')

    legend = '''  subgraph cluster_leg {
    label="Условные обозначения"; fontsize=11; style=rounded; color=black;
    l1 [label="модуль", shape=box]; l2 [label="библиотека", shape=box, peripheries=2];
    l3 [label="область\\nданных", shape=ellipse];
    l4 [label="○ связь по данным   ● связь по управлению\\n↺ циклический   ◇ условный   1 однократный", shape=plaintext];
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
