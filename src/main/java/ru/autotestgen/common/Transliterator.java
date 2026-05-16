package ru.autotestgen.common;

import java.util.Map;

/**
 * Converts Cyrillic names to valid Java identifiers.
 * "ГСК/ОГСК" -> "GskOgsk", "Совещание" -> "Soveshchanie"
 */
public class Transliterator {

    private static final Map<Character, String> MAPPING = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"),
            Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"),
            Map.entry('ё', "yo"), Map.entry('ж', "zh"), Map.entry('з', "z"),
            Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"),
            Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"),
            Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya")
    );

    /**
     * Converts a Cyrillic name to a PascalCase Java class name.
     */
    public static String toClassName(String russianName) {
        if (russianName == null || russianName.isEmpty()) return "Unknown";

        // Split by non-letter characters (spaces, slashes, dashes, etc.)
        String[] words = russianName.split("[^а-яА-Яa-zA-Z0-9]+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;
            String transliterated = transliterate(word);
            if (!transliterated.isEmpty()) {
                result.append(Character.toUpperCase(transliterated.charAt(0)));
                if (transliterated.length() > 1) {
                    result.append(transliterated.substring(1));
                }
            }
        }

        String name = result.toString();
        if (name.isEmpty()) return "Unknown";

        // Ensure starts with letter
        if (!Character.isLetter(name.charAt(0))) {
            name = "E" + name;
        }
        return name;
    }

    /**
     * Converts a Cyrillic name to a camelCase Java method name.
     */
    public static String toMethodName(String russianName) {
        String className = toClassName(russianName);
        if (className.isEmpty()) return "unknown";
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * Converts a Cyrillic name to a valid Java field/variable name.
     */
    public static String toFieldName(String attrName) {
        if (attrName == null || attrName.isEmpty()) return "field";
        // If already Latin (like KEY_GB_SOCIETY), convert to camelCase
        if (attrName.matches("[A-Z_0-9]+")) {
            return snakeToCamel(attrName);
        }
        return toMethodName(attrName);
    }

    private static String transliterate(String word) {
        StringBuilder sb = new StringBuilder();
        for (char c : word.toCharArray()) {
            char lower = Character.toLowerCase(c);
            String mapped = MAPPING.get(lower);
            if (mapped != null) {
                if (Character.isUpperCase(c) && !mapped.isEmpty()) {
                    sb.append(Character.toUpperCase(mapped.charAt(0)));
                    if (mapped.length() > 1) sb.append(mapped.substring(1));
                } else {
                    sb.append(mapped);
                }
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String snakeToCamel(String snake) {
        String[] parts = snake.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
