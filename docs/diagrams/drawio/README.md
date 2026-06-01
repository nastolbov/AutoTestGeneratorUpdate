# Диаграммы в формате drawio

Каждый файл — отдельная диаграмма курсовой, актуализированная по коду
на ветке `claude/wonderful-dijkstra-Kdp5i` (июнь 2026).

## Условные обозначения

- **Жёлтый фон** на классе/поле/методе означает «добавлено после написания
  курсовой» (помечено также `⟵ новое`).
- **Пунктирная серая рамка** — класс был в курсовой, но удалён из кода
  (пример — `ReportService` в Generator).
- Sequence-диаграммы: сплошные стрелки = синхронный вызов,
  пунктирные = возврат, петля у одной lifeline = self-call.

## Сводная таблица

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 01 | `01-package-overview.drawio`            | Сводная диаграмма пакетов                   | Рис. 12    |

### Пакет UI

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 02 | `02-ui-detailed.drawio`                 | Детальная диаграмма классов                 | Рис. 19    |
| 08 | `08-ui-initial.drawio`                  | Исходная диаграмма классов                  | Рис. 13    |
| 09 | `09-ui-refined.drawio`                  | Уточнённая диаграмма классов                | Рис. 18    |
| 10 | `10-ui-seq-normal.drawio`               | Sequence — нормальный ход событий           | Рис. 14    |
| 11 | `11-ui-seq-userabort.drawio`            | Sequence — прерывание пользователем         | Рис. 15    |
| 12 | `12-ui-seq-sysabort.drawio`             | Sequence — прерывание системой              | Рис. 16    |
| 13 | `13-ui-collab.drawio`                   | Диаграмма кооперации                        | Рис. 17    |

### Пакет Parsing (parser/ в коде)

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 03 | `03-parsing-detailed.drawio`            | Детальная диаграмма классов                 | Рис. 26    |
| 14 | `14-parsing-initial.drawio`             | Исходная диаграмма классов                  | Рис. 20    |
| 15 | `15-parsing-refined.drawio`             | Уточнённая диаграмма классов                | Рис. 25    |
| 16 | `16-parsing-seq-normal.drawio`          | Sequence — нормальный ход событий           | Рис. 21    |
| 17 | `17-parsing-seq-userabort.drawio`       | Sequence — прерывание пользователем         | Рис. 22    |
| 18 | `18-parsing-seq-sysabort.drawio`        | Sequence — прерывание системой              | Рис. 23    |
| 19 | `19-parsing-collab.drawio`              | Диаграмма кооперации                        | Рис. 24    |

### Пакет Model

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 04 | `04-model-detailed.drawio`              | Детальная диаграмма классов                 | Рис. 33    |
| 20 | `20-model-initial.drawio`               | Исходная диаграмма классов                  | Рис. 27    |
| 21 | `21-model-refined.drawio`               | Уточнённая диаграмма классов                | Рис. 32    |
| 22 | `22-model-seq-normal.drawio`            | Sequence — нормальный ход событий           | Рис. 28    |
| 23 | `23-model-seq-userabort.drawio`         | Sequence — прерывание пользователем         | Рис. 29    |
| 24 | `24-model-seq-sysabort.drawio`          | Sequence — прерывание системой              | Рис. 30    |
| 25 | `25-model-collab.drawio`                | Диаграмма кооперации                        | Рис. 31    |

### Пакет Generator

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 05 | `05-generator-detailed.drawio`          | Детальная диаграмма классов                 | Рис. 40    |
| 26 | `26-generator-initial.drawio`           | Исходная диаграмма классов                  | Рис. 34    |
| 27 | `27-generator-refined.drawio`           | Уточнённая диаграмма классов                | Рис. 39    |
| 28 | `28-generator-seq-normal.drawio`        | Sequence — нормальный ход событий           | Рис. 35    |
| 29 | `29-generator-seq-userabort.drawio`     | Sequence — прерывание пользователем         | Рис. 36    |
| 30 | `30-generator-seq-sysabort.drawio`      | Sequence — прерывание системой              | Рис. 37    |
| 31 | `31-generator-collab.drawio`            | Диаграмма кооперации                        | Рис. 38    |

### Пакет Data

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 06 | `06-data-detailed.drawio`               | Детальная диаграмма классов                 | Рис. 47    |
| 32 | `32-data-initial.drawio`                | Исходная диаграмма классов                  | Рис. 41    |
| 33 | `33-data-refined.drawio`                | Уточнённая диаграмма классов                | Рис. 46    |
| 34 | `34-data-seq-normal.drawio`             | Sequence — нормальный ход событий           | Рис. 42    |
| 35 | `35-data-seq-userabort.drawio`          | Sequence — прерывание пользователем         | Рис. 43    |
| 36 | `36-data-seq-sysabort.drawio`           | Sequence — прерывание системой              | Рис. 44    |
| 37 | `37-data-collab.drawio`                 | Диаграмма кооперации                        | Рис. 45    |

### Пакет Common

| #  | Файл                                    | Что показывает                              | В курсовой |
|----|-----------------------------------------|---------------------------------------------|------------|
| 07 | `07-common-detailed.drawio`             | Детальная диаграмма классов                 | Рис. 54    |
| 38 | `38-common-initial.drawio`              | Исходная диаграмма классов                  | Рис. 48    |
| 39 | `39-common-refined.drawio`              | Уточнённая диаграмма классов                | Рис. 53    |
| 40 | `40-common-seq-normal.drawio`           | Sequence — нормальный ход событий           | Рис. 49    |
| 41 | `41-common-seq-userabort.drawio`        | Sequence — прерывание пользователем         | Рис. 50    |
| 42 | `42-common-seq-sysabort.drawio`         | Sequence — прерывание системой              | Рис. 51    |
| 43 | `43-common-collab.drawio`               | Диаграмма кооперации                        | Рис. 52    |

## Как открыть

- В браузере: [app.diagrams.net](https://app.diagrams.net) → File → Open from Device.
- В VS Code: расширение [Draw.io Integration](https://marketplace.visualstudio.com/items?itemName=hediet.vscode-drawio).
- Standalone приложение: [drawio-desktop](https://github.com/jgraph/drawio-desktop).

## Как экспортировать в PNG

Из drawio-desktop:
```
drawio --export --format png --output 02-ui-detailed.png 02-ui-detailed.drawio
```

PNG-копии детальных диаграмм (PlantUML-рендеринг) уже лежат в каталоге выше
(`docs/diagrams/*.png`) — содержание то же, но без редактируемости drawio.

## Содержимое sequence/collaboration

Потоки вызовов нарисованы **по реальному коду** на ветке. Например, поток
«нормальный ход событий UI» (Рис. 14):

```
onSelectXml → onParse → new XmlModelParser → parse(file) → AppModel →
onGenerate → new TestGenerator(config) → generate(model) → ✓ →
onRunTests → new TestRunner → run(...) → TestRunResult →
saveRun(result) → displayResults(result)
```

Это то, что реально делает `MainController` в `src/main/java/ru/autotestgen/ui/MainController.java`.
