package ru.autotestgen.model;

public class Property {
    private String guid;
    private String name;
    private String stereoType;
    private String dmodule;
    private String attrName;
    private String tableName;
    private AttrType attrType;
    private boolean required;
    private String mask;
    private int orderNumber;
    private boolean flagDisplay;

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStereoType() { return stereoType; }
    public void setStereoType(String stereoType) { this.stereoType = stereoType; }

    public String getDmodule() { return dmodule; }
    public void setDmodule(String dmodule) { this.dmodule = dmodule; }

    public String getAttrName() { return attrName; }
    public void setAttrName(String attrName) { this.attrName = attrName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public AttrType getAttrType() { return attrType; }
    public void setAttrType(AttrType attrType) { this.attrType = attrType; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getMask() { return mask; }
    public void setMask(String mask) { this.mask = mask; }

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public boolean isFlagDisplay() { return flagDisplay; }
    public void setFlagDisplay(boolean flagDisplay) { this.flagDisplay = flagDisplay; }
}
