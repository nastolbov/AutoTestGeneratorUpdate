package ru.autotestgen.model;

public enum AttrType {
    STRING, DECIMAL, DATE, DATETIME;

    public static AttrType fromXml(String value) {
        if (value == null || value.isEmpty()) return STRING;
        return switch (value.toLowerCase()) {
            case "decimal" -> DECIMAL;
            case "date" -> DATE;
            case "datetime" -> DATETIME;
            default -> STRING;
        };
    }
}
