package ru.autotestgen.parser;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Статические вспомогательные методы для всех парсеров: доступ к атрибутам,
 * безопасный разбор целых чисел и перемотка курсора до закрывающего элемента.
 */
public final class StaxUtils {

    private StaxUtils() {}

    public static String attr(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value != null ? value : "";
    }

    public static int parseInt(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void skipToEnd(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }
}
