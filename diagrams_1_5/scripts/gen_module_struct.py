# -*- coding: utf-8 -*-
"""Модульная структура (Graphviz): дерево вызовов/использования функциональных модулей.
Ч/Б, Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def main():
    nodes = []
    for k, name, pkg, kind, _ in MM.MODULES:
        peri = ", peripheries=2" if kind == "library" else ""
        nodes.append(f'  {k} [label="{name}\\n({pkg})", shape=box, style=filled, fillcolor=white{peri}];')
    edges = [f'  {s} -> {d};' for s, d, *_ in MM.EDGES]
    dot = f'''digraph modstruct {{
  bgcolor=white; rankdir=TB; nodesep=0.35; ranksep=0.6; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=12, color=black, fontcolor=black];
  edge [color=black, arrowsize=0.8];
{chr(10).join(nodes)}
{chr(10).join(edges)}
}}'''
    p = "/tmp/mod_struct.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/mod_struct.png"], check=True)
    print("OK mod_struct")


if __name__ == "__main__":
    main()
