# -*- coding: utf-8 -*-
"""Схемы для раздела 1.3, выполненные по ГОСТ 19.701-90 (ИСО 5807-85)
«Схемы алгоритмов, программ, данных и систем».

Применяемые условные обозначения:
  • терминатор (овал) — начало/конец схемы («Начало», «Конец»);
  • процесс — прямоугольник;
  • решение — ромб;
  • предопределённый процесс (подпрограмма) — прямоугольник с двумя
    вертикальными линиями по краям;
  • данные (ввод-вывод) — параллелограмм;
  • линии потока — сверху вниз и слева направо, со стрелками.

Все схемы чёрно-белые. Содержание выверено по коду программы (ru.autotestgen.*)."""
import subprocess, os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
FONT = "DejaVu Sans"
MONO_TTF = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def render(dot, name):
    path = f"/tmp/{name}.dot"
    open(path, "w").write(dot)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", path, "-o", f"{OUT}/{name}.png"], check=True)
    print("OK", name)


def predef(nid, text):
    """Узел «предопределённый процесс» (подпрограмма) по ГОСТ — прямоугольник
    с двумя вертикальными линиями по краям (через HTML-таблицу graphviz)."""
    html = text.replace("\\n", "<BR/>")
    return (f'{nid} [shape=none, margin=0, label=<'
            f'<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="8">'
            f'<TR><TD WIDTH="7" HEIGHT="1" BORDER="1" SIDES="R"></TD>'
            f'<TD>{html}</TD>'
            f'<TD WIDTH="7" BORDER="1" SIDES="L"></TD></TR></TABLE>>];')


# Общие стили узлов по ГОСТ (вставляются в начало каждого графа)
GOST_HEAD = f'''
  bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=normal];
'''
TERM = 'node [shape=ellipse, style=filled, fillcolor=white, width=1.3, height=0.5];'   # терминатор
PROC = 'node [shape=box, style=filled, fillcolor=white];'                              # процесс
DEC  = 'node [shape=diamond, style=filled, fillcolor=white, height=1.0, width=2.0];'   # решение
DATA = 'node [shape=parallelogram, style=filled, fillcolor=white];'                    # данные

# Узлы «предопределённый процесс» (вынесены в переменные: f-строка Python не допускает
# обратный слэш внутри выражений {...}).
SUB_OBJ   = predef("obj",   "Разобрать сущность")
SUB_SEA   = predef("sea",   "Разобрать блок поисков")
SUB_PRIM  = predef("prim",  "Создать объект страницы\\nи тестовый класс")
SUB_CHILD = predef("child", "Создать тест\\nв составе родителя")

# ============ Рис. 1 — Схема процесса обработки информации (конвейер) ============
pipeline = f'''
digraph Pipeline {{
  rankdir=TB; {GOST_HEAD}
  nodesep=0.45; ranksep=0.40;

  {DATA}
  in1  [label="XML-модель метаданных E3Core"];
  d1   [label="Модель метаданных"];
  d2   [label="Тестовый проект (Page Object + тесты)"];
  d3   [label="Отчёты Surefire (XML)"];
  out  [label="Сводка и отчёты (HTML, CSV)"];
  par  [label="Параметры запуска:\\nадрес сайта, учётные\\nданные, каталог"];

  {PROC}
  p1 [label="1. Разбор XML (потоковый, StAX)"];
  p2 [label="2. Классификация сущностей"];
  p3 [label="3. Генерация тестового проекта"];
  p4 [label="4. Запуск тестов (Maven Surefire)"];
  p5 [label="5. Анализ результатов"];

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

# ============ Рис. 4 — Алгоритм потокового разбора XML (StAX), ГОСТ 19.701-90 ============
stax = f'''
digraph Stax {{
  rankdir=TB; {GOST_HEAD}
  ranksep=0.40; nodesep=0.45;

  {TERM}
  beg [label="Начало"];
  end [label="Конец"];

  {DATA}
  open  [label="Открыть поток XML"];
  ret   [label="Вернуть модель\\nметаданных"];

  {PROC}
  read  [label="Прочитать следующее\\nсобытие (токен)"];
  cat   [label="Сохранить подсистему\\nи идентификатор"];
  skip  [label="Перемотать до\\nзакрывающего тега"];

  {SUB_OBJ}
  {SUB_SEA}

  {DEC}
  dEnd  [label="Конец\\nдокумента?"];
  dStart[label="Начало\\nэлемента?"];
  dWhich[label="Какой\\nэлемент?"];

  beg -> open -> read -> dEnd;
  dEnd -> ret [label="да"];
  ret  -> end;
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
render(stax, "ris_4_stax_parse")

# ============ Рис. 5 — Алгоритм классификации сущностей, ГОСТ 19.701-90 ============
classify = f'''
digraph Classify {{
  rankdir=TB; {GOST_HEAD}
  ranksep=0.38; nodesep=0.40;

  {TERM}
  beg [label="Начало"];
  end [label="Конец"];

  {DEC}
  d1 [label="Совпадает с grid-\\nвкладкой родителя?"];
  d2 [label="Узел дерева\\n(addFromTree=1)\\nсо своим CRUD?"];
  d3 [label="featureName\\nначинается с V_S_?"];
  d4 [label="Все поиски без\\nпараметров (pick-one)?"];
  d5 [label="Только цель\\nFK-пикера?"];

  {PROC}
  child1 [label="CHILD —\\nдочерняя (вкладка-грид)"];
  child2 [label="CHILD —\\nузел дерева"];
  ref    [label="REFERENCE_DICTIONARY —\\nсправочник"];
  prim   [label="PRIMARY —\\nосновная сущность"];

  beg -> d1;
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
  child1 -> end; child2 -> end; ref -> end; prim -> end;
}}
'''
render(classify, "ris_5_classification")

# ============ Рис. 6 — Паттерн Page Object (схема взаимодействия) ============
pageobj = f'''
digraph PageObject {{
  rankdir=TB; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [shape=box, style="filled", fillcolor=white, fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=10, color=black, fontcolor=black, arrowhead=normal];
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
render(pageobj, "ris_6_page_object")

# ============ Рис. 7 — Алгоритм генерации тестового проекта, ГОСТ 19.701-90 ============
gen = f'''
digraph Generate {{
  rankdir=TB; {GOST_HEAD}
  ranksep=0.40; nodesep=0.45;

  {TERM}
  beg [label="Начало"];
  end [label="Конец"];

  {PROC}
  a1 [label="Создать структуру проекта\\n(файл сборки, конфигурация запуска)"];
  a2 [label="Сгенерировать инфраструктуру\\n(общий драйвер браузера,\\nбазовый класс, тестовые данные)"];
  a3 [label="Взять очередную сущность"];
  skip  [label="Пропустить (справочник)"];
  rep   [label="Сохранить отчёт\\nо классификации"];

  {SUB_PRIM}
  {SUB_CHILD}

  {DEC}
  dk [label="Тип\\nсущности?"];
  dm [label="Остались\\nсущности?"];

  beg -> a1 -> a2 -> a3 -> dk;
  dk -> prim  [label="основная"];
  dk -> child [label="дочерняя"];
  dk -> skip  [label="справочник"];
  prim  -> dm;
  child -> dm;
  skip  -> dm;
  dm -> a3 [label="да  *для каждой сущности"];
  dm -> rep [label="нет"];
  rep -> end;
}}
'''
render(gen, "ris_7_generation")

# ============ Рис. 8 — Алгоритм подбора тестовых данных, ГОСТ 19.701-90 (компактный) ============
data = f'''
digraph TestData {{
  rankdir=TB; {GOST_HEAD}
  ranksep=0.42; nodesep=0.45;

  {TERM}
  beg [label="Начало"];
  end [label="Конец"];

  {DATA}
  inp    [label="Свойство (атрибут)"];
  pick   [label="Выбор из выпадающего\\nсписка (значение не задаётся)"];
  dt     [label="Текущая дата / время"];
  byType [label="Значение по типу: строка /\\nчисло / дата / дата-время\\n(см. таблицу правил)"];

  {PROC}
  bymask [label="Сформировать по маске\\n(цифры, буквы, разделители)"];

  {DEC}
  d1 [label="Справочник/ссылка\\n(Directory/Ref)?"];
  d2 [label="Задана\\nмаска?"];
  d3 [label="Маска похожа\\nна дату/время?"];

  beg -> inp -> d1;
  d1 -> pick   [label="да"];
  d1 -> d2     [label="нет"];
  d2 -> d3     [label="да"];
  d3 -> dt     [label="да"];
  d3 -> bymask [label="нет"];
  d2 -> byType [label="нет"];
  pick -> end; dt -> end; bymask -> end; byType -> end;
}}
'''
render(data, "ris_8_test_data")

# ============ Рис. 9 — Алгоритм запуска тестов и анализа результатов, ГОСТ 19.701-90 ============
run = f'''
digraph Run {{
  rankdir=TB; {GOST_HEAD}
  ranksep=0.40; nodesep=0.45;

  {TERM}
  beg [label="Начало"];
  end [label="Конец"];

  {PROC}
  r1 [label="Запустить сборку\\n(фаза test, Surefire)"];
  r2 [label="Выполнить тесты\\nв браузере (Selenium)"];
  r4 [label="Разобрать отчёты:\\nстатусы, сообщения, время"];
  r5 [label="Связать скриншоты\\nс тест-кейсами"];
  r6 [label="Сформировать сводку\\n(всего / успешно / провалено)"];

  {DATA}
  r3 [label="XML-отчёты Surefire\\n(каталог surefire-reports)"];
  r7 [label="Сводка, отчёты HTML, CSV"];

  node [shape=cylinder, style=filled, fillcolor=white];
  db [label="База отчётов"];

  beg -> r1 -> r2 -> r3 -> r4 -> r5 -> r6 -> r7 -> end;
  r6 -> db [label="сохранение", fontsize=9, constraint=false];
}}
'''
render(run, "ris_9_run_analyze")

# ============ Рис. 3 — Фрагмент входной XML-модели (оформлен как рисунок) ============
def render_xml_fragment(lines, name, size=22, pad=20):
    font = ImageFont.truetype(MONO_TTF, size)
    asc, desc = font.getmetrics()
    lh = asc + desc + 4
    tmp = Image.new("RGB", (10, 10))
    dd = ImageDraw.Draw(tmp)
    maxw = max(dd.textlength(ln, font=font) for ln in lines)
    W = int(maxw) + 2 * pad
    H = lh * len(lines) + 2 * pad
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, W - 2, H - 2], outline=(120, 120, 120), width=2)
    y = pad
    for ln in lines:
        d.text((pad, y), ln, font=font, fill=(0, 0, 0))
        y += lh
    img.save(f"{OUT}/{name}.png")
    print("OK", name, f"{W}x{H}")

xml_lines = [
    '<e3:Category CategoryName="...::Гаражно-строительные кооперативы" GUID="5EA196000083">',
    ' <e:Object name="ГСК/ОГСК" keyName="KEY_GB_SOCIETY" featureName="V_GSK_GB_SOCIETY">',
    '  <e:Properties stereoType="" name="Сведения ГСК/ОГСК" type_link="P">   <!-- карточка -->',
    '   <e:Property name="Наименование ГСК/ОГСК" attrName="GBS_NAME"',
    '             attrType="string" necessarily="1" flag_display="1" mask=""/>',
    '   <e:Property name="Тип ГСК/ОГСК" attrName="KEY_TYPE_SOCIETY" stereoType="Directory"',
    '             dmodule="dropDownObjectList" attrType="decimal" necessarily="1"/>',
    '   <e:Property name="НДЗ" attrName="DATE_BEGIN" attrType="date"',
    '             necessarily="1" mask="99.99.9999"/>',
    '   <e3:Operation operationMethod="SP_GB_SOCIETY">',
    '     <e3:Modifier title="Добавить"            modifyType="I"/>',
    '     <e3:Modifier title="Сохранить Изменения" modifyType="U"/>',
    '     <e3:Modifier title="Удалить"             modifyType="D"/>',
    '   </e3:Operation>',
    '  </e:Properties>',
    '  <e:Properties stereoType="Grid" name="История ГСК/ОГСК"> ... </e:Properties>',
    ' </e:Object>',
    '</e3:Category>',
    '<e3:Searches>',
    ' <e3:Search name="по параметрам" searchObjectGUID="5EB3B333010E">',
    '   <e3:SearchParam name="LAST_NAME" title="Фамилия" valueType="string"/>',
    '   <e3:SearchParam name="DATE_BEGIN_OT" title="НДЗ от" valueType="date" mask="99.99.9999"/>',
    '   <e3:SearchResult>',
    '     <e3:SearchResultProperty name="SearchName" title="Наименование" visible="true"/>',
    '   </e3:SearchResult>',
    ' </e3:Search>',
    '</e3:Searches>',
]
render_xml_fragment(xml_lines, "ris_3_xml_fragment")

# ============ Трёхпанельный браузер объектов E3Core (схема рабочего пространства) ============
browser = f'''
digraph Browser {{
  bgcolor=white; fontname="{FONT}"; node [fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  br [shape=none, margin=0, label=<
    <TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" CELLPADDING="10">
      <TR><TD COLSPAN="2" BGCOLOR="#EEEEEE">Главное меню подсистемы (НСИ, пункты ППС, Сервис) и панель инструментов</TD></TR>
      <TR>
        <TD WIDTH="230" HEIGHT="180" VALIGN="middle">Дерево объектов<BR/>(загруженные объекты<BR/>и их связи)</TD>
        <TD WIDTH="330" HEIGHT="90" VALIGN="middle">Список групп свойств<BR/>выбранного объекта</TD>
      </TR>
      <TR>
        <TD WIDTH="230" VALIGN="middle">Окно поиска:<BR/>дерево поисков →<BR/>параметры → результаты</TD>
        <TD WIDTH="330" HEIGHT="110" VALIGN="middle">Представление группы свойств:<BR/>карточка (атрибут/значение),<BR/>грид (таблица), отчёты</TD>
      </TR>
    </TABLE>>];
}}
'''
render(browser, "ris_browser")

# ============ Доменная модель сущностей АИС ГСК ============
domain = f'''
digraph Domain {{
  rankdir=LR; bgcolor=white; fontname="{FONT}"; fontsize=12;
  node [shape=box, style=filled, fillcolor=white, fontname="{FONT}", fontsize=11, color=black, fontcolor=black];
  edge [fontname="{FONT}", fontsize=9, color=black, fontcolor=black];
  nodesep=0.25; ranksep=1.1;

  GSK  [label="ГСК/ОГСК", penwidth=2];
  CONF [label="Совещание", penwidth=2];
  HIST [label="История ГСК/ОГСК"];
  DOCG [label="Документ ГСК/ОГСК"];
  INV  [label="Приглашённый ГСК"];
  PART [label="Участник совещания"];
  AGEN [label="Повестка совещания"];
  DOCC [label="Документ совещания"];

  node [shape=note];
  OFF  [label="Должностное лицо"];
  TYPE [label="Тип ГСК/ОГСК"];
  DIST [label="Район"];
  CAUSE[label="Причина смены"];

  // композиция (вкладка-грид / узел дерева) — сплошная линия
  edge [arrowhead=vee, style=solid];
  GSK -> HIST; GSK -> DOCG;
  CONF -> INV; CONF -> PART; CONF -> AGEN; CONF -> DOCC;

  // ссылки на справочники (FK) — пунктир
  edge [arrowhead=open, style=dashed];
  GSK -> TYPE; GSK -> DIST; GSK -> CAUSE; GSK -> OFF;
  CONF -> OFF;
}}
'''
render(domain, "ris_domain")

print("Готово: схемы 1.3 по ГОСТ 19.701-90 + фрагмент XML + браузер E3Core + домен АИС ГСК")
