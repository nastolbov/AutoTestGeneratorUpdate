"""
Генератор ДПС — версия 4.
Логика:
  • Фиксированный набор объектов на пакет — присутствует во всех 3 ДПС
  • Каждая активация ОБЯЗАТЕЛЬНО закрывается через return (return-стрелка
    создаёт визуальный выход из блока активации)
  • Если класс не участвует в сценарии — стоит с пустой линией жизни
  • Внешний вход: стрелка извне идёт в один из классов пакета
  • Внешний выход: return-стрелка обратно за пределы пакета
  • Z-order: lifelines(1) → activations(3) → boxes(5) → arrows(8)
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch
import os

OUT_DIR = "/home/user/AutoTestGeneratorUpdate/diagrams"
FONT = "Liberation Serif"


def render(filename, objects, messages):
    n = len(objects)
    obj_spacing = 2.7
    fig_w = max(11, n * obj_spacing + 2)

    # Высоту фигуры считаем от РЕАЛЬНОГО числа сообщений + отступы под боксы
    BOX_H = 0.75
    HEADER = 0.6   # отступ от низа боксов до первого сообщения
    FOOTER = 0.4   # отступ от последнего сообщения до низа линий жизни
    STEP   = 0.55  # шаг между сообщениями
    # Y-координаты в единицах сетки:
    #   box_top  = msg_count * STEP + HEADER + FOOTER + BOX_H
    msg_count = max(len(messages), 1)
    canvas_h = msg_count * STEP + HEADER + FOOTER + BOX_H + 0.2
    fig_h = max(3.0, canvas_h * 0.55)

    fig, ax = plt.subplots(figsize=(fig_w, fig_h), dpi=140)
    canvas_w = (n + 1) * obj_spacing
    ax.set_xlim(0, canvas_w)
    ax.set_ylim(0, canvas_h)
    ax.axis('off')

    x_pos = {obj: (i + 1) * obj_spacing for i, obj in enumerate(objects)}

    box_w = 2.0
    box_h = BOX_H
    box_top_y = canvas_h - 0.1
    box_bot_y = box_top_y - box_h
    life_bottom = FOOTER  # низ линий жизни — сразу под последним сообщением

    msg_top_y = box_bot_y - HEADER
    msg_bottom_y = life_bottom + 0.1
    if messages:
        y_step = STEP
        msg_y = [msg_top_y - i * y_step for i in range(len(messages))]
    else:
        msg_y = []

    # Активации — каждый return ОБЯЗАТЕЛЬНО закрывает активацию
    activations = {obj: [] for obj in objects}
    active_start = {}
    for i, m in enumerate(messages):
        y = msg_y[i]
        kind = m.get("kind", "sync")
        to = m["to"]
        frm = m["from"]

        if kind == "return":
            if frm in active_start:
                activations[frm].append((active_start.pop(frm), y))
        elif kind == "destroy":
            if to in active_start:
                activations[to].append((active_start.pop(to), y))
        else:
            if to != frm and to not in active_start:
                active_start[to] = y

    # Незакрытые активации — закрыть до низа (не должно остаться)
    for obj, ys in list(active_start.items()):
        activations[obj].append((ys, msg_bottom_y - 0.15))

    # 1) Линии жизни
    for obj in objects:
        x = x_pos[obj]
        ax.plot([x, x], [box_bot_y, life_bottom],
                linestyle=(0, (5, 4)), color='black', linewidth=1.1,
                zorder=1)

    # 2) Активации
    act_w = 0.22
    for obj, acts in activations.items():
        x = x_pos[obj]
        for y_start, y_end in acts:
            h = y_start - y_end
            if h > 0.05:
                ax.add_patch(Rectangle(
                    (x - act_w / 2, y_end), act_w, h,
                    linewidth=1.1, edgecolor='black', facecolor='white',
                    zorder=3))

    # 3) Блоки объектов
    for obj in objects:
        x = x_pos[obj]
        ax.add_patch(Rectangle(
            (x - box_w / 2, box_bot_y), box_w, box_h,
            linewidth=1.4, edgecolor='black', facecolor='white', zorder=5))
        text_y = box_bot_y + box_h / 2
        ax.text(x, text_y, obj,
                ha='center', va='center',
                fontname=FONT, fontsize=12,
                zorder=6)
        underline_w = min(box_w - 0.2, max(0.5, len(obj) * 0.078))
        ax.plot([x - underline_w / 2, x + underline_w / 2],
                [text_y - 0.17, text_y - 0.17],
                color='black', linewidth=0.9, zorder=6)

    # 4) Стрелки и подписи
    for i, m in enumerate(messages):
        y = msg_y[i]
        from_x = x_pos[m["from"]]
        to_x = x_pos[m["to"]]
        kind = m.get("kind", "sync")
        label = m["label"]

        if from_x == to_x or kind == "self":
            loop_w = 0.75
            x0 = from_x + act_w / 2
            ax.plot([x0, x0 + loop_w], [y, y],
                    color='black', linewidth=1.3, zorder=8)
            ax.plot([x0 + loop_w, x0 + loop_w], [y, y - 0.3],
                    color='black', linewidth=1.3, zorder=8)
            ax.annotate("", xy=(x0, y - 0.3), xytext=(x0 + loop_w, y - 0.3),
                        arrowprops=dict(arrowstyle='-|>', color='black',
                                        lw=1.3, mutation_scale=14),
                        zorder=8)
            ax.text(x0 + loop_w + 0.18, y - 0.15, label,
                    ha='left', va='center', fontname=FONT, fontsize=10.5,
                    zorder=9)
            continue

        going_right = to_x > from_x
        x_start = from_x + (act_w / 2 if going_right else -act_w / 2)
        x_end = to_x - (act_w / 2 if going_right else -act_w / 2)

        if kind == "return":
            ls = '--'; style = '->'; ms = 12
        elif kind == "create":
            ls = '--'; style = '-|>'; ms = 14
        elif kind == "async":
            ls = '-'; style = '->'; ms = 14
        elif kind == "destroy":
            ls = '-'; style = '-|>'; ms = 14
        else:
            ls = '-'; style = '-|>'; ms = 14

        ax.annotate("", xy=(x_end, y), xytext=(x_start, y),
                    arrowprops=dict(arrowstyle=style, color='black',
                                    lw=1.3, mutation_scale=ms, linestyle=ls),
                    zorder=8)

        mid_x = (from_x + to_x) / 2
        ax.text(mid_x, y + 0.14, label,
                ha='center', va='bottom',
                fontname=FONT, fontsize=10.5, zorder=9)

        if kind == "destroy":
            xs = 0.28
            ax.plot([to_x - xs, to_x + xs], [y - xs, y + xs],
                    color='black', linewidth=3, zorder=10)
            ax.plot([to_x - xs, to_x + xs], [y + xs, y - xs],
                    color='black', linewidth=3, zorder=10)

    plt.tight_layout(pad=0.5)
    plt.savefig(os.path.join(OUT_DIR, filename), dpi=140,
                bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close(fig)
    print(f"  ✓ {filename}")


# ====================================================
# UI пакет — фиксированный набор:
# :Пользователь, :App, :MainController, :TestCaseRow
# ====================================================
UI_OBJ = [":Пользователь", ":App", ":MainController", ":TestCaseRow"]

print("UI:")
render("seq-ui-normal.png", UI_OBJ, [
    {"from": ":Пользователь", "to": ":App", "label": "1: launch(args)"},
    {"from": ":App", "to": ":MainController", "label": "2: load('main.fxml')", "kind": "create"},
    {"from": ":MainController", "to": ":App", "label": "3: контроллер готов", "kind": "return"},
    {"from": ":App", "to": ":Пользователь", "label": "4: окно показано", "kind": "return"},
    {"from": ":Пользователь", "to": ":MainController", "label": "5: onRunTests()"},
    {"from": ":MainController", "to": ":TestCaseRow", "label": "6: new(...)", "kind": "create"},
    {"from": ":TestCaseRow", "to": ":MainController", "label": "7: строки готовы", "kind": "return"},
    {"from": ":MainController", "to": ":Пользователь", "label": "8: показ таблицы", "kind": "return"},
])

render("seq-ui-user-interrupt.png", UI_OBJ, [
    {"from": ":Пользователь", "to": ":App", "label": "1: launch(args)"},
    {"from": ":App", "to": ":MainController", "label": "2: load('main.fxml')", "kind": "create"},
    {"from": ":MainController", "to": ":App", "label": "3: готово", "kind": "return"},
    {"from": ":App", "to": ":Пользователь", "label": "4: окно показано", "kind": "return"},
    {"from": ":Пользователь", "to": ":MainController", "label": "5: onSelectXml()"},
    {"from": ":MainController", "to": ":MainController", "label": "6: FileChooser → Cancel", "kind": "self"},
    {"from": ":MainController", "to": ":Пользователь", "label": "7: путь не изменён", "kind": "return"},
])

render("seq-ui-system-interrupt.png", UI_OBJ, [
    {"from": ":Пользователь", "to": ":App", "label": "1: launch(args)"},
    {"from": ":App", "to": ":App", "label": "2: load('main.fxml') → IOException", "kind": "self"},
    {"from": ":App", "to": ":MainController", "label": "3: НЕ создан"},
    {"from": ":MainController", "to": ":App", "label": "4: исключение", "kind": "return"},
    {"from": ":App", "to": ":Пользователь", "label": "5: stderr трейс", "kind": "return"},
])

# ====================================================
# PARSER пакет — фиксированный набор:
# :MainController, :XmlModelParser, :EntityParser,
# :PropertyGroupParser, :SearchParser, StaxUtils, XmlNamespaces
# ====================================================
PARSER_OBJ = [":MainController", ":XmlModelParser", ":EntityParser",
              ":PropertyGroupParser", ":SearchParser", "StaxUtils", "XmlNamespaces"]

print("Parser:")
render("seq-parser-normal.png", PARSER_OBJ, [
    {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
    {"from": ":XmlModelParser", "to": "XmlNamespaces", "label": "2: NS_E3 (сверка)"},
    {"from": "XmlNamespaces", "to": ":XmlModelParser", "label": "3: константа", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":EntityParser", "label": "4: parseObject(reader)"},
    {"from": ":EntityParser", "to": ":PropertyGroupParser", "label": "5: parsePropertyGroup(reader)"},
    {"from": ":PropertyGroupParser", "to": "StaxUtils", "label": "6: attr(reader, name)"},
    {"from": "StaxUtils", "to": ":PropertyGroupParser", "label": "7: значение", "kind": "return"},
    {"from": ":PropertyGroupParser", "to": ":EntityParser", "label": "8: PropertyGroup", "kind": "return"},
    {"from": ":EntityParser", "to": ":XmlModelParser", "label": "9: EntityObject", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":SearchParser", "label": "10: parseSearches(reader)"},
    {"from": ":SearchParser", "to": ":XmlModelParser", "label": "11: List<Search>", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":MainController", "label": "12: AppModel", "kind": "return"},
])

render("seq-parser-user-interrupt.png", PARSER_OBJ, [
    {"from": ":MainController", "to": ":MainController", "label": "1: onSelectXml() → Cancel", "kind": "self"},
    {"from": ":MainController", "to": ":MainController", "label": "2: parse() не вызван", "kind": "self"},
])

render("seq-parser-system-interrupt.png", PARSER_OBJ, [
    {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
    {"from": ":XmlModelParser", "to": ":EntityParser", "label": "2: parseObject(reader)"},
    {"from": ":EntityParser", "to": "StaxUtils", "label": "3: attr(reader, name)"},
    {"from": "StaxUtils", "to": ":EntityParser", "label": "4: XMLStreamException", "kind": "return"},
    {"from": ":EntityParser", "to": ":XmlModelParser", "label": "5: throw наверх", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":MainController", "label": "6: ParserException", "kind": "return"},
])

# ====================================================
# MODEL пакет — фиксированный набор:
# :XmlModelParser, :AppModel, :EntityObject, :PropertyGroup,
# :Property, EntityClassifier, :Classification
# ====================================================
MODEL_OBJ = [":XmlModelParser", ":AppModel", ":EntityObject", ":PropertyGroup",
             ":Property", "EntityClassifier", ":Classification"]

print("Model:")
render("seq-model-normal.png", MODEL_OBJ, [
    {"from": ":XmlModelParser", "to": ":AppModel", "label": "1: new AppModel()", "kind": "create"},
    {"from": ":AppModel", "to": ":XmlModelParser", "label": "2: пустая модель", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":EntityObject", "label": "3: new EntityObject(...)", "kind": "create"},
    {"from": ":EntityObject", "to": ":XmlModelParser", "label": "4: ok", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":PropertyGroup", "label": "5: new PropertyGroup(...)", "kind": "create"},
    {"from": ":PropertyGroup", "to": ":XmlModelParser", "label": "6: ok", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":Property", "label": "7: new Property(...)", "kind": "create"},
    {"from": ":Property", "to": ":XmlModelParser", "label": "8: ok", "kind": "return"},
    {"from": ":XmlModelParser", "to": "EntityClassifier", "label": "9: classify(entity, model)"},
    {"from": "EntityClassifier", "to": ":Classification", "label": "10: new(kind, reason)", "kind": "create"},
    {"from": ":Classification", "to": "EntityClassifier", "label": "11: объект", "kind": "return"},
    {"from": "EntityClassifier", "to": ":XmlModelParser", "label": "12: Classification", "kind": "return"},
])

render("seq-model-user-interrupt.png", MODEL_OBJ, [
    {"from": ":XmlModelParser", "to": ":AppModel", "label": "1: new AppModel()", "kind": "create"},
    {"from": ":AppModel", "to": ":XmlModelParser", "label": "2: пустая модель (парсинг прерван)", "kind": "return"},
])

render("seq-model-system-interrupt.png", MODEL_OBJ, [
    {"from": ":XmlModelParser", "to": ":AppModel", "label": "1: new AppModel()", "kind": "create"},
    {"from": ":AppModel", "to": ":XmlModelParser", "label": "2: ok", "kind": "return"},
    {"from": ":XmlModelParser", "to": ":EntityObject", "label": "3: new EntityObject() → NPE", "kind": "create"},
    {"from": ":EntityObject", "to": ":XmlModelParser", "label": "4: NullPointerException", "kind": "return"},
])

# ====================================================
# GENERATOR пакет — фиксированный набор (8):
# :MainController, :TestConfig, :TestGenerator, :PageObjectWriter,
# :TestClassWriter, TestDataFactory, :TestRunner, :RunReportWriter
# ====================================================
GEN_OBJ = [":MainController", ":TestConfig", ":TestGenerator", ":PageObjectWriter",
           ":TestClassWriter", "TestDataFactory", ":TestRunner", ":RunReportWriter"]

print("Generator:")
render("seq-generator-normal.png", GEN_OBJ, [
    {"from": ":MainController", "to": ":TestConfig", "label": "1: new(...) + setters", "kind": "create"},
    {"from": ":TestConfig", "to": ":MainController", "label": "2: config", "kind": "return"},
    {"from": ":MainController", "to": ":TestGenerator", "label": "3: new(config)", "kind": "create"},
    {"from": ":MainController", "to": ":TestGenerator", "label": "4: generate(model)"},
    {"from": ":TestGenerator", "to": ":PageObjectWriter", "label": "5: write(entity)"},
    {"from": ":PageObjectWriter", "to": "TestDataFactory", "label": "6: generateValue(prop)"},
    {"from": "TestDataFactory", "to": ":PageObjectWriter", "label": "7: значение", "kind": "return"},
    {"from": ":PageObjectWriter", "to": ":TestGenerator", "label": "8: PageObject готов", "kind": "return"},
    {"from": ":TestGenerator", "to": ":TestClassWriter", "label": "9: write(entity, model)"},
    {"from": ":TestClassWriter", "to": ":TestGenerator", "label": "10: TestClass готов", "kind": "return"},
    {"from": ":TestGenerator", "to": ":MainController", "label": "11: проект готов", "kind": "return"},
    {"from": ":MainController", "to": ":TestRunner", "label": "12: run(...)"},
    {"from": ":TestRunner", "to": ":MainController", "label": "13: TestRunResult", "kind": "return"},
    {"from": ":MainController", "to": ":RunReportWriter", "label": "14: writeHtml + writeCsv"},
    {"from": ":RunReportWriter", "to": ":MainController", "label": "15: отчёты записаны", "kind": "return"},
])

render("seq-generator-user-interrupt.png", GEN_OBJ, [
    {"from": ":MainController", "to": ":TestConfig", "label": "1: new(...)", "kind": "create"},
    {"from": ":TestConfig", "to": ":MainController", "label": "2: config", "kind": "return"},
    {"from": ":MainController", "to": ":TestGenerator", "label": "3: new(config)", "kind": "create"},
    {"from": ":MainController", "to": ":TestGenerator", "label": "4: generate(model)"},
    {"from": ":TestGenerator", "to": ":TestGenerator", "label": "5: закрытие окна — Task убит", "kind": "self"},
    {"from": ":TestGenerator", "to": ":MainController", "label": "6: частично сгенерировано", "kind": "return"},
])

render("seq-generator-system-interrupt.png", GEN_OBJ, [
    {"from": ":MainController", "to": ":TestRunner", "label": "1: run(outputDir, ...)"},
    {"from": ":TestRunner", "to": ":TestRunner", "label": "2: ProcessBuilder('mvn') → IOException", "kind": "self"},
    {"from": ":TestRunner", "to": ":MainController", "label": "3: throw наверх", "kind": "return"},
])

# ====================================================
# DATA пакет — фиксированный набор (6):
# :MainController, :ReportDao, :SchemaInitializer,
# :DatabaseConnection, :TestRunDao, :TestCaseDao
# ====================================================
DATA_OBJ = [":MainController", ":ReportDao", ":SchemaInitializer",
            ":DatabaseConnection", ":TestRunDao", ":TestCaseDao"]

print("Data:")
render("seq-data-normal.png", DATA_OBJ, [
    {"from": ":MainController", "to": ":ReportDao", "label": "1: new ReportDao()", "kind": "create"},
    {"from": ":ReportDao", "to": ":SchemaInitializer", "label": "2: initialize()"},
    {"from": ":SchemaInitializer", "to": ":DatabaseConnection", "label": "3: open()"},
    {"from": ":DatabaseConnection", "to": ":SchemaInitializer", "label": "4: Connection", "kind": "return"},
    {"from": ":SchemaInitializer", "to": ":ReportDao", "label": "5: схема готова", "kind": "return"},
    {"from": ":ReportDao", "to": ":MainController", "label": "6: DAO готов", "kind": "return"},
    {"from": ":MainController", "to": ":ReportDao", "label": "7: saveRun(result)"},
    {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "8: open()"},
    {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "9: Connection", "kind": "return"},
    {"from": ":ReportDao", "to": ":TestRunDao", "label": "10: insert(conn, result)"},
    {"from": ":TestRunDao", "to": ":ReportDao", "label": "11: runId", "kind": "return"},
    {"from": ":ReportDao", "to": ":TestCaseDao", "label": "12: insertBatch(conn, runId, cases)"},
    {"from": ":TestCaseDao", "to": ":ReportDao", "label": "13: void", "kind": "return"},
    {"from": ":ReportDao", "to": ":MainController", "label": "14: void (commit OK)", "kind": "return"},
])

render("seq-data-user-interrupt.png", DATA_OBJ, [
    {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
    {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "2: open()"},
    {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "3: Connection", "kind": "return"},
    {"from": ":ReportDao", "to": ":TestRunDao", "label": "4: insert(conn, result)"},
    {"from": ":TestRunDao", "to": ":ReportDao", "label": "5: runId (асинхронно при закрытии окна)", "kind": "return"},
    {"from": ":ReportDao", "to": ":MainController", "label": "6: commit (Task жив)", "kind": "return"},
])

render("seq-data-system-interrupt.png", DATA_OBJ, [
    {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
    {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "2: open()"},
    {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "3: Connection", "kind": "return"},
    {"from": ":ReportDao", "to": ":TestRunDao", "label": "4: insert(conn, result)"},
    {"from": ":TestRunDao", "to": ":TestRunDao", "label": "5: INSERT → SQLException", "kind": "self"},
    {"from": ":TestRunDao", "to": ":ReportDao", "label": "6: throw наверх", "kind": "return"},
    {"from": ":ReportDao", "to": ":MainController", "label": "7: void (graceful, лог в stderr)", "kind": "return"},
])

# ====================================================
# COMMON пакет — фиксированный набор (5):
# :TestGenerator (внешний), Transliterator, :JavaFileWriter,
# :ParserException, :XmlModelParser (внешний — для exception)
# ====================================================
COMMON_OBJ = ["Transliterator", ":JavaFileWriter", ":ParserException"]

print("Common:")
# Только классы пакета common — взаимодействие через утилиту JavaFileWriter,
# который использует Transliterator косвенно (через подаваемые строки)
render("seq-common-normal.png", COMMON_OBJ, [
    {"from": "Transliterator", "to": ":JavaFileWriter", "label": "1: имя класса для writeLine", "kind": "async"},
    {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "2: openBlock + writeLine\n   накопление StringBuilder", "kind": "self"},
    {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "3: writeToFile → Files.write", "kind": "self"},
])

render("seq-common-user-interrupt.png", COMMON_OBJ, [
    {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "1: writeLine [синхронно,\n   прерывание невозможно]", "kind": "self"},
    {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "2: writeToFile завершается\n   до конца файла", "kind": "self"},
])

render("seq-common-system-interrupt.png", COMMON_OBJ, [
    {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "1: writeToFile → IOException\n   (диск переполнен)", "kind": "self"},
    {"from": ":JavaFileWriter", "to": ":ParserException", "label": "2: new(msg, cause)", "kind": "create"},
    {"from": ":ParserException", "to": ":JavaFileWriter", "label": "3: исключение готово", "kind": "return"},
])

print("\nГотово.")
