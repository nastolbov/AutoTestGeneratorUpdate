# Пакет «Parsing» (в коде `parser/`) — диаграмма из курсовой актуальна

Сверка: раздел 1.5.2.10 курсовой (Рис. 26 — детальная диаграмма классов
пакета «Parsing»).

Класс единственный — `XmlModelParser` — и совпадает с кодом
полностью:

- Все 3 поля (`NS_E`, `NS_E3`, `NS_MD`) — те же XML namespaces.
- Все 11 методов того же имени и сигнатуры:
  `parse(File)`, `parseObject`, `parseAssociation`, `parsePropertyGroup`,
  `parseProperty`, `parseOperation`, `parseSearches`, `parseSingleSearch`,
  `skipToEnd`, `attr`, `parseInt`.

Единственное расхождение — **имя пакета**: в курсовой «Parsing», в коде
реально `ru.autotestgen.parser`. Это упомянуто в `package-overview` —
саму классовую диаграмму перерисовывать не нужно.
