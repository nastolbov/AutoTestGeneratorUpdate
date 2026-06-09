# Диаграммы для диплома — порядок по структуре документа

Файл собран по структуре твоего диплома (см. рукописный план):
**1.4 ВИ → 1.5 Проектирование пакетов → 1.5.3 Модульная структура → 1.7 Тестирование**.

Каждая диаграмма имеет путь к PNG-файлу в `diagrams/`, готовому к вставке в Word.
Подписи снизу удалены — добавляй их сам по нумерации диплома.

---

## 1.4. Разработка спецификаций

### 1.4.1. Диаграмма вариантов использования
![](diagrams/usecase.png)

### 1.4.2. Контекстная диаграмма классов
*(пока не делал — нужна?)*

### 1.4.3. Диаграммы последовательности системы
*(уровень use case — используем общую диаграмму взаимодействия)*

### 1.4.4. Диаграмма деятельности «Сгенерировать автотесты»
*(пока не делал — нужна?)*

### 1.4.5. ER-диаграмма БД
![](diagrams/er-database.png)

### 1.4.6. Диаграмма переходов состояний
![](diagrams/state-diagram.png)

---

## 1.5. Проектирование программного обеспечения

### 1.5.1. Диаграмма пакетов
![](diagrams/packages.png)

---

### 1.5.2. Проектирование классов в пакетах

#### 1.5.2.1. Пакет «UI»

##### Исходная диаграмма классов
![](diagrams/cls-ui-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-ui-detail.png)

---

#### 1.5.2.2. Пакет «Parser»

##### Исходная диаграмма классов
![](diagrams/cls-parser-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-parser-detail.png)

---

#### 1.5.2.3. Пакет «Model»

##### Исходная диаграмма классов
![](diagrams/cls-model-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-model-detail.png)

---

#### 1.5.2.4. Пакет «Generator»

##### Исходная диаграмма классов
![](diagrams/cls-generator-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-generator-detail.png)

---

#### 1.5.2.5. Пакет «Data»

##### Исходная диаграмма классов
![](diagrams/cls-data-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-data-detail.png)

---

#### 1.5.2.6. Пакет «Common»

##### Исходная диаграмма классов
![](diagrams/cls-common-initial.png)

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

##### Детальная диаграмма классов *(приложение)*
![](diagrams/cls-common-detail.png)

---

### 1.5.3. Построение диаграммы компонентов и модульной структуры

#### Диаграмма компонентов
![](diagrams/components.png)

#### Модульная структура (по Л. Константайну)
![](diagrams/constantine-module-structure.png)

> Краткая спецификация модулей в основном тексте, подробная — в Прил. 2 (текст программы) и Прил. 3 (спецификация модулей). См. `TABLES_vers2.md` §4.4 — там 28 модулей (= 28 `.java`-файлов) с входными/выходными параметрами.

---

### 1.5.4. Диаграмма размещения
![](diagrams/deployment.png)

---

## Сводка: где какая диаграмма

| Файл                                 | Что показывает                                                  |
| ------------------------------------ | --------------------------------------------------------------- |
| `usecase.png`                        | Рис. 2 — варианты использования                                 |
| `er-database.png`                    | Рис. 10 — ER-диаграмма БД                                       |
| `state-diagram.png`                  | Рис. 11 — переходы состояний                                    |
| `packages.png`                       | Рис. 12 — диаграмма пакетов                                     |
| `cls-X-initial.png`                  | Исходная диаграмма классов пакета X                              |
| `seq-X-normal.png`                   | ДПС пакета X — нормальный ход                                   |
| `seq-X-user-interrupt.png`           | ДПС пакета X — прерывание пользователем                          |
| `seq-X-system-interrupt.png`         | ДПС пакета X — прерывание системой                              |
| `coop-X.png`                         | Диаграмма кооперации пакета X                                   |
| `cls-X-refined.png`                  | Уточнённая диаграмма классов пакета X                            |
| `cls-X-detail.png`                   | Детальная диаграмма классов пакета X (для приложения)            |
| `components.png`                     | Рис. 55 — компоненты системы                                    |
| `constantine-module-structure.png`   | Рис. 56 — модульная структура (Константайн)                      |
| `deployment.png`                     | Рис. 28 — размещение                                            |

Где `X` ∈ `{ui, parser, model, generator, data, common}`.

**Итого:** 12 системных + 6 × 6 = 36 диаграмм пакетов + 18 ДПС = **48 PNG-файлов**.
