#!/usr/bin/env python3
# Приложение 2 «Текст программы» (стиль РПЗ Столбова).
# Код вставляется как ОБЫЧНЫЙ ТЕКСТ непрерывным потоком в ДВЕ КОЛОНКИ (колоночные секции),
# без принудительных разрывов и пустых мест. Над/под кодом — подпись, что это рисунок:
#   вступление «На рис. П2.N представлен текст класса X.» и подпись «Рис. П2.N. Текст класса X».
# Вступление/подпись — во всю ширину (14pt, интервал 1.5). Код — Times New Roman 8pt, инт. ровно 9pt.
import os
from docx import Document
from docx.shared import Pt, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

ROOT = "/home/user/AutoTestGeneratorUpdate"
OUT = os.path.join(ROOT, "docs", "Приложение_2_Текст_программы.docx")

PACKAGES = ["ui", "parser", "model", "generator", "data", "common"]
JAVA_BASE = os.path.join(ROOT, "src/main/java/ru/autotestgen")
FXML = os.path.join(ROOT, "src/main/resources/fxml/main.fxml")

WRAP_WIDTH = 56
CODE_LH_PT = 9
RED_LINE = Emu(269875)

PAGE_W, PAGE_H = Emu(7560310), Emu(10692130)
M_L, M_R, M_T, M_B = Emu(1080135), Emu(540385), Emu(720090), Emu(720090)

def files_for(pkg):
    d = os.path.join(JAVA_BASE, pkg)
    out = [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith(".java")]
    if pkg == "ui":
        out.append(FXML)
    return out

all_files = []
for pkg in PACKAGES:
    all_files.extend(files_for(pkg))

def wrap_code(line):
    line = line.replace("\t", "    ").rstrip("\n")
    if len(line) <= WRAP_WIDTH:
        return [line]
    indent = len(line) - len(line.lstrip(" "))
    cont = " " * min(indent + 4, 8)
    out, s, first = [], line, True
    while len(s) > WRAP_WIDTH:
        lo = (indent if first else len(cont)) + 1
        bp = s.rfind(" ", lo, WRAP_WIDTH + 1)
        if bp < lo or bp - len(cont) < 10:
            bp = WRAP_WIDTH
        out.append(s[:bp])
        s = cont + s[bp:].lstrip(" ")
        first = False
    out.append(s)
    return out

def read_code_lines(path):
    with open(path, encoding="utf-8") as f:
        raw = f.read().split("\n")
    if raw and raw[-1] == "":
        raw = raw[:-1]
    lines = []
    for ln in raw:
        lines.extend(wrap_code(ln))
    return lines

def kind_word(fname):
    return "файла" if fname.endswith(".fxml") else "класса"

# ====================== ДОКУМЕНТ ======================
doc = Document()

def setup_section(sec, cols):
    sec.page_width, sec.page_height = PAGE_W, PAGE_H
    sec.left_margin, sec.right_margin = M_L, M_R
    sec.top_margin, sec.bottom_margin = M_T, M_B
    sectPr = sec._sectPr
    for c in sectPr.findall(qn("w:cols")):
        sectPr.remove(c)
    el = OxmlElement("w:cols")
    el.set(qn("w:num"), str(cols))
    el.set(qn("w:space"), "400")
    pgMar = sectPr.find(qn("w:pgMar"))
    (pgMar.addnext(el) if pgMar is not None else sectPr.append(el))

setup_section(doc.sections[0], 1)

st = doc.styles["Normal"]
st.font.name = "Times New Roman"; st.font.size = Pt(12)
st.paragraph_format.line_spacing = 1.0; st.paragraph_format.space_after = Pt(0)

def big_par(text, align, indent=False):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    pf.space_after = Pt(0)
    if indent:
        pf.first_line_indent = RED_LINE
    p.alignment = align
    r = p.add_run(text); r.font.name = "Times New Roman"; r.font.size = Pt(14)
    return p

def enter_14():
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    p.paragraph_format.space_after = Pt(0)
    r = p.add_run(""); r.font.name = "Times New Roman"; r.font.size = Pt(14)

def code_line(text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.EXACTLY; pf.line_spacing = Pt(CODE_LH_PT)
    pf.space_after = Pt(0); pf.space_before = Pt(0)
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = p.add_run(text if text != "" else " ")
    r.font.name = "Times New Roman"; r.font.size = Pt(8)
    rpr = r._element.get_or_add_rPr(); rf = rpr.get_or_add_rFonts()
    rf.set(qn("w:ascii"), "Times New Roman"); rf.set(qn("w:hAnsi"), "Times New Roman")

# Заголовок приложения (1 колонка)
big_par("Приложение 2", WD_ALIGN_PARAGRAPH.RIGHT)
big_par("Текст программы", WD_ALIGN_PARAGRAPH.CENTER, indent=True)

for idx, path in enumerate(all_files, start=1):
    fname = os.path.basename(path)
    num = "П2.%d" % idx
    lines = read_code_lines(path)
    # вступление (1 колонка, во всю ширину)
    big_par("На рис. %s представлен текст %s %s." % (num, kind_word(fname), fname),
            WD_ALIGN_PARAGRAPH.JUSTIFY, indent=True)
    # переход в 2 колонки для кода
    setup_section(doc.add_section(WD_SECTION.CONTINUOUS), 2)
    for ln in lines:
        code_line(ln)
    # назад в 1 колонку для подписи
    setup_section(doc.add_section(WD_SECTION.CONTINUOUS), 1)
    big_par("Рис. %s. Текст %s %s" % (num, kind_word(fname), fname), WD_ALIGN_PARAGRAPH.CENTER)
    enter_14()

doc.save(OUT)
print("Сохранено:", OUT, "| файлов:", len(all_files))
