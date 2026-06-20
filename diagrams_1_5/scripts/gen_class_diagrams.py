# -*- coding: utf-8 -*-
"""Детальные диаграммы классов пакетов Parser (Рис.39) и Generator (Рис.53).
Содержимое выверено: Parser — 1-в-1 с оригиналом (media/image40.png) + код;
Generator — по коду ru.autotestgen.generator (оригинала в репозитории нет).
UML-классы — HTML-таблицы (имя | поля | методы).
Виды связи: композиция — ◆ (заливной ромб у владельца); направленная
ассоциация — сплошная линия с открытой стрелкой ▷; зависимость — пунктир ⇢.
- Parser: SearchParser и XmlNamespaces — в НИЖНЕМ ряду.
- Generator: иерархия (dot), стрелки разведены.
Выход: diagrams_1_5/ris_39_parser_classes.png, ris_53_generator_classes.png."""
import subprocess
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
F = "Liberation Serif"
BR = '<br align="left"/>'


def uml(name, attrs, methods):
    a = (BR.join(attrs) + BR) if attrs else " "
    m = (BR.join(methods) + BR) if methods else " "
    return ('<<table border="1" cellborder="0" cellspacing="0" cellpadding="3">'
            f'<tr><td align="center"><b>{name}</b></td></tr><hr/>'
            f'<tr><td align="left">{a}</td></tr><hr/>'
            f'<tr><td align="left">{m}</td></tr></table>>')


def run(name, dot, engine):
    open(f"/tmp/{name}.dot", "w").write(dot)
    subprocess.run([engine, "-Tpng", "-Gdpi=200", f"/tmp/{name}.dot", "-o", f"{OUT}/{name}.png"], check=True)
    w, h = Image.open(f"{OUT}/{name}.png").size
    print(f"{name}: {w}x{h}  ratio {w/h:.2f}")


ASSOC = 'arrowhead=vee'                                   # направленная ассоциация (сплошная)
COMP = 'dir=both, arrowtail=diamond, arrowhead=none, headlabel="1"'  # композиция (◆ у владельца)
DEP = 'style=dashed, arrowhead=vee'                        # зависимость (⇢)

# ============================ Parser (Рис.39) ============================
P = {
 "EP": uml("EntityParser",
           ["- pgParser: PropertyGroupParser"],
           ["+ EntityParser()",
            "+ EntityParser(pgParser: PropertyGroupParser)",
            "+ parseObject(reader: XMLStreamReader): model::EntityObject",
            "- parseAssociation(reader: XMLStreamReader): model::Association"]),
 "PGP": uml("PropertyGroupParser", [],
            ["+ parsePropertyGroup(reader: XMLStreamReader): model::PropertyGroup",
             "- parseProperty(reader: XMLStreamReader): model::Property",
             "- parseOperation(reader: XMLStreamReader): model::Operation"]),
 "SU": uml("StaxUtils", [],
           ["- StaxUtils()",
            "+ attr(reader: XMLStreamReader, name: String): String",
            "+ parseInt(value: String): int",
            "+ skipToEnd(reader: XMLStreamReader): void"]),
 "XMP": uml("XmlModelParser",
            ["- entityParser: EntityParser", "- searchParser: SearchParser"],
            ["+ XmlModelParser()",
             "+ XmlModelParser(entityParser: EntityParser, searchParser: SearchParser)",
             "+ parse(xmlFile: File): model::AppModel"]),
 "SP": uml("SearchParser", [],
           ["+ parseSearches(reader: XMLStreamReader): List&lt;model::Search&gt;",
            "- parseSingleSearch(reader: XMLStreamReader): model::Search"]),
 "XN": uml("XmlNamespaces",
           ["+ NS_E: String", "+ NS_E3: String", "+ NS_MD: String"],
           ["- XmlNamespaces()"]),
}
pos = {"EP": (0, 5.0), "PGP": (6.4, 5.0), "XMP": (0, 2.6), "SU": (6.4, 2.6),
       "SP": (0, 0.2), "XN": (6.4, 0.2)}
nodes = "\n".join(f'{k} [shape=plaintext, label={P[k]}, pos="{x},{y}!"];' for k, (x, y) in pos.items())
edges = "\n".join([
    f'XMP -> EP [{COMP}];',     # XmlModelParser ◆— EntityParser (поле entityParser)
    f'XMP -> SP [{COMP}];',     # XmlModelParser ◆— SearchParser (поле searchParser)
    f'EP -> PGP [{COMP}];',     # EntityParser ◆— PropertyGroupParser (поле pgParser)
    f'EP -> SU [{ASSOC}];',     # EntityParser ▷ StaxUtils
    f'EP -> XN [{ASSOC}];',     # EntityParser ▷ XmlNamespaces
    f'PGP -> SU [{ASSOC}];',    # PropertyGroupParser ▷ StaxUtils
    f'SP -> SU [{ASSOC}];',     # SearchParser ▷ StaxUtils
])
run("ris_39_parser_classes", f'''digraph G {{
  layout=neato; bgcolor=white; splines=true; overlap=false; sep="+16"; esep="+8";
  node [fontname="{F}", fontsize=12];
  edge [fontname="{F}", fontsize=11, color=black, penwidth=1.1, labelfontsize=11];
{nodes}
{edges}
}}''', "neato")

# ============================ Generator (Рис.53) ============================
G = {
 "TG": uml("TestGenerator",
           ["- config: TestConfig"],
           ["+ TestGenerator(config: TestConfig)",
            "+ generate(model: AppModel): void",
            "+ folderNameForXml(xmlFileName: String): String {static}",
            "+ resolveProjectDir(baseOutput: Path, xmlFileName: String): Path {static}",
            "- generatePom() / generateSharedDriver()",
            "- generateBaseTest() / generateTestData()"]),
 "TCfg": uml("TestConfig",
             ["- baseUrl, login, password: String", "- outputDir: Path",
              "- browserType, basePackage, siteType: String",
              "- subsystemName, testLevel, sourceXmlName: String",
              "- smokeAllSubsystems: boolean"],
             ["+ геттеры/сеттеры всех полей"]),
 "POW": uml("PageObjectWriter",
            ["- basePackage: String"],
            ["+ PageObjectWriter(basePackage: String)",
             "+ write(entity: EntityObject, outputDir: Path): void"]),
 "TCW": uml("TestClassWriter",
            ["- basePackage: String", "- testLevel: String"],
            ["+ TestClassWriter(basePackage: String, testLevel: String)",
             "+ write(entity: EntityObject, model: AppModel, outputDir: Path): void",
             "+ write(entity, model, outputDir, disabledReason: String): void",
             "+ writeChildTest(entity, model, outputDir, twin): void",
             "+ writeTreeChildTest(entity, model, outputDir, parent): void"]),
 "TDF": uml("TestDataFactory", [],
            ["+ generateValue(property: Property): String {static}",
             "+ generateSearchParamValue(param: SearchParam): String {static}",
             "+ generateFromMask(mask: String): String {static}",
             "+ generateValueExpression(property: Property): String {static}"]),
 "TR": uml("TestRunner", [],
           ["+ run(projectDir: Path, xmlFileName: String, baseUrl: String): TestRunResult",
            "+ run(…, lineConsumer / testFilter, fastMode): TestRunResult",
            "+ getLastMavenOutput(): String",
            "- parseSurefireReports(reportsDir: Path, result: TestRunResult): void",
            "- linkScreenshots(shotDir: Path, result: TestRunResult): void"]),
 "RRW": uml("RunReportWriter", [],
            ["+ write(htmlPath: Path, result: TestRunResult): void",
             "+ writeCsv(csvPath: Path, result: TestRunResult): void"]),
}
gnodes = "\n".join(f'{k} [shape=plaintext, label={v}];' for k, v in G.items())
gedges = "\n".join([
    '{ rank=same; TG; TR; }',
    '{ rank=same; TCfg; POW; TCW; RRW; }',
    f'TG -> TCfg [{ASSOC}];',   # поле config: TestConfig (ассоциация)
    f'TG -> POW [{DEP}];',      # создаёт/использует
    f'TG -> TCW [{DEP}];',
    f'POW -> TDF [{DEP}];',     # static-вызовы TestDataFactory
    f'TCW -> TDF [{DEP}];',
    f'TR -> RRW [{DEP}];',      # формирует отчёты
])
run("ris_53_generator_classes", f'''digraph G {{
  layout=dot; rankdir=TB; bgcolor=white; splines=polyline;
  nodesep=0.7; ranksep=1.05;
  node [shape=plaintext, fontname="{F}", fontsize=12];
  edge [fontname="{F}", fontsize=11, color=black, penwidth=1.1];
{gnodes}
{gedges}
}}''', "dot")

print("done")
