# -*- coding: utf-8 -*-
"""Модульная структура программы по файлам (простые блоки): программное средство → пакеты
→ модули (.java). Каждый модуль — отдельный прямоугольник с именем файла (без полей и
методов). Ч/Б, Liberation Serif. Высокие пакеты разбиваются на колонки, чтобы рисунок
имел пропорции страницы."""
import subprocess, os, sys, html
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import const_model as CM

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"
COLH = 9                      # макс. модулей в одной колонке пакета
PKG_RU = {"ui": "ui — интерфейс", "parser": "parser — разбор XML",
          "model": "model — модель данных", "generator": "generator — генерация тестов",
          "data": "data — хранилище отчётов", "common": "common — утилиты"}


def nid(fn):
    return "m_" + fn.replace(".", "_").replace("-", "_")


def chunks(lst, k):
    return [lst[i:i + k] for i in range(0, len(lst), k)]


def main():
    by_pkg = {}
    for pkg, fn, cls, kind, consts in CM.MODULES:
        by_pkg.setdefault(pkg, []).append(fn)

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
            for fn in col:
                lines.append(f'    {nid(fn)} [label="{html.escape(fn)}", shape=box, '
                             f'style=filled, fillcolor=white];')
            for a, b in zip(col, col[1:]):              # вертикальная цепочка в колонке
                edges.append(f'  {nid(a)} -> {nid(b)} [style=invis];')
            edges.append(f'  ROOT -> {nid(col[0])};')    # корень → верхушка колонки
        lines.append('  }')

    dot = f'''digraph modfiles {{
  bgcolor=white; rankdir=TB; nodesep=0.28; ranksep=0.7; compound=true; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [color=black, arrowsize=0.7];
{chr(10).join(lines)}
{chr(10).join(edges)}
}}'''
    p = "/tmp/module_files_struct.dot"
    open(p, "w", encoding="utf-8").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", p, "-o",
                    f"{OUT}/module_files_struct.png"], check=True)
    print("OK module_files_struct.png")


if __name__ == "__main__":
    main()
