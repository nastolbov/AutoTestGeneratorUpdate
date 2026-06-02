package ru.autotestgen.generator;

import ru.autotestgen.model.AttrType;
import ru.autotestgen.model.Property;
import ru.autotestgen.model.SearchParam;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Generates appropriate test data values based on field type, mask, and stereoType.
 * Respects mask patterns from XML metadata to produce valid input.
 */
public class TestDataFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final Random RND = new Random(42); // fixed seed for reproducibility

    /** Текущее МСК-время минус 10 минут — реалистичное значение для DATETIME-полей,
     *  не падает в будущее (некоторые валидаторы отвергают «дата позже now»). */
    private static String nowMskMinus10() {
        return ZonedDateTime.now(MSK).minusMinutes(10).format(DATE_TIME_FORMAT);
    }

    private static String todayMsk() {
        return ZonedDateTime.now(MSK).toLocalDate().format(DATE_FORMAT);
    }

    /**
     * Generates a test value for a Property, respecting mask if present.
     */
    public static String generateValue(Property property) {
        if ("Directory".equals(property.getStereoType()) || "Ref".equals(property.getStereoType())) {
            return null; // Dropdowns/references handled separately
        }

        String mask = property.getMask();

        // If mask is present, generate data matching the mask
        if (mask != null && !mask.isEmpty()) {
            return generateFromMask(mask);
        }

        return switch (property.getAttrType()) {
            case DECIMAL -> String.valueOf(100 + RND.nextInt(900));
            case DATE -> todayMsk();
            case DATETIME -> nowMskMinus10();
            case STRING -> "Test_" + property.getAttrName();
        };
    }

    /**
     * Generates a test value for a SearchParam, respecting its valueType and mask.
     */
    public static String generateSearchParamValue(SearchParam param) {
        String mask = param.getMask();
        if (mask != null && !mask.isEmpty()) {
            return generateFromMask(mask);
        }

        String type = param.getValueType();
        if (type == null) return "Test";

        return switch (type.toLowerCase()) {
            case "integer", "decimal", "number", "numeric" -> String.valueOf(100 + RND.nextInt(900));
            case "date" -> todayMsk();
            case "datetime" -> nowMskMinus10();
            default -> "Test_" + param.getName();
        };
    }

    /**
     * Generates data from an E3Core mask pattern.
     *
     * Placeholder grammar (must stay in sync with {@code matchesMask} in generated tests):
     *   digit  : '9', '0', '#'
     *   letter : 'a', 'A', 'L'
     *   any    : 'X', 'x', '*', '?'
     *   other chars are treated as literal separators (-, /, ., space, ...).
     *
     * E.g., mask="999999999999" (INN) -> "123456789012"
     * E.g., mask="99-99" -> "12-34"
     *
     * Special case: masks that look like date or date-time patterns return TODAY's date instead
     * of the synthetic digit sequence. The previous "12.34.5678" output got rejected by the server
     * as an invalid date, masking the real check.
     */
    public static String generateFromMask(String mask) {
        if (mask == null || mask.isEmpty()) return "";
        if (looksLikeDateMask(mask)) return todayMsk();
        if (looksLikeDateTimeMask(mask)) return nowMskMinus10();
        StringBuilder sb = new StringBuilder();
        int digitCounter = 1;
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            if (isDigitMaskChar(c)) {
                sb.append((digitCounter++) % 10); // digits 1,2,3,...,0,1,2,...
            } else if (isLetterMaskChar(c) || isAnyMaskChar(c)) {
                sb.append((char) ('A' + (i % 26)));
            } else {
                sb.append(c); // literal separators like -, /, .
            }
        }
        return sb.toString();
    }

    /** Mask placeholder for a digit position. */
    static boolean isDigitMaskChar(char c) {
        return c == '9' || c == '0' || c == '#';
    }

    /** Mask placeholder for a letter position. */
    static boolean isLetterMaskChar(char c) {
        return c == 'a' || c == 'A' || c == 'L';
    }

    /** Mask placeholder for an "any character" position. */
    static boolean isAnyMaskChar(char c) {
        return c == 'X' || c == 'x' || c == '*' || c == '?';
    }

    /** True for masks like "99.99.9999", "00/00/0000", "##-##-####". */
    private static boolean looksLikeDateMask(String mask) {
        return mask != null && mask.matches("[90#]{2}[./\\-][90#]{2}[./\\-][90#]{4}");
    }

    /** True for masks like "99.99.9999 99:99". */
    private static boolean looksLikeDateTimeMask(String mask) {
        return mask != null && mask.matches("[90#]{2}[./\\-][90#]{2}[./\\-][90#]{4}\\s+[90#]{2}[:.][90#]{2}");
    }

    /**
     * Returns a Java code expression (as a String literal in generated code) for the value.
     */
    public static String generateValueExpression(Property property) {
        String value = generateValue(property);
        if (value == null) return "null";
        return "\"" + value + "\"";
    }

    /**
     * Java source EXPRESSION (NOT a baked literal) that yields a per-run-UNIQUE value at test
     * runtime. Static values like "Test_GBS_NAME" or INN "123456789012" are identical on every
     * run, so on a unique-constrained field (name, ИНН, кадастровый №) the server rejects the
     * second+ insert and the create looks like it "didn't save". Masks are preserved:
     *   - date / datetime masks  → a valid current date/time literal (uniqueness not needed);
     *   - any other mask         → uniqDigits("&lt;mask&gt;"): same shape, runtime-unique digits;
     *   - free-text STRING       → "Test_&lt;attr&gt;_" + uniqSuffix(): unique suffix;
     *   - DECIMAL                → uniqDigits("999999"): unique number.
     * The helpers uniqSuffix() / uniqDigits(String) are emitted into each page object class, so
     * these expressions are only valid inside page-object methods (fillAllFields / fillRequiredFields).
     */
    public static String generateValueCode(Property property) {
        if ("Directory".equals(property.getStereoType()) || "Ref".equals(property.getStereoType())) {
            return "null"; // FK / reference — picked from dropdown, not typed
        }
        String mask = property.getMask();
        if (mask != null && !mask.isEmpty()) {
            if (looksLikeDateMask(mask))     return "\"" + todayMsk() + "\"";
            if (looksLikeDateTimeMask(mask)) return "\"" + nowMskMinus10() + "\"";
            return "uniqDigits(\"" + escapeJavaString(mask) + "\")";
        }
        return switch (property.getAttrType()) {
            case DECIMAL  -> "uniqDigits(\"999999\")";
            case DATE     -> "\"" + todayMsk() + "\"";
            case DATETIME -> "\"" + nowMskMinus10() + "\"";
            case STRING   -> "\"Test_" + escapeJavaString(property.getAttrName()) + "_\" + uniqSuffix()";
        };
    }

    /** Escapes a value so it can be safely embedded inside a Java string literal in generated code. */
    static String escapeJavaString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
