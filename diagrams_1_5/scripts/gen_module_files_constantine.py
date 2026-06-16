# -*- coding: utf-8 -*-
"""Структурная карта Константайна по всем файлам программы (модуль = файл .java).
Узлы: модуль — прямоугольник; библиотека — двойная рамка; класс данных (POJO) — овал;
перечисление — пунктирный овал; исключение — пунктирный прямоугольник. Все обращения —
сплошные стрелки (сцепление по данным/управлению). Куплеты подписаны на рёбрах с
направлением (↓ — передача в вызываемый узел, ↑ — возврат) и видом (○ — по данным,
● — по управлению). Узлы сгруппированы по пакетам. Ч/Б, Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_files_model as MF

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def node_attr(kind):
    return {
        "module":    'shape=box',
        "library":   'shape=box, peripheries=2',
        "pojo":      'shape=ellipse',
        "enum":      'shape=ellipse, style="filled,dashed"',
        "exception": 'shape=box, style="filled,dashed"',
    }[kind]


def edge_label(dd, du, ctrl):
    parts = [f"↓○ {x}" for x in dd] + [f"↑○ {x}" for x in du] + [f"↑● {x}" for x in ctrl]
    return "\\n".join(parts)


def main():
    # узлы по пакетам (кластеры)
    clusters = []
    for pkg in MF.PKG_ORDER:
        ns = []
        for k, label, p, kind in MF.NODES:
            if p != pkg:
                continue
            ns.append(f'    {k} [label="{label}", style=filled, fillcolor=white, {node_attr(kind)}];')
        if not ns:
            continue
        clusters.append(
            f'  subgraph cluster_{pkg} {{\n'
            f'    label="{MF.PKG_RU.get(pkg, pkg)}"; style=dashed; color="#888888"; fontsize=12; labeljust=l;\n'
            + "\n".join(ns) + "\n  }")

    edges = []
    for s, d, dd, du, ctrl, _cond in MF.EDGES:
        lbl = edge_label(dd, du, ctrl)
        edges.append(f'  {s} -> {d} [label="{lbl}", fontsize=8];')

    legend = '''  subgraph cluster_leg {
    label="Условные обозначения (по Л. Константайну)"; labelloc=b; fontsize=11;
    style=dashed; color=black;
    l1 [label="модуль", shape=box];
    l2 [label="библиотека", shape=box, peripheries=2];
    l3 [label="класс\\nданных", shape=ellipse];
    l4 [label="перечис-\\nление", shape=ellipse, style=dashed];
    l5 [label="↓○ ↑○  связь по данным (вход / выход)\\n↑●  связь по управлению (признак)", shape=plaintext];
    l1 -> l2 [style=invis]; l2 -> l3 [style=invis]; l3 -> l4 [style=invis]; l4 -> l5 [style=invis];
  }'''

    dot = f'''digraph constantine_files {{
  bgcolor=white; rankdir=LR; nodesep=0.30; ranksep=0.95; compound=true; concentrate=true; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=10, color=black, fontcolor=black];
  edge [fontname="{FONT}", color=black, fontcolor=black, arrowsize=0.7];
{chr(10).join(clusters)}
{chr(10).join(edges)}
{legend}
}}'''
    p = "/tmp/module_files_constantine.dot"
    open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o",
                    f"{OUT}/module_files_constantine.png"], check=True)
    print("OK module_files_constantine.png")


if __name__ == "__main__":
    main()
