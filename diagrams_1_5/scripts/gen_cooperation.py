# -*- coding: utf-8 -*-
"""Диаграммы кооперации (Graphviz) по сценариям scenarios.py. Объекты-боксы (внешние
с «Пакет::Класс»), нумерованные русские сообщения всех сценариев (нормальный 1..,
прерывание пользователем п1.., системой с1..). Набор объектов = диаграммам
последовательности пакета. Управление из края — узел-граница (точка). Ч/Б."""
import subprocess, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenarios as S

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', '\\"')


def build(pkg):
    used = S.used_object_keys(pkg)
    # собрать сообщения с нумерацией по сценариям
    pair_msgs = {}   # (frm,to) -> [ "num: text", ... ]
    order = []
    for flow_key in ("normal", "user", "system"):
        flow = S.SC[pkg]["flows"].get(flow_key, [])
        pref = S.FLOW_NUM_PREFIX[flow_key]
        for i, (frm, to, text, kind) in enumerate(flow, 1):
            num = f"{pref}{i}"
            key = (frm, to)
            if key not in pair_msgs:
                pair_msgs[key] = []; order.append(key)
            arrow = "create " if kind == "create" else ""
            pair_msgs[key].append(f"{num}: {arrow}{text}")

    def nid(k):
        return "boundary" if k == S.EDGE else "o_" + k

    nodes = ['  boundary [shape=point, width=0.10, color=black, xlabel="граница\\nпакета"];']
    for k in used:
        lbl = esc(S.label_of(pkg, k))
        nodes.append(f'  {nid(k)} [shape=box, style=filled, fillcolor=white, label="{lbl}"];')

    edges = []
    for (frm, to) in order:
        msgs = pair_msgs[(frm, to)]
        lbl = "\\n".join(esc(m) for m in msgs)
        a, b = nid(frm), nid(to)
        if a == b:  # self
            edges.append(f'  {a} -> {a} [label="{lbl}"];')
        else:
            edges.append(f'  {a} -> {b} [label="{lbl}"];')

    dot = f'''digraph coop_{pkg} {{
  bgcolor=white; rankdir=LR; nodesep=0.6; ranksep=1.3; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=12, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
{chr(10).join(nodes)}
{chr(10).join(edges)}
}}'''
    return dot


def main():
    for pkg in S.SC:
        dot = build(pkg)
        p = f"/tmp/coop_{pkg}.dot"; open(p, "w", encoding="utf-8").write(dot)
        subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o", f"{OUT}/coop_{pkg}.png"], check=True)
        print("OK coop", pkg)


if __name__ == "__main__":
    main()
