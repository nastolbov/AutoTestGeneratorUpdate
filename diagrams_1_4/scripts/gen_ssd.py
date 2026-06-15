# -*- coding: utf-8 -*-
"""Диаграммы последовательности системы (рис. 6.10) — актёр ↔ System (чёрный ящик)."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
os.makedirs(OUT, exist_ok=True)
TTF = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
TTF_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

f_lbl  = ImageFont.truetype(TTF, 17)
f_sys  = ImageFont.truetype(TTF_B, 19)
f_note = ImageFont.truetype(TTF, 15)
f_title= ImageFont.truetype(TTF_B, 19)

BLACK=(0,0,0); GRAY=(120,120,120); PURPLE=(106,27,154); NOTEBG=(255,249,196)

def text_w(d, s, f):
    return d.textbbox((0,0), s, font=f)[2]

def draw_actor(d, cx, top):
    lw=3; c=BLACK
    d.ellipse([cx-16,top,cx+16,top+32], outline=c, width=lw)
    d.line([cx,top+32,cx,top+88], fill=c, width=lw)
    d.line([cx-30,top+52,cx+30,top+52], fill=c, width=lw)
    d.line([cx,top+88,cx-26,top+130], fill=c, width=lw)
    d.line([cx,top+88,cx+26,top+130], fill=c, width=lw)

def arrow_right(d, x1, x2, y, solid=True):
    if solid:
        d.line([x1,y,x2,y], fill=BLACK, width=2)
        d.polygon([(x2,y),(x2-13,y-6),(x2-13,y+6)], fill=BLACK)
    else:
        # пунктир
        x=x1
        while x<x2:
            d.line([x,y,min(x+8,x2),y], fill=BLACK, width=2); x+=14
        d.polygon([(x2,y),(x2-13,y-6),(x2-13,y+6)], fill=BLACK)

def arrow_left(d, x1, x2, y, dashed=True):
    # от x1 (право) к x2 (лево)
    if dashed:
        x=x1
        while x>x2:
            d.line([x,y,max(x-8,x2),y], fill=GRAY, width=2); x-=14
    else:
        d.line([x1,y,x2,y], fill=GRAY, width=2)
    d.line([x2,y,x2+13,y-6], fill=GRAY, width=2)
    d.line([x2,y,x2+13,y+6], fill=GRAY, width=2)

def note_box(d, x, y, lines, font):
    w=max(text_w(d,l,font) for l in lines)+24
    h=len(lines)*22+14
    cut=12
    pts=[(x,y),(x+w-cut,y),(x+w,y+cut),(x+w,y+h),(x,y+h)]
    d.polygon(pts, fill=NOTEBG, outline=(150,150,150))
    d.line([(x+w-cut,y),(x+w-cut,y+cut),(x+w,y+cut)], fill=(150,150,150), width=1)
    for i,l in enumerate(lines):
        d.text((x+12,y+8+i*22), l, font=font, fill=(90,70,0))
    return w,h

def make_ssd(name, title, messages, notes=None):
    """messages: list of dict {dir:'call'|'return', text:str}. notes: list of (after_index, [lines])."""
    x_actor=120; x_sys=900
    note_x=x_sys+30
    # измеряем ширину примечаний, чтобы холст не обрезал их
    scratch=ImageDraw.Draw(Image.new("RGB",(10,10)))
    note_right=0
    if notes:
        for (_idx,lines) in notes:
            wmax=max(text_w(scratch,l,f_note) for l in lines)+24
            note_right=max(note_right, note_x+wmax)
    W=max(1180, note_right+30)
    top_y=40
    head_bottom=top_y+150
    step=70
    n=len(messages)
    H=head_bottom+40+n*step+60
    img=Image.new("RGB",(W,H),(255,255,255))
    d=ImageDraw.Draw(img)
    # заголовок
    d.text((30,8), title, font=f_title, fill=BLACK)
    # актёр
    draw_actor(d, x_actor, top_y+6)
    d.text((x_actor-text_w(d,"Пользователь",f_lbl)//2, top_y+142), "Пользователь", font=f_lbl, fill=BLACK)
    # System box
    bw=140; bh=44
    d.rectangle([x_sys-bw//2, top_y+20, x_sys+bw//2, top_y+20+bh], outline=BLACK, width=2, fill=(232,234,246))
    d.text((x_sys-text_w(d,"System",f_sys)//2, top_y+30), "System", font=f_sys, fill=(40,53,147))
    # линии жизни
    bottom=H-30
    for xx in (x_actor, x_sys):
        yy=head_bottom
        while yy<bottom:
            d.line([xx,yy,xx,min(yy+7,bottom)], fill=GRAY, width=2); yy+=13
    # сообщения
    y=head_bottom+45
    for i,m in enumerate(messages):
        if m["dir"]=="call":
            d.text((x_actor+20, y-26), m["text"], font=f_lbl, fill=BLACK)
            arrow_right(d, x_actor, x_sys, y, solid=True)
            d.rectangle([x_sys-6, y, x_sys+6, y+24], fill=(197,202,233), outline=(40,53,147))
        else:
            t=m["text"]; tw=text_w(d,t,f_lbl)
            d.text((x_sys-20-tw, y-26), t, font=f_lbl, fill=GRAY)
            arrow_left(d, x_sys, x_actor, y, dashed=True)
        # примечание справа от System
        if notes:
            for (idx,lines) in notes:
                if idx==i:
                    note_box(d, x_sys+30, y-30, lines, f_note)
        y+=step
    img.save(f"{OUT}/{name}.png")
    print("OK", name)

# ---- SSD 1: Подготовить параметры ----
make_ssd("ris_3_ssd_params", "Диаграмма последовательности системы: «Подготовить параметры»", [
    {"dir":"call","text":"указатьФайлМетаданных(путь)"},
    {"dir":"call","text":"указатьКаталогГенерации(путь)"},
    {"dir":"call","text":"указатьАдресСайта(URL)"},
    {"dir":"call","text":"указатьУчётныеДанные(логин, пароль)"},
    {"dir":"call","text":"выбратьТипСайта(тип)"},
    {"dir":"call","text":"указатьПодсистему(имя)"},
    {"dir":"return","text":"параметры приняты, готовность к работе"},
])

# ---- SSD 2: Разобрать метаданные ----
make_ssd("ris_4_ssd_parse", "Диаграмма последовательности системы: «Разобрать метаданные»", [
    {"dir":"call","text":"разобратьМетаданные(файлXML)"},
    {"dir":"return","text":"модель метаданных, перечень сущностей с типами"},
], notes=[(1, ["Альтернатива: при ошибке формата —", "возврат «ошибка разбора»"])])

# ---- SSD 3: Сгенерировать автотесты ----
make_ssd("ris_5_ssd_generate", "Диаграмма последовательности системы: «Сгенерировать автотесты»", [
    {"dir":"call","text":"сгенерироватьАвтотесты(модель, каталог)"},
    {"dir":"return","text":"тестовый проект, сводка о генерации"},
], notes=[(0, ["Система классифицирует", "сущности и формирует", "тестовый проект"])])

# ---- SSD 4: Запустить автотесты (+ include сохранение) ----
make_ssd("ris_6_ssd_run", "Диаграмма последовательности системы: «Запустить автотесты»", [
    {"dir":"call","text":"запуститьАвтотесты(фильтрТестов)"},
    {"dir":"return","text":"результаты: всего / успешно / провалено, отчёты"},
], notes=[(0, ["«include»: сохранитьРезультаты(прогон)", "— запись прогона в базу отчётов"])])

print("done")
