package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class AppModel {
    private String categoryName;
    private String guid;
    private List<EntityObject> entities = new ArrayList<>();
    private List<Search> searches = new ArrayList<>();

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public List<EntityObject> getEntities() { return entities; }
    public void setEntities(List<EntityObject> entities) { this.entities = entities; }

    public List<Search> getSearches() { return searches; }
    public void setSearches(List<Search> searches) { this.searches = searches; }

    public EntityObject findEntityByGuid(String guid) {
        return entities.stream()
                .filter(e -> e.getGuid().equals(guid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the last segment of CategoryName after "::" — typically the subsystem name.
     * For "Logical View::Гаражно-строительные кооперативы" returns "Гаражно-строительные кооперативы".
     * Returns an empty string if CategoryName is not set or contains no "::".
     */
    public String getSubsystemNameFromCategory() {
        if (categoryName == null || categoryName.isEmpty()) return "";
        int sep = categoryName.lastIndexOf("::");
        if (sep < 0) return categoryName.trim();
        return categoryName.substring(sep + 2).trim();
    }
}
