# -*- coding: utf-8 -*-
"""Модульная структура (18 модулей, старая схема) для версии в стиле ВКР Сафиуллина.
Все модули — обычные прямоугольники; сплошные рёбра вызовов/использования.
Ч/Б, Liberation Serif. Вывод: diagrams_1_5/mod_struct_18.png."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model_s18 as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def main():
    nodes = [f'  {k} [label="{name}", shape=box, style=filled, fillcolor=white];'
             for k, name, *_ in MM.MODULES18]
    edges = [f'  {s} -> {d};' for s, d, *_ in MM.EDGES18]
    dot = f'''digraph modstruct18 {{
  bgcolor=white; rankdir=TB; nodesep=0.35; ranksep=0.6; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=12, color=black, fontcolor=black];
  edge [color=black, arrowsize=0.8];
{chr(10).join(nodes)}
{chr(10).join(edges)}
}}'''
    p = "/tmp/mod_struct_18.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/mod_struct_18.png"], check=True)
    print("OK mod_struct_18")


if __name__ == "__main__":
    main()
