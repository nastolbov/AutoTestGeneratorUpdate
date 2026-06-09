"""
Генератор UML sequence diagrams (ДПС) — версия 2.
Правильная нотация по §7 учебника Иванова Г.С., рис. 7.6-7.9:
  • Объект — прямоугольник с подчёркнутым именем ":Класс" сверху
  • Линия жизни — пунктирная вертикаль от нижней грани объекта вниз
  • Активация — узкий БЕЛЫЙ прямоугольник с чёрной рамкой,
    наложенный поверх линии жизни на время активности объекта
  • Синхронное сообщение — сплошная стрелка с заполненным треугольником
  • Возврат — пунктирная стрелка
  • Self-call — петля
  • Уничтожение — большой ✕ на конце линии жизни
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch, ConnectionPatch
import os

OUT_DIR = "/home/user/AutoTestGeneratorUpdate/diagrams"
FONT = "Liberation Serif"

# Y-координаты:
#   1.0 — низ диаграммы
#   8.5 — низ прямоугольников с именами
#   9.5 — верх прямоугольников
#   Сообщения раскидываются от ~8.0 до 1.5

def render(filename, objects, messages, figsize=None):
    """
    objects: список имён ":Класс" или "Имя объекта"
    messages: список словарей:
        {"from": "A", "to": "B", "label": "1: метод()",
         "kind": "sync"/"return"/"async"/"create"/"destroy"/"self"}
    """
    n = len(objects)
    if figsize is None:
        figsize = (max(9, n * 2.2), max(6, len(messages) * 0.55 + 2.5))

    fig, ax = plt.subplots(figsize=figsize, dpi=140)
    ax.set_xlim(0, n + 1)
    ax.set_ylim(0, 10)
    ax.axis('off')

    # X-координаты объектов
    x_pos = {obj: i + 1 for i, obj in enumerate(objects)}

    # 1) Сами прямоугольники объектов сверху + подчёркнутое имя
    box_h = 0.7
    box_top = 9.6
    box_bot = box_top - box_h
    for obj in objects:
        x = x_pos[obj]
        # Рамка объекта
        ax.add_patch(Rectangle((x - 0.7, box_bot), 1.4, box_h,
                               linewidth=1.3, edgecolor='black', facecolor='white'))
        # Имя с подчёркиванием (UML: имя объекта подчёркнуто)
        ax.text(x, (box_top + box_bot) / 2, obj,
                ha='center', va='center', fontname=FONT, fontsize=11,
                fontweight='normal')
        # Подчёркивание имени — линия под текстом
        text_y = (box_top + box_bot) / 2 - 0.13
        # Длина подчёркивания — приблизительно ширина текста
        underline_w = max(0.4, len(obj) * 0.062)
        ax.plot([x - underline_w / 2, x + underline_w / 2],
                [text_y, text_y], color='black', linewidth=0.8)

    # 2) Линии жизни — пунктир сверху донизу
    life_bottom = 0.5
    for obj in objects:
        x = x_pos[obj]
        ax.plot([x, x], [box_bot - 0.02, life_bottom],
                linestyle=(0, (4, 4)), color='black', linewidth=0.9)

    # 3) Y-координаты сообщений
    msg_top = box_bot - 0.6
    msg_count = len(messages)
    if msg_count > 0:
        y_step = (msg_top - 1.2) / max(msg_count, 1)
        y_pos = [msg_top - i * y_step for i in range(msg_count)]
    else:
        y_pos = []

    # 4) Активации — определяем, какой объект активен в какой момент
    # Простая логика: активация = узкий прямоугольник от первого вызова к объекту до последнего "return" от него
    activations = {}  # obj -> list of (y_start, y_end)
    active_starts = {}  # obj -> y_start
    for i, m in enumerate(messages):
        y = y_pos[i]
        kind = m.get("kind", "sync")
        to = m["to"]
        frm = m["from"]
        if kind in ("return",):
            # завершить активацию у frm (он возвращает результат)
            if frm in active_starts:
                activations.setdefault(frm, []).append((active_starts.pop(frm), y))
        elif kind == "destroy":
            # завершить + поставить X
            if to in active_starts:
                activations.setdefault(to, []).append((active_starts.pop(to), y))
        else:  # sync, async, create, self
            if to != frm and to not in active_starts:
                active_starts[to] = y
    # Закрыть незакрытые активации до низа
    for obj, y_start in list(active_starts.items()):
        activations.setdefault(obj, []).append((y_start, life_bottom + 0.3))

    # Нарисовать активации поверх линий жизни
    act_w = 0.18
    for obj, acts in activations.items():
        x = x_pos[obj]
        for y_start, y_end in acts:
            h = y_start - y_end
            if h > 0.05:
                ax.add_patch(Rectangle((x - act_w / 2, y_end), act_w, h,
                                       linewidth=1.0, edgecolor='black', facecolor='white'))

    # 5) Рисуем сообщения
    for i, m in enumerate(messages):
        y = y_pos[i]
        from_x = x_pos[m["from"]]
        to_x = x_pos[m["to"]]
        kind = m.get("kind", "sync")
        label = m["label"]

        if from_x == to_x:
            # self-call: петля справа
            lw = 0.55
            # рисуем рамку петли вручную через 3 линии
            ax.plot([from_x + act_w / 2, from_x + act_w / 2 + lw], [y, y],
                    color='black', linewidth=1.2)
            ax.plot([from_x + act_w / 2 + lw, from_x + act_w / 2 + lw],
                    [y, y - 0.25], color='black', linewidth=1.2)
            # стрелка обратно
            arr = FancyArrowPatch(
                (from_x + act_w / 2 + lw, y - 0.25),
                (from_x + act_w / 2, y - 0.25),
                arrowstyle='-|>', mutation_scale=10,
                color='black', linewidth=1.2)
            ax.add_patch(arr)
            ax.text(from_x + act_w / 2 + lw + 0.1, y - 0.12, label,
                    ha='left', va='center', fontname=FONT, fontsize=9)
        else:
            # сторона активации: от края узкого прямоугольника
            x_start = from_x + (act_w / 2 if to_x > from_x else -act_w / 2)
            x_end = to_x - (act_w / 2 if to_x > from_x else -act_w / 2)

            # Тип стрелки
            if kind == "return":
                style = '->'      # лёгкая стрелка
                ls = '--'         # пунктир
            elif kind == "async":
                style = '->'      # половинка стрелки в UML, но в matplotlib не из коробки
                ls = '-'
            elif kind == "create":
                style = '-|>'     # с заполненным треугольником
                ls = '--'         # пунктир для create
            elif kind == "destroy":
                style = '-|>'
                ls = '-'
            else:  # sync
                style = '-|>'
                ls = '-'

            arr = FancyArrowPatch((x_start, y), (x_end, y),
                                  arrowstyle=style, mutation_scale=14,
                                  color='black', linewidth=1.2,
                                  linestyle=ls)
            ax.add_patch(arr)

            # подпись над стрелкой по центру
            mid_x = (from_x + to_x) / 2
            ax.text(mid_x, y + 0.12, label,
                    ha='center', va='bottom', fontname=FONT, fontsize=9)

            # ✕ при уничтожении
            if kind == "destroy":
                xs = 0.2
                ax.plot([to_x - xs, to_x + xs], [y - xs, y + xs],
                        color='black', linewidth=2.5)
                ax.plot([to_x - xs, to_x + xs], [y + xs, y - xs],
                        color='black', linewidth=2.5)

    plt.tight_layout(pad=0.4)
    plt.savefig(os.path.join(OUT_DIR, filename), dpi=140,
                bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close(fig)
    print(f"  ✓ {filename}")


# ============================================
# ============   UI ПАКЕТ   ==================
# ============================================
print("UI пакет:")
render("seq-ui-normal.png",
    objects=[":Пользователь", ":App", ":MainController", ":Task", ":TestCaseRow"],
    messages=[
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
    objects=[":Пользователь", ":MainController", ":FileChooser", ":Task"],
    messages=[
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
    objects=[":Пользователь", ":App", ":FXMLLoader", ":MainController"],
    messages=[
        {"from": ":Пользователь", "to": ":App", "label": "1: launch()"},
        {"from": ":App", "to": ":FXMLLoader", "label": "2: load('main.fxml')"},
        {"from": ":FXMLLoader", "to": ":App", "label": "3: IOException", "kind": "return"},
        {"from": ":App", "to": ":MainController", "label": "4: НЕ создан", "kind": "destroy"},
        {"from": ":App", "to": ":Пользователь", "label": "5: stderr трейс", "kind": "return"},
    ])

# ============================================
# ============  PARSER ПАКЕТ  ================
# ============================================
print("Parser пакет:")
render("seq-parser-normal.png",
    objects=[":MainController", ":XmlModelParser", ":EntityParser", ":PropertyGroupParser", ":SearchParser"],
    messages=[
        {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
        {"from": ":XmlModelParser", "to": ":EntityParser", "label": "2: parseObject(reader) [цикл]"},
        {"from": ":EntityParser", "to": ":PropertyGroupParser", "label": "3: parsePropertyGroup(reader)"},
        {"from": ":PropertyGroupParser", "to": ":EntityParser", "label": "4: PropertyGroup", "kind": "return"},
        {"from": ":EntityParser", "to": ":XmlModelParser", "label": "5: EntityObject", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":SearchParser", "label": "6: parseSearches(reader)"},
        {"from": ":SearchParser", "to": ":XmlModelParser", "label": "7: List<Search>", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":MainController", "label": "8: AppModel", "kind": "return"},
    ])

render("seq-parser-user-interrupt.png",
    objects=[":Пользователь", ":MainController", ":FileChooser", ":XmlModelParser"],
    messages=[
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onSelectXml + onParse"},
        {"from": ":MainController", "to": ":FileChooser", "label": "2: showOpenDialog()"},
        {"from": ":Пользователь", "to": ":FileChooser", "label": "3: Cancel"},
        {"from": ":FileChooser", "to": ":MainController", "label": "4: null", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "5: if(file==null)→return", "kind": "self"},
        {"from": ":MainController", "to": ":XmlModelParser", "label": "6: parse() НЕ вызван", "kind": "destroy"},
    ])

render("seq-parser-system-interrupt.png",
    objects=[":MainController", ":XmlModelParser", ":XMLStreamReader", ":ParserException"],
    messages=[
        {"from": ":MainController", "to": ":XmlModelParser", "label": "1: parse(file)"},
        {"from": ":XmlModelParser", "to": ":XMLStreamReader", "label": "2: createXMLStreamReader()"},
        {"from": ":XMLStreamReader", "to": ":XmlModelParser", "label": "3: XMLStreamException", "kind": "return"},
        {"from": ":XmlModelParser", "to": ":ParserException", "label": "4: new(msg, cause)", "kind": "create"},
        {"from": ":XmlModelParser", "to": ":MainController", "label": "5: throw наверх", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "6: showAlert('ошибка')", "kind": "self"},
    ])

# ============================================
# ============  MODEL ПАКЕТ   ================
# ============================================
print("Model пакет:")
render("seq-model-normal.png",
    objects=[":XmlModelParser", ":AppModel", ":EntityObject", ":PropertyGroup", ":Property"],
    messages=[
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
    objects=[":Пользователь", ":MainController", ":AppModel"],
    messages=[
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onGenerate()"},
        {"from": ":MainController", "to": ":MainController", "label": "2: if(currentModel==null)", "kind": "self"},
        {"from": ":MainController", "to": ":AppModel", "label": "3: НЕ используется", "kind": "destroy"},
        {"from": ":MainController", "to": ":Пользователь", "label": "4: showAlert('сначала parse')", "kind": "return"},
    ])

render("seq-model-system-interrupt.png",
    objects=[":TestGenerator", ":EntityObject", ":PropertyGroup"],
    messages=[
        {"from": ":TestGenerator", "to": ":EntityObject", "label": "1: getFormView()"},
        {"from": ":EntityObject", "to": ":TestGenerator", "label": "2: null (нет formView)", "kind": "return"},
        {"from": ":TestGenerator", "to": ":PropertyGroup", "label": "3: .getProperties()"},
        {"from": ":PropertyGroup", "to": ":TestGenerator", "label": "4: NullPointerException", "kind": "return"},
        {"from": ":TestGenerator", "to": ":TestGenerator", "label": "5: catch + лог + skip", "kind": "self"},
    ])

# ============================================
# ==========  GENERATOR ПАКЕТ  ===============
# ============================================
print("Generator пакет:")
render("seq-generator-normal.png",
    objects=[":MainController", ":TestGenerator", ":PageObjectWriter", ":TestClassWriter", ":TestDataFactory"],
    messages=[
        {"from": ":MainController", "to": ":TestGenerator", "label": "1: generate(model)"},
        {"from": ":TestGenerator", "to": ":TestGenerator", "label": "2: writePom + writeBaseTest", "kind": "self"},
        {"from": ":TestGenerator", "to": ":PageObjectWriter", "label": "3: write(entity) [цикл]"},
        {"from": ":PageObjectWriter", "to": ":TestDataFactory", "label": "4: generateValue(prop)"},
        {"from": ":TestDataFactory", "to": ":PageObjectWriter", "label": "5: значение", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":TestGenerator", "label": "6: PageObject готов", "kind": "return"},
        {"from": ":TestGenerator", "to": ":TestClassWriter", "label": "7: write(entity,model) [цикл]"},
        {"from": ":TestClassWriter", "to": ":TestGenerator", "label": "8: TestClass готов", "kind": "return"},
        {"from": ":TestGenerator", "to": ":MainController", "label": "9: проект сгенерирован", "kind": "return"},
    ])

render("seq-generator-user-interrupt.png",
    objects=[":Пользователь", ":MainController", ":TestRunner", ":Process"],
    messages=[
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onRunTests()"},
        {"from": ":MainController", "to": ":TestRunner", "label": "2: run(...)"},
        {"from": ":TestRunner", "to": ":Process", "label": "3: start('mvn test')", "kind": "create"},
        {"from": ":Пользователь", "to": ":MainController", "label": "4: закрыть окно"},
        {"from": ":MainController", "to": ":TestRunner", "label": "5: cancel() (НЕ реализовано)", "kind": "self"},
        {"from": ":TestRunner", "to": ":Process", "label": "6: остаётся жив", "kind": "destroy"},
    ])

render("seq-generator-system-interrupt.png",
    objects=[":MainController", ":TestRunner", ":ProcessBuilder"],
    messages=[
        {"from": ":MainController", "to": ":TestRunner", "label": "1: run(outputDir, ...)"},
        {"from": ":TestRunner", "to": ":ProcessBuilder", "label": "2: start('mvn', 'test')"},
        {"from": ":ProcessBuilder", "to": ":TestRunner", "label": "3: IOException ('mvn not found')", "kind": "return"},
        {"from": ":TestRunner", "to": ":MainController", "label": "4: throw наверх", "kind": "return"},
        {"from": ":MainController", "to": ":MainController", "label": "5: Task.onFailed → showAlert", "kind": "self"},
    ])

# ============================================
# ===========  DATA ПАКЕТ  ===================
# ============================================
print("Data пакет:")
render("seq-data-normal.png",
    objects=[":MainController", ":ReportDao", ":TestRunDao", ":TestCaseDao", ":autotestgen.db"],
    messages=[
        {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "2: conn = open()", "kind": "self"},
        {"from": ":ReportDao", "to": ":TestRunDao", "label": "3: insert(conn, result)"},
        {"from": ":TestRunDao", "to": ":autotestgen.db", "label": "4: INSERT test_run"},
        {"from": ":autotestgen.db", "to": ":TestRunDao", "label": "5: runId", "kind": "return"},
        {"from": ":TestRunDao", "to": ":ReportDao", "label": "6: runId", "kind": "return"},
        {"from": ":ReportDao", "to": ":TestCaseDao", "label": "7: insertBatch(conn, runId, cases)"},
        {"from": ":TestCaseDao", "to": ":autotestgen.db", "label": "8: batch INSERT test_case"},
        {"from": ":ReportDao", "to": ":autotestgen.db", "label": "9: commit()"},
        {"from": ":ReportDao", "to": ":MainController", "label": "10: void", "kind": "return"},
    ])

render("seq-data-user-interrupt.png",
    objects=[":Пользователь", ":MainController", ":ReportDao"],
    messages=[
        {"from": ":Пользователь", "to": ":MainController", "label": "1: тесты завершены"},
        {"from": ":MainController", "to": ":ReportDao", "label": "2: saveRun(result) (в Task)"},
        {"from": ":Пользователь", "to": ":MainController", "label": "3: закрытие окна сейчас"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "4: транзакция продолжается асинхронно", "kind": "self"},
        {"from": ":ReportDao", "to": ":MainController", "label": "5: commit или rollback", "kind": "return"},
    ])

render("seq-data-system-interrupt.png",
    objects=[":MainController", ":ReportDao", ":TestRunDao", ":autotestgen.db"],
    messages=[
        {"from": ":MainController", "to": ":ReportDao", "label": "1: saveRun(result)"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "2: open + setAutoCommit(false)", "kind": "self"},
        {"from": ":ReportDao", "to": ":TestRunDao", "label": "3: insert(conn, result)"},
        {"from": ":TestRunDao", "to": ":autotestgen.db", "label": "4: INSERT (диск переполнен)"},
        {"from": ":autotestgen.db", "to": ":TestRunDao", "label": "5: SQLException", "kind": "return"},
        {"from": ":TestRunDao", "to": ":ReportDao", "label": "6: throw наверх", "kind": "return"},
        {"from": ":ReportDao", "to": ":ReportDao", "label": "7: catch → stderr log", "kind": "self"},
        {"from": ":ReportDao", "to": ":MainController", "label": "8: void (graceful)", "kind": "return"},
    ])

# ============================================
# ==========  COMMON ПАКЕТ  ==================
# ============================================
print("Common пакет:")
render("seq-common-normal.png",
    objects=[":PageObjectWriter", ":Transliterator", ":JavaFileWriter", ":Files"],
    messages=[
        {"from": ":PageObjectWriter", "to": ":Transliterator", "label": "1: toClassName(name)"},
        {"from": ":Transliterator", "to": ":PageObjectWriter", "label": "2: 'GskOgsk'", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "3: new()", "kind": "create"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "4: openBlock(...)"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "5: writeLine(...) [цикл]"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "6: closeBlock()"},
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "7: writeToFile(dir, name)"},
        {"from": ":JavaFileWriter", "to": ":Files", "label": "8: Files.write(path, UTF-8)"},
        {"from": ":Files", "to": ":JavaFileWriter", "label": "9: OK", "kind": "return"},
        {"from": ":JavaFileWriter", "to": ":PageObjectWriter", "label": "10: void", "kind": "return"},
    ])

render("seq-common-user-interrupt.png",
    objects=[":Пользователь", ":MainController", ":JavaFileWriter"],
    messages=[
        {"from": ":Пользователь", "to": ":MainController", "label": "1: onGenerate()"},
        {"from": ":MainController", "to": ":JavaFileWriter", "label": "2: writer.writeLine(...) [синхронно]"},
        {"from": ":Пользователь", "to": ":MainController", "label": "3: закрыть окно сейчас"},
        {"from": ":MainController", "to": ":JavaFileWriter", "label": "4: дописывает до конца файла", "kind": "self"},
        {"from": ":JavaFileWriter", "to": ":MainController", "label": "5: текущий файл записан", "kind": "return"},
    ])

render("seq-common-system-interrupt.png",
    objects=[":PageObjectWriter", ":JavaFileWriter", ":Files"],
    messages=[
        {"from": ":PageObjectWriter", "to": ":JavaFileWriter", "label": "1: writeToFile(dir, name)"},
        {"from": ":JavaFileWriter", "to": ":Files", "label": "2: createDirectories(dir)"},
        {"from": ":Files", "to": ":JavaFileWriter", "label": "3: AccessDeniedException", "kind": "return"},
        {"from": ":JavaFileWriter", "to": ":PageObjectWriter", "label": "4: IOException", "kind": "return"},
        {"from": ":PageObjectWriter", "to": ":PageObjectWriter", "label": "5: throw наверх в TestGenerator", "kind": "self"},
    ])

print("\nВсего ДПС:", 18)
