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
 * Генерирует тестовые значения по типу поля, маске и стереотипу.
 * Учитывает маски из XML-метаданных, чтобы значение было корректным.
 */
public class TestDataFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final Random RND = new Random(42); // фиксированное зерно для воспроизводимости

    /** Текущее МСК-время минус 10 минут — реалистичное значение для DATETIME-полей,
     *  не падает в будущее (некоторые валидаторы отвергают «дата позже now»). */
    private static String nowMskMinus10() {
        return ZonedDateTime.now(MSK).minusMinutes(10).format(DATE_TIME_FORMAT);
    }

    private static String todayMsk() {
        return ZonedDateTime.now(MSK).toLocalDate().format(DATE_FORMAT);
    }

    /**
     * Генерирует тестовое значение для свойства с учётом маски, если она задана.
     */
    public static String generateValue(Property property) {
        if ("Directory".equals(property.getStereoType()) || "Ref".equals(property.getStereoType())) {
            return null; // выпадающие списки/ссылки обрабатываются отдельно
        }

        String mask = property.getMask();

        // Если маска задана — генерируем значение по ней
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
     * Генерирует тестовое значение для параметра поиска с учётом его типа и маски.
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
     * Генерирует значение по маске E3Core.
     *
     * Грамматика плейсхолдеров (должна совпадать с {@code matchesMask} в сгенерированных тестах):
     *   цифра  : '9', '0', '#'
     *   буква  : 'a', 'A', 'L'
     *   любой  : 'X', 'x', '*', '?'
     *   остальные символы считаются литеральными разделителями (-, /, ., пробел, ...).
     *
     * Напр., mask="999999999999" (ИНН) -> "123456789012"
     * Напр., mask="99-99" -> "12-34"
     *
     * Особый случай: маски, похожие на дату или дату-время, возвращают сегодняшнюю дату вместо
     * синтетической последовательности цифр — иначе сервер отвергает значение как некорректную дату.
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
                sb.append((digitCounter++) % 10); // цифры 1,2,3,...,0,1,2,...
            } else if (isLetterMaskChar(c) || isAnyMaskChar(c)) {
                sb.append((char) ('A' + (i % 26)));
            } else {
                sb.append(c); // литеральные разделители вроде -, /, .
            }
        }
        return sb.toString();
    }

    /**
     * Значение, которое реально НАБИРАЮТ в masked-поле (Ext-маска сама подставляет литералы-разделители).
     * Для дат/дат-времени возвращаем отформатированное значение (разделители совпадают с вводом).
     * Для прочих масок (например "99ч99мин") возвращаем только символы плейсхолдеров — иначе ввод
     * литералов ('ч','м','и','н') ломает маску и поле остаётся пустым ("__ч__мин"). Универсально для
     * масок любых новых систем.
     */
    public static String maskTypingValue(String mask) {
        if (mask == null || mask.isEmpty()) return "";
        if (looksLikeDateMask(mask) || looksLikeDateTimeMask(mask)) return generateFromMask(mask);
        StringBuilder sb = new StringBuilder();
        int digitCounter = 1;
        for (int i = 0; i < mask.length(); i++) {
            char c = mask.charAt(i);
            if (isDigitMaskChar(c)) {
                sb.append((digitCounter++) % 10);
            } else if (isLetterMaskChar(c) || isAnyMaskChar(c)) {
                sb.append((char) ('A' + (i % 26)));
            }
            // литералы (ч, м, и, н, -, /, .) НЕ набираем — маска подставит их сама
        }
        return sb.toString();
    }

    /** Плейсхолдер маски для позиции цифры. */
    static boolean isDigitMaskChar(char c) {
        return c == '9' || c == '0' || c == '#';
    }

    /** Плейсхолдер маски для позиции буквы. */
    static boolean isLetterMaskChar(char c) {
        return c == 'a' || c == 'A' || c == 'L';
    }

    /** Плейсхолдер маски для позиции «любой символ». */
    static boolean isAnyMaskChar(char c) {
        return c == 'X' || c == 'x' || c == '*' || c == '?';
    }

    /** true для масок вида "99.99.9999", "00/00/0000", "##-##-####". */
    private static boolean looksLikeDateMask(String mask) {
        return mask != null && mask.matches("[90#]{2}[./\\-][90#]{2}[./\\-][90#]{4}");
    }

    /** true для масок вида "99.99.9999 99:99". */
    private static boolean looksLikeDateTimeMask(String mask) {
        return mask != null && mask.matches("[90#]{2}[./\\-][90#]{2}[./\\-][90#]{4}\\s+[90#]{2}[:.][90#]{2}");
    }

    /**
     * Возвращает значение как выражение Java (строковый литерал в сгенерированном коде).
     */
    public static String generateValueExpression(Property property) {
        String value = generateValue(property);
        if (value == null) return "null";
        return "\"" + value + "\"";
    }
}
