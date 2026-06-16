# -*- coding: utf-8 -*-
"""Модульная структура программы по файлам: программное средство → пакеты → модули (.java),
внутри каждого модуля перечислены его константы (все final-поля и enum-константы).
Ч/Б, Liberation Serif. Высокие пакеты разбиваются на несколько колонок, чтобы рисунок
имел пропорции страницы."""
import subprocess, os, sys, html
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import const_model as CM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"
COLH = 8                      # макс. модулей в одной колонке пакета
PKG_RU = {"ui": "ui — интерфейс", "parser": "parser — разбор XML",
          "model": "model — модель данных", "generator": "generator — генерация тестов",
          "data": "data — хранилище отчётов", "common": "common — утилиты"}


def nid(fn):
    return "m_" + fn.replace(".", "_").replace("-", "_")


def chunks(lst, k):
    return [lst[i:i + k] for i in range(0, len(lst), k)]


def box_label(fn, cls, kind, consts):
    head = html.escape(fn)
    if consts:
        rows = []
        for c in consts:
            nm = html.escape(c["name"])
            rows.append(nm if c["static"] else f'<I>{nm}</I>')
        body = "<BR/>".join(rows)
    else:
        body = '<FONT COLOR="#666666"><I>нет констант</I></FONT>'
    return (f'<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="3">'
            f'<TR><TD BGCOLOR="#E0E0E0"><B>{head}</B></TD></TR>'
            f'<TR><TD ALIGN="LEFT" BALIGN="LEFT">{body}</TD></TR></TABLE>>')


def main():
    by_pkg = {}
    for pkg, fn, cls, kind, consts in CM.MODULES:
        by_pkg.setdefault(pkg, []).append((fn, cls, kind, consts))

    lines, edges = [], []
    lines.append('  ROOT [label="Программное средство\\nAutoTestGenerator", '
                 'shape=box, style="filled,bold", fillcolor="#CCCCCC", fontsize=14];')
    for pkg in CM.PKG_ORDER:
        mods = by_pkg.get(pkg, [])
        if not mods:
            continue
        cols = chunks(mods, COLH)
        lines.append(f'  subgraph cluster_{pkg} {{')
        lines.append(f'    label="{PKG_RU.get(pkg, pkg)}"; style=dashed; color=black; '
                     f'fontsize=13; labeljust=l;')
        for col in cols:
            for fn, cls, kind, consts in col:
                lines.append(f'    {nid(fn)} [label={box_label(fn, cls, kind, consts)}, '
                             f'shape=plaintext];')
            # вертикальная цепочка внутри колонки (невидимые рёбра)
            for a, b in zip(col, col[1:]):
                edges.append(f'  {nid(a[0])} -> {nid(b[0])} [style=invis];')
            # связь от корня к верхушке каждой колонки
            edges.append(f'  ROOT -> {nid(col[0][0])};')
        lines.append('  }')

    dot = f'''digraph modconst {{
  bgcolor=white; rankdir=TB; nodesep=0.30; ranksep=0.7; compound=true; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [color=black, arrowsize=0.7];
{chr(10).join(lines)}
{chr(10).join(edges)}
}}'''
    p = "/tmp/module_const_struct.dot"
    open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o",
                    f"{OUT}/module_const_struct.png"], check=True)
    print("OK module_const_struct.png")


if __name__ == "__main__":
    main()
