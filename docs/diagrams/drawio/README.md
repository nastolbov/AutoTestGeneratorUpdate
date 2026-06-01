# Диаграммы в формате drawio

Каждый файл — отдельная диаграмма курсовой, актуализированная по коду
на ветке `claude/wonderful-dijkstra-Kdp5i` (июнь 2026).

## Список файлов

| #  | Файл                              | Что показывает                                         | Соотв. в курсовой |
|----|-----------------------------------|--------------------------------------------------------|-------------------|
| 01 | `01-package-overview.drawio`      | Сводная диаграмма пакетов (6 пакетов + зависимости)    | Рис. 12           |
| 02 | `02-ui-detailed.drawio`           | Детальная диаграмма классов пакета «UI»                | Рис. 19           |
| 03 | `03-parsing-detailed.drawio`      | Детальная диаграмма классов пакета «Parsing» (parser/) | Рис. 26           |
| 04 | `04-model-detailed.drawio`        | Детальная диаграмма классов пакета «Model»             | Рис. 33           |
| 05 | `05-generator-detailed.drawio`    | Детальная диаграмма классов пакета «Generator»         | Рис. 40           |
| 06 | `06-data-detailed.drawio`         | Детальная диаграмма классов пакета «Data»              | Рис. 47           |
| 07 | `07-common-detailed.drawio`       | Детальная диаграмма классов пакета «Common»            | Рис. 54           |

## Условные обозначения

- **Жёлтый фон** на классе/поле/методе означает «добавлено после написания
  курсовой» (помечено также `⟵ новое`).
- **Пунктирная серая рамка** означает «класс был в курсовой, но удалён
  из кода» (пример — `ReportService` в Generator).
- В разделах с DTO (`TestRunResult`, `TestCaseResult`) рамкой «model»
  показано фактическое местоположение, хотя курсовая помещала их в Data.

## Как открыть

- В браузере: [app.diagrams.net](https://app.diagrams.net) → File → Open from Device.
- В VS Code: расширение [Draw.io Integration](https://marketplace.visualstudio.com/items?itemName=hediet.vscode-drawio).
- Standalone приложение: [drawio-desktop](https://github.com/jgraph/drawio-desktop).

## Как экспортировать в PNG

Из drawio-desktop:
```
drawio --export --format png --output 02-ui-detailed.png 02-ui-detailed.drawio
```

PNG-копии всех схем (PlantUML-рендеринг) уже лежат в каталоге выше
(`docs/diagrams/*.png`) — содержание то же, но без редактируемости drawio.
