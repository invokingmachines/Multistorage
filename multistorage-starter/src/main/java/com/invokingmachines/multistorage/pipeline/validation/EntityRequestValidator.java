package com.invokingmachines.multistorage.pipeline.validation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.pipeline.CascadeType;
import com.invokingmachines.multistorage.pipeline.OperationType;
import com.invokingmachines.multistorage.pipeline.meta.QueryMetaFilter;
import com.invokingmachines.multistorage.query.service.QueryCompiler;

import java.util.List;
import java.util.Map;

@org.springframework.stereotype.Component
public class EntityRequestValidator implements RequestValidator<Map<String, Object>> {

    @Override
    public OperationType getOperationType() {
        return OperationType.UPSERT;
    }

    @Override
    public QueryMeta validate(Map<String, Object> request, QueryMeta fullMeta, String targetTableName) {
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);
        validateEntityFields(request, tableName, fullMeta, true);
        return QueryMetaFilter.filterForEntity(fullMeta, tableName, request);
    }

    private void validateEntityFields(Map<String, Object> entity, String tableName, QueryMeta meta, boolean isRoot) {
        TableMeta table = meta.getTables().get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        for (Map.Entry<String, Object> e : entity.entrySet()) {
            String key = e.getKey();
            if (table.getColumns().containsKey(key) || table.getColumns().values().stream()
                    .anyMatch(c -> key.equals(c.getAlias()))) {
                continue;
            }
            RelationMeta rel = table.getRelations().get(key);
            if (rel == null) {
                throw new IllegalArgumentException("Field not found: " + key + " in table " + tableName);
            }
            if (!isRoot && rel.getCascade() == CascadeType.NONE) {
                throw new IllegalArgumentException("Cannot update nested entity via relation " + key + ": cascade is NONE");
            }
            Object val = e.getValue();
            if (val instanceof Map) {
                validateEntityFields((Map<String, Object>) val, rel.getToTable(), meta, false);
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) {
                        validateEntityFields((Map<String, Object>) item, rel.getToTable(), meta, false);
                    }
                }
            }
        }
    }
}
