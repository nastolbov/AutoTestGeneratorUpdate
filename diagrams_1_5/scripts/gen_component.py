# -*- coding: utf-8 -*-
"""Диаграмма компонентов (Graphviz) по методичке 7.5: компонент (component),
файл/папка (note), БД (cylinder); зависимости — пунктир, прочие связи — сплошные
с подписью. Ч/Б, Liberation Serif."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"
SHAPE = {"app": "component", "lib": "component", "ext": "component",
         "file": "note", "folder": "folder", "db": "cylinder"}
EDGE_STYLE = {
    "dep": 'style=dashed, arrowhead=vee, label="«use»"',
    "input": 'style=solid, arrowhead=vee, label="вход"',
    "create": 'style=solid, arrowhead=vee, label="создаёт"',
    "run": 'style=solid, arrowhead=vee, label="mvn test"',
    "read": 'style=solid, arrowhead=vee, label="читает"',
}


def main():
    nodes = []
    for k, name, typ, ver, _ in MM.COMPONENTS:
        lbl = name + (f"\\n{{{ver}}}" if ver and ver != "—" else "")
        nodes.append(f'  {k} [label="{lbl}", shape={SHAPE[typ]}, style=filled, fillcolor=white];')
    edges = [f'  {s} -> {d} [{EDGE_STYLE[kind]}, fontsize=9];' for s, d, kind in MM.COMP_EDGES]
    dot = f'''digraph components {{
  bgcolor=white; rankdir=LR; nodesep=0.5; ranksep=1.0; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", color=black, fontcolor=black, arrowsize=0.8];
{chr(10).join(nodes)}
{chr(10).join(edges)}
}}'''
    p = "/tmp/components.dot"; open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/components.png"], check=True)
    print("OK components")


if __name__ == "__main__":
    main()
