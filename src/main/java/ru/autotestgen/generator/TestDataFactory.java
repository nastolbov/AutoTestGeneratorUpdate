package ru.autotestgen.generator;

import ru.autotestgen.model.AttrType;
import ru.autotestgen.model.Property;
import ru.autotestgen.model.SearchParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Generates appropriate test data values based on field type, mask, and stereoType.
 * Respects mask patterns from XML metadata to produce valid input.
 */
public class TestDataFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Random RND = new Random(42); // fixed seed for reproducibility

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
            case DATE -> LocalDate.now().format(DATE_FORMAT);
            case DATETIME -> LocalDate.now().format(DATE_FORMAT) + " 12:00";
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
            case "date" -> LocalDate.now().format(DATE_FORMAT);
            case "datetime" -> LocalDate.now().format(DATE_FORMAT) + " 12:00";
            default -> "Test_" + param.getName();
        };
    }

    /**
     * Generates data from an E3Core mask pattern.
     * '9' = digit, 'A' = letter, 'X' = alphanumeric, other chars = literal.
     * E.g., mask="999999999999" (INN) -> "123456789012"
     * E.g., mask="99-99" -> "12-34"
     */
    public static String generateFromMask(String mask) {
        StringBuilder sb = new StringBuilder();
        int digitCounter = 1;
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            switch (c) {
                case '9' -> sb.append((digitCounter++) % 10); // digits 1,2,3,...,0,1,2,...
                case 'A' -> sb.append((char) ('A' + (i % 26)));
                case 'X' -> sb.append((char) ('A' + (i % 26)));
                default -> sb.append(c); // literal separators like -, /, .
            }
        }
        return sb.toString();
    }

    /**
     * Returns a Java code expression (as a String literal in generated code) for the value.
     */
    public static String generateValueExpression(Property property) {
        String value = generateValue(property);
        if (value == null) return "null";
        return "\"" + value + "\"";
    }
}
