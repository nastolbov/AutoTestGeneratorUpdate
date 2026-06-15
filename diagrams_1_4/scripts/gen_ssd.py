# -*- coding: utf-8 -*-
"""Ч/Б диаграммы последовательности системы (рис. 6.10) — актёр → System (чёрный ящик).
Только события Пользователь → Система, без обратных стрелок. По одной диаграмме на ВИ."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TTF_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

f_lbl  = ImageFont.truetype(TTF, 17)
f_sys  = ImageFont.truetype(TTF_B, 19)
f_note = ImageFont.truetype(TTF, 15)
f_title= ImageFont.truetype(TTF_B, 19)

BLACK=(0,0,0); WHITE=(255,255,255)

def text_w(d, s, f): return d.textbbox((0,0), s, font=f)[2]

def draw_actor(d, cx, top):
    lw=3
    d.ellipse([cx-16,top,cx+16,top+32], outline=BLACK, width=lw)
    d.line([cx,top+32,cx,top+88], fill=BLACK, width=lw)
    d.line([cx-30,top+52,cx+30,top+52], fill=BLACK, width=lw)
    d.line([cx,top+88,cx-26,top+130], fill=BLACK, width=lw)
    d.line([cx,top+88,cx+26,top+130], fill=BLACK, width=lw)

def arrow_right(d, x1, x2, y):
    d.line([x1,y,x2,y], fill=BLACK, width=2)
    d.polygon([(x2,y),(x2-13,y-6),(x2-13,y+6)], fill=BLACK)

def note_box(d, x, y, lines, font):
    w=max(text_w(d,l,font) for l in lines)+24
    h=len(lines)*22+14; cut=12
    d.polygon([(x,y),(x+w-cut,y),(x+w,y+cut),(x+w,y+h),(x,y+h)], fill=WHITE, outline=BLACK)
    d.line([(x+w-cut,y),(x+w-cut,y+cut),(x+w,y+cut)], fill=BLACK, width=1)
    for i,l in enumerate(lines):
        d.text((x+12,y+8+i*22), l, font=font, fill=BLACK)

def make_ssd(name, title, events, notes=None):
    """events: список подписей сообщений Пользователь→Система. notes: [(after_index,[lines])]."""
    x_actor=120; x_sys=900; note_x=x_sys+30
    scratch=ImageDraw.Draw(Image.new("RGB",(10,10)))
    note_right=0
    if notes:
        for (_i,lines) in notes:
            note_right=max(note_right, note_x+max(text_w(scratch,l,f_note) for l in lines)+24)
    # ширина холста: учесть и длинные подписи событий
    ev_right=max((20+text_w(scratch,e,f_lbl) for e in events), default=0)+x_actor
    W=max(1180, note_right+30, ev_right+60)
    top_y=40; head_bottom=top_y+150; step=70
    n=len(events); H=head_bottom+40+n*step+60
    img=Image.new("RGB",(W,H),WHITE); d=ImageDraw.Draw(img)
    d.text((30,8), title, font=f_title, fill=BLACK)
    draw_actor(d, x_actor, top_y+6)
    d.text((x_actor-text_w(d,"Пользователь",f_lbl)//2, top_y+142), "Пользователь", font=f_lbl, fill=BLACK)
    bw=140; bh=44
    d.rectangle([x_sys-bw//2, top_y+20, x_sys+bw//2, top_y+20+bh], outline=BLACK, width=2, fill=WHITE)
    d.text((x_sys-text_w(d,"System",f_sys)//2, top_y+30), "System", font=f_sys, fill=BLACK)
    bottom=H-30
    for xx in (x_actor, x_sys):
        yy=head_bottom
        while yy<bottom:
            d.line([xx,yy,xx,min(yy+7,bottom)], fill=BLACK, width=1); yy+=13
    y=head_bottom+45
    for i,ev in enumerate(events):
        d.text((x_actor+20, y-26), ev, font=f_lbl, fill=BLACK)
        arrow_right(d, x_actor, x_sys, y)
        d.rectangle([x_sys-6, y, x_sys+6, y+24], fill=WHITE, outline=BLACK)  # активация
        if notes:
            for (idx,lines) in notes:
                if idx==i: note_box(d, note_x, y-30, lines, f_note)
        y+=step
    img.save(f"{OUT}/{name}.png"); print("OK", name)

# 1 — Подготовить параметры (6 событий)
make_ssd("ris_3_ssd_params", "Диаграмма последовательности системы: «Подготовить параметры»", [
    "указатьФайлМетаданных(путь)", "указатьКаталогГенерации(путь)", "указатьАдресСайта(URL)",
    "указатьУчётныеДанные(логин, пароль)", "выбратьТипСайта(тип)", "указатьПодсистему(имя)"])

# 2 — Разобрать метаданные
make_ssd("ris_4_ssd_parse", "Диаграмма последовательности системы: «Разобрать метаданные»",
    ["разобратьМетаданные(файлXML)"])

# 3 — Сгенерировать автотесты
make_ssd("ris_5_ssd_generate", "Диаграмма последовательности системы: «Сгенерировать автотесты»",
    ["сгенерироватьАвтотесты(модель, каталог)"])

# 4 — Запустить автотесты
make_ssd("ris_6_ssd_run", "Диаграмма последовательности системы: «Запустить автотесты»",
    ["запуститьАвтотесты(фильтрТестов)"],
    notes=[(0, ["«include»: сохранитьРезультаты(прогон)"])])

# 5 — Сохранить результаты прогона (include)
make_ssd("ris_7_ssd_save", "Диаграмма последовательности системы: «Сохранить результаты прогона»",
    ["сохранитьРезультаты(прогон)"],
    notes=[(0, ["«include» из варианта", "«Запустить автотесты»"])])

# 6 — Просмотреть историю прогонов
make_ssd("ris_8_ssd_history", "Диаграмма последовательности системы: «Просмотреть историю прогонов»",
    ["просмотретьИсториюПрогонов()"])

print("ssd done")
