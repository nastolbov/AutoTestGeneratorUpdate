# -*- coding: utf-8 -*-
"""Ч/Б диаграммы последовательности системы по шаблону ВКР:
без заголовка, «System» — прямоугольник, пунктирные линии жизни, сплошные стрелки
Пользователь → Система с подписью по центру, без блоков активации."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TTF_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
f_lbl = ImageFont.truetype(TTF, 16)
f_sys = ImageFont.truetype(TTF, 18)
BLACK=(0,0,0); WHITE=(255,255,255)

def text_w(d, s, f): return d.textbbox((0,0), s, font=f)[2]

def draw_actor(d, cx, top):
    lw=2
    d.ellipse([cx-15,top,cx+15,top+30], outline=BLACK, width=lw)
    d.line([cx,top+30,cx,top+82], fill=BLACK, width=lw)
    d.line([cx-28,top+48,cx+28,top+48], fill=BLACK, width=lw)
    d.line([cx,top+82,cx-24,top+120], fill=BLACK, width=lw)
    d.line([cx,top+82,cx+24,top+120], fill=BLACK, width=lw)

def dashed_v(d, x, y0, y1):
    y=y0
    while y<y1:
        d.line([x,y,x,min(y+6,y1)], fill=BLACK, width=1); y+=11

def arrow_right(d, x1, x2, y):
    d.line([x1,y,x2,y], fill=BLACK, width=2)
    d.polygon([(x2,y),(x2-12,y-5),(x2-12,y+5)], fill=BLACK)

def make_ssd(name, title, events, notes=None):
    # title/notes игнорируются — строго по шаблону ВКР
    actor_x=95; sys_x=760
    box_w, box_h = 220, 62
    top=18
    actor_label_y = top+10+124
    life_top = actor_label_y+26
    step=62
    n=len(events)
    H = life_top + 30 + n*step + 30
    W = 1040
    img=Image.new("RGB",(W,H),WHITE); d=ImageDraw.Draw(img)
    # актёр
    draw_actor(d, actor_x, top+10)
    d.text((actor_x-text_w(d,"Пользователь",f_lbl)//2, actor_label_y), "Пользователь", font=f_lbl, fill=BLACK)
    # System — прямоугольник
    d.rectangle([sys_x-box_w//2, top, sys_x+box_w//2, top+box_h], outline=BLACK, width=2, fill=WHITE)
    d.text((sys_x-text_w(d,"System",f_sys)//2, top+box_h//2-11), "System", font=f_sys, fill=BLACK)
    # линии жизни (пунктир)
    bottom=H-22
    dashed_v(d, actor_x, life_top, bottom)
    dashed_v(d, sys_x, top+box_h, bottom)
    # события: сплошная стрелка, подпись по центру над стрелкой
    mid=(actor_x+sys_x)//2
    y=life_top+34
    for ev in events:
        tw=text_w(d, ev, f_lbl)
        d.text((mid-tw//2, y-22), ev, font=f_lbl, fill=BLACK)
        arrow_right(d, actor_x, sys_x, y)
        y+=step
    img.save(f"{OUT}/{name}.png"); print("OK", name)

make_ssd("ris_3_ssd_params", "", [
    "указатьФайлМетаданных(путь)", "указатьКаталогГенерации(путь)", "указатьАдресСайта(URL)",
    "указатьУчётныеДанные(логин, пароль)", "выбратьТипСайта(тип)", "указатьПодсистему(имя)"])
make_ssd("ris_4_ssd_parse", "", ["разобратьМетаданные(файлXML)", "просмотретьРезультатРазбора()"])
make_ssd("ris_5_ssd_generate", "", ["сгенерироватьАвтотесты(модель, каталог)", "просмотретьСводкуГенерации()"])
make_ssd("ris_6_ssd_run", "", ["запуститьАвтотесты(фильтрТестов)", "просмотретьРезультатыПрогона()"])
make_ssd("ris_7_ssd_save", "", ["сохранитьРезультаты(прогон)", "просмотретьУведомлениеОСохранении()"])
make_ssd("ris_8_ssd_history", "", ["запроситьИсториюПрогонов()", "просмотретьДетализациюПрогона()"])
print("ssd done")
