package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntitySelectBuilder {

    private EntitySelectBuilder() {}

    public static List<List<String>> expandedSelectFromEntity(Map<String, Object> entity, String rootTableName, QueryMeta meta) {
        TableMeta table = meta.getTables().get(rootTableName);
        if (table == null) return List.of(List.of("id"));
        List<List<String>> result = new ArrayList<>();
        Set<String> colNames = new LinkedHashSet<>();
        for (var col : table.getColumns().values()) {
            colNames.add(col.getName());
        }
        colNames.stream().map(List::of).forEach(result::add);
        if (table.getRelations() == null) return result;
        for (Map.Entry<String, Object> e : entity.entrySet()) {
            RelationMeta rel = table.getRelations().get(e.getKey());
            if (rel == null) continue;
            result.add(List.of(rel.getAlias(), "*"));
        }
        return result.isEmpty() ? List.of(List.of("id")) : result;
    }
}
