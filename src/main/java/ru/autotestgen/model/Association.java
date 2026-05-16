package ru.autotestgen.model;

public class Association {
    private String guid;
    private String roleA;
    private String roleB;
    private String roleACaption;
    private String featureName;
    private String associationId;
    private String associateItemName;
    private String associateItemGuid;
    private String searchGuid;
    private boolean flagDisplay;

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getRoleA() { return roleA; }
    public void setRoleA(String roleA) { this.roleA = roleA; }

    public String getRoleB() { return roleB; }
    public void setRoleB(String roleB) { this.roleB = roleB; }

    public String getRoleACaption() { return roleACaption; }
    public void setRoleACaption(String roleACaption) { this.roleACaption = roleACaption; }

    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }

    public String getAssociationId() { return associationId; }
    public void setAssociationId(String associationId) { this.associationId = associationId; }

    public String getAssociateItemName() { return associateItemName; }
    public void setAssociateItemName(String associateItemName) { this.associateItemName = associateItemName; }

    public String getAssociateItemGuid() { return associateItemGuid; }
    public void setAssociateItemGuid(String associateItemGuid) { this.associateItemGuid = associateItemGuid; }

    public String getSearchGuid() { return searchGuid; }
    public void setSearchGuid(String searchGuid) { this.searchGuid = searchGuid; }

    public boolean isFlagDisplay() { return flagDisplay; }
    public void setFlagDisplay(boolean flagDisplay) { this.flagDisplay = flagDisplay; }
}
