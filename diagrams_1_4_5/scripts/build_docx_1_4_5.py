# -*- coding: utf-8 -*-
"""Фрагмент раздела 1.4.5 — ЛОГИЧЕСКАЯ модель базы данных (для вставки перед
описанием физической модели). По реальной схеме SQLite программы.
Форматирование: Times New Roman 14, межстрочный 1,5, красная строка 0,75;
рисунок и подпись по центру; таблицы — 12 пт, межстрочный 1, по левому краю."""
import os
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from PIL import Image

DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(DIA, "..", "Раздел_1.4.5_логическая_модель.docx")
FONT = "Times New Roman"

doc = Document()
st = doc.styles["Normal"]
st.font.name = FONT; st.font.size = Pt(14)
st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
st.paragraph_format.line_spacing = 1.5; st.paragraph_format.space_after = Pt(0)
sec = doc.sections[0]
sec.left_margin = Cm(3); sec.right_margin = Cm(1.5); sec.top_margin = Cm(2); sec.bottom_margin = Cm(2)
tbl_no = 33  # таблицы логической модели идут перед физическими (34, 35)


def _f(run, size=14, bold=False):
    run.font.name = FONT; run.font.size = Pt(size); run.bold = bold
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)


def body(text):
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Cm(0.75)
    _f(p.add_run(text), 14); return p


def lead(title, text):
    """Абзац с полужирным зачином (имя сущности) и обычным продолжением."""
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Cm(0.75)
    _f(p.add_run(title), 14, bold=True); _f(p.add_run(text), 14); return p


def figure(fname, caption, width_cm):
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6); p.paragraph_format.keep_together = True
    p.add_run().add_picture(os.path.join(DIA, fname), width=Cm(width_cm))
    cap = doc.add_paragraph(); cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_after = Pt(10)
    _f(cap.add_run(caption), 14)


def _shade(cell, hexc):
    tcPr = cell._tc.get_or_add_tcPr(); sh = OxmlElement("w:shd")
    sh.set(qn("w:val"), "clear"); sh.set(qn("w:color"), "auto"); sh.set(qn("w:fill"), hexc); tcPr.append(sh)


def _ct(cell, text, bold=False, size=12):
    cell.text = ""; p = cell.paragraphs[0]; p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.line_spacing = 1.0; p.paragraph_format.space_after = Pt(0)
    _f(p.add_run(text), size, bold=bold)


def table_caption(title):
    global tbl_no; tbl_no += 1
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.RIGHT; p.paragraph_format.space_before = Pt(8)
    _f(p.add_run(f"Таблица {tbl_no}"), 14)
    p2 = doc.add_paragraph(); p2.alignment = WD_ALIGN_PARAGRAPH.CENTER; p2.paragraph_format.space_after = Pt(2)
    _f(p2.add_run(title), 14)


def grid(headers, rows, widths):
    t = doc.add_table(rows=1 + len(rows), cols=len(headers)); t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for j, h in enumerate(headers):
        _ct(t.rows[0].cells[j], h, bold=True); _shade(t.rows[0].cells[j], "D9D9D9")
    for i, row in enumerate(rows, start=1):
        for j, v in enumerate(row):
            _ct(t.rows[i].cells[j], v)
    for i in range(len(rows) + 1):
        for j, wd in enumerate(widths):
            t.rows[i].cells[j].width = Cm(wd)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# ==================== Логическая модель (вставка в 1.4.5) ====================
body("Проектирование структур данных выполняется на двух уровнях. На логическом уровне модель "
     "описывает информационные сущности предметной области, их атрибуты, ключи и связи между "
     "сущностями без привязки к конкретной СУБД — то есть без указания физических типов данных, "
     "индексов и иных особенностей реализации хранилища. На физическом уровне логическая модель "
     "преобразуется в конкретные таблицы выбранной СУБД (SQLite) с фактическими типами столбцов, "
     "первичными и внешними ключами и ограничениями (см. далее).")
body("Логическая модель базы данных приведена на рисунке 10. В ней выделены две сущности: «Прогон "
     "тестов» — сведения об отдельном запуске набора автотестов, и «Результат теста» — результат "
     "выполнения отдельного тест-кейса в рамках прогона. Сущности связаны отношением «один ко "
     "многим» (1:N): один прогон содержит множество результатов тестов, и каждый результат "
     "принадлежит ровно одному прогону. Связь идентифицирующая и реализуется внешним ключом «ИД "
     "прогона» в сущности «Результат теста», ссылающимся на первичный ключ сущности «Прогон тестов».")
figure("ris_logical_db.png", "Рис. 10. Логическая модель базы данных", 15.5)
body("На логическом уровне для каждой сущности фиксируется состав атрибутов с указанием логического "
     "типа (целочисленный, строковый, дата-время, логический) — без привязки к конкретным типам "
     "SQLite, которые задаются в физической модели. Детальные спецификации с физическими типами "
     "приведены далее в таблицах 34 и 35.")
lead("«Прогон тестов»", " — идентификатор прогона (первичный ключ, целочисленный), дата и время "
     "прогона (дата-время), файл метамодели (строковый), адрес тестируемого сайта (строковый), а "
     "также итоговые показатели: всего тестов, успешно, провалено, пропущено (целочисленные) и "
     "длительность прогона в миллисекундах (целочисленный).")
lead("«Результат теста»", " — идентификатор результата (первичный ключ, целочисленный), "
     "идентификатор прогона (внешний ключ на сущность «Прогон тестов», целочисленный), класс теста и "
     "метод теста (строковые), признак успешности (логический), сообщение об ошибке (строковый, "
     "может отсутствовать) и длительность выполнения в миллисекундах (целочисленный).")
body("Таким образом, логическая модель отвечает на вопрос «какие данные и в каких связях хранятся», "
     "а физическая модель (рисунок 11, таблицы 34–35) — «как именно эти данные реализованы в "
     "выбранной СУБД».")

doc.save(OUT)
print("Сохранено:", OUT)
