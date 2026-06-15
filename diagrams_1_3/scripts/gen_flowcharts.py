# -*- coding: utf-8 -*-
"""Чистые ортогональные схемы алгоритмов (с условиями) для раздела 1.3.
Рисуются вручную (PIL) ради ровной раскладки: прямые углы, выровненные блоки,
аккуратные входы/выходы стрелок, без пересечений. Перекрывают graphviz-версии
ris_4_stax_parse, ris_5_classification, ris_7_generation, ris_8_test_data.
Обозначения по ГОСТ 19.701-90: терминатор — овал, процесс — прямоугольник,
решение — ромб, предопределённый процесс — прямоугольник с двумя линиями,
данные — параллелограмм. Шрифт — Liberation Serif (под Times New Roman диплома)."""
import os, math
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TTF = "/usr/share/fonts/truetype/liberation/LiberationSerif-Regular.ttf"
SC = 2  # суперсэмплинг ради чётких линий и текста


class FC:
    def __init__(self, w, h):
        self.W, self.H = w * SC, h * SC
        self.im = Image.new("RGB", (self.W, self.H), "white")
        self.d = ImageDraw.Draw(self.im)
        self.fn = ImageFont.truetype(TTF, 15 * SC)
        self.fe = ImageFont.truetype(TTF, 13 * SC)
        self.nodes = {}

    # ---------- текст ----------
    def _text(self, cx, cy, text, font):
        lines = text.split("\n")
        asc, desc = font.getmetrics(); lh = asc + desc + 2 * SC
        ty = cy - lh * len(lines) / 2
        for ln in lines:
            w = self.d.textlength(ln, font=font)
            self.d.text((cx - w / 2, ty), ln, font=font, fill="black")
            ty += lh

    # ---------- фигуры (координаты в «логических» px, *SC внутри) ----------
    def _box(self, x, y, w, h):
        return (x * SC, y * SC, w * SC, h * SC)

    def node(self, nid, kind, x, y, w, h, text):
        self.nodes[nid] = (x, y, w, h)
        cx, cy, ww, hh = x * SC, y * SC, w * SC, h * SC
        l, t, r, b = cx - ww / 2, cy - hh / 2, cx + ww / 2, cy + hh / 2
        lw = 2 * SC
        if kind == "proc":
            self.d.rectangle([l, t, r, b], outline="black", width=lw, fill="white")
        elif kind == "term":
            self.d.ellipse([l, t, r, b], outline="black", width=lw, fill="white")
        elif kind == "dec":
            self.d.polygon([(cx, t), (r, cy), (cx, b), (l, cy)], outline="black", width=lw, fill="white")
        elif kind == "data":
            s = hh * 0.4
            self.d.polygon([(l + s, t), (r, t), (r - s, b), (l, b)], outline="black", width=lw, fill="white")
        elif kind == "predef":
            self.d.rectangle([l, t, r, b], outline="black", width=lw, fill="white")
            self.d.line([(l + 8 * SC, t), (l + 8 * SC, b)], fill="black", width=lw)
            self.d.line([(r - 8 * SC, t), (r - 8 * SC, b)], fill="black", width=lw)
        self._text(cx, cy, text, self.fn)

    # якоря (в логических px)
    def A(self, nid, side):
        x, y, w, h = self.nodes[nid]
        return {"t": (x, y - h / 2), "b": (x, y + h / 2),
                "l": (x - w / 2, y), "r": (x + w / 2, y), "c": (x, y)}[side]

    # ---------- стрелки (ортогональные, точки в логических px) ----------
    def arrow(self, pts, label=None, lxy=None, la="c", head=True):
        P = [(x * SC, y * SC) for x, y in pts]
        self.d.line(P, fill="black", width=2 * SC, joint="curve")
        if head:
            (x0, y0), (x1, y1) = P[-2], P[-1]
            ang = math.atan2(y1 - y0, x1 - x0); L = 11 * SC; a = math.radians(26)
            self.d.polygon([(x1, y1),
                            (x1 - L * math.cos(ang - a), y1 - L * math.sin(ang - a)),
                            (x1 - L * math.cos(ang + a), y1 - L * math.sin(ang + a))], fill="black")
        if label:
            self._label(lxy, label, la)

    def _label(self, xy, text, la):
        x, y = xy[0] * SC, xy[1] * SC
        w = self.d.textlength(text, font=self.fe); hh = self.fe.size + 4 * SC
        tx = x - w / 2 if la == "c" else (x if la == "l" else x - w)
        self.d.rectangle([tx - 3 * SC, y - 2 * SC, tx + w + 3 * SC, y + hh], fill="white")
        self._text(tx + w / 2, y + hh / 2, text, self.fe)

    def save(self, name):
        self.im.resize((self.W // SC, self.H // SC), Image.LANCZOS).save(f"{OUT}/{name}.png")
        print("OK", name, f"{self.W // SC}x{self.H // SC}")


# ================= Рис. 7 — Генерация тестового проекта =================
def generation():
    c = FC(900, 1180)
    CX = 330
    c.node("beg", "term", CX, 45, 120, 50, "Начало")
    c.node("a1", "proc", CX, 145, 290, 56, "Создать структуру проекта\n(файл сборки, конфигурация)")
    c.node("a2", "proc", CX, 265, 290, 66, "Сгенерировать инфраструктуру\n(драйвер, базовый класс,\nтестовые данные)")
    c.node("a3", "proc", CX, 390, 290, 50, "Взять очередную сущность")
    c.node("dk", "dec", CX, 520, 230, 104, "Тип\nсущности?")
    c.node("prim", "predef", 150, 700, 250, 64, "Создать Page Object\nи тестовый класс")
    c.node("child", "predef", 620, 700, 240, 64, "Создать тест\nв составе родителя")
    c.node("dm", "dec", CX, 870, 230, 104, "Остались\nсущности?")
    c.node("rep", "proc", CX, 1010, 290, 56, "Сохранить отчёт\nо классификации")
    c.node("end", "term", CX, 1120, 120, 50, "Конец")

    # основной поток
    c.arrow([c.A("beg", "b"), c.A("a1", "t")])
    c.arrow([c.A("a1", "b"), c.A("a2", "t")])
    c.arrow([c.A("a2", "b"), c.A("a3", "t")])
    c.arrow([c.A("a3", "b"), c.A("dk", "t")])
    # ветви типа сущности: основная — влево, дочерняя — вправо, справочник — вниз
    c.arrow([c.A("dk", "l"), (150, 520), c.A("prim", "t")], "основная", (175, 482), "l")
    c.arrow([c.A("dk", "r"), (620, 520), c.A("child", "t")], "дочерняя", (470, 482), "l")
    c.arrow([c.A("dk", "b"), c.A("dm", "t")], "справочник (пропуск)", (CX + 14, 690), "l")
    # сходятся к «Остались сущности?»
    c.arrow([c.A("prim", "b"), (150, 870), c.A("dm", "l")])
    c.arrow([c.A("child", "b"), (620, 870), c.A("dm", "r")])
    # петля «да» — назад к «Взять очередную сущность»
    c.arrow([c.A("dm", "r"), (790, 870), (790, 390), c.A("a3", "r")], "да  (*для каждой сущности)", (600, 845), "l")
    # «нет» — к отчёту и концу
    c.arrow([c.A("dm", "b"), c.A("rep", "t")], "нет", (CX + 14, 955), "l")
    c.arrow([c.A("rep", "b"), c.A("end", "t")])
    c.save("ris_7_generation")


generation()


# ================= Рис. 5 — Классификация сущностей =================
def classification():
    c = FC(1000, 1130)
    CX, RX, BUS = 300, 660, 850
    c.node("beg", "term", CX, 45, 130, 50, "Начало")
    c.node("d1", "dec", CX, 175, 280, 112, "Совпадает с grid-\nвкладкой родителя?")
    c.node("d2", "dec", CX, 340, 280, 124, "Узел дерева\n(addFromTree=1)\nсо своим CRUD?")
    c.node("d3", "dec", CX, 510, 280, 112, "featureName\nначинается с V_S_?")
    c.node("d4", "dec", CX, 675, 280, 112, "Все поиски без\nпараметров (pick-one)?")
    c.node("d5", "dec", CX, 840, 250, 104, "Только цель\nFK-пикера?")
    c.node("prim", "proc", CX, 975, 280, 56, "PRIMARY —\nосновная сущность")
    c.node("end", "term", CX, 1085, 130, 50, "Конец")
    c.node("child1", "proc", RX, 175, 270, 60, "CHILD —\nдочерняя (вкладка-грид)")
    c.node("child2", "proc", RX, 340, 250, 60, "CHILD —\nузел дерева")
    c.node("ref", "proc", RX, 675, 280, 64, "REFERENCE_DICTIONARY —\nсправочник")

    c.arrow([c.A("beg", "b"), c.A("d1", "t")])
    c.arrow([c.A("d1", "b"), c.A("d2", "t")], "нет", (CX + 14, 250), "l")
    c.arrow([c.A("d2", "b"), c.A("d3", "t")], "нет", (CX + 14, 420), "l")
    c.arrow([c.A("d3", "b"), c.A("d4", "t")], "нет", (CX + 14, 590), "l")
    c.arrow([c.A("d4", "b"), c.A("d5", "t")], "нет", (CX + 14, 755), "l")
    c.arrow([c.A("d5", "b"), c.A("prim", "t")], "нет", (CX + 14, 912), "l")
    c.arrow([c.A("prim", "b"), c.A("end", "t")])
    c.arrow([c.A("d1", "r"), c.A("child1", "l")], "да", (455, 158), "l")
    c.arrow([c.A("d2", "r"), c.A("child2", "l")], "да", (455, 323), "l")
    c.arrow([c.A("d3", "r"), (RX, 510), c.A("ref", "t")], "да", (455, 493), "l")
    c.arrow([c.A("d4", "r"), c.A("ref", "l")], "да", (455, 658), "l")
    c.arrow([c.A("d5", "r"), (RX, 840), c.A("ref", "b")], "да", (455, 823), "l")
    c.arrow([c.A("child1", "r"), (BUS, 175), (BUS, 1085), c.A("end", "r")], head=True)
    c.arrow([c.A("child2", "r"), (BUS, 340)], head=False)
    c.arrow([c.A("ref", "r"), (BUS, 675)], head=False)
    c.save("ris_5_classification")


classification()


# ================= Рис. 8 — Подбор тестовых данных =================
def testdata():
    c = FC(980, 900)
    CX, LBUS = 450, 30
    c.node("beg", "term", CX, 45, 120, 48, "Начало")
    c.node("inp", "data", CX, 135, 240, 50, "Свойство (атрибут)")
    c.node("d1", "dec", CX, 250, 250, 104, "Справочник/ссылка\n(Directory/Ref)?")
    c.node("d2", "dec", CX, 400, 220, 96, "Задана\nмаска?")
    c.node("d3", "dec", CX, 545, 250, 104, "Маска похожа\nна дату/время?")
    c.node("bymask", "proc", CX, 695, 280, 56, "Сформировать по маске\n(цифры, буквы, разделители)")
    c.node("pick", "data", 175, 250, 240, 60, "Выбор из выпадающего\nсписка (не задаётся)")
    c.node("dt", "data", 175, 545, 210, 50, "Текущая дата / время")
    c.node("byType", "data", 745, 400, 240, 70, "Значение по типу:\nстрока / число / дата\n(см. таблицу правил)")
    c.node("end", "term", CX, 820, 120, 48, "Конец")

    c.arrow([c.A("beg", "b"), c.A("inp", "t")])
    c.arrow([c.A("inp", "b"), c.A("d1", "t")])
    c.arrow([c.A("d1", "l"), c.A("pick", "r")], "да", (300, 232), "c")
    c.arrow([c.A("d1", "b"), c.A("d2", "t")], "нет", (CX + 14, 330), "l")
    c.arrow([c.A("d2", "b"), c.A("d3", "t")], "да", (CX + 14, 470), "l")
    c.arrow([c.A("d2", "r"), c.A("byType", "l")], "нет", (575, 382), "c")
    c.arrow([c.A("d3", "l"), c.A("dt", "r")], "да", (300, 527), "c")
    c.arrow([c.A("d3", "b"), c.A("bymask", "t")], "нет", (CX + 14, 632), "l")
    c.arrow([c.A("pick", "l"), (LBUS, 250), (LBUS, 820), c.A("end", "l")], head=True)
    c.arrow([c.A("dt", "l"), (LBUS, 545)], head=False)
    c.arrow([c.A("byType", "b"), (745, 820), c.A("end", "r")], head=True)
    c.arrow([c.A("bymask", "b"), c.A("end", "t")], head=True)
    c.save("ris_8_test_data")


testdata()


# ================= Рис. 4 — Потоковый разбор XML (StAX) =================
def stax():
    c = FC(1040, 1060)
    CX, LOOPL, LOOPR = 430, 30, 910
    c.node("beg", "term", CX, 45, 120, 48, "Начало")
    c.node("open", "data", CX, 135, 230, 50, "Открыть поток XML")
    c.node("read", "proc", CX, 250, 260, 56, "Прочитать следующее\nсобытие (токен)")
    c.node("dEnd", "dec", CX, 390, 240, 104, "Конец\nдокумента?")
    c.node("dStart", "dec", CX, 540, 240, 104, "Начало\nэлемента?")
    c.node("dWhich", "dec", CX, 690, 240, 104, "Какой\nэлемент?")
    c.node("ret", "data", 165, 390, 200, 56, "Вернуть модель\nметаданных")
    c.node("end", "term", 165, 500, 120, 48, "Конец")
    c.node("cat", "proc", 130, 850, 200, 58, "Сохранить подсистему\nи идентификатор")
    c.node("obj", "predef", 350, 850, 175, 58, "Разобрать\nсущность")
    c.node("sea", "predef", 560, 850, 195, 58, "Разобрать\nблок поисков")
    c.node("skip", "proc", 790, 850, 200, 58, "Перемотать до\nзакрывающего тега")

    c.arrow([c.A("beg", "b"), c.A("open", "t")])
    c.arrow([c.A("open", "b"), c.A("read", "t")])
    c.arrow([c.A("read", "b"), c.A("dEnd", "t")])
    c.arrow([c.A("dEnd", "l"), c.A("ret", "r")], "да", (270, 372), "c")
    c.arrow([c.A("ret", "b"), c.A("end", "t")])
    c.arrow([c.A("dEnd", "b"), c.A("dStart", "t")], "нет", (CX + 14, 470), "l")
    c.arrow([c.A("dStart", "l"), (LOOPL, 540), (LOOPL, 250), c.A("read", "l")], "нет", (315, 522), "c")
    c.arrow([c.A("dStart", "b"), c.A("dWhich", "t")], "да", (CX + 14, 620), "l")
    c.arrow([c.A("dWhich", "b"), (CX, 760), (130, 760), c.A("cat", "t")], "Category", (135, 788), "c")
    c.arrow([(CX, 760), (350, 760), c.A("obj", "t")], "Object", (350, 788), "c", head=True)
    c.arrow([(CX, 760), (560, 760), c.A("sea", "t")], "Searches", (560, 788), "c", head=True)
    c.arrow([(CX, 760), (790, 760), c.A("skip", "t")], "иной", (790, 788), "c", head=True)
    c.arrow([c.A("cat", "b"), (130, 950), (LOOPR, 950), (LOOPR, 250), c.A("read", "r")], head=True)
    c.arrow([c.A("obj", "b"), (350, 950)], head=False)
    c.arrow([c.A("sea", "b"), (560, 950)], head=False)
    c.arrow([c.A("skip", "b"), (790, 950)], head=False)
    c.save("ris_4_stax_parse")


stax()
