# -*- coding: utf-8 -*-
"""Экспорт диаграммы деятельности «Сгенерировать автотесты» в формат draw.io (.drawio).
Логика та же, что в ris_10_activity_generate.png; фигуры редактируемые."""
import os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ris_10_activity_generate.drawio")

ACT   = "rounded=1;whiteSpace=wrap;html=1;arcSize=14;fillColor=#FFFFFF;strokeColor=#000000;fontColor=#000000;fontSize=12;"
DEC   = "rhombus;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;fontColor=#000000;fontSize=12;"
MERGE = "rhombus;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;"
START = "ellipse;whiteSpace=wrap;html=1;fillColor=#000000;strokeColor=#000000;"
RING  = "ellipse;whiteSpace=wrap;html=1;fillColor=none;strokeColor=#000000;strokeWidth=2;"
DOT   = "ellipse;whiteSpace=wrap;html=1;fillColor=#000000;strokeColor=#000000;"
EDGE  = ("edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;"
         "endArrow=block;endFill=1;strokeColor=#000000;fontColor=#000000;fontSize=11;")

# id: (label, style, x, y, w, h)
nodes = {
    "start":  ("",                                          START, 470, 30,  30, 30),
    "check":  ("Проверить наличие модели\nи каталога генерации", ACT, 380, 100, 210, 50),
    "dparam": ("Параметры\nкорректны?",                     DEC,   420, 200, 130, 80),
    "struct": ("Создать структуру\nMaven-проекта",          ACT,   380, 330, 210, 50),
    "err":    ("Вывести сообщение\nоб ошибке",              ACT,   680, 210, 180, 50),
    "infra":  ("Сгенерировать инфраструктуру\n(драйвер, базовый тест, данные)", ACT, 375, 415, 220, 50),
    "clazz":  ("Классифицировать\nочередную сущность",      ACT,   380, 500, 210, 50),
    "dkind":  ("Тип\nсущности?",                            DEC,   420, 590, 130, 80),
    "prim":   ("Создать Page Object\nи тестовый класс",     ACT,   60,  710, 190, 50),
    "child":  ("Создать тест\nв составе родителя",          ACT,   280, 710, 190, 50),
    "merge":  ("",                                          MERGE, 470, 720, 30, 30),
    "dmore":  ("Остались\nсущности?",                       DEC,   420, 820, 130, 80),
    "report": ("Сохранить отчёт\nо классификации",          ACT,   380, 950, 210, 50),
    "summ":   ("Сформировать сводку\nо генерации",          ACT,   380, 1035, 210, 50),
    "finring":("",                                          RING,  468, 1130, 34, 34),
    "findot": ("",                                          DOT,   475, 1137, 20, 20),
}
# (source, target, label)
edges = [
    ("start","check",""),
    ("check","dparam",""),
    ("dparam","struct","да"),
    ("dparam","err","нет"),
    ("err","finring",""),
    ("struct","infra",""),
    ("infra","clazz",""),
    ("clazz","dkind",""),
    ("dkind","prim","основная"),
    ("dkind","child","дочерняя"),
    ("dkind","merge","справочник"),
    ("prim","merge",""),
    ("child","merge",""),
    ("merge","dmore",""),
    ("dmore","report","нет"),
    ("dmore","clazz","да (*для каждой сущности)"),
    ("report","summ",""),
    ("summ","finring",""),
]

def esc(s):
    s = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace('"',"&quot;")
    return s.replace("\n","&#10;")

cells = []
for nid,(label,style,x,y,w,h) in nodes.items():
    cells.append(
        f'<mxCell id="{nid}" value="{esc(label)}" style="{style}" vertex="1" parent="1">'
        f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')
for i,(s,t,label) in enumerate(edges):
    cells.append(
        f'<mxCell id="e{i}" value="{esc(label)}" style="{EDGE}" edge="1" parent="1" source="{s}" target="{t}">'
        f'<mxGeometry relative="1" as="geometry"/></mxCell>')

xml = (
'<mxfile host="app.diagrams.net">\n'
'  <diagram name="Сгенерировать автотесты" id="activity-generate">\n'
'    <mxGraphModel dx="900" dy="700" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
'arrows="1" fold="1" page="1" pageScale="1" pageWidth="900" pageHeight="1250" math="0" shadow="0">\n'
'      <root>\n'
'        <mxCell id="0" />\n'
'        <mxCell id="1" parent="0" />\n'
+ "".join("        "+c+"\n" for c in cells) +
'      </root>\n'
'    </mxGraphModel>\n'
'  </diagram>\n'
'</mxfile>\n')

open(OUT, "w", encoding="utf-8").write(xml)
print("Сохранено:", OUT, "(", len(xml), "байт )")
