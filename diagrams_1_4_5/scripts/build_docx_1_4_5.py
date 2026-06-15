# -*- coding: utf-8 -*-
"""Полный фрагмент раздела 1.4.5 — логическая И физическая модели базы данных,
по реальной схеме SQLite программы (ru.autotestgen.data.SchemaInitializer).
Физическая часть дана развёрнуто (типы SQLite, ключи, ограничения, нормализация).
Форматирование: Times New Roman 14, межстрочный 1,5, красная строка 0,75;
рисунок и подпись по центру; таблицы — 12 пт, межстрочный 1, по левому краю.
Нумерация: табл. 34 — логическая, 35 — test_run, 36 — test_case."""
import os
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from PIL import Image

DIA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(DIA, "..", "Раздел_1.4.5_модель_БД.docx")
FONT = "Times New Roman"

doc = Document()
st = doc.styles["Normal"]
st.font.name = FONT; st.font.size = Pt(14)
st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
st.paragraph_format.line_spacing = 1.5; st.paragraph_format.space_after = Pt(0)
sec = doc.sections[0]
sec.left_margin = Cm(3); sec.right_margin = Cm(1.5); sec.top_margin = Cm(2); sec.bottom_margin = Cm(2)
tbl_no = 33


def _f(run, size=14, bold=False):
    run.font.name = FONT; run.font.size = Pt(size); run.bold = bold
    run._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)


def heading(text):
    p = doc.add_paragraph(); p.paragraph_format.space_before = Pt(12); p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.keep_with_next = True
    _f(p.add_run(text), 14, bold=True); return p


def body(text):
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.paragraph_format.first_line_indent = Cm(0.75)
    _f(p.add_run(text), 14); return p


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


# ==================== 1.4.5 ====================
heading("1.4.5 Проектирование структур данных и диаграммы отношений компонентов данных")
body("В качестве системы управления базой данных выбрана встраиваемая СУБД SQLite версии 3.42. "
     "Выбор обусловлен простотой развёртывания (СУБД не требует отдельного серверного процесса), "
     "кроссплатформенностью, поддержкой языка SQL и транзакций, а также тем, что вся база хранится в "
     "одном файле и создаётся автоматически при первом запуске приложения — это удобно для локального "
     "хранения истории прогонов в настольной программе. Для проектирования ER-диаграммы базы данных "
     "использовался CASE-инструмент ERwin Data Modeler. Проектирование выполнено на двух уровнях: "
     "логическом и физическом.")

# ---- Логическая модель ----
body("На логическом уровне модель описывает информационные сущности предметной области, их "
     "атрибуты, ключи и связи между сущностями без привязки к конкретной СУБД — без указания "
     "физических типов данных, ограничений и индексов. Логическая модель базы данных приведена на "
     "рис. 10. В ней выделены две сущности: test_run — сведения об отдельном запуске набора "
     "автотестов (прогоне), и test_case — результат выполнения отдельного теста (тест-кейса) в рамках "
     "прогона. Сущности связаны отношением «один ко многим» (1:N): один прогон содержит множество "
     "результатов тестов, и каждый результат принадлежит ровно одному прогону. Связь идентифицирующая "
     "и реализуется внешним ключом run_id в сущности test_case, ссылающимся на первичный ключ id "
     "сущности test_run.")
figure("ris_logical_db.png", "Рис. 10. Логическая модель базы данных", 15.5)
body("Для каждой сущности на логическом уровне фиксируется состав атрибутов с указанием их "
     "назначения и роли ключа; имена атрибутов соответствуют полям реальной базы данных. Состав "
     "атрибутов сущностей логической модели приведён в таблице 34.")
table_caption("Атрибуты сущностей логической модели")
grid(["Сущность", "Атрибут", "Описание", "Ключ"],
     [["test_run", "id", "Идентификатор прогона", "PK"],
      ["", "run_date", "Дата и время прогона", "—"],
      ["", "xml_file", "Имя файла метамодели", "—"],
      ["", "base_url", "Адрес тестируемого сайта", "—"],
      ["", "total", "Всего тестов", "—"],
      ["", "passed", "Успешно", "—"],
      ["", "failed", "Провалено", "—"],
      ["", "skipped", "Пропущено", "—"],
      ["", "duration_ms", "Длительность прогона, мс", "—"],
      ["test_case", "id", "Идентификатор результата", "PK"],
      ["", "run_id", "Ссылка на прогон (test_run.id)", "FK"],
      ["", "class_name", "Класс теста", "—"],
      ["", "method_name", "Метод теста", "—"],
      ["", "passed", "Успешность (пройден/не пройден)", "—"],
      ["", "failure_msg", "Сообщение об ошибке (может отсутствовать)", "—"],
      ["", "duration_ms", "Длительность тест-кейса, мс", "—"]],
     widths=[2.6, 3.4, 7.5, 3.0])

# ---- Физическая модель (развёрнуто) ----
body("Физическая модель базы данных получается преобразованием логической модели в структуру "
     "выбранной СУБД и приведена на рис. 11. Каждой сущности соответствует таблица — test_run и "
     "test_case; логические типы атрибутов заменяются конкретными типами SQLite, добавляются "
     "ограничения целостности (обязательность заполнения, значения по умолчанию, первичные и внешние "
     "ключи) и реализуется связь между таблицами.")
figure("ris_physical_db.png", "Рис. 11. Физическая модель базы данных", 16.0)
body("Существенная особенность SQLite — динамическая типизация на основе классов хранения (INTEGER, "
     "TEXT, REAL, BLOB, NULL) и механизма сродства типов (type affinity): тип объявляется у столбца, "
     "однако фактический класс хранения определяется записываемым значением. В проектируемой базе "
     "используются два класса: INTEGER — для числовых атрибутов и логических признаков, и TEXT — для "
     "строковых значений. Отдельного типа для даты и времени в SQLite нет, поэтому дата и время "
     "прогона (run_date) хранятся в текстовом виде, а логический признак успешности (test_case.passed) "
     "— целочисленным значением 0 или 1.")
body("Первичный ключ id в обеих таблицах объявлен как INTEGER PRIMARY KEY AUTOINCREMENT. Это "
     "суррогатный целочисленный ключ, значения которого назначаются системой автоматически и "
     "монотонно возрастают, не переиспользуясь после удаления строк, что гарантирует уникальность "
     "идентификаторов на всём времени жизни базы. Связь «один ко многим» реализована внешним ключом "
     "run_id таблицы test_case с ограничением REFERENCES test_run(id): он связывает каждый результат "
     "теста с породившим его прогоном и обеспечивает ссылочную целостность данных.")
body("Ограничения целостности заданы следующим образом. Обязательные для заполнения столбцы помечены "
     "ограничением NOT NULL: для прогона это run_date, xml_file и base_url, для результата теста — "
     "run_id, class_name и method_name. Числовые счётчики прогона (total, passed, failed, skipped, "
     "duration_ms) и длительность тест-кейса имеют значение по умолчанию DEFAULT 0, а признак "
     "успешности test_case.passed — DEFAULT 1. Столбец failure_msg допускает значение NULL, поскольку "
     "у успешно пройденного теста сообщение об ошибке отсутствует.")
body("Схема базы данных создаётся при первом запуске приложения оператором CREATE TABLE IF NOT "
     "EXISTS, поэтому повторный запуск не пересоздаёт уже существующие таблицы и не приводит к потере "
     "накопленной истории прогонов. Вся база располагается в одном файле в рабочем каталоге "
     "приложения.")
body("Структура базы данных нормализована и соответствует третьей нормальной форме. Атрибуты, "
     "относящиеся к прогону в целом, вынесены в таблицу test_run, а атрибуты отдельного теста — в "
     "таблицу test_case. Такое разделение исключает дублирование сведений о прогоне в каждой строке "
     "результата (они хранятся однократно и связываются по ключу), устраняет аномалии вставки и "
     "обновления; повторяющихся групп и транзитивных зависимостей нет — все неключевые атрибуты "
     "зависят только от первичного ключа своей таблицы.")
body("Спецификации таблиц test_run и test_case с указанием типов столбцов и ограничений приведены в "
     "таблицах 35 и 36 соответственно.")
table_caption("Спецификация таблицы test_run")
grid(["Столбец", "Тип", "Ограничения", "Описание"],
     [["id", "INTEGER", "PRIMARY KEY AUTOINCREMENT", "Идентификатор прогона"],
      ["run_date", "TEXT", "NOT NULL", "Дата и время прогона"],
      ["xml_file", "TEXT", "NOT NULL", "Имя файла метамодели"],
      ["base_url", "TEXT", "NOT NULL", "Адрес тестируемого сайта"],
      ["total", "INTEGER", "NOT NULL, DEFAULT 0", "Всего тестов"],
      ["passed", "INTEGER", "NOT NULL, DEFAULT 0", "Успешно"],
      ["failed", "INTEGER", "NOT NULL, DEFAULT 0", "Провалено"],
      ["skipped", "INTEGER", "NOT NULL, DEFAULT 0", "Пропущено"],
      ["duration_ms", "INTEGER", "NOT NULL, DEFAULT 0", "Длительность прогона, мс"]],
     widths=[3.0, 2.2, 5.1, 6.2])
table_caption("Спецификация таблицы test_case")
grid(["Столбец", "Тип", "Ограничения", "Описание"],
     [["id", "INTEGER", "PRIMARY KEY AUTOINCREMENT", "Идентификатор результата"],
      ["run_id", "INTEGER", "NOT NULL, REFERENCES test_run(id)", "Внешний ключ на прогон"],
      ["class_name", "TEXT", "NOT NULL", "Класс теста"],
      ["method_name", "TEXT", "NOT NULL", "Метод теста"],
      ["passed", "INTEGER", "NOT NULL, DEFAULT 1", "Успешность (1 — пройден, 0 — нет)"],
      ["failure_msg", "TEXT", "допускает NULL", "Сообщение об ошибке"],
      ["duration_ms", "INTEGER", "NOT NULL, DEFAULT 0", "Длительность тест-кейса, мс"]],
     widths=[3.0, 2.2, 5.6, 5.7])
body("Таким образом, логическая модель определяет, какие данные и в каких связях хранятся, а "
     "физическая модель задаёт их реализацию в СУБД SQLite с конкретными типами, ключами и "
     "ограничениями целостности.")

doc.save(OUT)
print("Сохранено:", OUT)
