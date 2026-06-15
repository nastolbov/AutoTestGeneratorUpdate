# -*- coding: utf-8 -*-
"""Диаграмма пакетов (1.5.1): 6 пакетов как UML-пакеты, сгруппированы по слоям,
зависимости — пунктирные стрелки направления вызовов. Ч/Б, Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apimodel as M

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"

LAYERS = [
    ("PresentationLayer", ["ui"]),
    ("BusinessLayer", ["parser", "generator", "model"]),
    ("DataLayer", ["data"]),
    ("Global (CrossLayer)", ["common"]),
]


def plural_class(n):
    if 11 <= n % 100 <= 14:
        return "классов"
    d = n % 10
    return "класс" if d == 1 else ("класса" if 2 <= d <= 4 else "классов")


def main():
    api = M.load_api()
    clusters = []
    for i, (layer, pkgs) in enumerate(LAYERS):
        nodes = []
        for p in pkgs:
            n = len(api[p])
            glob = "\\nGlobal" if p == "common" else ""
            nodes.append(f'    {p} [label="{p}\\n({n} {plural_class(n)}){glob}"];')
        clusters.append(f'''  subgraph cluster_{i} {{
    label="{layer}"; labelloc=t; fontsize=13; style="rounded"; color=black; penwidth=1.2; margin=14;
{chr(10).join(nodes)}
  }}''')
    edges = "\n".join(f'  {a} -> {b};' for a, b in M.PKG_DEPS)
    dot = f'''digraph packages {{
  bgcolor=white; rankdir=TB; nodesep=0.6; ranksep=0.9; fontname="{FONT}";
  node [shape=tab, style=filled, fillcolor=white, color=black, fontcolor=black,
        fontname="{FONT}", fontsize=12, width=1.7, height=0.9];
  edge [style=dashed, arrowhead=vee, color=black];
{chr(10).join(clusters)}
{edges}
}}'''
    p = "/tmp/pkg_diagram.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/pkg_diagram.png"], check=True)
    print("OK pkg_diagram")


if __name__ == "__main__":
    main()
