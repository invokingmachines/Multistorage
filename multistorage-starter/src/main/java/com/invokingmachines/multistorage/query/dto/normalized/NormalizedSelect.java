package com.invokingmachines.multistorage.query.dto.normalized;

import java.util.List;
import java.util.Map;

public final class NormalizedSelect {

    private final String tableName;
    private final String sqlAlias;
    private final List<NormalizedColumn> columns;
    private final Map<String, NormalizedSelect> relations;

    public NormalizedSelect(
            String tableName,
            String sqlAlias,
            List<NormalizedColumn> columns,
            Map<String, NormalizedSelect> relations
    ) {
        this.tableName = tableName;
        this.sqlAlias = sqlAlias;
        this.columns = columns;
        this.relations = relations;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSqlAlias() {
        return sqlAlias;
    }

    public List<NormalizedColumn> getColumns() {
        return columns;
    }

    public Map<String, NormalizedSelect> getRelations() {
        return relations;
    }

    public static final class NormalizedColumn {
        private final String physicalName;
        private final String outputKey;

        public NormalizedColumn(String physicalName, String outputKey) {
            this.physicalName = physicalName;
            this.outputKey = outputKey;
        }

        public String getPhysicalName() {
            return physicalName;
        }

        public String getOutputKey() {
            return outputKey;
        }
    }
}
