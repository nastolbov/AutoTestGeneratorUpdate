"""
Сборка DIPLOMA_READY.md — единый файл с диаграммами и таблицами
в порядке диплома. Таблицы из TABLES_vers2.md вставляются inline.
"""
import re

src = open('/home/user/AutoTestGeneratorUpdate/TABLES_vers2.md').read()

# Извлекаем секции по номерам
def get_section(num, level=3):
    """Возвращает текст секции '### NUM. ...' до следующего заголовка того же уровня."""
    pat = rf'^{"#"*level} {re.escape(num)}\.\s+([^\n]*)\n([\s\S]*?)(?=^{"#"*level} \d|^{"#"*(level-1)} |\Z)'
    m = re.search(pat, src, re.MULTILINE)
    if not m:
        return ""
    title, body = m.group(1), m.group(2)
    return f"### {num}. {title}\n{body.rstrip()}\n"


def extract_subtables(section_text):
    """Из секции с подзаголовками **Класс `X`** собирает {cls: {'fields': tbl, 'methods': tbl}}."""
    out = {}
    pattern = r'\*\*(?:Класс\s+)?`([^`]+)`\*\*[^\n]*\n+(\|[^\n]+\n(?:\|[^\n]+\n)+)'
    for m in re.finditer(pattern, section_text):
        cls = m.group(1)
        tbl = m.group(2).rstrip()
        if cls not in out:
            out[cls] = {"fields": None, "methods": None}
        if "Параметры" in tbl.split('\n')[0]:
            out[cls]["methods"] = tbl
        else:
            out[cls]["fields"] = tbl
    return out

def get_table_only(section_text):
    """Из текста секции вытаскивает только таблицу (от первой | до последней)."""
    lines = section_text.split('\n')
    out = []
    in_table = False
    for line in lines:
        if line.startswith('|'):
            out.append(line)
            in_table = True
        elif in_table and not line.startswith('|') and line.strip():
            break
    return '\n'.join(out)

# Определяем структуру диплома
sections = []

# Заголовок
sections.append("""# Готовый материал для диплома

Файл собран в порядке структуры диплома. Каждый раздел содержит диаграмму
и сразу под ней — таблицу (или таблицы), относящиеся к ней.

**Как вставлять в Word:**
1. Открой этот файл на GitHub (он отрендерится как HTML).
2. Скопируй нужную таблицу — она вставится в Word с форматированием.
3. Картинки — сохрани из `diagrams/` или скопируй прямо из браузера.

---

## 1.4. Разработка спецификаций

### 1.4.1. Диаграмма вариантов использования

![](diagrams/usecase.png)

### 1.4.5. ER-диаграмма базы данных

![](diagrams/er-database.png)

### 1.4.6. Диаграмма переходов состояний

![](diagrams/state-diagram.png)

---

## 1.5. Проектирование программного обеспечения

### 1.5.1. Диаграмма пакетов

![](diagrams/packages.png)

---

### 1.5.2. Проектирование классов в пакетах
""")

# Для каждого пакета — единая секция
PACKAGES = [
    ("UI", "ui", "1.1", "2.1, 2.2", "3.1, 3.2, 3.3"),  # (name, slug, classes_section, fields_sections, methods_sections)
    ("Parser", "parser", "1.3", "2.17", "3.14-3.18"),
    ("Model", "model", "1.2", "2.3-2.16", "3.4-3.13"),
    ("Generator", "generator", "1.6", "2.20, 2.21", "3.23-3.29"),
    ("Data", "data", "1.5", "2.19", "3.22"),
    ("Common", "common", "1.4", "2.18", "3.19, 3.20, 3.21"),
]

# UI
sections.append(f"""
#### 1.5.2.1. Пакет «UI»

##### Исходная диаграмма классов
![](diagrams/cls-ui-initial.png)

**Таблица. Описание классов пакета «UI»**

{get_table_only(get_section('1.1'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-ui-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-ui-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-ui-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-ui.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-ui-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-ui-detail.png)

**Таблица. Описание полей класса «MainController»**

{get_table_only(get_section('2.1'))}

**Таблица. Описание полей класса «MainController.TestCaseRow»**

{get_table_only(get_section('2.2'))}

**Таблица. Описание методов класса «App»**

{get_table_only(get_section('3.1'))}

**Таблица. Описание методов класса «MainController»**

{get_table_only(get_section('3.2'))}

**Таблица. Описание методов класса «MainController.TestCaseRow»**

{get_table_only(get_section('3.3'))}

---
""")

# Parser
sections.append(f"""
#### 1.5.2.2. Пакет «Parser»

##### Исходная диаграмма классов
![](diagrams/cls-parser-initial.png)

**Таблица. Описание классов пакета «Parser»**

{get_table_only(get_section('1.3'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-parser-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-parser-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-parser-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-parser.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-parser-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-parser-detail.png)

**Таблица. Описание полей классов пакета «Parser»**

{get_table_only(get_section('2.17'))}

**Таблица. Описание методов класса «XmlModelParser»**

{get_table_only(get_section('3.14'))}

**Таблица. Описание методов класса «EntityParser»**

{get_table_only(get_section('3.15'))}

**Таблица. Описание методов класса «PropertyGroupParser»**

{get_table_only(get_section('3.16'))}

**Таблица. Описание методов класса «SearchParser»**

{get_table_only(get_section('3.17'))}

**Таблица. Описание методов класса «StaxUtils»**

{get_table_only(get_section('3.18'))}

---
""")

# Model
sections.append(f"""
#### 1.5.2.3. Пакет «Model»

##### Исходная диаграмма классов
![](diagrams/cls-model-initial.png)

**Таблица. Описание классов пакета «Model»**

{get_table_only(get_section('1.2'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-model-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-model-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-model-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-model.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-model-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-model-detail.png)

**Таблица. Описание полей класса «AppModel»**

{get_table_only(get_section('2.3'))}

**Таблица. Описание полей класса «EntityObject»**

{get_table_only(get_section('2.4'))}

**Таблица. Описание полей класса «PropertyGroup»**

{get_table_only(get_section('2.5'))}

**Таблица. Описание полей класса «Property»**

{get_table_only(get_section('2.6'))}

**Таблица. Описание методов класса «AppModel»**

{get_table_only(get_section('3.4'))}

**Таблица. Описание методов класса «EntityObject»**

{get_table_only(get_section('3.5'))}

**Таблица. Описание методов класса «PropertyGroup»**

{get_table_only(get_section('3.6'))}

**Таблица. Описание методов класса «EntityClassifier»**

{get_table_only(get_section('3.11'))}

---
""")

# Generator
sections.append(f"""
#### 1.5.2.4. Пакет «Generator»

##### Исходная диаграмма классов
![](diagrams/cls-generator-initial.png)

**Таблица. Описание классов пакета «Generator»**

{get_table_only(get_section('1.6'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-generator-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-generator-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-generator-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-generator.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-generator-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-generator-detail.png)

**Таблица. Описание полей класса «TestConfig»**

{get_table_only(get_section('2.20'))}

**Таблица. Описание полей классов «TestGenerator» и «TestRunner»**

{get_table_only(get_section('2.21'))}

**Таблица. Описание методов класса «TestConfig»**

{get_table_only(get_section('3.23'))}

**Таблица. Описание методов класса «TestGenerator»**

{get_table_only(get_section('3.24'))}

**Таблица. Описание методов класса «PageObjectWriter»**

{get_table_only(get_section('3.25'))}

**Таблица. Описание методов класса «TestClassWriter»**

{get_table_only(get_section('3.26'))}

**Таблица. Описание методов класса «TestDataFactory»**

{get_table_only(get_section('3.27'))}

**Таблица. Описание методов класса «TestRunner»**

{get_table_only(get_section('3.28'))}

**Таблица. Описание методов класса «RunReportWriter»**

{get_table_only(get_section('3.29'))}

---
""")

# Data — отдельные таблицы по каждому классу
data_fields  = extract_subtables(get_section('2.19'))
data_methods = extract_subtables(get_section('3.22'))
DATA_CLASSES = ["DatabaseConnection", "SchemaInitializer", "TestRunDao", "TestCaseDao", "ReportDao"]

def data_tables_block():
    parts = []
    for cls in DATA_CLASSES:
        f = data_fields.get(cls, {}).get("fields")
        m = data_methods.get(cls, {}).get("methods")
        if f:
            parts.append(f"\n**Таблица. Описание полей класса «{cls}»**\n\n{f}")
        if m:
            parts.append(f"\n**Таблица. Описание методов класса «{cls}»**\n\n{m}")
    return "\n".join(parts)

sections.append(f"""
#### 1.5.2.5. Пакет «Data»

##### Исходная диаграмма классов
![](diagrams/cls-data-initial.png)

**Таблица. Описание классов пакета «Data»**

{get_table_only(get_section('1.5'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-data-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-data-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-data-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-data.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-data-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-data-detail.png)
{data_tables_block()}

---
""")

# Common
sections.append(f"""
#### 1.5.2.6. Пакет «Common»

##### Исходная диаграмма классов
![](diagrams/cls-common-initial.png)

**Таблица. Описание классов пакета «Common»**

{get_table_only(get_section('1.4'))}

##### Диаграммы последовательностей

**Нормальный ход событий:**
![](diagrams/seq-common-normal.png)

**Прерывание пользователем:**
![](diagrams/seq-common-user-interrupt.png)

**Прерывание системой:**
![](diagrams/seq-common-system-interrupt.png)

##### Диаграмма кооперации
![](diagrams/coop-common.png)

##### Уточнённая диаграмма классов
![](diagrams/cls-common-refined.png)

##### Детальная диаграмма классов
![](diagrams/cls-common-detail.png)

**Таблица. Описание полей класса «JavaFileWriter»**

{get_table_only(get_section('2.18'))}

**Таблица. Описание методов класса «Transliterator»**

{get_table_only(get_section('3.19'))}

**Таблица. Описание методов класса «JavaFileWriter»**

{get_table_only(get_section('3.20'))}

**Таблица. Описание методов класса «ParserException»**

{get_table_only(get_section('3.21'))}

---
""")

# 1.5.3 Module structure (Constantine)
sections.append(f"""
### 1.5.3. Модульная структура (по Л. Константайну)

![](diagrams/constantine-module-structure.png)

**Таблица 92. Спецификация модулей программы** *(для §1.5.3 — краткая форма)*

{get_table_only(get_section('4.4'))}

---

### 1.5.4. Диаграмма размещения

![](diagrams/deployment.png)

---

### 1.5.5. Диаграмма компонентов

![](diagrams/components.png)

---

## Приложение 3. Спецификация программной документации и программного обеспечения

**Таблица П3.1. Спецификация программной документации и программного обеспечения** *(детальная форма для Прил. 3)*

{get_table_only(get_section('4.6'))}

---

## Итоги

- **6 пакетов** проекта: UI, Parser, Model, Generator, Data, Common
- **41 .java-файл** (24 модуля с поведением + 17 POJO в model)
- **Все диаграммы** в `diagrams/` готовы к печати (ч/б, Liberation Serif)
- **Полное соответствие** с актуальным кодом ветки vers2

Все диаграммы пересобираются скриптами:
- `diagrams/gen_seq.py` — все 18 ДПС
- `diagrams/gen_constantine.py` — модульная структура Константайна
- `diagrams/*.dot` — все остальные через graphviz (`dot -Tpng -Gdpi=140 X.dot -o X.png`)
""")

# Сохраняем
out = '\n'.join(sections)
with open('/home/user/AutoTestGeneratorUpdate/DIPLOMA_READY.md', 'w') as f:
    f.write(out)

print(f"Готово. Размер: {len(out)} байт, {len(out.split(chr(10)))} строк")
