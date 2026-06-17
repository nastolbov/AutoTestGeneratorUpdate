package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class Operation {
    private String guid;
    private String operationMethod;
    private String operationModule;
    private List<OperationParam> params = new ArrayList<>();
    private List<Modifier> modifiers = new ArrayList<>();

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getOperationMethod() { return operationMethod; }
    public void setOperationMethod(String operationMethod) { this.operationMethod = operationMethod; }

    public String getOperationModule() { return operationModule; }
    public void setOperationModule(String operationModule) { this.operationModule = operationModule; }

    public List<OperationParam> getParams() { return params; }
    public void setParams(List<OperationParam> params) { this.params = params; }

    public List<Modifier> getModifiers() { return modifiers; }
    public void setModifiers(List<Modifier> modifiers) { this.modifiers = modifiers; }
}
