# -*- coding: utf-8 -*-
"""Диаграммы последовательности (Pillow) по сценариям scenarios.py.
- Фиксированный набор линий жизни на пакет: во ВСЕХ 3 сценариях показаны одни и те
  же объекты (= объединение объектов сценариев); неиспользуемые стоят пустыми.
- Стек вызовов: каждая активация ЗАКРЫВАЕТСЯ возвратной (пунктирной) стрелкой,
  выходящей из блока обратно к вызывающему — линии жизни не «живут вечно».
- Подписи сообщений на переднем плане (белая подложка).
- Управление из края (без актёра «Пользователь»), разрыв «✕» при прерывании.
Ч/Б, шрифт Liberation Serif."""
import os, sys
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenarios as S

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf"
TTF_B = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
F = ImageFont.truetype(TTF, 15)
FM = ImageFont.truetype(TTF, 13)
BLACK = (0, 0, 0); WHITE = (255, 255, 255)

LEFT = 50
PITCH = 300
TOP = 20
BOX_H = 40
STEP = 54
ACT_W = 10


def tw(d, s, f):
    b = d.textbbox((0, 0), s, font=f); return b[2] - b[0]


def label(d, cx, y, s, f=FM, anchor="m"):
    """Подпись с белой подложкой (передний план)."""
    w = tw(d, s, f); h = 15
    x0 = cx - w // 2 if anchor == "m" else cx
    d.rectangle([x0 - 2, y - 1, x0 + w + 2, y + h], fill=WHITE)
    d.text((x0, y - 1), s, font=f, fill=BLACK)


def dashed_v(d, x, y0, y1):
    y = y0
    while y < y1:
        d.line([x, y, x, min(y + 6, y1)], fill=BLACK, width=1); y += 11


def arrow(d, x1, x2, y, dashed=False):
    if dashed:
        x = x1; dr = 1 if x2 > x1 else -1
        while (x2 - x) * dr > 0:
            nx = x + dr * 6
            if (x2 - nx) * dr < 0:
                nx = x2
            d.line([x, y, nx, y], fill=BLACK, width=1); x = nx + dr * 4
    else:
        d.line([x1, y, x2, y], fill=BLACK, width=2)
    a = 11 if x2 >= x1 else -11
    d.polygon([(x2, y), (x2 - a, y - 5), (x2 - a, y + 5)], fill=BLACK)


def render(pkg, flow_key):
    flow = S.SC[pkg]["flows"][flow_key]
    used = S.used_object_keys(pkg)                  # фиксированный набор для всех сценариев
    x = {k: LEFT + 160 + i * PITCH for i, k in enumerate(used)}
    n = len(used)
    W = LEFT + 160 + (n - 1) * PITCH + 220
    life_top = TOP + BOX_H
    H = life_top + 30 + (len(flow) + 2) * STEP + 50

    img = Image.new("RGB", (W, H), WHITE); d = ImageDraw.Draw(img)

    def xof(k):
        return LEFT if k == S.EDGE else x[k]

    # 1-й проход — геометрия
    arrows, selfs, labels, bars = [], [], [], []
    stack = []; initiator = None
    y = life_top + 30
    for frm, to, text, kind in flow:
        if frm == S.EDGE and to != S.EDGE and initiator is None:
            initiator = to
        if kind == "self":
            selfs.append((xof(frm), y, text)); y += STEP; continue
        if kind in ("call", "create"):
            xa, xb = xof(frm), xof(to)
            arrows.append((xa, xb, y, False))
            if kind == "create":
                labels.append(((xa + xb) // 2, y - 30, "«create»"))
            labels.append(((xa + xb) // 2, y - 16, text))
            stack.append((frm, to, y)); y += STEP
        elif kind == "ret":
            a, b, y0 = stack.pop() if stack else (to, frm, y - STEP)
            xa, xb = xof(b), xof(a)
            arrows.append((xa, xb, y, True))
            labels.append(((xa + xb) // 2, y - 16, text))
            if b != S.EDGE:
                bars.append((b, y0, y)); y += STEP
    while stack:
        a, b, y0 = stack.pop()
        xa, xb = xof(b), xof(a)
        arrows.append((xa, xb, y, True))
        labels.append(((xa + xb) // 2, y - 16, "возврат"))
        if b != S.EDGE:
            bars.append((b, y0, y))
        y += STEP
    bottom = y + 6

    # боксы объектов + линии жизни
    for k in used:
        lbl = S.label_of(pkg, k); cx = x[k]
        w = max(tw(d, lbl, F) + 24, 90)
        d.rectangle([cx - w // 2, TOP, cx + w // 2, TOP + BOX_H], outline=BLACK, width=2, fill=WHITE)
        d.text((cx - tw(d, lbl, F) // 2, TOP + BOX_H // 2 - 9), lbl, font=F, fill=BLACK)
        dashed_v(d, cx, TOP + BOX_H, bottom)

    # полосы активации (белая заливка поверх линии жизни)
    for k, y0, y1 in bars:
        cx = x[k]
        d.rectangle([cx - ACT_W // 2, y0 - 4, cx + ACT_W // 2, y1], outline=BLACK, width=1, fill=WHITE)

    # стрелки
    for xa, xb, yy, dashed in arrows:
        off = ACT_W // 2
        x2 = xb + (off if xb > xa else -off) if xb != LEFT else xb
        arrow(d, xa, x2, yy, dashed=dashed)

    # самовызовы (петля справа)
    for cx, yy, text in selfs:
        d.line([cx + ACT_W // 2, yy, cx + 40, yy], fill=BLACK, width=2)
        d.line([cx + 40, yy, cx + 40, yy + 14], fill=BLACK, width=2)
        arrow(d, cx + 40, cx + ACT_W // 2 + 1, yy + 14)
        label(d, cx + 46, yy - 8, text, anchor="l")

    # подписи поверх всего
    for cx, yy, text in labels:
        label(d, cx, yy, text)

    # разрыв «✕» при прерывании
    if flow_key in ("user", "system") and initiator:
        cx = x[initiator]; yy = bottom - 12
        d.line([cx - 10, yy - 10, cx + 10, yy + 10], fill=BLACK, width=3)
        d.line([cx - 10, yy + 10, cx + 10, yy - 10], fill=BLACK, width=3)

    name = f"seq_{pkg}_{flow_key}"
    img.save(f"{OUT}/{name}.png"); print("OK", name)


def main():
    for pkg in S.SC:
        for flow_key in S.SC[pkg]["flows"]:
            render(pkg, flow_key)
    print("sequence done")


if __name__ == "__main__":
    main()
