# -*- coding: utf-8 -*-
"""Диаграмма переходов состояний (State Diagram) для раздела 1.4.6 ВКР.
Построена по фактическому коду программы (ru.autotestgen.ui.MainController):
строгий порядок команд «Разобрать → Сгенерировать → Запустить» (кнопки
включаются последовательно), автосохранение прогона в БД и формирование
отчётов, просмотр истории, обработка ошибок. Ч/б, состояния — прямоугольники
со скруглёнными углами, переходы подписаны событием/действием."""
import subprocess, os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "DejaVu Sans"

dot = f'''
digraph StateDiagram {{
  rankdir=TB; bgcolor=white; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=9, color=black, fontcolor=black, arrowhead=vee];
  nodesep=0.5; ranksep=0.55;

  init [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin  [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor=white];
  S1 [label="Ожидание ввода"];
  S2 [label="Метаданные разобраны"];
  S3 [label="Тесты сгенерированы"];
  S4 [label="Выполнение тестов"];
  S5 [label="Результаты получены"];
  S6 [label="Просмотр истории"];

  init -> S1;
  S1 -> S1 [label="ошибка разбора / сообщение"];
  S1 -> S2 [label="разобрать метаданные\\n[файл корректен]"];
  S2 -> S3 [label="сгенерировать автотесты\\n[каталог доступен]"];
  S3 -> S4 [label="запустить автотесты /\\nзапустить выбранные"];
  S4 -> S5 [label="прогон завершён /\\nсохранить в БД,\\nотчёты HTML, CSV"];
  S4 -> S3 [label="ошибка запуска /\\nсообщение"];
  S5 -> S4 [label="запустить снова"];
  S5 -> S6 [label="просмотреть\\nисторию"];
  S6 -> S5 [label="назад"];
  S5 -> fin [label="выход"];

  // история прогонов доступна и до запуска
  S3 -> S6 [label="просмотреть историю", style=dashed, constraint=false];
  {{ rank=same; S5; S6; }}
}}
'''
path = "/tmp/ris_12_state.dot"
open(path, "w").write(dot)
subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/ris_12_state_transition.png"], check=True)
print("OK ris_12_state_transition.png")
