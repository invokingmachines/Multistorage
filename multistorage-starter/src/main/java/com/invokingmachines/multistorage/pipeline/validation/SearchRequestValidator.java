package com.invokingmachines.multistorage.pipeline.validation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Criteria;
import com.invokingmachines.multistorage.dto.query.Criterion;
import com.invokingmachines.multistorage.dto.query.Node;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.pipeline.OperationType;
import com.invokingmachines.multistorage.pipeline.meta.QueryMetaFilter;
import com.invokingmachines.multistorage.query.service.QueryCompiler;

import java.util.List;

@org.springframework.stereotype.Component
public class SearchRequestValidator implements RequestValidator<Query> {

    @Override
    public OperationType getOperationType() {
        return OperationType.SEARCH;
    }

    @Override
    public QueryMeta validate(Query request, QueryMeta fullMeta, String targetTableName) {
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);
        TableMeta table = fullMeta.getTables().get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table not found: " + targetTableName);
        }
        if (request.getSelect() != null) {
            for (List<String> path : request.getSelect()) {
                if (path != null && !path.isEmpty() && !"*".equals(path.get(path.size() - 1))) {
                    validatePath(path, tableName, fullMeta);
                } else if (path != null && path.size() >= 2 && "*".equals(path.get(path.size() - 1))) {
                    validateRelationChain(path.subList(0, path.size() - 1), tableName, fullMeta);
                }
            }
        }
        if (request.getWhere() != null) {
            validateCriteria(request.getWhere(), tableName, fullMeta);
        }
        return QueryMetaFilter.filterForSearch(fullMeta, tableName, request);
    }

    private void validatePath(List<String> path, String tableName, QueryMeta meta) {
        if (path.size() == 1) {
            validateColumn(tableName, path.get(0), meta);
            return;
        }
        TableMeta current = meta.getTables().get(tableName);
        for (int i = 0; i < path.size() - 1; i++) {
            RelationMeta rel = current.getRelations().get(path.get(i));
            if (rel == null) {
                throw new IllegalArgumentException("Relation not found: " + path.get(i) + " in table " + current.getName());
            }
            current = meta.getTables().get(rel.getToTable());
        }
        validateColumn(current.getName(), path.get(path.size() - 1), meta);
    }

    private void validateRelationChain(List<String> chain, String tableName, QueryMeta meta) {
        TableMeta current = meta.getTables().get(tableName);
        for (String relAlias : chain) {
            RelationMeta rel = current.getRelations().get(relAlias);
            if (rel == null) {
                throw new IllegalArgumentException("Relation not found: " + relAlias + " in table " + current.getName());
            }
            current = meta.getTables().get(rel.getToTable());
        }
    }

    private void validateColumn(String tableName, String columnRef, QueryMeta meta) {
        TableMeta table = meta.getTables().get(tableName);
        if (table.getColumns().containsKey(columnRef)) return;
        if (table.getColumns().values().stream().anyMatch(c -> columnRef.equals(c.getAlias()))) return;
        throw new IllegalArgumentException("Column not found: " + columnRef + " in table " + tableName);
    }

    private void validateCriteria(Criteria criteria, String tableName, QueryMeta meta) {
        for (Node n : criteria.getCriteria()) {
            if (n instanceof Criterion) {
                List<String> field = ((Criterion) n).getField();
                if (field != null && !field.isEmpty()) {
                    validatePath(field, tableName, meta);
                }
            } else if (n instanceof Criteria) {
                validateCriteria((Criteria) n, tableName, meta);
            }
        }
    }
}
