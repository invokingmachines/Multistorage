package com.invokingmachines.multistorage.pipeline.operation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.pipeline.CascadeType;
import com.invokingmachines.multistorage.query.service.MetaAliasMapper;
import com.invokingmachines.multistorage.query.service.ValueConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JdbcEntityPersistor implements EntityPersistor {

    private static final String PK_COLUMN = "id";

    private final JdbcTemplate jdbcTemplate;
    private final ValueConverter valueConverter;
    private final MetaAliasMapper metaAliasMapper;

    @Override
    public Map<String, Object> upsert(String targetTableName, Map<String, Object> entity, QueryMeta meta) {
        TableMeta table = meta.getTables().get(targetTableName);
        Map<String, Object> flat = new LinkedHashMap<>(entity);
        Map<String, Object> manyRelations = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : new LinkedHashMap<>(flat).entrySet()) {
            RelationMeta rel = table.getRelations().get(e.getKey());
            if (rel == null) continue;
            if (rel.getCascade() == CascadeType.NONE) continue;
            flat.remove(e.getKey());
            if (rel.isOneToMany()) {
                manyRelations.put(e.getKey(), e.getValue());
            } else {
                Object parentId = persistRelation(rel, e.getValue(), meta);
                flat.put(rel.getFromColumn(), parentId);
            }
        }
        Object id = flat.get(PK_COLUMN) != null ? flat.get(PK_COLUMN) : resolveByAlias(table, PK_COLUMN, flat);
        Map<String, Object> result;
        if (id != null) {
            result = update(targetTableName, flat, table);
        } else {
            result = insert(targetTableName, flat, table);
        }
        Object parentId = result.get(PK_COLUMN);
        for (Map.Entry<String, Object> e : manyRelations.entrySet()) {
            RelationMeta rel = table.getRelations().get(e.getKey());
            if (rel.getCascade() == CascadeType.PERSIST || rel.getCascade() == CascadeType.PERSIST_MERGE) {
                persistChildren(rel, e.getValue(), parentId, meta);
            }
        }
        return result;
    }

    @Override
    public void delete(String targetTableName, Object id, QueryMeta meta) {
        TableMeta table = meta.getTables().get(targetTableName);
        String sql = "DELETE FROM \"" + table.getName() + "\" WHERE \"" + PK_COLUMN + "\" = ?";
        jdbcTemplate.update(sql, id);
    }

    private Object persistRelation(RelationMeta rel, Object value, QueryMeta meta) {
        if (value instanceof Map) {
            Map<String, Object> child = new LinkedHashMap<>((Map<String, Object>) value);
            child.put(rel.getToColumn(), null);
            Map<String, Object> saved = upsert(rel.getToTable(), child, meta);
            return saved.get(PK_COLUMN);
        }
        return value;
    }

    private void persistChildren(RelationMeta rel, Object value, Object parentId, QueryMeta meta) {
        if (!(value instanceof List)) return;
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                Map<String, Object> child = new LinkedHashMap<>((Map<String, Object>) item);
                child.put(rel.getFromColumn(), parentId);
                upsert(rel.getToTable(), child, meta);
            }
        }
    }

    private Map<String, Object> insert(String tableName, Map<String, Object> entity, TableMeta table) {
        Map<String, Object> filtered = filterColumns(entity, table);
        filtered.remove(PK_COLUMN);
        List<String> cols = new java.util.ArrayList<>(filtered.keySet());
        List<Object> vals = new java.util.ArrayList<>(filtered.values());
        String colsStr = cols.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", "));
        String placeholders = cols.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO \"" + table.getName() + "\" (" + colsStr + ") VALUES (" + placeholders + ") RETURNING \"" + PK_COLUMN + "\"";
        Long generatedId = jdbcTemplate.queryForObject(sql, vals.toArray(), Long.class);
        Map<String, Object> result = new LinkedHashMap<>(entity);
        result.put(PK_COLUMN, generatedId);
        return result;
    }

    private Map<String, Object> update(String tableName, Map<String, Object> entity, TableMeta table) {
        Map<String, Object> filtered = filterColumns(entity, table);
        Object id = filtered.remove(PK_COLUMN);
        if (filtered.isEmpty()) {
            return new LinkedHashMap<>(entity);
        }
        String setClause = filtered.keySet().stream()
                .map(c -> "\"" + c + "\" = ?")
                .collect(Collectors.joining(", "));
        String sql = "UPDATE \"" + table.getName() + "\" SET " + setClause + " WHERE \"" + PK_COLUMN + "\" = ?";
        List<Object> params = filtered.values().stream().collect(Collectors.toList());
        params.add(id);
        jdbcTemplate.update(sql, params.toArray());
        return new LinkedHashMap<>(entity);
    }

    private Map<String, Object> filterColumns(Map<String, Object> entity, TableMeta table) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> dataTypes = metaAliasMapper.columnDataTypes(table);
        for (Map.Entry<String, Object> e : entity.entrySet()) {
            String colName = metaAliasMapper.resolveColumnName(table, e.getKey());
            if (colName != null) {
                String dataType = dataTypes.get(e.getKey()) != null ? dataTypes.get(e.getKey()) : dataTypes.get(colName);
                result.put(colName, valueConverter.toJdbcValue(e.getValue(), dataType));
            }
        }
        return result;
    }

    private Object resolveByAlias(TableMeta table, String colRef, Map<String, Object> entity) {
        for (Map.Entry<String, Object> e : entity.entrySet()) {
            String resolved = metaAliasMapper.resolveColumnName(table, e.getKey());
            if (resolved != null && PK_COLUMN.equals(resolved)) {
                return e.getValue();
            }
        }
        return entity.get(colRef);
    }
}
