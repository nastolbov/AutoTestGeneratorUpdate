# -*- coding: utf-8 -*-
"""Диаграмма кооперации пакета «ui» (Рис. 30) — компактная 2D-раскладка вместо
длинной горизонтальной полосы. Объекты размещены в плоскости, на связях — номера
сообщений, полный перечень сообщений — в легенде. Построено по коду пакета ui
(App, Launcher, MainController, MainController.EntityNode, MainController.TestCaseRow)."""
import subprocess, os
OUT = os.path.dirname(os.path.abspath(__file__))
FONT = "Liberation Serif"

legend = (
 "Сообщения:\\l"
 "1: main(args)\\l"
 "2: launch(App.class)\\l"
 "3: «create» загрузка main.fxml\\l"
 "4: контроллер готов\\l"
 "5: окно показано\\l"
 "6: приложение запущено\\l"
 "7: onParse()\\l"
 "8: разобрать метаданные: подсистема Parser\\l"
 "9: модель метаданных\\l"
 "10: «create» создать узлы дерева\\l"
 "11: узлы готовы\\l"
 "12: дерево сущностей показано\\l"
 "13: onRunTests()\\l"
 "14: генерация и прогон: подсистемы Generator, Data\\l"
 "15: результат прогона\\l"
 "16: «create» создать строки результатов\\l"
 "17: строки готовы\\l"
 "18: таблица результатов показана\\l"
 "—— альтернативы ——\\l"
 "п1: onSelectXml(); п2: открыть диалог выбора файла — отменён;\\l"
 "п3: файл не выбран — разбор не выполняется\\l"
 "с1: onParse(); с2: разобрать метаданные: подсистема Parser;\\l"
 "с3: ошибка разбора (исключение);\\l"
 "с4: showAlert(сообщение об ошибке); с5: сообщение об ошибке показано\\l"
)

dot = f'''
digraph Coop {{
  layout=neato; bgcolor=white; fontname="{FONT}";
  node [fontname="{FONT}", fontsize=14, color=black, fontcolor=black, penwidth=1.3];
  edge [fontname="{FONT}", fontsize=12, color=black, fontcolor=black, penwidth=1.1];

  gr [label="граница\\nпакета", shape=box, style="rounded", pos="0,1.3!"];
  L  [label="Launcher", shape=box, pos="2.0,3.1!"];
  A  [label="App", shape=box, pos="4.3,3.1!"];
  MC [label=":MainController", shape=box, pos="4.3,1.3!"];
  EN [label=":EntityNode", shape=box, pos="7.0,2.3!"];
  TC [label=":TestCaseRow", shape=box, pos="7.0,0.5!"];

  gr -> L  [label="1: main(args)"];
  L  -> A  [label="2: launch()"];
  A  -> MC [label="3: «create»\\n4: готов"];
  A  -> L  [label="5: окно показано", style=dashed];
  L  -> gr [label="6: приложение\\nзапущено", style=dashed];
  gr -> MC [label="7, 8, 9, 12, 13, 14, 15, 18\\n(+ альт. п1–п3, с1–с5)"];
  MC -> EN [label="10: создать узлы\\n11: узлы готовы"];
  MC -> TC [label="16: создать строки\\n17: строки готовы"];

  leg [shape=note, fontsize=12, pos="2.6,-1.6!", label="{legend}"];
}}
'''
open("/tmp/ris30.dot","w").write(dot)
subprocess.run(["neato","-Tpng","-Gdpi=200","/tmp/ris30.dot","-o",f"{OUT}/ris_30_coop_ui.png"],check=True)
from PIL import Image
w,h=Image.open(f"{OUT}/ris_30_coop_ui.png").size
print("OK ris_30_coop_ui.png", f"{w}x{h} ratio {w/h:.2f}")
