package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class EntityObject {
    private String guid;
    private String name;
    private String keyName;
    private String featureName;
    private String nameValueMethod;
    private List<Association> associations = new ArrayList<>();
    private List<PropertyGroup> propertyGroups = new ArrayList<>();

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }

    public String getNameValueMethod() { return nameValueMethod; }
    public void setNameValueMethod(String nameValueMethod) { this.nameValueMethod = nameValueMethod; }

    public List<Association> getAssociations() { return associations; }
    public void setAssociations(List<Association> associations) { this.associations = associations; }

    public List<PropertyGroup> getPropertyGroups() { return propertyGroups; }
    public void setPropertyGroups(List<PropertyGroup> propertyGroups) { this.propertyGroups = propertyGroups; }

    public boolean hasCrudOperations() {
        return propertyGroups.stream()
                .anyMatch(pg -> pg.getOperation() != null && !pg.getOperation().getModifiers().isEmpty());
    }

    public PropertyGroup getFormView() {
        return propertyGroups.stream()
                .filter(PropertyGroup::isFormView)
                .findFirst()
                .orElse(null);
    }
}
