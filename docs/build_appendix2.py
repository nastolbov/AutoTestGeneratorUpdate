#!/usr/bin/env python3
# Приложение 2 «Текст программы» (стиль РПЗ).
# Код — обычный текст, поток в 2 колонки (колоночные секции), БЕЗ пробелов и принудительных разрывов.
# Комментарии из кода удалены. Метка «Рис. П2.N Продолжение» ставится АВТОМАТИЧЕСКИ в колонтитуле
# раздела (different-first-page): на первой странице листинга её нет, на каждой следующей — сверху справа.
# Вступление и подпись «Рис. П2.N. Текст класса X» — во всю ширину, 14pt/1.5.
import os, re
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

# ---------- удаление комментариев ----------
def strip_java_comments(src):
    res = []
    i, n, state = 0, len(src), "code"
    while i < n:
        c = src[i]; nx = src[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nx == "/": state = "line"; i += 2; continue
            if c == "/" and nx == "*": state = "block"; i += 2; continue
            if c == '"': res.append(c); state = "str"; i += 1; continue
            if c == "'": res.append(c); state = "chr"; i += 1; continue
            res.append(c); i += 1; continue
        if state == "str":
            res.append(c)
            if c == "\\": res.append(nx); i += 2; continue
            if c == '"': state = "code"
            i += 1; continue
        if state == "chr":
            res.append(c)
            if c == "\\": res.append(nx); i += 2; continue
            if c == "'": state = "code"
            i += 1; continue
        if state == "line":
            if c == "\n": res.append("\n"); state = "code"
            i += 1; continue
        if state == "block":
            if c == "*" and nx == "/": state = "code"; i += 2; continue
            i += 1; continue
    return "".join(res)

def strip_fxml_comments(src):
    return re.sub(r"<!--.*?-->", "", src, flags=re.DOTALL)

def clean_lines(text):
    lines = [ln.rstrip() for ln in text.split("\n")]
    out = []
    for ln in lines:
        if ln.strip() == "":
            if not out or out[-1] == "":   # без ведущих и сдвоенных пустых
                continue
            out.append("")
        else:
            out.append(ln)
    while out and out[-1] == "":
        out.pop()
    return out

def wrap_code(line):
    line = line.replace("\t", "    ")
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
        out.append(s[:bp]); s = cont + s[bp:].lstrip(" "); first = False
    out.append(s)
    return out

def read_code_lines(path):
    with open(path, encoding="utf-8") as f:
        src = f.read()
    src = strip_fxml_comments(src) if path.endswith(".fxml") else strip_java_comments(src)
    lines = clean_lines(src)
    wrapped = []
    for ln in lines:
        wrapped.extend(wrap_code(ln))
    return wrapped

def kind_word(fname):
    return "файла" if fname.endswith(".fxml") else "класса"

# ====================== ДОКУМЕНТ ======================
doc = Document()

def set_cols(sectPr, cols):
    for c in sectPr.findall(qn("w:cols")):
        sectPr.remove(c)
    el = OxmlElement("w:cols"); el.set(qn("w:num"), str(cols)); el.set(qn("w:space"), "400")
    pgMar = sectPr.find(qn("w:pgMar"))
    (pgMar.addnext(el) if pgMar is not None else sectPr.append(el))

def setup_section(sec, cols):
    sec.page_width, sec.page_height = PAGE_W, PAGE_H
    sec.left_margin, sec.right_margin = M_L, M_R
    sec.top_margin, sec.bottom_margin = M_T, M_B
    set_cols(sec._sectPr, cols)

def clear_para(p):
    for r in list(p.runs):
        r._element.getparent().remove(r._element)

def set_continuation_header(sec, text):
    """На первой странице секции колонтитула нет, на следующих — text (справа).
    Все четыре колонтитула отвязываем от предыдущей секции (иначе наследуются)."""
    sec.different_first_page_header_footer = True
    fph = sec.first_page_header; fph.is_linked_to_previous = False; clear_para(fph.paragraphs[0])
    ff = sec.first_page_footer;  ff.is_linked_to_previous = False;  clear_para(ff.paragraphs[0])
    f = sec.footer;              f.is_linked_to_previous = False;   clear_para(f.paragraphs[0])
    h = sec.header
    h.is_linked_to_previous = False
    p = h.paragraphs[0]
    clear_para(p)
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r = p.add_run(text); r.font.name = "Times New Roman"; r.font.size = Pt(12)

def clear_header(sec):
    """Секция без колонтитула (вступление/подпись)."""
    sec.different_first_page_header_footer = False
    h = sec.header
    h.is_linked_to_previous = False
    clear_para(h.paragraphs[0])
    f = sec.footer
    f.is_linked_to_previous = False
    clear_para(f.paragraphs[0])

def set_caption_footer(sec, text):
    """Подпись внизу ПЕРВОЙ страницы листинга (многостраничный случай).
    Вызывать ПОСЛЕ set_continuation_header (там колонтитулы уже отвязаны)."""
    p = sec.first_page_footer.paragraphs[0]
    clear_para(p)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text); r.font.name = "Times New Roman"; r.font.size = Pt(14)

setup_section(doc.sections[0], 1)
clear_header(doc.sections[0])

st = doc.styles["Normal"]
st.font.name = "Times New Roman"; st.font.size = Pt(12)
st.paragraph_format.line_spacing = 1.0; st.paragraph_format.space_after = Pt(0)

def big_par(text, align, indent=False):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE; pf.space_after = Pt(0)
    if indent:
        pf.first_line_indent = RED_LINE
    p.alignment = align
    r = p.add_run(text); r.font.name = "Times New Roman"; r.font.size = Pt(14)

def enter_14():
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE; p.paragraph_format.space_after = Pt(0)
    r = p.add_run(""); r.font.name = "Times New Roman"; r.font.size = Pt(14)

def code_line(text):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.EXACTLY; pf.line_spacing = Pt(CODE_LH_PT)
    pf.space_after = Pt(0); pf.space_before = Pt(0)
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = p.add_run(text if text != "" else " "); r.font.name = "Times New Roman"; r.font.size = Pt(8)
    rpr = r._element.get_or_add_rPr(); rf = rpr.get_or_add_rFonts()
    rf.set(qn("w:ascii"), "Times New Roman"); rf.set(qn("w:hAnsi"), "Times New Roman")

big_par("Приложение 2", WD_ALIGN_PARAGRAPH.RIGHT)
big_par("Текст программы", WD_ALIGN_PARAGRAPH.CENTER, indent=True)

# Непрерывный поток без разрывов страниц. Многостраничные листинги (>130 строк):
# подпись «Рис. П2.N. Текст…» — в колонтитуле НИЗА первой страницы (первое появление кода),
# на продолжении сверху — «Рис. П2.N Продолжение». Короткие — подпись в теле после кода.
LONG_THRESHOLD = 130

for idx, path in enumerate(all_files, start=1):
    fname = os.path.basename(path)
    num = "П2.%d" % idx
    lines = read_code_lines(path)
    caption = "Рис. %s. Текст %s %s" % (num, kind_word(fname), fname)
    is_long = len(lines) > LONG_THRESHOLD
    # вступление (1 колонка)
    big_par("На рис. %s представлен текст %s %s." % (num, kind_word(fname), fname),
            WD_ALIGN_PARAGRAPH.JUSTIFY, indent=True)
    # код в 2 колонки + колонтитул продолжения
    sec = doc.add_section(WD_SECTION.CONTINUOUS); setup_section(sec, 2)
    set_continuation_header(sec, "Рис. %s Продолжение" % num)
    if is_long:
        set_caption_footer(sec, caption)   # подпись внизу первой (переносимой) страницы
    for ln in lines:
        code_line(ln)
    # назад в 1 колонку
    sec = doc.add_section(WD_SECTION.CONTINUOUS); setup_section(sec, 1); clear_header(sec)
    if not is_long:
        big_par(caption, WD_ALIGN_PARAGRAPH.CENTER)   # короткий — подпись в теле
    enter_14()

doc.save(OUT)
print("Сохранено:", OUT, "| файлов:", len(all_files))
