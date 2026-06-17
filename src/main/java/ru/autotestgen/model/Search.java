package ru.autotestgen.model;

import java.util.ArrayList;
import java.util.List;

public class Search {
    private String guid;
    private String name;
    private String searchObjectGuid;
    private String query;
    private int minParamCount;
    private List<SearchParam> params = new ArrayList<>();
    private SearchResult result;

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSearchObjectGuid() { return searchObjectGuid; }
    public void setSearchObjectGuid(String searchObjectGuid) { this.searchObjectGuid = searchObjectGuid; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getMinParamCount() { return minParamCount; }
    public void setMinParamCount(int minParamCount) { this.minParamCount = minParamCount; }

    public List<SearchParam> getParams() { return params; }
    public void setParams(List<SearchParam> params) { this.params = params; }

    public SearchResult getResult() { return result; }
    public void setResult(SearchResult result) { this.result = result; }
}
