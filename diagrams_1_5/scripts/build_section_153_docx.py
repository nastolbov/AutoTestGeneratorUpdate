# -*- coding: utf-8 -*-
"""Сборка раздела 1.5.3 (Раздел_1.5.3_исправленный.docx): модульная структура,
карта Константайна, диаграмма компонентов, таблицы связности и сцепления.
Стиль и нумерация — как в 1.5 (заголовки 14 без жирного, подпись таблицы в две
строки, рисунок по центру). Рисунки с №143, таблицы с №180."""
import os, sys
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import module_model as MM

DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ROOT = os.path.join(DIA, "..")
OUT = os.path.join(ROOT, "Раздел_1.5.3_исправленный.docx")
FONT = "Times New Roman"
USABLE_W = 16.5; MAX_H = 21.0
fig_no = 142; tbl_no = 179
TYPE_RU = {"app": "приложение", "lib": "библиотека (jar)", "ext": "внешний компонент",
           "file": "файл", "folder": "каталог", "db": "база данных"}

doc = Document()
st = doc.styles["Normal"]; st.font.name = FONT; st.font.size = Pt(14)
st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
st.paragraph_format.line_spacing = 1.5; st.paragraph_format.space_after = Pt(0)
sec = doc.sections[0]
sec.left_margin = Cm(3); sec.right_margin = Cm(1.5); sec.top_margin = Cm(2); sec.bottom_margin = Cm(2)


def _f(run, size=14, bold=False, italic=False):
    run.font.name = FONT; run.font.size = Pt(size); run.bold = bold; run.italic = italic
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)


def heading(text):
    p = doc.add_paragraph(); p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(6); p.paragraph_format.keep_with_next = True
    p.paragraph_format.line_spacing = 1.5
    _f(p.add_run(text), 14, bold=False); return p


def body(text):
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Cm(0.75); p.paragraph_format.line_spacing = 1.5
    _f(p.add_run(text), 14); return p


def gap(n):
    for _ in range(n):
        p = doc.add_paragraph(); p.paragraph_format.line_spacing = 1.5
        _f(p.add_run(""), 14)


def next_fig():
    global fig_no; fig_no += 1; return fig_no


def next_tbl():
    global tbl_no; tbl_no += 1; return tbl_no


def figure(fname, num, caption):
    w, h = Image.open(os.path.join(DIA, fname)).size
    disp = USABLE_W
    if (h / w) * USABLE_W > MAX_H:
        disp = MAX_H * w / h
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.line_spacing = 1.5; p.paragraph_format.space_before = Pt(6)
    p.add_run().add_picture(os.path.join(DIA, fname), width=Cm(disp))
    c = doc.add_paragraph(); c.alignment = WD_ALIGN_PARAGRAPH.CENTER; c.paragraph_format.line_spacing = 1.5
    _f(c.add_run(f"Рисунок {num} – {caption}"), 14)
    gap(1)


def _shade(cell, hexc):
    tcPr = cell._tc.get_or_add_tcPr(); sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear"); sh.set(qn("w:color"), "auto"); sh.set(qn("w:fill"), hexc); tcPr.append(sh)


def _ct(cell, text):
    cell.text = ""; p = cell.paragraphs[0]; p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.line_spacing = 1.0; p.paragraph_format.space_after = Pt(0)
    _f(p.add_run(text), 12)


def table(num, caption, headers, rows, widths):
    p1 = doc.add_paragraph(); p1.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p1.paragraph_format.line_spacing = 1.0; p1.paragraph_format.space_before = Pt(6)
    _f(p1.add_run(f"Таблица {num}"), 14)
    p2 = doc.add_paragraph(); p2.alignment = WD_ALIGN_PARAGRAPH.CENTER; p2.paragraph_format.line_spacing = 1.0
    _f(p2.add_run(caption), 14)
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.style = "Table Grid"; t.alignment = WD_TABLE_ALIGNMENT.LEFT; t.autofit = False
    el = OxmlElement("w:tblLayout"); el.set(qn("w:type"), "fixed"); t._tbl.tblPr.append(el)
    for j, h in enumerate(headers):
        _ct(t.rows[0].cells[j], h); _shade(t.rows[0].cells[j], "D9D9D9")
    for i, row in enumerate(rows, 1):
        for j, v in enumerate(row):
            _ct(t.rows[i].cells[j], v)
    for i in range(len(rows) + 1):
        for j, wd in enumerate(widths):
            t.rows[i].cells[j].width = Cm(wd)
    gap(2)


# ============================================================ содержимое
n_lib = sum(1 for m in MM.MODULES if m[3] == "library")
n_func = len(MM.MODULES) - n_lib
heading("1.5.3 Построение диаграммы компонентов и модульной структуры")
body("Информационная система AutoTestGenerator построена по модульному принципу. Под модулем понимается "
     f"функциональный класс, выполняющий законченную задачу; всего система содержит {len(MM.MODULES)} модуля, "
     f"распределённых по шести пакетам (ui, parser, model, generator, data, common). Из них {n_func} — "
     f"функциональные модули и {n_lib} — повторно используемые библиотеки (вспомогательные модули без "
     "собственного управления). Классы-данные пакета model не являются модулями и рассматриваются как "
     "область данных «модель метаданных».")

# модульная структура
f1 = next_fig()
body(f"Модульная структура системы (дерево вызовов и использования модулей) приведена на рисунке {f1}. "
     f"На схеме показаны все модули системы ({len(MM.MODULES)}), включая {n_lib} повторно используемые "
     "библиотеки (StaxUtils, XmlNamespaces, DatabaseConnection, JavaFileWriter, Transliterator), которые "
     "изображены так же, как функциональные модули, — обычными прямоугольниками. Различие функциональных "
     "модулей и библиотек отражено в таблице спецификации (в описании библиотек дана пометка «Библиотека …») "
     "и на структурной карте Константайна, где обращение к библиотекам показано двойной рамкой и пунктиром "
     "(common coupling).")
figure("mod_struct.png", f1, "Модульная структура информационной системы")
t1 = next_tbl()
body(f"Краткая спецификация модулей системы (включая повторно используемые библиотеки) приведена в таблице {t1}.")
table(t1, "Краткая спецификация модулей системы",
      ["Наименование модуля", "Входные данные", "Выходные данные", "Описание"],
      [[m[1], m[4], m[5], m[6]] for m in MM.MODULES], [3.3, 3.7, 3.7, 5.8])

# карта Константайна
f2 = next_fig()
body(f"Для анализа модульной структуры и оценки взаимодействия модулей построена структурная карта "
     f"Константайна (рисунок {f2}). Все вызовы и обращения к библиотекам показаны сплошными стрелками. "
     "У каждой связи подписаны куплеты — "
     "передаваемые данные и управляющие признаки; направление обмена обозначено стрелкой (↓ — передача в "
     "вызываемый модуль, ↑ — возврат вызывающему), а вид связи — кружком: ○ — связь по данным, ● — связь по "
     "управлению. Модули изображены прямоугольником, библиотеки — двойной рамкой, области данных — овалом.")
figure("constantine.png", f2, "Структурная карта Константайна информационной системы")

# связность
t2 = next_tbl()
body(f"Качество модульной структуры оценивается показателями связности и сцепления. Связность модуля — мера "
     f"зависимости его частей (чем выше, тем лучше). Оценка связности модулей приведена в таблице {t2}.")
table(t2, "Связность модулей", ["Модуль", "Тип связности", "Обоснование"],
      [[m[1], MM.COHESION[m[0]][0], MM.COHESION[m[0]][1]] for m in MM.MODULES], [4.0, 3.2, 9.3])

# сцепление
t3 = next_tbl()
body(f"Сцепление определяет уровень зависимости между модулями; наиболее рациональным считается сцепление по "
     f"данным. Оценка сцепления по ключевым связям приведена в таблице {t3}.")
table(t3, "Сцепление модулей", ["Модуль-источник", "Модуль-приёмник", "Тип сцепления", "Передаваемые данные"],
      [[s, d, typ, dat] for s, d, typ, dat in MM.COUPLING], [3.6, 3.6, 3.3, 6.0])
body("Преобладает сцепление по данным и по образцу, что соответствует рекомендациям структурного "
     "проектирования; сцепление по управлению используется минимально — при возврате признака успешности "
     "разбора метамодели (XmlModelParser) и вида классифицируемой сущности (EntityClassifier). Обращения к "
     "повторно используемым библиотекам — сцепление по данным (передаются параметры и результат, без общего "
     "изменяемого состояния).")

# диаграмма компонентов
f3 = next_fig()
body(f"Диаграмма компонентов (рисунок {f3}) отражает физическую структуру системы: основное приложение "
     "autotestgenerator.jar, используемые библиотеки и внешние компоненты, а также файлы и хранилища данных. "
     "Зависимости показаны пунктирными стрелками.")
figure("components.png", f3, "Диаграмма компонентов")
t4 = next_tbl()
body(f"Описание компонентов системы приведено в таблице {t4}.")
table(t4, "Описание компонентов системы", ["Компонент", "Тип", "Версия", "Назначение"],
      [[c[1], TYPE_RU[c[2]], c[3], c[4]] for c in MM.COMPONENTS], [3.8, 3.2, 1.8, 7.7])

doc.save(OUT)
print("Сохранено:", OUT)
print(f"Рисунков: {fig_no - 142} (143..{fig_no}) | Таблиц: {tbl_no - 179} (180..{tbl_no})")
