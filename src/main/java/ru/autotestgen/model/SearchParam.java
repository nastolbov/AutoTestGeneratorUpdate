package ru.autotestgen.model;

public class SearchParam {
    private String name;
    private String title;
    private String valueType;
    private String mask;
    private boolean required;
    private int orderNumber;
    private String searchGuid;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getMask() { return mask; }
    public void setMask(String mask) { this.mask = mask; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public String getSearchGuid() { return searchGuid; }
    public void setSearchGuid(String searchGuid) { this.searchGuid = searchGuid; }
}
