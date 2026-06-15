# -*- coding: utf-8 -*-
"""Диаграммы последовательности (Pillow) по сценариям scenarios.py.
Объекты-боксы + пунктирные линии жизни, сплошные вызовы / пунктирные возвраты с
русскими подписями, самовызовы, полосы активации (стек), управление из края (без
актёра «Пользователь»), разрыв «✕» при прерывании. Ч/Б, Liberation Serif."""
import os, sys
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenarios as S

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf"
TTF_B = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
F = ImageFont.truetype(TTF, 15)      # имена объектов
FM = ImageFont.truetype(TTF, 13)     # подписи сообщений
FB = ImageFont.truetype(TTF_B, 22)
BLACK = (0, 0, 0); WHITE = (255, 255, 255)

LEFT = 40                 # начало стрелок «из края»
PITCH = 300               # шаг между линиями жизни
TOP = 20
BOX_H = 40
STEP = 56                 # вертикальный шаг сообщения
ACT_W = 10                # ширина полосы активации


def tw(d, s, f):
    b = d.textbbox((0, 0), s, font=f); return b[2] - b[0]


def dashed_v(d, x, y0, y1):
    y = y0
    while y < y1:
        d.line([x, y, x, min(y + 6, y1)], fill=BLACK, width=1); y += 11


def arrow(d, x1, x2, y, dashed=False):
    if dashed:
        x, step = x1, 7; d_ = 1 if x2 > x1 else -1
        while (x2 - x) * d_ > 0:
            nx = x + d_ * 5
            if (x2 - nx) * d_ < 0:
                nx = x2
            d.line([x, y, nx, y], fill=BLACK, width=1); x = nx + d_ * 4
    else:
        d.line([x1, y, x2, y], fill=BLACK, width=2)
    a = 11 if x2 > x1 else -11
    d.polygon([(x2, y), (x2 - a, y - 5), (x2 - a, y + 5)], fill=BLACK)


def render(pkg, flow_key):
    flow = S.SC[pkg]["flows"][flow_key]
    # объекты, участвующие в этом сценарии, в порядке SC objects
    used = []
    for k, _ in S.SC[pkg]["objects"]:
        if any((frm == k or to == k) for frm, to, _, _ in flow) and k not in used:
            used.append(k)
    x = {k: LEFT + 150 + i * PITCH for i, k in enumerate(used)}
    n = len(used)
    W = LEFT + 150 + (n - 1) * PITCH + 200
    life_top = TOP + BOX_H
    H = life_top + 30 + len(flow) * STEP + 50
    img = Image.new("RGB", (W, H), WHITE); d = ImageDraw.Draw(img)

    # боксы объектов + линии жизни
    bottom = H - 30
    for k in used:
        lbl = S.label_of(pkg, k)
        w = max(tw(d, lbl, F) + 24, 90); cx = x[k]
        d.rectangle([cx - w // 2, TOP, cx + w // 2, TOP + BOX_H], outline=BLACK, width=2, fill=WHITE)
        d.text((cx - tw(d, lbl, F) // 2, TOP + BOX_H // 2 - 9), lbl, font=F, fill=BLACK)
        dashed_v(d, cx, TOP + BOX_H, bottom)

    # активации (стек на объект)
    act = {k: [] for k in used}
    act_bars = []

    def xof(k):
        return LEFT if k == S.EDGE else x[k]

    initiator = None
    y = life_top + 34
    for frm, to, text, kind in flow:
        if initiator is None and frm == S.EDGE and to != S.EDGE:
            initiator = to
        xa, xb = xof(frm), xof(to)
        if kind == "self" or frm == to:
            # самовызов: маленькая петля справа
            d.line([xa + ACT_W // 2, y, xa + 36, y], fill=BLACK, width=2)
            d.line([xa + 36, y, xa + 36, y + 16], fill=BLACK, width=2)
            arrow(d, xa + 36, xa + ACT_W // 2 + 1, y + 16)
            d.text((xa + 42, y - 8), text, font=FM, fill=BLACK)
            y += STEP
            continue
        dashed = (kind == "ret")
        # подпись по центру над стрелкой
        midx = (xa + xb) // 2
        d.text((midx - tw(d, text, FM) // 2, y - 20), text, font=FM, fill=BLACK)
        if kind == "create":
            d.text((midx - tw(d, "«create»", FM) // 2, y - 33), "«create»", font=FM, fill=BLACK)
        arrow(d, xa, xb + (ACT_W // 2 if xb > xa else -ACT_W // 2), y, dashed=dashed)
        # активации
        if kind in ("call", "create") and to != S.EDGE:
            act[to].append(y)
        if kind == "ret" and frm != S.EDGE and act.get(frm):
            y0 = act[frm].pop(); act_bars.append((frm, y0, y))
        y += STEP

    # закрыть оставшиеся активации до низа
    for k in used:
        for y0 in act[k]:
            act_bars.append((k, y0, y - STEP + 14))
    for k, y0, y1 in act_bars:
        cx = x[k]
        d.rectangle([cx - ACT_W // 2, y0 - 6, cx + ACT_W // 2, y1], outline=BLACK, width=1, fill=WHITE)

    # разрыв «✕» при прерывании
    if flow_key in ("user", "system") and initiator:
        cx = x[initiator]; yy = y - 4
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
