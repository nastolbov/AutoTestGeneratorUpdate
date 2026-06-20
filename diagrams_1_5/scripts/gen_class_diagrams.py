# -*- coding: utf-8 -*-
"""Детальные диаграммы классов пакетов Parser (Рис.39) и Generator (Рис.53).
Пересозданы по коду ru.autotestgen.{parser,generator}. UML-классы — HTML-таблицы
с тремя компартментами (имя | атрибуты | методы). Стрелки: ассоциация — vee,
композиция — ромб, зависимость — пунктир.
- Parser: SearchParser и XmlNamespaces вынесены в НИЖНИЙ ряд (neato, pinned pos).
- Generator: иерархия (dot) с большими отступами, чтобы стрелки не слипались.
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


ASSOC = 'arrowhead=vee'
COMP = 'dir=both, arrowtail=diamond, arrowhead=vee, arrowsize=1.1'
DEP = 'style=dashed, arrowhead=vee'

# ============================ Parser (Рис.39) ============================
P = {
 "EP": uml("EntityParser",
           ["- pgParser: PropertyGroupParser"],
           ["+ EntityParser()", "+ EntityParser(pgParser: PropertyGroupParser)",
            "+ parseObject(reader: XMLStreamReader): EntityObject",
            "- parseAssociation(reader: XMLStreamReader): Association"]),
 "PGP": uml("PropertyGroupParser", [],
            ["+ parsePropertyGroup(reader: XMLStreamReader): PropertyGroup",
             "- parseProperty(reader: XMLStreamReader): Property",
             "- parseOperation(reader: XMLStreamReader): Operation"]),
 "SU": uml("StaxUtils", [],
           ["+ attr(reader: XMLStreamReader, name: String): String",
            "+ parseInt(value: String): int",
            "+ skipToEnd(reader: XMLStreamReader): void"]),
 "XMP": uml("XmlModelParser",
            ["- entityParser: EntityParser", "- searchParser: SearchParser"],
            ["+ XmlModelParser()",
             "+ XmlModelParser(entityParser: EntityParser, searchParser: SearchParser)",
             "+ parse(xmlFile: File): AppModel"]),
 "SP": uml("SearchParser", [],
           ["+ parseSearches(reader: XMLStreamReader): List&lt;Search&gt;",
            "- parseSingleSearch(reader: XMLStreamReader): Search"]),
 "XN": uml("XmlNamespaces",
           ["+ NS_E: String", "+ NS_E3: String", "+ NS_MD: String"], []),
}
# позиции (y вверх): верхний ряд — EP,PGP; средний — XMP,SU; нижний — SP,XN
pos = {"EP": (0, 5.0), "PGP": (6.2, 5.0), "XMP": (0, 2.6), "SU": (6.2, 2.6),
       "SP": (0, 0.2), "XN": (6.2, 0.2)}
nodes = "\n".join(f'{k} [shape=plaintext, label={P[k]}, pos="{x},{y}!"];' for k, (x, y) in pos.items())
edges = "\n".join([
    f'XMP -> EP [{ASSOC}];',
    f'XMP -> SP [{ASSOC}];',
    f'EP -> PGP [{COMP}];',
    f'EP -> SU [{DEP}];',
    f'EP -> XN [{DEP}];',
    f'PGP -> SU [{DEP}];',
    f'SP -> SU [{DEP}];',
])
run("ris_39_parser_classes", f'''digraph G {{
  layout=neato; bgcolor=white; splines=true; overlap=false; sep="+16"; esep="+8";
  node [fontname="{F}", fontsize=12]; edge [fontname="{F}", fontsize=11, color=black, penwidth=1.1];
{nodes}
{edges}
}}''', "neato")

# ============================ Generator (Рис.53) ============================
G = {
 "TG": uml("TestGenerator", [],
           ["+ generate(model: AppModel, config: TestConfig): void"]),
 "TCfg": uml("TestConfig",
             ["- url, login, password: String", "- outputDir, basePackage: String",
              "- fastMode: boolean", "- testLevel: String"],
             ["+ get/set …()"]),
 "POW": uml("PageObjectWriter", [],
            ["+ write(entity: EntityObject, srcDir: Path): void"]),
 "TCW": uml("TestClassWriter",
            ["- basePackage: String", "- testLevel: String"],
            ["+ write(entity, model, srcDir): void",
             "+ writeChildTest(entity, model, srcDir, cls): void",
             "+ writeTreeChildTest(entity, model, srcDir, cls): void"]),
 "TDF": uml("TestDataFactory", [],
            ["+ generateValue(property: Property): String",
             "+ generateFromMask(mask: String): String"]),
 "TR": uml("TestRunner", [],
           ["+ run(projectDir, xmlFile, baseUrl, filter, fastMode): TestRunResult",
            "- parseSurefireReports(reportsDir): List&lt;TestCaseResult&gt;"]),
 "RRW": uml("RunReportWriter", [],
            ["+ write(result: TestRunResult, dir: Path): void",
             "- writeCsv(result, dir): void"]),
}
gnodes = "\n".join(f'{k} [shape=plaintext, label={v}];' for k, v in G.items())
gedges = "\n".join([
    '{ rank=same; TG; TR; }',
    '{ rank=same; POW; TCW; TCfg; RRW; }',
    f'TG -> TCfg [{DEP}, label="config"];',
    f'TG -> POW [{DEP}];',
    f'TG -> TCW [{DEP}];',
    f'TR -> RRW [{DEP}];',
    f'POW -> TDF [{DEP}];',
    f'TCW -> TDF [{DEP}];',
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
