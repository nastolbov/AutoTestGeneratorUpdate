# Диаграммы модели и логики тестов (по XML «Гаражно-строительные кооперативы»)

Сгенерировано строго по коду генератора и сгенерированным тест-классам.

## Структура
- `00_model_structure.png` — карта модели: 5 PRIMARY, 6 CHILD (вкладки/узлы дерева),
  8 справочников/FK (тесты не генерируются). Рёбра — родитель → ребёнок.

## По сущностям (CRUD/тест-методы, что реально генерируется)
- `entity_<Класс>Test.png` — для каждой тестируемой сущности: цепочка её @Test-методов
  (цвет = тип: create/update/delete/archive/валидация/поля/грид/поиск).

## Логика тестов (флоучарты строго по коду)
- `logic_modal_create.png`, `logic_modal_update.png` — модальные create/update (Type 1).
- `logic_delete_seed.png`, `logic_archive_seed.png` — seed-then-delete/archive (без заглушек).
- `logic_inline_create.png`, `logic_inline_update.png` — inline-справочник (Type 2): клавиатура+маски, dismissErrorPopup(trunc), clickGridRefresh, честная проверка счётчика.
- `logic_child_setup.png` — надёжный @BeforeEach дочерней сущности (3 попытки → честный fail, не skip).
- `logic_child_create.png`, `logic_child_update.png`, `logic_child_delete.png`, `logic_child_grid_columns.png` — дочерние grid-вкладки.
- `logic_treechild_fields.png` — узел дерева (Повестка).
- `logic_fields_present.png`, `logic_search.png`, `logic_grid_view.png`, `logic_required_validation.png` — smoke/поиск/грид/валидация.
