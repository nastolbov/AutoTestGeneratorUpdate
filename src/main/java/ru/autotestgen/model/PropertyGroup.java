package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class PropertyGroup {
    private String guid;
    private String name;
    private String stereoType;
    private String dmodule;
    private String typeLink;
    private int orderNumber;
    private boolean flagDisplay;
    private List<Property> properties = new ArrayList<>();
    private Operation operation;

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStereoType() { return stereoType; }
    public void setStereoType(String stereoType) { this.stereoType = stereoType; }

    public String getDmodule() { return dmodule; }
    public void setDmodule(String dmodule) { this.dmodule = dmodule; }

    public String getTypeLink() { return typeLink; }
    public void setTypeLink(String typeLink) { this.typeLink = typeLink; }

    public int getOrderNumber() { return orderNumber; }
    public void setOrderNumber(int orderNumber) { this.orderNumber = orderNumber; }

    public boolean isFlagDisplay() { return flagDisplay; }
    public void setFlagDisplay(boolean flagDisplay) { this.flagDisplay = flagDisplay; }

    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }

    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public boolean isFormView() { return "P".equals(typeLink); }
    public boolean isGridView() { return "Grid".equals(stereoType); }
}
