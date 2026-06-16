# -*- coding: utf-8 -*-
"""Рис. 30 — диаграмма кооперации пакета «ui». Все сообщения изображены на самой
диаграмме (текстовыми блоками рядом со связями), 2D-раскладка, не вытянутая."""
import subprocess, os
from PIL import Image
OUT = os.path.dirname(os.path.abspath(__file__))
F = "Liberation Serif"

# блоки сообщений у связи граница↔:MainController (как в оригинале: сверху и снизу от линии)
b1 = ("7: onParse()\\l9: модель метаданных\\l13: onRunTests()\\l15: результат прогона\\l"
      "п1: onSelectXml()\\lп2: открыть диалог выбора файла — отменён\\l"
      "с1: onParse()\\lс3: ошибка разбора (исключение)\\l")
b2 = ("8: разобрать метаданные: подсистема Parser\\l12: дерево сущностей показано\\l"
      "14: генерация и прогон: подсистемы Generator, Data\\l18: таблица результатов показана\\l"
      "п3: файл не выбран — разбор не выполняется\\lс2: разобрать метаданные: подсистема Parser\\l"
      "с4: showAlert(сообщение об ошибке)\\lс5: сообщение об ошибке показано\\l")

dot = f'''
digraph Coop {{
  layout=neato; bgcolor=white; fontname="{F}"; splines=true; overlap=false;
  node [fontname="{F}", fontsize=15, color=black, fontcolor=black, penwidth=1.4];
  edge [fontname="{F}", fontsize=13, color=black, fontcolor=black, penwidth=1.1];

  gr [label="граница\\nпакета", shape=box, style="rounded", pos="0,3.0!"];
  L  [label="Launcher", shape=box, pos="3.2,6.6!"];
  A  [label="App",      shape=box, pos="6.4,6.6!"];
  MC [label=":MainController", shape=box, pos="7.6,3.0!"];
  EN [label=":EntityNode",  shape=box, pos="11.4,4.2!"];
  TC [label=":TestCaseRow", shape=box, pos="11.4,1.6!"];

  // простые связи — подпись прямо на стрелке
  gr -> L  [label="1: main(args)"];
  L  -> A  [label="2: launch(App.class)"];
  A  -> MC [label="3: «create» загрузка main.fxml\\l4: контроллер готов\\l"];
  A  -> L  [label="5: окно показано", style=dashed];
  L  -> gr [label="6: приложение запущено", style=dashed];
  gr -> MC [label=" "];
  MC -> EN [label="10: «create» создать узлы дерева\\l11: узлы готовы\\l"];
  MC -> TC [label="16: «create» создать строки результатов\\l17: строки готовы\\l"];

  // блоки сообщений у связи граница↔:MainController (сверху и снизу линии)
  m1 [shape=plaintext, fontsize=13, pos="3.6,4.35!", label="{b1}"];
  m2 [shape=plaintext, fontsize=13, pos="3.6,1.55!", label="{b2}"];
}}
'''
open("/tmp/ris30.dot","w").write(dot)
subprocess.run(["neato","-Tpng","-Gdpi=200","/tmp/ris30.dot","-o",f"{OUT}/ris_30_coop_ui.png"],check=True)
w,h=Image.open(f"{OUT}/ris_30_coop_ui.png").size
print("OK", f"{w}x{h} ratio {w/h:.2f}")
