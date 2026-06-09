"""
Структурная карта Константайна по правилам §5.3 Иванова Г.С.
Рисуем КАЖДЫЙ вызов с couples в виде ПАРАЛЛЕЛЬНЫХ мини-стрелок:
  ↓○ — data couple вниз (передача данных)
  ↑○ — data couple вверх (возврат данных)
  ↓● — control couple (флаг управления)
И маркеры особых вызовов:
  1 — однократный
  ◇ — условный
  ⟲ — циклический
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch, Circle, RegularPolygon, Ellipse
from matplotlib.lines import Line2D
import os

OUT = "/home/user/AutoTestGeneratorUpdate/diagrams/constantine-module-structure.png"
FONT = "Liberation Serif"

# ============== ОПРЕДЕЛЯЕМ ПОЗИЦИИ МОДУЛЕЙ ==============
# (x, y, w, h, kind) — kind ∈ 'module', 'library', 'data', 'subsystem'
modules = {
    # Уровень 1
    "App":                   (11.16, 16, 3.2, 1.0, "module"),
    # Уровень 2
    "MainController":        (11.16, 13.5, 3.6, 1.0, "module"),
    # Уровень 3: фасады
    "XmlModelParser":        (2.17, 11, 3.8, 1.0, "module"),
    "TestConfig":            (6.20, 11, 3.0, 1.0, "data"),
    "TestGenerator":         (9.92, 11, 3.6, 1.0, "module"),
    "TestRunner":            (14.88, 11, 3.0, 1.0, "module"),
    "ReportDao":             (19.22, 11, 3.0, 1.0, "module"),
    # Уровень 4: исполнители
    "EntityParser":          (1.24, 8.5, 3.4, 1.0, "module"),
    "SearchParser":          (4.03, 8.5, 3.4, 1.0, "module"),
    "PageObjectWriter":      (8.37, 8.5, 3.6, 1.0, "module"),
    "TestClassWriter":       (11.16, 8.5, 3.6, 1.0, "module"),
    "EntityClassifier":      (6.20, 8.5, 3.4, 1.0, "library"),
    "TestDataFactory":       (13.95, 8.5, 3.6, 1.0, "library"),
    "RunReportWriter":       (14.88, 8.5, 3.6, 1.0, "module"),  # справа от Runner
    "DatabaseConnection":    (17.36, 8.5, 3.4, 1.0, "module"),
    "SchemaInitializer":     (19.84, 8.5, 3.4, 1.0, "module"),
    "TestRunDao":            (22.01, 8.5, 3.0, 1.0, "module"),
    "TestCaseDao":           (24.18, 8.5, 3.0, 1.0, "module"),
    # Уровень 5
    "PropertyGroupParser":   (1.24, 6, 4.2, 1.0, "module"),
    # Уровень 6: библиотеки
    "StaxUtils":             (1.86, 3.5, 2.8, 1.0, "library"),
    "XmlNamespaces":         (4.03, 3.5, 3.2, 1.0, "library"),
    "JavaFileWriter":        (8.06, 3.5, 3.4, 1.0, "library"),
    "Transliterator":        (11.16, 3.5, 3.4, 1.0, "library"),
    "ParserException":(7,   1, 3.4, 1.0, "library"),
    # Уровень 7: артефакты
    "autotestgen.db":        (18.29, 1, 3.0, 1.0, "data"),
    "generated-tests/":      (13.02, 1, 3.5, 1.0, "data"),
}

# Перерасчёт позиций — расширить, переразложить
# Делаем 7 уровней по Y, X разносим по нужным колонкам
modules = {
    "App":                   (12.40, 15.5, 3.0, 0.9, "module"),
    "MainController":        (12.40, 13.0, 3.6, 0.9, "module"),
    # Уровень 3
    "XmlModelParser":        (2.17, 10.5, 4.0, 0.9, "module"),
    "TestConfig":            (6.51, 10.5, 3.2, 0.9, "data"),
    "TestGenerator":         (10.23, 10.5, 3.6, 0.9, "module"),
    "TestRunner":            (15.19, 10.5, 3.2, 0.9, "module"),
    "ReportDao":             (19.84, 10.5, 3.0, 0.9, "module"),
    # Уровень 4
    "EntityParser":          (1.86, 7.5, 3.4, 0.9, "module"),
    "SearchParser":          (4.65, 7.5, 3.4, 0.9, "module"),
    "EntityClassifier":      (7.44, 7.5, 3.4, 0.9, "library"),
    "PageObjectWriter":      (9.80, 7.5, 3.8, 0.9, "module"),
    "TestClassWriter":       (12.52, 7.5, 3.8, 0.9, "module"),
    "TestDataFactory":       (15.19, 7.5, 3.4, 0.9, "library"),
    "RunReportWriter":       (17.67, 7.5, 3.6, 0.9, "module"),
    "DatabaseConnection":    (20.46, 7.5, 3.8, 0.9, "module"),
    "SchemaInitializer":     (22.94, 7.5, 3.6, 0.9, "module"),
    "TestRunDao":            (25.11, 7.5, 3.0, 0.9, "module"),
    "TestCaseDao":           (26.97, 7.5, 3.0, 0.9, "module"),
    # Уровень 5
    "PropertyGroupParser":   (1.86, 5.0, 4.4, 0.9, "module"),
    # Уровень 6: библиотеки
    "StaxUtils":             (1.86, 2.5, 3.0, 0.9, "library"),
    "XmlNamespaces":         (4.03, 2.5, 3.4, 0.9, "library"),
    "JavaFileWriter":        (9.61, 2.5, 3.6, 0.9, "library"),
    "Transliterator":        (12.71, 2.5, 3.6, 0.9, "library"),
    "ParserException":       (6.51, 2.5, 3.4, 0.9, "library"),
    # Уровень 7
    "generated-tests/":      (15.50, 0.3, 3.6, 0.9, "data"),
    "autotestgen.db":        (20.46, 0.3, 3.4, 0.9, "data"),
}

# ============== ВЫЗОВЫ С COUPLES ==============
# Каждый вызов: (from, to, special, data_down, data_up, ctrl_down)
calls = [
    # Главный вызов — однократный
    ("App", "MainController", "1", [], [], []),

    # MainController → фасады
    ("MainController", "XmlModelParser",  None, ["File"], ["AppModel"], []),
    ("MainController", "TestConfig",       None, ["settings"], [], []),
    ("MainController", "TestGenerator",   None, ["AppModel"], [], []),
    ("MainController", "TestRunner",      None, ["dir", "filter"], ["result"], ["fastMode"]),
    ("MainController", "ReportDao",       None, ["result"], ["List"], []),

    # XmlModelParser
    ("XmlModelParser", "EntityParser", None, ["reader"], ["EntityObject"], []),
    ("XmlModelParser", "SearchParser", None, ["reader"], ["List<Search>"], []),
    ("EntityParser", "PropertyGroupParser", None, ["reader"], ["PG"], []),

    # TestGenerator
    ("TestGenerator", "EntityClassifier", "cycle", ["entity"], ["kind"], []),
    ("TestGenerator", "PageObjectWriter", "cycle", ["entity"], [], []),
    ("TestGenerator", "TestClassWriter",  "cycle", ["entity"], [], []),
    ("TestGenerator", "TestDataFactory",  None, ["property"], ["value"], []),

    # TestRunner
    ("TestRunner", "RunReportWriter", None, ["result"], [], []),

    # ReportDao
    ("ReportDao", "DatabaseConnection", None, ["url"], ["conn"], []),
    ("ReportDao", "SchemaInitializer",  "1", [], [], []),
    ("ReportDao", "TestRunDao",  None, ["result"], ["runId"], []),
    ("ReportDao", "TestCaseDao", None, ["cases"], [], []),

    # Утилиты
    ("EntityParser", "StaxUtils", None, [], [], []),
    ("PropertyGroupParser", "StaxUtils", None, [], [], []),
    ("SearchParser", "StaxUtils", None, [], [], []),
    ("PageObjectWriter", "JavaFileWriter", None, [], [], []),
    ("TestClassWriter", "JavaFileWriter", None, [], [], []),
    ("PageObjectWriter", "Transliterator", None, [], [], []),
    ("TestClassWriter",  "Transliterator", None, [], [], []),
    ("XmlModelParser", "ParserException", None, [], [], []),

    # Артефакты
    ("TestGenerator", "generated-tests/", None, [], [], []),
    ("TestRunner",    "generated-tests/", None, [], [], []),
    ("DatabaseConnection", "autotestgen.db", None, [], [], []),
]

# ============== РЕНДЕР ==============
fig, ax = plt.subplots(figsize=(16, 16), dpi=120)
ax.set_xlim(0, 30)
ax.set_ylim(-1, 17)
ax.axis('off')

# Рисуем модули
for name, (cx, cy, w, h, kind) in modules.items():
    if kind == "library":
        # двойная рамка
        ax.add_patch(Rectangle((cx - w/2 - 0.06, cy - h/2 - 0.06), w + 0.12, h + 0.12,
                               linewidth=1.0, edgecolor='black', facecolor='white'))
    if kind == "data":
        # овал
        ax.add_patch(Ellipse((cx, cy), w, h, linewidth=1.2,
                             edgecolor='black', facecolor='white'))
    else:
        # прямоугольник
        ax.add_patch(Rectangle((cx - w/2, cy - h/2), w, h,
                               linewidth=1.2, edgecolor='black', facecolor='white'))
    # текст
    ax.text(cx, cy, name, ha='center', va='center', fontname=FONT, fontsize=12)

def edge_point(name, dx, dy):
    """Точка на границе модуля в сторону (dx, dy)"""
    cx, cy, w, h, _ = modules[name]
    # вычисляем пересечение луча из центра с прямоугольником
    if abs(dx) < 1e-6:
        return (cx, cy + (h/2) * (1 if dy > 0 else -1))
    if abs(dy) < 1e-6:
        return (cx + (w/2) * (1 if dx > 0 else -1), cy)
    # пересечение с горизонтальной или вертикальной гранью
    t_x = (w/2) / abs(dx)
    t_y = (h/2) / abs(dy)
    t = min(t_x, t_y)
    return (cx + dx * t, cy + dy * t)

# Рисуем вызовы
for from_name, to_name, special, data_d, data_u, ctrl_d in calls:
    fx, fy, fw, fh, _ = modules[from_name]
    tx, ty, tw, th, _ = modules[to_name]
    dx, dy = tx - fx, ty - fy
    fpt = edge_point(from_name, dx, dy)
    tpt = edge_point(to_name, -dx, -dy)

    # Основная линия вызова
    ax.annotate("", xy=tpt, xytext=fpt,
                arrowprops=dict(arrowstyle='-|>', color='black',
                                lw=1.0, mutation_scale=12),
                zorder=2)

    # Маркер особого вызова на середине линии
    midx, midy = (fpt[0] + tpt[0])/2, (fpt[1] + tpt[1])/2
    if special == "1":
        ax.text(midx + 0.3, midy, "1", fontname=FONT, fontsize=14, ha='left', va='center', fontweight='bold')
    elif special == "cycle":
        ax.add_patch(Circle((midx + 0.4, midy), 0.18,
                            linewidth=1.0, edgecolor='black', facecolor='white'))

    # Couples: рисуем СБОКУ от основной линии, параллельно ей
    # Нормаль к линии (перпендикуляр)
    import math
    length = max(math.hypot(dx, dy), 0.001)
    nx, ny = -dy / length, dx / length  # нормаль слева
    couple_offset = 0.85

    def couple_arrow(name, side, going_down, kind):
        """side=+1 справа, -1 слева; going_down=True если вниз; kind='data'/'control'"""
        ox = nx * couple_offset * side
        oy = ny * couple_offset * side
        # короткая стрелка вдоль направления вызова
        seg_len = 0.7
        sdx = dx / length * seg_len
        sdy = dy / length * seg_len
        if not going_down:
            sdx, sdy = -sdx, -sdy
        mid_a = (midx + ox, midy + oy)
        start = (mid_a[0] - sdx/2, mid_a[1] - sdy/2)
        end   = (mid_a[0] + sdx/2, mid_a[1] + sdy/2)
        # рисуем линию + кружок на конце
        ax.plot([start[0], end[0]], [start[1], end[1]],
                color='black', lw=0.9, zorder=2)
        face = 'white' if kind == 'data' else 'black'
        ax.add_patch(Circle(end, 0.13, linewidth=0.9,
                            edgecolor='black', facecolor=face, zorder=3))
        # подпись
        ax.text(end[0] + ox * 0.5, end[1] + oy * 0.5, name,
                ha='center', va='center', fontname=FONT, fontsize=11)

    # Раскладываем couples
    side_left, side_right = -1, +1
    for i, d in enumerate(data_d):
        couple_arrow(d, side_left, True, 'data')
    for i, d in enumerate(data_u):
        couple_arrow(d, side_right, False, 'data')
    for i, c in enumerate(ctrl_d):
        couple_arrow(c, side_left + 0.5, True, 'control')

plt.tight_layout(pad=0.5)
plt.savefig(OUT, dpi=120, bbox_inches='tight',
            facecolor='white', edgecolor='none')
plt.close(fig)
print(f"Создан {OUT}")
