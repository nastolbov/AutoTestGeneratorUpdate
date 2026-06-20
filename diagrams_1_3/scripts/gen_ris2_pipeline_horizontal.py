# -*- coding: utf-8 -*-
"""Горизонтальная (альбомная) версия Рис.2 «Общая схема процесса обработки
информации» (конвейер) для презентации. Содержание идентично графу
`ris_1_pipeline` из gen_graphviz.py (ГОСТ 19.701-90), но раскладка — «змейкой»
в два ряда (neato, фиксированные позиции): верхний ряд слева-направо, нижний —
справа-налево, поэтому схема становится альбомной (~2.4:1), а не в столбик.
Выход: diagrams_1_3/ris_2_pipeline_h.png."""
import subprocess
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "Liberation Serif"

# узел: (id, label, shape, x, y). Все блоки — одного размера (fixedsize).
# Правая колонка из 3 блоков: Параметры (верх) → Генерация (2-й из 3) → Тестовый проект (низ).
NODES = [
    # верхний ряд (анализ)
    ("in1", "XML-модель\\nметаданных E3Core", "parallelogram", 0, 5.0),
    ("p1",  "1. Разбор XML\\n(потоковый, StAX)", "box", 3.0, 5.0),
    ("d1",  "Модель\\nметаданных", "parallelogram", 6.0, 5.0),
    ("p2",  "2. Классификация\\nсущностей", "box", 9.0, 5.0),
    ("par", "Параметры запуска\\n(адрес, логин, каталог)", "parallelogram", 12.0, 5.0),
    # средний ряд — генерация (2-й из 3 блоков справа)
    ("p3",  "3. Генерация\\nтестового проекта", "box", 12.0, 2.5),
    # нижний ряд (выполнение)
    ("d2",  "Тестовый проект\\n(Page Object, тесты)", "parallelogram", 12.0, 0.0),
    ("p4",  "4. Запуск тестов\\n(Maven Surefire)", "box", 9.0, 0.0),
    ("d3",  "Отчёты Surefire\\n(XML)", "parallelogram", 6.0, 0.0),
    ("p5",  "5. Анализ\\nрезультатов", "box", 3.0, 0.0),
    ("out", "Сводка и отчёты\\n(HTML, CSV)", "parallelogram", 0, 0.0),
]
EDGES = [
    ("in1", "p1", ""), ("p1", "d1", ""), ("d1", "p2", ""),
    ("p2", "par", ""),  # классификация → параметры запуска (горизонтально)
    ("par", "p3", ""),  # через параметры запуска — в генерацию (вниз)
    ("p3", "d2", ""),   # генерация → тестовый проект
    ("d2", "p4", ""), ("p4", "d3", ""), ("d3", "p5", ""), ("p5", "out", ""),
]

ns = []
for nid, label, shape, x, y in NODES:
    ns.append(f'{nid} [label="{label}", shape={shape}, style=filled, fillcolor=white, '
              f'fixedsize=true, width=2.8, height=1.0, pos="{x},{y}!"];')
es = []
for s, d, kind in EDGES:
    es.append(f'{s} -> {d} [style=dashed];' if kind == "dashed" else f'{s} -> {d};')

dot = f'''digraph Pipeline {{
  layout=neato; bgcolor=white; fontname="{FONT}"; splines=true; overlap=false; sep="+6";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black, penwidth=1.2];
  edge [fontname="{FONT}", fontsize=11, color=black, fontcolor=black, arrowhead=normal, penwidth=1.1];
{chr(10).join(ns)}
{chr(10).join(es)}
}}'''
path = "/tmp/ris_2_pipeline_h.dot"
open(path, "w").write(dot)
subprocess.run(["neato", "-Tpng", "-Gdpi=200", path, "-o", f"{OUT}/ris_2_pipeline_h.png"], check=True)
w, h = Image.open(f"{OUT}/ris_2_pipeline_h.png").size
print(f"ris_2_pipeline_h.png: {w}x{h}  ratio {w/h:.2f}")
