package com.invokingmachines.multistorage.pipeline.meta;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Criteria;
import com.invokingmachines.multistorage.dto.query.Criterion;
import com.invokingmachines.multistorage.dto.query.Node;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.query.service.QueryCompiler;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QueryMetaFilter {

    private QueryMetaFilter() {}

    public static QueryMeta filterForSearch(QueryMeta fullMeta, String rootTableName, Query query) {
        Set<String> tableNames = collectSearchTables(query, rootTableName, fullMeta);
        return subMeta(fullMeta, tableNames);
    }

    public static QueryMeta filterForEntity(QueryMeta fullMeta, String rootTableName, Map<String, Object> entity) {
        Set<String> tableNames = collectEntityTables(entity, rootTableName, fullMeta);
        return subMeta(fullMeta, tableNames);
    }

    private static Set<String> collectSearchTables(Query query, String tableName, QueryMeta meta) {
        Set<String> result = new HashSet<>();
        result.add(QueryCompiler.resolveTargetToTableName(meta, tableName));
        TableMeta table = meta.getTables().get(result.iterator().next());
        if (table == null) return result;
        if (query.getSelect() != null) {
            for (List<String> path : query.getSelect()) {
                if (path != null && path.size() >= 2 && "*".equals(path.get(path.size() - 1))) {
                    collectChainTables(path.subList(0, path.size() - 1), table.getName(), meta, result);
                }
            }
        }
        if (query.getWhere() != null) {
            collectCriteriaTables(query.getWhere(), table.getName(), meta, result);
        }
        return result;
    }

    private static void collectCriteriaTables(Criteria criteria, String tableName, QueryMeta meta, Set<String> result) {
        for (Node n : criteria.getCriteria()) {
            if (n instanceof Criterion) {
                List<String> field = ((Criterion) n).getField();
                if (field != null && field.size() > 1) {
                    collectChainTables(field.subList(0, field.size() - 1), tableName, meta, result);
                }
            } else if (n instanceof Criteria) {
                collectCriteriaTables((Criteria) n, tableName, meta, result);
            }
        }
    }

    private static void collectChainTables(List<String> chain, String tableName, QueryMeta meta, Set<String> result) {
        TableMeta current = meta.getTables().get(tableName);
        for (String relAlias : chain) {
            if (current == null) return;
            RelationMeta rel = current.getRelations().get(relAlias);
            if (rel == null) return;
            result.add(rel.getToTable());
            current = meta.getTables().get(rel.getToTable());
        }
    }

    private static Set<String> collectEntityTables(Map<String, Object> entity, String tableName, QueryMeta meta) {
        Set<String> result = new HashSet<>();
        result.add(QueryCompiler.resolveTargetToTableName(meta, tableName));
        TableMeta table = meta.getTables().get(tableName);
        if (table == null) return result;
        for (Map.Entry<String, Object> e : entity.entrySet()) {
            RelationMeta rel = table.getRelations().get(e.getKey());
            if (rel != null) {
                result.add(rel.getToTable());
                Object val = e.getValue();
                if (val instanceof Map) {
                    result.addAll(collectEntityTables((Map<String, Object>) val, rel.getToTable(), meta));
                } else if (val instanceof Iterable) {
                    for (Object item : (Iterable<?>) val) {
                        if (item instanceof Map) {
                            result.addAll(collectEntityTables((Map<String, Object>) item, rel.getToTable(), meta));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static QueryMeta subMeta(QueryMeta fullMeta, Set<String> tableNames) {
        Map<String, TableMeta> filtered = new LinkedHashMap<>();
        for (String tn : tableNames) {
            TableMeta t = fullMeta.getTables().get(tn);
            if (t != null) filtered.put(tn, t);
        }
        return QueryMeta.builder().tables(filtered).build();
    }
}
