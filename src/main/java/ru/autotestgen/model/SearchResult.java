package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {
    private String idObjectName;
    private List<SearchResultProperty> properties = new ArrayList<>();

    public String getIdObjectName() { return idObjectName; }
    public void setIdObjectName(String idObjectName) { this.idObjectName = idObjectName; }

    public List<SearchResultProperty> getProperties() { return properties; }
    public void setProperties(List<SearchResultProperty> properties) { this.properties = properties; }
}
