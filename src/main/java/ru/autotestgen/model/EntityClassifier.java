package ru.autotestgen.model;

import java.util.List;
import java.util.Locale;

/**
 * Классифицирует сущности XML-модели на PRIMARY / CHILD / REFERENCE_DICTIONARY.
 * Нужна, чтобы не генерировать отдельный тест-класс для справочников и дочерних
 * сущностей, недоступных из меню, и показать в отчёте тип каждой сущности и причину.
 */
public final class EntityClassifier {

    private EntityClassifier() {}

    /** Результат классификации с человекочитаемой причиной для отчёта. */
    public static final class Classification {
        public final EntityKind kind;
        public final String reason;
        /** Для CHILD: родительская сущность, на чьей grid-вкладке размещён этот ребёнок. */
        public final EntityObject parentEntity;
        /** Для CHILD: PropertyGroup типа Grid внутри родителя. */
        public final PropertyGroup parentGrid;

        public Classification(EntityKind kind, String reason) {
            this(kind, reason, null, null);
        }

        public Classification(EntityKind kind, String reason,
                              EntityObject parentEntity, PropertyGroup parentGrid) {
            this.kind = kind;
            this.reason = reason;
            this.parentEntity = parentEntity;
            this.parentGrid = parentGrid;
        }
    }

    /**
     * Признаки CHILD: имя сущности совпадает с именем какого-нибудь Properties[stereoType="Grid"]
     * внутри другой сущности, и у неё нет собственных CRUD-операций.
     * Признаки REFERENCE_DICTIONARY: featureName начинается на "V_S_", или
     *   у всех связанных Search список params пуст (классический pick-one справочник).
     * Всё остальное — PRIMARY.
     */
    public static Classification classify(EntityObject entity, AppModel model) {
        Classification childResult = findParentGrid(entity, model);
        if (childResult != null) {
            return childResult;
        }
        // Ребёнок-узел дерева: сущность со своим CRUD, которую родитель показывает узлом дерева
        // (ассоциация с addFromTree="1"), а не пунктом главного меню. Как самостоятельная PRIMARY
        // она недостижима, поэтому помечаем CHILD с parentGrid=null (узел дерева, не grid-вкладка).
        EntityObject treeParent = findTreeParent(entity, model);
        if (treeParent != null && entity.hasCrudOperations()) {
            return new Classification(EntityKind.CHILD,
                    "tree node under '" + treeParent.getName() + "' (addFromTree=1)",
                    treeParent, null);
        }
        String dictReason = isReferenceDictionary(entity, model);
        if (dictReason != null) {
            return new Classification(EntityKind.REFERENCE_DICTIONARY, dictReason);
        }
        String fkOnlyReason = isFkTargetOnly(entity, model);
        if (fkOnlyReason != null) {
            return new Classification(EntityKind.REFERENCE_DICTIONARY, fkOnlyReason);
        }
        return new Classification(EntityKind.PRIMARY, "has menu entry, CRUD and own searches");
    }

    private static Classification findParentGrid(EntityObject entity, AppModel model) {
        if (entity.hasCrudOperations()) return null;
        for (EntityObject other : model.getEntities()) {
            if (other.getGuid() != null && other.getGuid().equals(entity.getGuid())) continue;
            for (PropertyGroup pg : other.getPropertyGroups()) {
                if (!"Grid".equals(pg.getStereoType())) continue;
                String gridName = pg.getName();
                if (gridName == null || gridName.isEmpty()) continue;
                if (gridName.equalsIgnoreCase(other.getName())) continue;
                if (nameStemsMatch(entity.getName(), gridName)) {
                    String reason = "tab/grid '" + gridName + "' inside '" + other.getName() + "'";
                    return new Classification(EntityKind.CHILD, reason, other, pg);
                }
            }
        }
        return null;
    }

    /**
     * Возвращает сущность, которая показывает {@code entity} узлом левого дерева (ассоциация с
     * addFromTree="1", указывающая на неё). Такие дети доступны через карточку родителя
     * и раскрытие узла дерева, а не из главного меню.
     */
    static EntityObject findTreeParent(EntityObject entity, AppModel model) {
        String guid = entity.getGuid();
        if (guid == null) return null;
        for (EntityObject other : model.getEntities()) {
            if (other == entity) continue;
            if (other.getGuid() != null && other.getGuid().equals(guid)) continue;
            for (Association a : other.getAssociations()) {
                if (guid.equals(a.getAssociateItemGuid()) && a.isAddFromTree()) {
                    return other;
                }
            }
        }
        return null;
    }

    /**
     * Для встроенного списка-справочника (CRUD живёт на группе Grid, у сущности нет своей формы)
     * находит «карточку»-двойника: другую сущность с формой и собственными CRUD-операциями, чьё имя
     * совпадает по корням слов и которая использует ту же таблицу. Если такой двойник есть, строки
     * списка реально создаются/редактируются через его модальную форму (например, список
     * «Должностные лица» ↔ карточка «Должностное лицо»), поэтому сам список не должен генерировать
     * дублирующий inline-CRUD-тест. Возвращает null для настоящих встроенных справочников, где
     * единственная редактируемая поверхность — грид (двойник только для чтения), они сохраняют
     * свои inline-CRUD-тесты.
     */
    public static EntityObject findModalTwin(EntityObject entity, AppModel model) {
        if (entity.getFormView() != null) return null;       // у сущности уже есть своя модальная форма
        if (!entity.hasCrudOperations()) return null;
        String gridTable = gridColumnTable(entity);
        for (EntityObject other : model.getEntities()) {
            if (other == entity) continue;
            if (other.getGuid() != null && other.getGuid().equals(entity.getGuid())) continue;
            PropertyGroup twinForm = other.getFormView();
            if (twinForm == null) continue;                  // у двойника должна быть модальная форма ...
            if (!other.hasCrudOperations()) continue;        // ... со своими CRUD-операциями
            if (!nameStemsMatch(entity.getName(), other.getName())) continue;
            if (gridTable != null) {
                String twinTable = twinForm.getProperties().stream()
                        .map(Property::getTableName)
                        .filter(t -> t != null && !t.isEmpty())
                        .findFirst().orElse(null);
                if (twinTable != null && !twinTable.equalsIgnoreCase(gridTable)) continue;
            }
            return other;
        }
        return null;
    }

    /** Первое непустое table_name среди колонок грида сущности (таблица, которую правит грид). */
    private static String gridColumnTable(EntityObject entity) {
        for (PropertyGroup pg : entity.getPropertyGroups()) {
            if (!"Grid".equals(pg.getStereoType())) continue;
            for (Property p : pg.getProperties()) {
                if (p.getTableName() != null && !p.getTableName().isEmpty()) {
                    return p.getTableName();
                }
            }
        }
        return null;
    }

    /**
     * Сущность считается «целью FK-пикера» (а не реальной самостоятельной), если она
     * присутствует в чужих ассоциациях как AssociateItem, но ни в одной из этих
     * родительских сущностей нет PropertyGroup со stereoType="Grid", чьё имя
     * по корням слов совпадает с именем нашей сущности. То есть никто не показывает
     * её строки таблицей в табе карточки — её только подбирают как FK.
     * Используется чтобы отсеять Адрес / Дом на улице — их нет в основном меню,
     * они открываются только из карточки Должностного лица как picker.
     */
    private static String isFkTargetOnly(EntityObject entity, AppModel model) {
        String guid = entity.getGuid();
        if (guid == null) return null;
        int fkRefs = 0;
        int navRefs = 0;
        String referrer = null;
        for (EntityObject other : model.getEntities()) {
            if (other == entity) continue;
            if (other.getGuid() != null && other.getGuid().equals(guid)) continue;
            // Пропускаем ссылки от сущностей, которые сами являются детьми этой (обратный
            // указатель на родителя не должен считаться «кто-то использует меня как FK»).
            if (isChildOf(other, entity)) continue;
            for (Association a : other.getAssociations()) {
                if (!guid.equals(a.getAssociateItemGuid())) continue;
                // addFromTree=1 или flag_display=1 значит, что родитель показывает эту сущность в UI
                // (узел дерева или вкладка-грид) — это настоящий навигируемый ребёнок, а не FK-пикер.
                if (a.isAddFromTree() || a.isFlagDisplay()) {
                    navRefs++;
                } else {
                    fkRefs++;
                    if (referrer == null) referrer = other.getName();
                }
            }
        }
        if (fkRefs > 0 && navRefs == 0) {
            return "FK picker target — referenced as AssociateItem from '" + referrer
                    + "' (all incoming associations have addFromTree=0 & flag_display=0)";
        }
        return null;
    }

    /** true, если `child` присутствует строкой грида внутри `parent` (по совпадению корней имён). */
    private static boolean isChildOf(EntityObject child, EntityObject parent) {
        for (PropertyGroup pg : parent.getPropertyGroups()) {
            if (!"Grid".equals(pg.getStereoType())) continue;
            String gridName = pg.getName();
            if (gridName == null || gridName.isEmpty()) continue;
            if (nameStemsMatch(child.getName(), gridName)) return true;
        }
        return false;
    }

    private static String isReferenceDictionary(EntityObject entity, AppModel model) {
        String feature = entity.getFeatureName();
        if (feature != null && feature.toUpperCase(Locale.ROOT).startsWith("V_S_")) {
            return "featureName starts with V_S_ (system dictionary prefix)";
        }
        List<Search> searches = model.getSearches().stream()
                .filter(s -> s.getSearchObjectGuid() != null
                        && s.getSearchObjectGuid().equals(entity.getGuid()))
                .toList();
        if (searches.isEmpty()) {
            return null;
        }
        boolean allEmptyParams = searches.stream().allMatch(s -> s.getParams().isEmpty());
        boolean allTrivialResult = searches.stream().allMatch(EntityClassifier::isTrivialResult);
        if (allEmptyParams && allTrivialResult) {
            return "all searches are pick-one (no params, result = SearchKey+SearchName)";
        }
        return null;
    }

    private static boolean isTrivialResult(Search s) {
        if (s.getResult() == null) return true;
        List<SearchResultProperty> props = s.getResult().getProperties();
        if (props == null || props.isEmpty()) return true;
        if (props.size() > 2) return false;
        for (SearchResultProperty p : props) {
            String n = p.getName();
            if (n == null) continue;
            if (!"SearchKey".equalsIgnoreCase(n) && !"SearchName".equalsIgnoreCase(n)) return false;
        }
        return true;
    }

    private static boolean nameStemsMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equalsIgnoreCase(b)) return true;
        String[] aw = a.split("[\\s/]+");
        String[] bw = b.split("[\\s/]+");
        if (aw.length != bw.length) return false;
        for (int i = 0; i < aw.length; i++) {
            String wa = aw[i].toLowerCase(Locale.ROOT);
            String wb = bw[i].toLowerCase(Locale.ROOT);
            if (wa.equals(wb)) continue;
            int min = Math.min(wa.length(), wb.length());
            int prefix = Math.min(min - 1, Math.max(3, min - 2));
            if (prefix <= 0) return false;
            if (!wa.substring(0, prefix).equals(wb.substring(0, prefix))) return false;
        }
        return true;
    }
}
