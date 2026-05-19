package ru.autotestgen.model;

import java.util.List;
import java.util.Locale;

/**
 * Классифицирует сущности XML-модели на PRIMARY / CHILD / REFERENCE_DICTIONARY.
 * Это нужно чтобы:
 *   - не генерировать отдельный тест-класс для справочников и дочерних сущностей,
 *     которые в меню недоступны (там и берётся 21 skipped из текущего прогона);
 *   - явно показать в отчёте, какая сущность к какому типу отнесена и почему.
 */
public final class EntityClassifier {

    private EntityClassifier() {}

    /** Результат классификации с человекочитаемой причиной для отчёта. */
    public static final class Classification {
        public final EntityKind kind;
        public final String reason;
        public Classification(EntityKind kind, String reason) {
            this.kind = kind;
            this.reason = reason;
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
        String childReason = findParentGridReason(entity, model);
        if (childReason != null) {
            return new Classification(EntityKind.CHILD, childReason);
        }
        String dictReason = isReferenceDictionary(entity, model);
        if (dictReason != null) {
            return new Classification(EntityKind.REFERENCE_DICTIONARY, dictReason);
        }
        return new Classification(EntityKind.PRIMARY, "has menu entry, CRUD and own searches");
    }

    private static String findParentGridReason(EntityObject entity, AppModel model) {
        if (entity.hasCrudOperations()) return null;
        for (EntityObject other : model.getEntities()) {
            if (other.getGuid() != null && other.getGuid().equals(entity.getGuid())) continue;
            for (PropertyGroup pg : other.getPropertyGroups()) {
                if (!"Grid".equals(pg.getStereoType())) continue;
                String gridName = pg.getName();
                if (gridName == null || gridName.isEmpty()) continue;
                if (gridName.equalsIgnoreCase(other.getName())) continue;
                if (nameStemsMatch(entity.getName(), gridName)) {
                    return "tab/grid '" + gridName + "' inside '" + other.getName() + "'";
                }
            }
        }
        return null;
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
