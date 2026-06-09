"""
Генератор UML sequence diagrams (ДПС) — версия 3 (по образцу рис. 7.8).
Логика рендеринга:
  1) Считаем макет
  2) Рисуем ЛИНИИ ЖИЗНИ (zorder=1) — полная пунктирная вертикаль
  3) Рисуем АКТИВАЦИИ (zorder=3) — белые прямоугольники с рамкой,
     перекрывают линию жизни в активные периоды
  4) Рисуем БЛОКИ ОБЪЕКТОВ (zorder=5) — непрозрачные, поверх всего сверху
  5) Рисуем СТРЕЛКИ И ПОДПИСИ (zorder=8) — поверх всего
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch
import os

OUT_DIR = "/home/user/AutoTestGeneratorUpdate/diagrams"
FONT = "Liberation Serif"


def render(filename, objects, messages):
    """
    objects: список имён ":Класс"
    messages: список dict:
      {"from": str, "to": str, "label": str,
       "kind": "sync"/"return"/"async"/"create"/"destroy"/"self"}
    """
    n = len(objects)
    # ШИРОКОЕ расстояние между объектами — каждый объект занимает ~2.6 единицы
    obj_spacing = 2.6
    fig_w = max(10, n * obj_spacing + 2)
    fig_h = max(6, len(messages) * 0.6 + 3)

    fig, ax = plt.subplots(figsize=(fig_w, fig_h), dpi=140)

    # Размеры макета
    canvas_w = (n + 1) * obj_spacing
    canvas_h = 10.0
    ax.set_xlim(0, canvas_w)
    ax.set_ylim(0, canvas_h)
    ax.axis('off')

    # X-координаты объектов (равномерно с отступом 1 слева)
    x_pos = {obj: (i + 1) * obj_spacing for i, obj in enumerate(objects)}

    # Размер прямоугольника объекта
    box_w = 1.8
    box_h = 0.7
    box_top_y = canvas_h - 0.3
    box_bot_y = box_top_y - box_h

    # Нижняя точка линии жизни
    life_bottom = 0.4

    # ============ Шаг 1: расчёт активаций ============
    # Простая логика:
    # — Когда объект получает сообщение (не return) — у него начинается активация
    # — Когда объект отправляет return — активация заканчивается
    # — В конце все незакрытые активации закрываются до низа

    # Y-координаты сообщений
    msg_top_y = box_bot_y - 0.5
    msg_bottom_y = life_bottom + 0.5
    if messages:
        y_step = (msg_top_y - msg_bottom_y) / max(len(messages), 1)
        msg_y = [msg_top_y - i * y_step for i in range(len(messages))]
    else:
        msg_y = []

    activations = {obj: [] for obj in objects}
    active_start = {}
    for i, m in enumerate(messages):
        y = msg_y[i]
        kind = m.get("kind", "sync")
        to = m["to"]
        frm = m["from"]

        if kind == "return":
            # closure активации у frm (он возвращает)
            if frm in active_start:
                activations[frm].append((active_start.pop(frm), y))
        elif kind == "destroy":
            # X на конце линии — активация to закрывается
            if to in active_start:
                activations[to].append((active_start.pop(to), y))
        else:
            # sync/async/create/self — у to начинается активация если не было
            if to != frm and to not in active_start:
                active_start[to] = y

    # Закрыть всё незакрытое до низа
    for obj, ys in list(active_start.items()):
        activations[obj].append((ys, msg_bottom_y - 0.2))

    # ============ Шаг 2: рисуем ЛИНИИ ЖИЗНИ (zorder=1) ============
    for obj in objects:
        x = x_pos[obj]
        ax.plot([x, x], [box_bot_y, life_bottom],
                linestyle=(0, (5, 4)), color='black', linewidth=1.1,
                zorder=1)

    # ============ Шаг 3: рисуем АКТИВАЦИИ (zorder=3) ============
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

    # ============ Шаг 4: рисуем БЛОКИ ОБЪЕКТОВ (zorder=5) ============
    for obj in objects:
        x = x_pos[obj]
        # Прямоугольник
        ax.add_patch(Rectangle(
            (x - box_w / 2, box_bot_y), box_w, box_h,
            linewidth=1.4, edgecolor='black', facecolor='white',
            zorder=5))
        # Имя объекта
        text_y = box_bot_y + box_h / 2
        ax.text(x, text_y, obj,
                ha='center', va='center',
                fontname=FONT, fontsize=11,
                zorder=6)
        # Подчёркивание (UML: имя экземпляра подчёркнуто)
        # Длина = по ширине текста
        underline_w = min(box_w - 0.2, max(0.5, len(obj) * 0.075))
        ax.plot([x - underline_w / 2, x + underline_w / 2],
                [text_y - 0.16, text_y - 0.16],
                color='black', linewidth=0.9, zorder=6)

    # ============ Шаг 5: рисуем СТРЕЛКИ И ПОДПИСИ (zorder=8) ============
    for i, m in enumerate(messages):
        y = msg_y[i]
        from_x = x_pos[m["from"]]
        to_x = x_pos[m["to"]]
        kind = m.get("kind", "sync")
        label = m["label"]

        if from_x == to_x or kind == "self":
            # Self-call: петля справа
            loop_w = 0.7
            x0 = from_x + act_w / 2
            ax.plot([x0, x0 + loop_w], [y, y],
                    color='black', linewidth=1.3, zorder=8)
            ax.plot([x0 + loop_w, x0 + loop_w], [y, y - 0.28],
                    color='black', linewidth=1.3, zorder=8)
            ax.annotate("", xy=(x0, y - 0.28), xytext=(x0 + loop_w, y - 0.28),
                        arrowprops=dict(arrowstyle='-|>', color='black',
                                        lw=1.3, mutation_scale=14),
                        zorder=8)
            # Подпись справа от петли
            ax.text(x0 + loop_w + 0.15, y - 0.14, label,
                    ha='left', va='center', fontname=FONT, fontsize=9.5,
                    zorder=9)
            continue

        # Обычное сообщение: стрелка от края активации источника
        # до края активации цели (или линии жизни, если активации нет)
        going_right = to_x > from_x
        x_start = from_x + (act_w / 2 if going_right else -act_w / 2)
        x_end = to_x - (act_w / 2 if going_right else -act_w / 2)

        # Стиль стрелки
        if kind == "return":
            ls = '--'
            style = '->'
            ms = 12
        elif kind == "create":
            ls = '--'
            style = '-|>'
            ms = 14
        elif kind == "async":
            ls = '-'
            style = '->'
            ms = 14
        elif kind == "destroy":
            ls = '-'
            style = '-|>'
            ms = 14
        else:  # sync
            ls = '-'
            style = '-|>'
            ms = 14

        ax.annotate("", xy=(x_end, y), xytext=(x_start, y),
                    arrowprops=dict(arrowstyle=style, color='black',
                                    lw=1.3, mutation_scale=ms,
                                    linestyle=ls),
                    zorder=8)

        # Подпись над стрелкой по центру
        mid_x = (from_x + to_x) / 2
        ax.text(mid_x, y + 0.13, label,
                ha='center', va='bottom',
                fontname=FONT, fontsize=9.5,
                zorder=9)

        # Уничтожение: большой ✕ на линии жизни цели в точке прихода
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


# ===================== UI =====================
print("UI:")
render("seq-ui-normal.png",
    [":Пользователь", ":App", ":MainController", ":Task", ":TestCaseRow"],
    [
        {"from": ":Пользователь", "to": ":App", "label": "1: launch(args)"},
        {"from": ":App", "to": ":MainController", "label": "2: load('main.fxml')", "kind": "create"},
        {"from": ":Пользователь", "to": ":MainController", "label": "3: onParse()"},
        {"from": ":Пользователь", "to": ":MainController", "label": "4: onGenerate()"},
        {"from": ":Пользователь", "to": ":MainController", "label": "5: onRunTests()"},
        {"from": ":MainController", "to": ":Task", "label": "6: start()", "kind": "async"},
        {"from": ":Task", "to": ":MainController", "label": "7: onSucceeded(result)", "kind": "return"},
        {"from": ":MainController", "to": ":TestCaseRow", "label": "8: new(...)", "kind": "create"},
        {"from": ":MainController", "to": ":Пользователь", "label": "9: показ таблицы", "kind": "return"},
    ])

render("seq-ui-user-interrupt.png",
    [":Пользователь", ":MainController", ":FileChooser", ":Task"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onSelectXml()"},
        {"from": ":MainController", "to": ":FileChooser", "label": "2: showOpenDialog()"},
        {"from": ":Пользователь", "to": ":FileChooser", "label": "3: Esc / Cancel"},
        {"from": ":FileChooser", "to": ":MainController", "label": "4: null (отмена)", "kind": "return"},
        {"from": ":Пользователь", "to": ":MainController", "label": "5: onRunTests()"},
        {"from": ":MainController", "to": ":Task", "label": "6: start()", "kind": "async"},
        {"from": ":Пользователь", "to": ":MainController", "label": "7: закрыть окно"},
        {"from": ":MainController", "to": ":Task", "label": "8: уничтожить", "kind": "destroy"},
    ])

render("seq-ui-system-interrupt.png",
    [":Пользователь", ":App", ":FXMLLoader", ":MainController"],
    [
        {"from": ":Пользователь", "to": ":App", "label": "1: launch()"},
        {"from": ":App", "to": ":FXMLLoader", "label": "2: load('main.fxml')"},
        {"from": ":FXMLLoader", "to": ":App", "label": "3: IOException", "kind": "return"},
        {"from": ":App", "to": ":MainController", "label": "4: НЕ создан", "kind": "destroy"},
        {"from": ":App", "to": ":Пользователь", "label": "5: stderr трейс", "kind": "return"},
    ])

# ===================== PARSER =====================
print("Parser:")
render("seq-parser-normal.png",
    [":MainController", ":XmlModelParser", ":EntityParser", ":PropertyGroupParser", ":SearchParser", "StaxUtils", "XmlNamespaces"],
    [
        {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
        {"from": ":XmlModelParser", "to": "XmlNamespaces", "label": "2: NS_E3 (сверка)"},
        {"from": ":XmlModelParser", "to": ":EntityParser", "label": "3: parseObject(reader) [цикл]"},
        {"from": ":EntityParser", "to": ":PropertyGroupParser", "label": "4: parsePropertyGroup(reader)"},
        {"from": ":PropertyGroupParser", "to": "StaxUtils", "label": "5: attr(reader, name)"},
        {"from": "StaxUtils", "to": ":PropertyGroupParser", "label": "6: значение", "kind": "return"},
        {"from": ":PropertyGroupParser", "to": ":EntityParser", "label": "7: PropertyGroup", "kind": "return"},
        {"from": ":EntityParser", "to": ":XmlModelParser", "label": "8: EntityObject", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":SearchParser", "label": "9: parseSearches(reader)"},
        {"from": ":SearchParser", "to": "StaxUtils", "label": "10: parseInt(value)"},
        {"from": "StaxUtils", "to": ":SearchParser", "label": "11: int", "kind": "return"},
        {"from": ":SearchParser", "to": ":XmlModelParser", "label": "12: List<Search>", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":MainController", "label": "13: AppModel", "kind": "return"},
    ])

render("seq-parser-user-interrupt.png",
    [":Пользователь", ":MainController", ":FileChooser", ":XmlModelParser"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onSelectXml + onParse"},
        {"from": ":MainController", "to": ":FileChooser", "label": "2: showOpenDialog()"},
        {"from": ":Пользователь", "to": ":FileChooser", "label": "3: Cancel"},
        {"from": ":FileChooser", "to": ":MainController", "label": "4: null", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "5: if(file==null)→return", "kind": "self"},
        {"from": ":MainController", "to": ":XmlModelParser", "label": "6: parse() НЕ вызван", "kind": "destroy"},
    ])

render("seq-parser-system-interrupt.png",
    [":MainController", ":XmlModelParser", ":XMLStreamReader", ":ParserException"],
    [
        {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
        {"from": ":XmlModelParser", "to": ":XMLStreamReader", "label": "2: createXMLStreamReader()"},
        {"from": ":XMLStreamReader", "to": ":XmlModelParser", "label": "3: XMLStreamException", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":ParserException", "label": "4: new(msg, cause)", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":MainController", "label": "5: throw наверх", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "6: showAlert('ошибка')", "kind": "self"},
    ])

# ===================== MODEL =====================
print("Model:")
render("seq-model-normal.png",
    [":XmlModelParser", ":AppModel", ":EntityObject", ":PropertyGroup", ":Property"],
    [
        {"from": ":XmlModelParser", "to": ":AppModel", "label": "1: new AppModel()", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":EntityObject", "label": "2: new EntityObject(...)", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":PropertyGroup", "label": "3: new PropertyGroup(...)", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":Property", "label": "4: new Property(...)", "kind": "create"},
        {"from": ":PropertyGroup", "to": ":PropertyGroup", "label": "5: properties.add(p)", "kind": "self"},
        {"from": ":EntityObject", "to": ":EntityObject", "label": "6: propertyGroups.add(pg)", "kind": "self"},
        {"from": ":AppModel", "to": ":AppModel", "label": "7: entities.add(e)", "kind": "self"},
        {"from": ":AppModel", "to": ":XmlModelParser", "label": "8: модель заполнена", "kind": "return"},
    ])

render("seq-model-user-interrupt.png",
    [":Пользователь", ":MainController", ":AppModel"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onGenerate()"},
        {"from": ":MainController", "to": ":MainController", "label": "2: if(currentModel==null)", "kind": "self"},
        {"from": ":MainController", "to": ":AppModel", "label": "3: НЕ используется", "kind": "destroy"},
        {"from": ":MainController", "to": ":Пользователь", "label": "4: showAlert('сначала parse')", "kind": "return"},
    ])

render("seq-model-system-interrupt.png",
    [":TestGenerator", ":EntityObject", ":PropertyGroup"],
    [
        {"from": ":TestGenerator", "to": ":EntityObject", "label": "1: getFormView()"},
        {"from": ":EntityObject", "to": ":TestGenerator", "label": "2: null (нет formView)", "kind": "return"},
        {"from": ":TestGenerator", "to": ":PropertyGroup", "label": "3: .getProperties()"},
        {"from": ":PropertyGroup", "to": ":TestGenerator", "label": "4: NullPointerException", "kind": "return"},
        {"from": ":TestGenerator", "to": ":TestGenerator", "label": "5: catch + лог + skip", "kind": "self"},
    ])

# ===================== GENERATOR =====================
print("Generator:")
render("seq-generator-normal.png",
    [":MainController", ":TestConfig", ":TestGenerator", ":PageObjectWriter", ":TestClassWriter", "TestDataFactory", ":TestRunner", ":RunReportWriter"],
    [
        {"from": ":MainController", "to": ":TestConfig", "label": "1: new(...) + setters", "kind": "create"},
        {"from": ":MainController", "to": ":TestGenerator", "label": "2: new(config)", "kind": "create"},
        {"from": ":MainController", "to": ":TestGenerator", "label": "3: generate(model)"},
        {"from": ":TestGenerator", "to": ":TestGenerator", "label": "4: writePom + writeBaseTest", "kind": "self"},
        {"from": ":TestGenerator", "to": ":PageObjectWriter", "label": "5: write(entity) [цикл]"},
        {"from": ":PageObjectWriter", "to": "TestDataFactory", "label": "6: generateValue(prop)"},
        {"from": "TestDataFactory", "to": ":PageObjectWriter", "label": "7: значение", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":TestGenerator", "label": "8: PageObject готов", "kind": "return"},
        {"from": ":TestGenerator", "to": ":TestClassWriter", "label": "9: write(entity,model) [цикл]"},
        {"from": ":TestClassWriter", "to": ":TestGenerator", "label": "10: TestClass готов", "kind": "return"},
        {"from": ":TestGenerator", "to": ":MainController", "label": "11: проект готов", "kind": "return"},
        {"from": ":MainController", "to": ":TestRunner", "label": "12: run(outputDir, ...)"},
        {"from": ":TestRunner", "to": ":MainController", "label": "13: TestRunResult", "kind": "return"},
        {"from": ":MainController", "to": ":RunReportWriter", "label": "14: writeHtml + writeCsv"},
    ])

render("seq-generator-user-interrupt.png",
    [":Пользователь", ":MainController", ":TestRunner", ":Process"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onRunTests()"},
        {"from": ":MainController", "to": ":TestRunner", "label": "2: run(...)"},
        {"from": ":TestRunner", "to": ":Process", "label": "3: start('mvn test')", "kind": "create"},
        {"from": ":Пользователь", "to": ":MainController", "label": "4: закрыть окно"},
        {"from": ":MainController", "to": ":TestRunner", "label": "5: cancel() (НЕ реализовано)", "kind": "self"},
        {"from": ":TestRunner", "to": ":Process", "label": "6: остаётся жив", "kind": "destroy"},
    ])

render("seq-generator-system-interrupt.png",
    [":MainController", ":TestRunner", ":ProcessBuilder"],
    [
        {"from": ":MainController", "to": ":TestRunner", "label": "1: run(outputDir, ...)"},
        {"from": ":TestRunner", "to": ":ProcessBuilder", "label": "2: start('mvn', 'test')"},
        {"from": ":ProcessBuilder", "to": ":TestRunner", "label": "3: IOException", "kind": "return"},
        {"from": ":TestRunner", "to": ":MainController", "label": "4: throw наверх", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "5: Task.onFailed → showAlert", "kind": "self"},
    ])

# ===================== DATA =====================
print("Data:")
render("seq-data-normal.png",
    [":MainController", ":ReportDao", ":SchemaInitializer", ":DatabaseConnection", ":TestRunDao", ":TestCaseDao"],
    [
        {"from": ":MainController", "to": ":ReportDao", "label": "0: new ReportDao()", "kind": "create"},
        {"from": ":ReportDao", "to": ":SchemaInitializer", "label": "0.1: initialize()"},
        {"from": ":SchemaInitializer", "to": ":DatabaseConnection", "label": "0.2: open()"},
        {"from": ":DatabaseConnection", "to": ":SchemaInitializer", "label": "0.3: Connection", "kind": "return"},
        {"from": ":SchemaInitializer", "to": ":ReportDao", "label": "0.4: схема готова", "kind": "return"},
        {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
        {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "2: open()"},
        {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "3: Connection", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestRunDao", "label": "4: insert(conn, result)"},
        {"from": ":TestRunDao", "to": ":ReportDao", "label": "5: runId", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestCaseDao", "label": "6: insertBatch(conn, runId, cases)"},
        {"from": ":TestCaseDao", "to": ":ReportDao", "label": "7: void", "kind": "return"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "8: commit()", "kind": "self"},
        {"from": ":ReportDao", "to": ":MainController", "label": "9: void", "kind": "return"},
    ])

render("seq-data-user-interrupt.png",
    [":Пользователь", ":MainController", ":ReportDao", ":DatabaseConnection", ":TestRunDao"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: тесты завершены"},
        {"from": ":MainController", "to": ":ReportDao", "label": "2: saveRun(result) (в Task)"},
        {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "3: open()"},
        {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "4: Connection", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestRunDao", "label": "5: insert(conn, result)"},
        {"from": ":Пользователь", "to": ":MainController", "label": "6: закрытие окна"},
        {"from": ":TestRunDao", "to": ":ReportDao", "label": "7: runId", "kind": "return"},
        {"from": ":ReportDao", "to": ":MainController", "label": "8: commit (асинхронно)", "kind": "return"},
    ])

render("seq-data-system-interrupt.png",
    [":MainController", ":ReportDao", ":DatabaseConnection", ":TestRunDao", ":TestCaseDao"],
    [
        {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
        {"from": ":ReportDao", "to": ":DatabaseConnection", "label": "2: open()"},
        {"from": ":DatabaseConnection", "to": ":ReportDao", "label": "3: Connection", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestRunDao", "label": "4: insert(conn, result)"},
        {"from": ":TestRunDao", "to": ":TestRunDao", "label": "5: INSERT → SQLException\n(диск переполнен)", "kind": "self"},
        {"from": ":TestRunDao", "to": ":ReportDao", "label": "6: throw наверх", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestCaseDao", "label": "7: НЕ вызывается", "kind": "destroy"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "8: catch → stderr log", "kind": "self"},
        {"from": ":ReportDao", "to": ":MainController", "label": "9: void (graceful)", "kind": "return"},
    ])

# ===================== COMMON =====================
print("Common:")
render("seq-common-normal.png",
    [":PageObjectWriter", "Transliterator", ":JavaFileWriter", ":ParserException"],
    [
        {"from": ":PageObjectWriter", "to": "Transliterator", "label": "1: toClassName(name)"},
        {"from": "Transliterator", "to": ":PageObjectWriter", "label": "2: 'GskOgsk'", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "3: new()", "kind": "create"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "4: openBlock(...)"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "5: writeLine(...) [цикл]"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "6: closeBlock()"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "7: writeToFile(dir, name)"},
        {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "8: Files.write(path, UTF-8)", "kind": "self"},
        {"from": ":JavaFileWriter", "to": ":PageObjectWriter", "label": "9: void", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":ParserException", "label": "10: НЕ используется в нормальном сценарии", "kind": "destroy"},
    ])

render("seq-common-user-interrupt.png",
    [":Пользователь", ":MainController", ":JavaFileWriter"],
    [
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onGenerate()"},
        {"from": ":MainController", "to": ":JavaFileWriter", "label": "2: writer.writeLine(...) [синхронно]"},
        {"from": ":Пользователь", "to": ":MainController", "label": "3: закрыть окно сейчас"},
        {"from": ":MainController", "to": ":JavaFileWriter", "label": "4: дописывает до конца файла", "kind": "self"},
        {"from": ":JavaFileWriter", "to": ":MainController", "label": "5: текущий файл записан", "kind": "return"},
    ])

render("seq-common-system-interrupt.png",
    [":XmlModelParser", "Transliterator", ":JavaFileWriter", ":ParserException"],
    [
        {"from": ":XmlModelParser", "to": "Transliterator", "label": "1: toClassName(name) (OK)"},
        {"from": "Transliterator", "to": ":XmlModelParser", "label": "2: имя", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":JavaFileWriter", "label": "3: writeToFile (диск переполнен)"},
        {"from": ":JavaFileWriter", "to": ":JavaFileWriter", "label": "4: IOException", "kind": "self"},
        {"from": ":JavaFileWriter", "to": ":XmlModelParser", "label": "5: throw IOException", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":ParserException", "label": "6: new(msg, cause)", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":XmlModelParser", "label": "7: throw ParserException", "kind": "self"},
    ])

print("\nГотово.")
