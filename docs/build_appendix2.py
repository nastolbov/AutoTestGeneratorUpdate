#!/usr/bin/env python3
# Приложение 2 «Текст программы» для ВКР: весь исходный код в стиле РПЗ Столбова.
# Код = Times New Roman 8pt, одинарный; вступление/подпись = 14pt, интервал 1.5, красная строка.
# Многостраничные листинги: на каждой новой странице сверху справа «Продолжение рис. П2.N».
import os, textwrap
from docx import Document
from docx.shared import Pt, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn

ROOT = "/home/user/AutoTestGeneratorUpdate"
OUT = os.path.join(ROOT, "docs", "Приложение_2_Текст_программы.docx")

PACKAGES = ["ui", "parser", "model", "generator", "data", "common"]
JAVA_BASE = os.path.join(ROOT, "src/main/java/ru/autotestgen")
FXML = os.path.join(ROOT, "src/main/resources/fxml/main.fxml")

# --- параметры верстки (как в РПЗ) ---
WRAP_WIDTH       = 108   # макс. символов в строке кода (перенос длинных строк)
FIRST_PAGE_LINES = 64    # строк кода на первой странице листинга (под вступление)
CONT_PAGE_LINES  = 68    # строк кода на странице-продолжении (под «Продолжение рис.»)
RED_LINE = Emu(269875)   # красная строка как в РПЗ (вступление/подпись)

def files_for(pkg):
    d = os.path.join(JAVA_BASE, pkg)
    fs = sorted(f for f in os.listdir(d) if f.endswith(".java"))
    out = [os.path.join(d, f) for f in fs]
    if pkg == "ui":
        out.append(FXML)
    return out

all_files = []
for pkg in PACKAGES:
    all_files.extend(files_for(pkg))

def wrap_code(line):
    """Корректный перенос длинной строки кода с сохранением отступа.
    Гарантирует продвижение вперёд (нет зацикливания на длинных токенах без пробелов)."""
    line = line.replace("\t", "    ").rstrip("\n")
    if len(line) <= WRAP_WIDTH:
        return [line]
    indent = len(line) - len(line.lstrip(" "))
    cont = " " * min(indent + 4, 12)   # ограничиваем отступ продолжения
    out, s, first = [], line, True
    while len(s) > WRAP_WIDTH:
        lo = (indent if first else len(cont)) + 1
        bp = s.rfind(" ", lo, WRAP_WIDTH + 1)
        if bp < lo or bp - len(cont) < 16:   # нет удобного пробела или мало пользы — жёсткий разрез
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

def split_blocks(lines):
    """Бьёт строки на страничные блоки: первый меньше (под вступление)."""
    blocks, i = [], 0
    blocks.append(lines[i:i + FIRST_PAGE_LINES]); i += FIRST_PAGE_LINES
    while i < len(lines):
        blocks.append(lines[i:i + CONT_PAGE_LINES]); i += CONT_PAGE_LINES
    return [b for b in blocks if b]

# ====================== ДОКУМЕНТ ======================
doc = Document()
st = doc.styles["Normal"]
st.font.name = "Times New Roman"
st.font.size = Pt(12)
st.paragraph_format.line_spacing = 1.0
st.paragraph_format.space_after = Pt(0)

def big_par(text, align, indent=False, page_break=False):
    """Абзац 14pt, интервал 1.5 (вступление/подпись/заголовки)."""
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    pf.space_after = Pt(0)
    if indent:
        pf.first_line_indent = RED_LINE
    if page_break:
        pf.page_break_before = True
    p.alignment = align
    r = p.add_run(text)
    r.font.name = "Times New Roman"
    r.font.size = Pt(14)
    return p

def blank():
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(0)
    return p

def code_block(lines):
    """Блок строк кода: TNR 8pt, одинарный, выравнивание влево, без отступа."""
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing = 1.0
    pf.space_after = Pt(0)
    pf.space_before = Pt(0)
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = p.add_run()
    run.font.name = "Times New Roman"
    run.font.size = Pt(8)
    rpr = run._element.get_or_add_rPr(); rf = rpr.get_or_add_rFonts()
    rf.set(qn("w:ascii"), "Times New Roman"); rf.set(qn("w:hAnsi"), "Times New Roman")
    for i, ln in enumerate(lines):
        if i > 0:
            run.add_break()
        run.add_text(ln if ln != "" else " ")
    return p

def kind_word(fname):
    return "файла" if fname.endswith(".fxml") else "класса"

# Заголовок приложения (как в РПЗ: справа «Приложение 2», по центру «Текст программы»)
big_par("Приложение 2", WD_ALIGN_PARAGRAPH.RIGHT)
big_par("Текст программы", WD_ALIGN_PARAGRAPH.CENTER, indent=True)
blank()

for idx, path in enumerate(all_files, start=1):
    fname = os.path.basename(path)
    num = "П2.%d" % idx
    lines = read_code_lines(path)
    blocks = split_blocks(lines)
    # Вступление
    big_par("На рис. %s представлен текст %s %s." % (num, kind_word(fname), fname),
            WD_ALIGN_PARAGRAPH.JUSTIFY, indent=True)
    blank()
    # Блоки кода с продолжением рисунка на новых страницах
    for bi, block in enumerate(blocks):
        if bi > 0:
            big_par("Продолжение рис. %s" % num, WD_ALIGN_PARAGRAPH.RIGHT, page_break=True)
            blank()
        code_block(block)
    # Подпись рисунка
    big_par("Рис. %s. Текст %s %s" % (num, kind_word(fname), fname), WD_ALIGN_PARAGRAPH.CENTER)
    blank()

doc.save(OUT)
print("Сохранено:", OUT)
print("Файлов (рисунков):", len(all_files))
