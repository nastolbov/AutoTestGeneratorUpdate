package ru.autotestgen.model;

public enum ModifyType {
    INSERT("I"),
    UPDATE("U"),
    DELETE("D"),
    LOGICAL_EDIT("E"),
    ARCHIVE("A");

    private final String code;

    ModifyType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ModifyType fromCode(String code) {
        for (ModifyType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return null;
    }
}
