# -*- coding: utf-8 -*-
"""Ч/Б схемы алгоритмов для раздела 1.3 (анализ процесса обработки информации,
методы и алгоритмы). Все диаграммы построены по фактическому коду программы
AutoTestGenerator (пакеты ru.autotestgen.*) и описывают ЛОГИКУ обработки данных,
без привязки к конкретным классам и методам исходного кода."""
import subprocess, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "DejaVu Sans"

def render(dot, name):
    path = f"/tmp/{name}.dot"
    open(path, "w").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/{name}.png"], check=True)
    print("OK", name)

# ============ Рис. 1 — Общая схема процесса обработки информации (конвейер) ============
pipeline = f'''
digraph Pipeline {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  nodesep=0.45; ranksep=0.40;

  node [shape=parallelogram, style=filled, fillcolor=white];
  in1  [label="XML-модель метаданных E3Core"];
  d1   [label="Модель метаданных"];
  d2   [label="Тестовый проект (Page Object + тесты)"];
  d3   [label="Отчёты Surefire (XML)"];
  out  [label="Сводка и отчёты (HTML, CSV)"];

  node [shape=box, style="filled", fillcolor=white];
  p1 [label="1. Разбор XML (потоковый, StAX)"];
  p2 [label="2. Классификация сущностей"];
  p3 [label="3. Генерация тестового проекта"];
  p4 [label="4. Запуск тестов (Maven Surefire)"];
  p5 [label="5. Анализ результатов"];

  node [shape=note, style=filled, fillcolor=white];
  par [label="Параметры запуска:\\nадрес сайта,\\nучётные данные,\\nкаталог"];
  node [shape=cylinder, style=filled, fillcolor=white];
  db  [label="База отчётов\\n(SQLite)"];

  in1 -> p1 -> d1 -> p2 -> p3 -> d2 -> p4 -> d3 -> p5 -> out;
  par -> p3 [style=dashed, constraint=false];
  par -> p4 [style=dashed, constraint=false];
  p5  -> db [label="сохранение", fontsize=9, constraint=false];
  {{ rank=same; p3; par; }}
  {{ rank=same; p5; db; }}
}}
'''
render(pipeline, "ris_1_pipeline")

# ============ Рис. 2 — Структура XML-метамодели E3Core (дерево элементов) ============
xmltree = f'''
digraph XmlTree {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [shape=box, style="filled", fillcolor=white, fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [color=black, arrowhead=none];
  nodesep=0.30; ranksep=0.45;

  E3   [label="E3 (корень модели)"];
  Cat  [label="Category — подсистема"];
  Obj  [label="Object — сущность\\n(keyName, featureName/таблица)"];
  Assoc[label="AssociationObjectA — связь/ссылка\\n(role, addFromTree, flag_display)"];
  Props[label="Properties — группа свойств\\n(stereoType, type_link: карточка/грид)"];
  Prop [label="Property — атрибут\\n(attrName, attrType, necessarily,\\nmask, dmodule, stereoType)"];
  Oper [label="Operation — операция"];
  OpP  [label="OperationParam — параметр"];
  Mod  [label="Modifier — действие\\n(I, U, D, E, A)"];

  Srch [label="Searches — поиски"];
  Se   [label="Search — поиск\\n(searchObjectGUID)"];
  SQ   [label="SearchQuery — запрос"];
  SP   [label="SearchParam — параметр\\n(title, valueType, mask)"];
  SR   [label="SearchResult — результат"];
  SRP  [label="SearchResultProperty — колонка\\n(title, visible)"];

  E3 -> Cat; E3 -> Srch;
  Cat -> Obj;
  Obj -> Assoc; Obj -> Props;
  Props -> Prop; Props -> Oper;
  Oper -> OpP; Oper -> Mod;
  Srch -> Se;
  Se -> SQ; Se -> SP; Se -> SR;
  SR -> SRP;
}}
'''
render(xmltree, "ris_2_xml_structure")

# ============ Рис. 3 — Алгоритм потокового разбора XML (StAX) ============
stax = f'''
digraph Stax {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  ranksep=0.40; nodesep=0.45;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin   [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor=white];
  open  [label="Открыть поток XML"];
  read  [label="Прочитать следующее\\nсобытие (токен)"];
  cat   [label="Сохранить подсистему\\nи идентификатор"];
  obj   [label="Разобрать сущность\\n(делегировать парсеру Object)"];
  sea   [label="Разобрать блок поисков"];
  skip  [label="Перемотать до\\nзакрывающего тега"];
  ret   [label="Вернуть модель\\nметаданных"];

  node [shape=diamond, style=filled, fillcolor=white, height=0.9, width=1.9];
  dEnd  [label="Конец\\nдокумента?"];
  dStart[label="Начало\\nэлемента?"];
  dWhich[label="Какой\\nэлемент?"];

  start -> open -> read -> dEnd;
  dEnd -> ret [label="да"];
  ret  -> fin;
  dEnd -> dStart [label="нет"];
  dStart -> read [label="нет"];
  dStart -> dWhich [label="да"];
  dWhich -> cat  [label="Category"];
  dWhich -> obj  [label="Object"];
  dWhich -> sea  [label="Searches"];
  dWhich -> skip [label="иной"];
  cat  -> read;
  obj  -> read;
  sea  -> read;
  skip -> read;
}}
'''
render(stax, "ris_3_stax_parse")

# ============ Рис. 4 — Алгоритм классификации сущностей ============
classify = f'''
digraph Classify {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  ranksep=0.38; nodesep=0.40;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=diamond, style=filled, fillcolor=white, height=1.0, width=2.1];
  d1 [label="Совпадает с grid-\\nвкладкой родителя?"];
  d2 [label="Узел дерева\\n(addFromTree=1)\\nсо своим CRUD?"];
  d3 [label="featureName\\nначинается с V_S_?"];
  d4 [label="Все поиски без\\nпараметров (pick-one)?"];
  d5 [label="Только цель\\nFK-пикера?"];

  node [shape=box, style="filled", fillcolor=white];
  child1 [label="CHILD —\\nдочерняя (таб-грид)"];
  child2 [label="CHILD —\\nузел дерева"];
  ref    [label="REFERENCE_DICTIONARY —\\nсправочник"];
  prim   [label="PRIMARY —\\nосновная сущность"];

  start -> d1;
  d1 -> child1 [label="да"];
  d1 -> d2 [label="нет"];
  d2 -> child2 [label="да"];
  d2 -> d3 [label="нет"];
  d3 -> ref [label="да"];
  d3 -> d4 [label="нет"];
  d4 -> ref [label="да"];
  d4 -> d5 [label="нет"];
  d5 -> ref [label="да"];
  d5 -> prim [label="нет"];
}}
'''
render(classify, "ris_4_classification")

# ============ Рис. 5 — Паттерн Page Object ============
pageobj = f'''
digraph PageObject {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [shape=box, style="filled", fillcolor=white, fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  nodesep=0.5; ranksep=0.55;

  test [label="Тестовый класс (сценарий проверки):\\nналичие полей, валидация, создание,\\nизменение, удаление, поиск"];
  po   [label="Объект страницы (Page Object):\\nзаполнитьПоле(значение), нажатьДобавить(),\\nполеОтображается(имя), числоСтрокТаблицы()"];
  drv  [label="Selenium WebDriver"];
  br   [label="Браузер"];
  app  [label="Web-приложение E3Core\\n(карточка, грид, меню)"];

  test -> po  [label="вызывает методы"];
  po   -> drv [label="команды управления"];
  drv  -> br;
  br   -> app [label="HTTP"];

  note [shape=note, fillcolor=white,
        label="Локаторы элементов и детали\\nвзаимодействия инкапсулированы\\nв объекте страницы; тест\\nне обращается к странице напрямую"];
  po -> note [style=dashed, arrowhead=none, constraint=false];
  {{ rank=same; po; note; }}
}}
'''
render(pageobj, "ris_5_page_object")

# ============ Рис. 6 — Алгоритм генерации тестового проекта ============
gen = f'''
digraph Generate {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  ranksep=0.40; nodesep=0.45;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin   [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor=white];
  a1 [label="Создать структуру проекта\\n(файл сборки, конфигурация запуска)"];
  a2 [label="Сгенерировать инфраструктуру\\n(общий драйвер браузера,\\nбазовый класс, тестовые данные)"];
  a3 [label="Взять очередную сущность"];
  prim  [label="Создать объект страницы\\nи тестовый класс"];
  child [label="Создать тест\\nв составе родителя"];
  skip  [label="Пропустить\\n(справочник)"];
  rep   [label="Сохранить отчёт\\nо классификации"];

  node [shape=diamond, style=filled, fillcolor=white, height=0.95, width=1.9];
  dk [label="Тип\\nсущности?"];
  dm [label="Остались\\nсущности?"];

  start -> a1 -> a2 -> a3 -> dk;
  dk -> prim  [label="основная"];
  dk -> child [label="дочерняя"];
  dk -> skip  [label="справочник"];
  prim  -> dm;
  child -> dm;
  skip  -> dm;
  dm -> a3 [label="да  *для каждой сущности"];
  dm -> rep [label="нет"];
  rep -> fin;
}}
'''
render(gen, "ris_6_generation")

# ============ Рис. 7 — Алгоритм подбора тестовых данных по типу и маске ============
data = f'''
digraph TestData {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  ranksep=0.38; nodesep=0.40;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=diamond, style=filled, fillcolor=white, height=0.95, width=2.0];
  d1 [label="Справочник/ссылка\\n(Directory/Ref)?"];
  d2 [label="Задана\\nмаска?"];
  d3 [label="Маска похожа\\nна дату/время?"];
  d4 [label="Тип\\nзначения?"];

  node [shape=box, style="filled", fillcolor=white];
  pick [label="Значение не задаётся —\\nвыбор из выпадающего\\nсписка (пикер)"];
  bymask [label="Сформировать по маске\\n(цифры, буквы, разделители)"];
  dt   [label="Текущая дата / время"];
  s_str[label="Строка: «Test_<атрибут>»\\n(+ суффикс уникальности\\nдля обязательных)"];
  s_num[label="Число: случайное\\n100–999"];
  s_dat[label="Дата: сегодня"];
  s_dtm[label="Дата-время: now − 10 мин"];

  start -> d1;
  d1 -> pick [label="да"];
  d1 -> d2 [label="нет"];
  d2 -> d3 [label="да"];
  d3 -> dt [label="да"];
  d3 -> bymask [label="нет"];
  d2 -> d4 [label="нет"];
  d4 -> s_str [label="строка"];
  d4 -> s_num [label="число"];
  d4 -> s_dat [label="дата"];
  d4 -> s_dtm [label="дата-время"];
}}
'''
render(data, "ris_7_test_data")

# ============ Рис. 8 — Алгоритм запуска тестов и анализа результатов ============
run = f'''
digraph Run {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=vee];
  ranksep=0.40; nodesep=0.45;

  start [shape=circle, style=filled, fillcolor=black, label="", width=0.22];
  fin   [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.22];

  node [shape=box, style="rounded,filled", fillcolor=white];
  r1 [label="Запустить сборку\\n(фаза test, Surefire)"];
  r2 [label="Выполнить тесты\\nв браузере (Selenium)"];
  r3 [label="Сформировать XML-отчёты\\n(каталог surefire-reports)"];
  r4 [label="Разобрать отчёты:\\nстатусы, сообщения, время"];
  r5 [label="Связать скриншоты\\nс тест-кейсами"];
  r6 [label="Сформировать сводку\\n(всего / успешно / провалено)"];
  r7 [label="Сохранить в базе отчётов\\nи выгрузить HTML, CSV"];

  start -> r1 -> r2 -> r3 -> r4 -> r5 -> r6 -> r7 -> fin;
}}
'''
render(run, "ris_8_run_analyze")

print("Готово: 8 схем для раздела 1.3")
