# -*- coding: utf-8 -*-
"""Диаграмма переходов состояний (раздел 1.4.6) — построена по коду
ru.autotestgen.ui.MainController. Один источник модели даёт два артефакта:
  • ris_12_state_transition.png  — чёткая ручная отрисовка (PIL), прямой поток
    вниз, возвраты и история — отдельными линиями со стрелками;
  • ris_12_state_transition.drawio — тот же граф в формате draw.io (ортогональные
    связи), редактируемый.
Соответствует определению из методички (р. 4.2): конечный автомат — состояния и
переходы по управляющим воздействиям (командам/событиям)."""
import os, math, html
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# ---------- Модель: состояния (id -> (x,y,w,h,label)) ----------
BW, BH = 300, 64
ST = {
    "S1": (300, 110, BW, BH, "Ожидание ввода"),
    "S2": (300, 255, BW, BH, "Метаданные разобраны"),
    "S3": (300, 415, BW, BH, "Тесты сгенерированы"),
    "S4": (300, 585, BW, BH, "Выполнение тестов"),
    "S5": (300, 760, BW, BH, "Результаты получены"),
    "S6": (880, 760, BW, BH, "Просмотр истории"),
}
INIT = (450, 52, 16)         # cx, cy, r
FIN = (450, 950, 16)         # cx, cy, r (двойной кружок)

def cx(s): return ST[s][0] + ST[s][2] / 2
def right(s): return ST[s][0] + ST[s][2]
def left(s): return ST[s][0]
def top(s): return ST[s][1]
def bot(s): return ST[s][1] + ST[s][3]
def cy(s): return ST[s][1] + ST[s][3] / 2

# ---------- Рёбра: (points, label, label_xy, align) ----------
# align: 'r' = правый край у label_xy, 'l' = левый край, 'c' = центр
EDGES = [
    ([(INIT[0], INIT[1] + INIT[2]), (cx("S1"), top("S1"))], None, None, None),
    ([(cx("S1"), bot("S1")), (cx("S2"), top("S2"))],
     "разобрать метаданные\n[файл корректен]", (cx("S1") - 18, 200), 'r'),
    ([(cx("S2"), bot("S2")), (cx("S3"), top("S3"))],
     "сгенерировать автотесты\n[каталог доступен]", (cx("S2") - 18, 358), 'r'),
    ([(cx("S3"), bot("S3")), (cx("S4"), top("S4"))],
     "запустить автотесты /\nзапустить выбранные", (cx("S3") - 18, 522), 'r'),
    ([(cx("S4"), bot("S4")), (cx("S5"), top("S5"))],
     "прогон завершён /\nсохранить в БД,\nотчёты HTML, CSV", (cx("S4") - 18, 690), 'r'),
    ([(cx("S5"), bot("S5")), (FIN[0], FIN[1] - FIN[2])],
     "выход", (cx("S5") + 16, 875), 'l'),
    # возвраты справа
    ([(right("S4"), cy("S4") - 14), (705, cy("S4") - 14), (705, cy("S3")), (right("S3"), cy("S3"))],
     "ошибка запуска /\nсообщение", (715, 520), 'l'),
    ([(right("S5"), 770), (810, 770), (810, cy("S4") + 14), (right("S4"), cy("S4") + 14)],
     "запустить снова", (820, 695), 'l'),
    # самопереход по ошибке разбора (справа от S1)
    ([(right("S1"), cy("S1") - 12), (660, cy("S1") - 12), (660, cy("S1") + 12), (right("S1"), cy("S1") + 12)],
     "ошибка разбора /\nсообщение", (670, cy("S1") - 10), 'l'),
    # история (S5 <-> S6): две раздельные линии со стрелками
    ([(right("S5"), 804), (left("S6"), 804)],
     "просмотреть историю", ((right("S5") + left("S6")) / 2, 786), 'c'),
    ([(left("S6"), 814), (right("S5"), 814)],
     "назад", ((right("S5") + left("S6")) / 2, 828), 'c'),
]

# =================== Отрисовка PNG (PIL) ===================
W, H = 1180, 1040
img = Image.new("RGB", (W, H), "white")
d = ImageDraw.Draw(img)
fnode = ImageFont.truetype(TTF, 17)
flbl = ImageFont.truetype(TTF, 14)
BLACK = (0, 0, 0)

def text_block(xy, text, font, align):
    lines = text.split("\n")
    lh = font.getmetrics()[0] + font.getmetrics()[1] + 3
    x, y = xy
    y -= lh * len(lines) / 2
    for ln in lines:
        w = d.textlength(ln, font=font)
        tx = x - w if align == 'r' else (x - w / 2 if align == 'c' else x)
        d.text((tx, y), ln, font=font, fill=BLACK)
        y += lh

def arrow(points):
    d.line(points, fill=BLACK, width=2, joint="curve")
    (x0, y0), (x1, y1) = points[-2], points[-1]
    ang = math.atan2(y1 - y0, x1 - x0)
    L, a = 13, math.radians(26)
    for s in (+1, -1):
        d.line([(x1, y1), (x1 - L * math.cos(ang + s * a), y1 - L * math.sin(ang + s * a))],
               fill=BLACK, width=2)

# рёбра
for pts, lbl, lxy, align in EDGES:
    arrow(pts)
    if lbl:
        text_block(lxy, lbl, flbl, align)

# состояния
for sid, (x, y, w, h, label) in ST.items():
    d.rounded_rectangle([x, y, x + w, y + h], radius=16, outline=BLACK, width=2, fill="white")
    tw = d.textlength(label, font=fnode)
    d.text((x + (w - tw) / 2, y + h / 2 - 11), label, font=fnode, fill=BLACK)

# начальное / конечное
d.ellipse([INIT[0] - INIT[2], INIT[1] - INIT[2], INIT[0] + INIT[2], INIT[1] + INIT[2]], fill=BLACK)
d.ellipse([FIN[0] - FIN[2], FIN[1] - FIN[2], FIN[0] + FIN[2], FIN[1] + FIN[2]], outline=BLACK, width=2)
d.ellipse([FIN[0] - 8, FIN[1] - 8, FIN[0] + 8, FIN[1] + 8], fill=BLACK)

img.save(f"{OUT}/ris_12_state_transition.png")
print("OK ris_12_state_transition.png", img.size)

# =================== Экспорт в .drawio ===================
def esc(s): return html.escape(s).replace("\n", "&#10;")

cells = []
cells.append('<mxCell id="init" value="" style="ellipse;fillColor=#000000;strokeColor=#000000;" '
             f'vertex="1" parent="1"><mxGeometry x="{INIT[0]-12}" y="{INIT[1]-12}" width="24" height="24" as="geometry"/></mxCell>')
cells.append('<mxCell id="fin" value="" style="ellipse;shape=endState;fillColor=#000000;strokeColor=#000000;" '
             f'vertex="1" parent="1"><mxGeometry x="{FIN[0]-14}" y="{FIN[1]-14}" width="28" height="28" as="geometry"/></mxCell>')
for sid, (x, y, w, h, label) in ST.items():
    cells.append(f'<mxCell id="{sid}" value="{esc(label)}" '
                 'style="rounded=1;whiteSpace=wrap;html=1;arcSize=30;" '
                 f'vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>')

# рёбра drawio: source,target,label,exit,entry
DE = [
    ("init", "S1", "", None, None),
    ("S1", "S2", "разобрать метаданные [файл корректен]", (0.5, 1), (0.5, 0)),
    ("S2", "S3", "сгенерировать автотесты [каталог доступен]", (0.5, 1), (0.5, 0)),
    ("S3", "S4", "запустить автотесты / запустить выбранные", (0.5, 1), (0.5, 0)),
    ("S4", "S5", "прогон завершён / сохранить в БД, отчёты HTML, CSV", (0.5, 1), (0.5, 0)),
    ("S5", "fin", "выход", (0.5, 1), (0.5, 0)),
    ("S4", "S3", "ошибка запуска / сообщение", (1, 0.4), (1, 0.5)),
    ("S5", "S4", "запустить снова", (1, 0.16), (1, 0.6)),
    ("S1", "S1", "ошибка разбора / сообщение", (1, 0.3), (1, 0.7)),
    ("S5", "S6", "просмотреть историю", (1, 0.69), (0, 0.69)),
    ("S6", "S5", "назад", (0, 0.84), (1, 0.84)),
]
eid = 0
for src, tgt, lbl, ex, en in DE:
    eid += 1
    style = "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;"
    if ex: style += f"exitX={ex[0]};exitY={ex[1]};exitDx=0;exitDy=0;"
    if en: style += f"entryX={en[0]};entryY={en[1]};entryDx=0;entryDy=0;"
    cells.append(f'<mxCell id="e{eid}" value="{esc(lbl)}" style="{style}" '
                 f'edge="1" parent="1" source="{src}" target="{tgt}"><mxGeometry relative="1" as="geometry"/></mxCell>')

drawio = ('<mxfile host="app.diagrams.net">\n'
          '<diagram name="Диаграмма переходов состояний">\n'
          '<mxGraphModel dx="900" dy="700" grid="1" gridSize="10" guides="1" '
          'tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" '
          'pageWidth="827" pageHeight="1169" math="0" shadow="0">\n'
          '<root>\n<mxCell id="0"/>\n<mxCell id="1" parent="0"/>\n'
          + "\n".join(cells) +
          '\n</root>\n</mxGraphModel>\n</diagram>\n</mxfile>\n')
open(f"{OUT}/ris_12_state_transition.drawio", "w", encoding="utf-8").write(drawio)
print("OK ris_12_state_transition.drawio")
