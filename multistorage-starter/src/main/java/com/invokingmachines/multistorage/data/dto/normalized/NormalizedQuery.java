package com.invokingmachines.multistorage.data.dto.normalized;

import com.invokingmachines.multistorage.dto.query.Criteria;

import java.util.List;
import java.util.Map;

public final class NormalizedQuery {

    private final String rootSqlAlias;
    private final String rootTableName;
    private final NormalizedSelect select;
    private final Criteria where;
    private final Map<String, String> sqlAliasByRelationPath;
    private final boolean paged;
    private final int limit;
    private final int offset;
    private final String sortBy;
    private final boolean sortDesc;

    public NormalizedQuery(String rootSqlAlias,
                           String rootTableName,
                           NormalizedSelect select,
                           Criteria where,
                           Map<String, String> sqlAliasByRelationPath,
                           boolean paged,
                           int limit,
                           int offset,
                           String sortBy,
                           boolean sortDesc) {
        this.rootSqlAlias = rootSqlAlias;
        this.rootTableName = rootTableName;
        this.select = select;
        this.where = where;
        this.sqlAliasByRelationPath = sqlAliasByRelationPath;
        this.paged = paged;
        this.limit = limit;
        this.offset = offset;
        this.sortBy = sortBy;
        this.sortDesc = sortDesc;
    }

    public String getRootSqlAlias() {
        return rootSqlAlias;
    }

    public String getRootTableName() {
        return rootTableName;
    }

    public NormalizedSelect getSelect() {
        return select;
    }

    public Criteria getWhere() {
        return where;
    }

    public Map<String, String> getSqlAliasByRelationPath() {
        return sqlAliasByRelationPath;
    }

    public boolean isPaged() {
        return paged;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public String getSortBy() {
        return sortBy;
    }

    public boolean isSortDesc() {
        return sortDesc;
    }

    public String sqlAliasFor(List<String> relationPath) {
        String key = relationPath.isEmpty() ? "" : String.join(".", relationPath);
        return sqlAliasByRelationPath.getOrDefault(key, rootSqlAlias);
    }
}

