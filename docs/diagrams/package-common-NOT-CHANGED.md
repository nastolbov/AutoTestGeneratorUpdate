# Пакет «Common» — диаграмма из курсовой актуальна

Сверка: раздел 1.5.2.30 курсовой (Рис. 54 — детальная диаграмма классов
пакета «Common»).

Три класса: `JavaFileWriter`, `Transliterator`, `ParserException`. Все поля
и все методы совпадают с курсовой полностью:

- `JavaFileWriter` — поля `sb`, `indentLevel`, `INDENT` + методы
  `writeLine(String)`, `writeLine()`, `openBlock`, `closeBlock`, `indent`,
  `unindent`, `writeToFile`, `toString` — fluent API возвращает `this`.
- `Transliterator` — поле `MAPPING` и методы `toClassName`, `toMethodName`,
  `toFieldName`, `transliterate`, `snakeToCamel` — без изменений.
- `ParserException` — два конструктора (`String` и `String, Throwable`).

Перерисовка не требуется.
