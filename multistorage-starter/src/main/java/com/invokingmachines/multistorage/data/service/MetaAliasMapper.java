package com.invokingmachines.multistorage.data.service;

import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MetaAliasMapper {

    public Map<String, String> buildAliasesMapping(QueryMeta meta) {
        Map<String, String> aliases = new HashMap<>();
        for (TableMeta t : meta.getTables().values()) {
            aliases.put(t.getAlias(), t.getName());
            aliases.put(t.getName(), t.getName());
            for (ColumnMeta col : t.getColumns().values()) {
                aliases.put(col.getAlias(), col.getName());
                aliases.put(col.getName(), col.getName());
            }
            for (RelationMeta r : t.getRelations().values()) {
                aliases.put(r.getAlias(), r.getToTable());
            }
        }
        return aliases;
    }

    public String resolveColumnName(TableMeta table, String ref) {
        ColumnMeta byKey = table.getColumns().get(ref);
        if (byKey != null) return byKey.getName();
        return table.getColumns().values().stream()
                .filter(c -> ref.equals(c.getAlias()))
                .map(ColumnMeta::getName)
                .findFirst()
                .orElse(null);
    }

    public Map<String, String> columnDataTypes(TableMeta table) {
        Map<String, String> result = new HashMap<>();
        table.getColumns().forEach((name, col) -> {
            result.put(name, col.getDataType());
            if (col.getAlias() != null && !col.getAlias().equals(name)) {
                result.put(col.getAlias(), col.getDataType());
            }
        });
        return result;
    }
}
