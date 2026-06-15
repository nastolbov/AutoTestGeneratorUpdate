# -*- coding: utf-8 -*-
"""Генерация диаграмм разделов 1.4.1, 1.4.2, 1.4.3 (класс System), 1.4.4 через graphviz + актёр через PIL."""
import subprocess, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
os.makedirs(OUT, exist_ok=True)
FONT = "DejaVu Sans"

def render(dot, name):
    path = f"/tmp/{name}.dot"
    open(path, "w").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/{name}.png"], check=True)
    print("OK", name)

# ---------- Актёр (фигурка) через PIL ----------
def make_actor():
    W, H = 120, 200
    img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    d = ImageDraw.Draw(img)
    cx = W // 2
    lw = 4
    col = (0, 0, 0, 255)
    # голова
    d.ellipse([cx-22, 10, cx+22, 54], outline=col, width=lw)
    # туловище
    d.line([cx, 54, cx, 130], fill=col, width=lw)
    # руки
    d.line([cx-40, 85, cx+40, 85], fill=col, width=lw)
    # ноги
    d.line([cx, 130, cx-34, 188], fill=col, width=lw)
    d.line([cx, 130, cx+34, 188], fill=col, width=lw)
    img.save(f"{OUT}/actor.png")
    print("OK actor.png")

make_actor()

# ========== 1.4.1 — Диаграмма вариантов использования ==========
usecase = f'''
digraph UseCase {{
  rankdir=LR;
  fontname="{FONT}"; fontsize=13;
  node [fontname="{FONT}", fontsize=12];
  edge [fontname="{FONT}", fontsize=10];
  nodesep=0.45; ranksep=1.1;

  actor [shape=none, image="{OUT}/actor.png", label="Пользователь\\n(инженер по тестированию)", labelloc=b, imagescale=true, width=1.0, height=1.7];

  subgraph cluster_sys {{
    label="Информационная система генерации автотестов web-приложения по XML-модели";
    labelloc=t; fontsize=13; style="rounded"; color="#37474f"; penwidth=1.6; margin=18;
    UC1 [shape=ellipse, style=filled, fillcolor="#e3f2fd", label="Подготовить\\nпараметры"];
    UC2 [shape=ellipse, style=filled, fillcolor="#e3f2fd", label="Разобрать\\nметаданные"];
    UC3 [shape=ellipse, style=filled, fillcolor="#e3f2fd", label="Сгенерировать\\nавтотесты"];
    UC4 [shape=ellipse, style=filled, fillcolor="#e3f2fd", label="Запустить\\nавтотесты"];
    UC4a[shape=ellipse, style=filled, fillcolor="#fff8e1", label="Запустить\\nвыбранные тесты"];
    UC5 [shape=ellipse, style=filled, fillcolor="#fff8e1", label="Сохранить\\nрезультаты прогона"];
    UC6 [shape=ellipse, style=filled, fillcolor="#e3f2fd", label="Просмотреть\\nисторию прогонов"];
  }}

  edge [dir=none, color="#263238"];
  actor -> UC1; actor -> UC2; actor -> UC3; actor -> UC4; actor -> UC6;

  edge [dir=forward, style=dashed, color="#6a1b9a", fontcolor="#6a1b9a", arrowhead=vee];
  UC4  -> UC5  [label="«include»"];
  UC4a -> UC4  [label="«extend»"];
}}
'''
render(usecase, "ris_1_usecase")

# ========== 1.4.2 — Контекстная (концептуальная) диаграмма классов ==========
# Концепты предметной области + обобщение Сущности. Без операций/реализации.
cls = f'''
digraph Context {{
  rankdir=TB;
  fontname="{FONT}";
  node [shape=record, fontname="{FONT}", fontsize=11, style=filled, fillcolor="#eceff1", color="#455a64"];
  edge [fontname="{FONT}", fontsize=9, color="#37474f"];
  nodesep=0.5; ranksep=0.7;

  FileMeta   [label="{{Файл метаданных|формат: XML}}", fillcolor="#fff3e0"];
  Model      [label="{{Модель метаданных}}"];
  Entity     [label="{{Сущность|имя\\lключевое поле\\l}}"];
  PrimaryE   [label="{{Основная\\nсущность}}", fillcolor="#e8f5e9"];
  ChildE     [label="{{Дочерняя\\nсущность}}", fillcolor="#e8f5e9"];
  RefE       [label="{{Справочник}}", fillcolor="#e8f5e9"];
  PGroup     [label="{{Группа свойств|тип (карточка/грид)}}"];
  Prop       [label="{{Свойство|имя\\lтип\\lобязательность\\l}}"];
  Oper       [label="{{Операция|вид (CRUD)}}"];
  Search     [label="{{Поиск|параметры}}"];

  Project    [label="{{Тестовый проект}}", fillcolor="#e1f5fe"];
  Autotest   [label="{{Автотест\\n(тестовый класс)|виды проверок}}", fillcolor="#e1f5fe"];
  PageObject [label="{{Page Object}}", fillcolor="#e1f5fe"];

  Run        [label="{{Прогон тестов|дата\\lдлительность\\l}}", fillcolor="#f3e5f5"];
  CaseResult [label="{{Результат теста|статус\\lсообщение\\l}}", fillcolor="#f3e5f5"];
  Report     [label="{{Отчёт|HTML / CSV}}", fillcolor="#f3e5f5"];
  DB         [label="{{База данных\\nотчётов}}", fillcolor="#f3e5f5"];

  // Ассоциации (имя + множественность)
  edge [arrowhead=none];
  FileMeta -> Model      [label="порождает", taillabel="1", headlabel="1"];
  Model    -> Entity     [label="содержит", taillabel="1", headlabel="1..*"];
  Model    -> Search     [label="содержит", taillabel="1", headlabel="0..*"];
  Entity   -> PGroup     [label="состоит из", taillabel="1", headlabel="1..*"];
  PGroup   -> Prop       [label="включает", taillabel="1", headlabel="0..*"];
  PGroup   -> Oper       [label="определяет", taillabel="1", headlabel="0..1"];
  Entity   -> Search     [label="доступна через", taillabel="1", headlabel="0..*"];

  PrimaryE -> Autotest   [label="порождает", taillabel="1", headlabel="1"];
  Project  -> Autotest   [label="содержит", taillabel="1", headlabel="1..*"];
  Project  -> PageObject [label="содержит", taillabel="1", headlabel="1..*"];

  Project  -> Run        [label="выполняется в", taillabel="1", headlabel="0..*"];
  Run      -> CaseResult [label="содержит", taillabel="1", headlabel="1..*"];
  Run      -> Report     [label="формирует", taillabel="1", headlabel="1..*"];
  DB       -> Run        [label="хранит", taillabel="1", headlabel="0..*"];

  // Обобщение (треугольная стрелка к супертипу)
  edge [arrowhead=empty, arrowsize=1.4, color="#1b5e20"];
  PrimaryE -> Entity;
  ChildE   -> Entity;
  RefE     -> Entity;

  {{rank=same; PrimaryE; ChildE; RefE;}}
}}
'''
render(cls, "ris_2_context_class")

# ========== 1.4.3 — Класс системных операций (аналог рис. 6.11) ==========
sysops = f'''
digraph SystemOps {{
  fontname="{FONT}";
  node [shape=record, fontname="{FONT}", fontsize=12, style=filled, fillcolor="#e8eaf6", color="#283593"];
  System [label="{{System|+ подготовитьПараметры(путьXML, каталог, URL, логин, пароль, типСайта, подсистема)\\l+ разобратьМетаданные(файлXML) : модель, классификация\\l+ сгенерироватьАвтотесты(модель, каталог, параметрыЗапуска) : тестовыйПроект\\l+ запуститьАвтотесты(фильтрТестов) : результатыПрогона\\l+ запуститьВыбранныеТесты(сущности, видыТестов) : результатыПрогона\\l+ сохранитьРезультаты(прогон)\\l+ просмотретьИсториюПрогонов() : списокПрогонов\\l}}"];
}}
'''
render(sysops, "ris_7_system_ops")

# ========== 1.4.4 — Диаграмма деятельности «Сгенерировать автотесты» ==========
activity = f'''
digraph Activity {{
  fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11];
  edge [fontname="{FONT}", fontsize=10, color="#37474f"];
  ranksep=0.42; nodesep=0.45;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin   [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor="#e3f2fd", color="#1565c0"];
  a_check [label="Проверить наличие модели\\nи каталога генерации"];
  a_struct[label="Создать структуру\\nMaven-проекта (pom.xml, конфигурация)"];
  a_infra [label="Сгенерировать инфраструктуру\\n(общий драйвер, базовый тест, тестовые данные)"];
  a_class [label="Классифицировать\\nочередную сущность"];
  a_prim  [label="Создать Page Object\\nи тестовый класс"];
  a_child [label="Создать тест\\nв составе родителя"];
  a_report[label="Сохранить отчёт\\nо классификации"];
  a_sum   [label="Сформировать сводку\\nо генерации"];

  node [shape=diamond, style=filled, fillcolor="#fff8e1", color="#f9a825", height=0.9, width=1.7];
  d_param [label="Параметры\\nкорректны?"];
  d_kind  [label="Тип\\nсущности?"];
  d_more  [label="Остались\\nсущности?"];

  node [shape=box, style="filled", fillcolor="#ffcdd2", color="#c62828"];
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
render(activity, "ris_8_activity_generate")

print("\nГотово. Файлы:")
for f in sorted(os.listdir(OUT)):
    print(" ", f, os.path.getsize(os.path.join(OUT, f)), "б")
