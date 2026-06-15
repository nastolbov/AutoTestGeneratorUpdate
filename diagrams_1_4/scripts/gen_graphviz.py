# -*- coding: utf-8 -*-
"""Ч/Б diagrams через graphviz: ВИ (1.4.1), класс System (1.4.3), деятельность (1.4.4).
Контекстная диаграмма (1.4.2) НЕ генерируется — берётся из ВКР (ris_2_context_class.png)."""
import subprocess, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "DejaVu Sans"

def render(dot, name):
    path = f"/tmp/{name}.dot"
    open(path, "w").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/{name}.png"], check=True)
    print("OK", name)

# ---------- Актёр (ч/б фигурка) ----------
def make_actor():
    W, H = 120, 200
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    d = ImageDraw.Draw(img); cx = W//2; lw=4; col=(0,0,0,255)
    d.ellipse([cx-22,10,cx+22,54], outline=col, width=lw)
    d.line([cx,54,cx,130], fill=col, width=lw)
    d.line([cx-40,85,cx+40,85], fill=col, width=lw)
    d.line([cx,130,cx-34,188], fill=col, width=lw)
    d.line([cx,130,cx+34,188], fill=col, width=lw)
    img.save(f"{OUT}/actor.png")
make_actor()

# ========== 1.4.1 — Диаграмма вариантов использования (Ч/Б) ==========
usecase = f'''
digraph UseCase {{
  rankdir=LR; bgcolor=white;
  fontname="{FONT}"; fontsize=13;
  node [fontname="{FONT}", fontsize=12, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black];
  nodesep=0.45; ranksep=1.1;

  actor [shape=none, image="{OUT}/actor.png", label="Пользователь\\n(инженер по тестированию)", labelloc=b, imagescale=true, width=1.0, height=1.7];

  subgraph cluster_sys {{
    label="Информационная система генерации автотестов web-приложения по XML-модели";
    labelloc=t; fontsize=13; style="rounded"; color=black; penwidth=1.4; margin=18;
    node [shape=ellipse, style=filled, fillcolor=white];
    UC1 [label="Подготовить\\nпараметры"];
    UC2 [label="Разобрать\\nметаданные"];
    UC3 [label="Сгенерировать\\nавтотесты"];
    UC4 [label="Запустить\\nавтотесты"];
    UC5 [label="Сохранить\\nрезультаты прогона"];
    UC6 [label="Просмотреть\\nисторию прогонов"];
  }}

  edge [dir=none];
  actor -> UC1; actor -> UC2; actor -> UC3; actor -> UC4; actor -> UC6;

  edge [dir=forward, style=dashed, arrowhead=vee];
  UC4  -> UC5  [label="«include»"];
}}
'''
render(usecase, "ris_1_usecase")

# ========== 1.4.3 — Класс системных операций System (Ч/Б) ==========
sysops = f'''
digraph SystemOps {{
  bgcolor=white; fontname="{FONT}";
  node [shape=record, fontname="{FONT}", fontsize=12, style=filled, fillcolor=white, color=black, fontcolor=black];
  System [label="{{System|+ указатьФайлМетаданных(путь)\\l+ указатьКаталогГенерации(путь)\\l+ указатьАдресСайта(URL)\\l+ указатьУчётныеДанные(логин, пароль)\\l+ выбратьТипСайта(тип)\\l+ указатьПодсистему(имя)\\l+ разобратьМетаданные(файлXML)\\l+ сгенерироватьАвтотесты(модель, каталог)\\l+ запуститьАвтотесты(фильтрТестов)\\l+ сохранитьРезультаты(прогон)\\l+ просмотретьИсториюПрогонов()\\l}}"];
}}
'''
render(sysops, "ris_9_system_ops")

# ========== 1.4.4 — Диаграмма деятельности «Сгенерировать автотесты» (Ч/Б) ==========
activity = f'''
digraph Activity {{
  bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black];
  ranksep=0.42; nodesep=0.45;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin   [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor=white];
  a_check [label="Проверить наличие модели\\nи каталога генерации"];
  a_struct[label="Создать структуру\\nMaven-проекта (pom.xml, конфигурация)"];
  a_infra [label="Сгенерировать инфраструктуру\\n(общий драйвер, базовый тест, тестовые данные)"];
  a_class [label="Классифицировать\\nочередную сущность"];
  a_prim  [label="Создать Page Object\\nи тестовый класс"];
  a_child [label="Создать тест\\nв составе родителя"];
  a_report[label="Сохранить отчёт\\nо классификации"];
  a_sum   [label="Сформировать сводку\\nо генерации"];

  node [shape=diamond, style=filled, fillcolor=white, height=0.9, width=1.7];
  d_param [label="Параметры\\nкорректны?"];
  d_kind  [label="Тип\\nсущности?"];
  d_more  [label="Остались\\nсущности?"];

  node [shape=box, style="filled", fillcolor=white];
  err [label="Вывести сообщение\\nоб ошибке"];

  start -> a_check;
  a_check -> d_param;
  d_param -> err [label="нет"];
  err -> fin;
  d_param -> a_struct [label="да"];
  a_struct -> a_infra -> a_class -> d_kind;
  d_kind -> a_prim  [label="основная"];
  d_kind -> a_child [label="дочерняя"];
  d_kind -> d_more  [label="справочник\\n(пропуск)"];
  a_prim  -> d_more;
  a_child -> d_more;
  d_more -> a_class [label="да  *для каждой сущности"];
  d_more -> a_report [label="нет"];
  a_report -> a_sum -> fin;
}}
'''
render(activity, "ris_10_activity_generate")
print("graphviz done")
