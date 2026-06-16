# -*- coding: utf-8 -*-
"""Диаграммы кооперации пакетов parser/model/data/generator/common (раздел 1.5).
Компактная радиальная раскладка; на каждую пару объектов — ОДНА связь с общей
подписью (вызов + отклик + альтернативы), чтобы стрелки не накладывались и не были
растянуты. Содержание — по реальному коду и исходным диаграммам пользователя."""
import subprocess, os
from PIL import Image
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
F = "Liberation Serif"

def render(name, nodes, edges):
    ns=[]
    for nid,label,x,y in nodes:
        sh='shape=box, style="rounded"' if nid=="gr" else 'shape=box'
        ns.append(f'{nid} [label="{label}", {sh}, pos="{x},{y}!"];')
    es=[f'{s} -> {d} [label="{l}"];' for s,d,l in edges]
    dot=f'''digraph G {{
  layout=neato; bgcolor=white; fontname="{F}"; splines=true;
  node [fontname="{F}", fontsize=14, color=black, fontcolor=black, penwidth=1.4];
  edge [fontname="{F}", fontsize=12, color=black, fontcolor=black, penwidth=1.1, arrowhead=vee];
{chr(10).join(ns)}
{chr(10).join(es)}
}}'''
    open(f"/tmp/{name}.dot","w").write(dot)
    subprocess.run(["neato","-Tpng","-Gdpi=200",f"/tmp/{name}.dot","-o",f"{OUT}/{name}.png"],check=True)
    w,h=Image.open(f"{OUT}/{name}.png").size
    print(f"{name}: {w}x{h} ratio {w/h:.2f}")

# ---- parser (Рис. 37) ----
render("ris_37_coop_parser",
[("gr","граница\\nпакета",0,2.7),("XMP",":XmlModelParser",3.3,2.7),("SP",":SearchParser",3.3,0.5),
 ("EP",":EntityParser",6.7,2.7),("PGP",":PropertyGroupParser",8.8,4.6),("SU",":StaxUtils",9.9,2.7),
 ("XN",":XmlNamespaces",8.8,0.7)],
[("gr","XMP","1: parse(файл)\\l14: модель метаданных (Model)\\l(п1, с1 / п4, с6: с ошибкой)\\l"),
 ("XMP","EP","2: parseObject(reader)\\l11: объект сущности\\l(п2, с2 / п3, с5: ParserException)\\l"),
 ("XMP","SP","12: parseSearches(reader)\\l13: список поисков\\l"),
 ("EP","PGP","7: parsePropertyGroup(reader)\\l10: группа свойств\\l"),
 ("EP","XN","3: сверка namespace (NS_E3)\\l4: пространство имён\\l"),
 ("EP","SU","5: attr(reader, имя) (с3)\\l6: значение (с4: некорр. структура)\\l"),
 ("PGP","SU","8: attr() / parseInt()\\l9: значения атрибутов\\l")])

# ---- model (Рис. 44) ----
render("ris_44_coop_model",
[("gr","граница\\nпакета",0,2.4),("EC",":EntityClassifier",3.3,2.4),("EO",":EntityObject",7.0,4.4),
 ("PG",":PropertyGroup",7.6,2.4),("AM",":AppModel",7.0,0.4),("CL",":Classification",3.3,-1.3)],
[("gr","EC","1: classify(сущность, модель)\\l10: результат классификации\\l(п1, с1 / п4, с6: справочник)\\l"),
 ("EC","EO","2: getPropertyGroups()\\l3: группы свойств\\l(п2, с2 / п3, с3: пустой список)\\l"),
 ("EC","PG","4: getProperties()\\l5: список свойств\\l"),
 ("EC","AM","6: поиск родителя\\l7: сущность-родитель\\l"),
 ("EC","CL","8: create результат\\l9: результат готов\\l(с4: тривиальный / с5)\\l")])

# ---- data (Рис. 58) ----
render("ris_58_coop_data",
[("gr","граница\\nпакета",0,2.4),("RD",":ReportDao",3.3,2.4),("SI",":SchemaInitializer",6.7,4.6),
 ("DC",":DatabaseConnection",10.0,3.2),("TR",":TestRunDao",6.7,2.0),("TC",":TestCaseDao",6.7,0.0)],
[("gr","RD","1: saveRun(результат)\\l12: прогон сохранён\\l(п1, с1 / п4, с6: ошибка БД)\\l"),
 ("RD","SI","2: initialize()\\l5: схема готова\\l"),
 ("SI","DC","3: open()\\l4: Connection\\l"),
 ("RD","DC","6: open()\\l7: Connection\\l(п2, с2 / п3, с3)\\l"),
 ("RD","TR","8: insert(conn, результат)\\l9: runId\\l(с4 / с5: SQLException)\\l"),
 ("RD","TC","10: insertBatch(conn, runId, кейсы)\\l11: записи сохранены\\l")])

# ---- generator (Рис. 51) ----
render("ris_51_coop_generator",
[("gr","граница\\nпакета",0,3.0),("TG",":TestGenerator",3.6,4.8),("TR",":TestRunner",3.6,1.0),
 ("TCfg",":TestConfig",6.7,6.4),("POW",":PageObjectWriter",7.3,4.8),("TCW",":TestClassWriter",7.3,3.3),
 ("TDF",":TestDataFactory",10.6,4.0),("RRW",":RunReportWriter",3.6,-1.4)],
[("gr","TG","1: generate(модель)\\l12: проект сгенерирован\\l(п1 / п4: прервана)\\l"),
 ("TG","TCfg","2: чтение параметров\\l3: пути, адрес, уровень тестов\\l"),
 ("TG","POW","4: write(сущность)\\l7: Page Object готов\\l(п2 / п3)\\l"),
 ("TG","TCW","8: write(сущность, модель)\\l11: тест-класс готов\\l"),
 ("POW","TDF","5: generateValue(свойство)\\l6: значение\\l"),
 ("TCW","TDF","9: generateValue(свойство)\\l10: значение\\l"),
 ("gr","TR","13: run(каталог, фильтр)\\l17: результат прогона\\l(с1 / с4: с ошибкой)\\l"),
 ("TR","TR","14: запуск Maven (mvn test)\\l(с2; с3: ошибка)\\l"),
 ("TR","RRW","15: write(результат прогона)\\l16: отчёты HTML и CSV\\l")])

# ---- common (Рис. 65) ----
render("ris_65_coop_common",
[("gr","граница\\nпакета",0,2.0),("POW","generator::\\nPageObjectWriter",3.6,2.0),
 ("TL",":Transliterator",7.0,3.7),("JFW",":JavaFileWriter",7.0,0.8),("PE",":ParserException",7.0,-1.8)],
[("gr","POW","1: генерация класса\\l9: класс сгенерирован\\l(п1, с1 / п4, с6)\\l"),
 ("POW","TL","2: toClassName(рус. имя)\\l3: имя класса (латиница)\\l"),
 ("POW","JFW","4: writeLine; 7: writeToFile\\l6: строка добавлена; 8: файл записан\\l(п2, с2 / п3, с5)\\l"),
 ("JFW","JFW","5: openBlock() / closeBlock()\\l"),
 ("JFW","PE","с3: create исключение (IOException)\\lс4: ParserException\\l")])

print("done")
