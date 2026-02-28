package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class QueryCompiler {

    public CompiledQuery compile(Query query, QueryMeta meta, String target) {
        if (isEmptyWhere(query.getWhere()))
            query.setWhere(new Criteria(Logician.AND, List.of()));

        Map<String, String> aliasesMapping = mapAlliases(meta);
        List<List<String>> expandedSelect = expandSelect(query.getSelect(), meta, target);

        return CompiledQuery.builder()
                .sql(buildSql(expandedSelect, query, meta, aliasesMapping, target))
                .parameters(mapCriteriaToParams(query.getWhere(), meta, target, aliasesMapping))
                .expandedSelect(expandedSelect)
                .build();
    }

    private static boolean isEmptyWhere(Criteria c) {
        return c == null || c.getCriteria() == null || c.getCriteria().isEmpty();
    }

    private Map<String, String> mapAlliases(QueryMeta meta) {
        Map<String, String> aliases = new HashMap<>();
        for (TableMeta t: meta.getTables().values()) {
            aliases.put(t.getAlias(), t.getName());
            for (ColumnMeta col: t.getColumns().values()) {
                aliases.put(col.getAlias(), col.getName());
            }

            for (RelationMeta r: t.getRelations().values()) {
                aliases.put(r.getName(), r.getChildTable());
            }
        }
        return aliases;
    }

    private String buildSql(List<List<String>> expandedSelect, Query query, QueryMeta meta, Map<String, String> aliasesMapping, String target) {
        String select = buildSelect(target, expandedSelect, meta);
        String from = buildFrom(expandedSelect, query.getWhere(), meta, aliasesMapping, target);
        return select + from + buildWhere(target, query.getWhere()) + ";";
    }

    private List<List<String>> expandSelect(List<List<String>> select, QueryMeta meta, String target) {
        if (select == null || select.isEmpty()) {
            return expandStar(meta.getTables().get(target), target, null);
        }
        List<List<String>> result = new ArrayList<>();
        for (List<String> path : select) {
            if (path == null || path.isEmpty()) continue;
            if (path.size() == 1 && "*".equals(path.getFirst())) {
                result.addAll(expandStar(meta.getTables().get(target), target, null));
            } else if (path.size() == 2 && "*".equals(path.get(1))) {
                String relationName = path.get(0);
                RelationMeta relation = meta.getTables().get(target).getRelations().get(relationName);
                TableMeta relationTable = relation != null ? resolveTableByPhysicalName(meta, relation.getChildTable()) : null;
                if (relationTable != null)
                    result.addAll(expandStar(relationTable, relationName, relationName));
            } else {
                result.add(path);
            }
        }
        return result;
    }

    private TableMeta resolveTableByPhysicalName(QueryMeta meta, String physicalName) {
        return meta.getTables().values().stream()
                .filter(t -> physicalName.equals(t.getName()))
                .findFirst()
                .orElse(meta.getTables().get(physicalName));
    }

    private List<List<String>> expandStar(TableMeta table, String tableAlias, String relationPrefix) {
        return table.getColumns().values().stream()
                .filter(c -> Boolean.TRUE.equals(c.getReadable()))
                .map(c -> relationPrefix == null ? List.of(c.getAlias()) : List.of(relationPrefix, c.getAlias()))
                .toList();
    }

    private String buildWhere(String target, Criteria criteria) {
        return " WHERE" + parseCriteria(target, criteria);
    }

    private String parseCriteria(String target, Criteria criteria) {
        if (criteria.getCriteria().isEmpty()) {
            return " (1=1) ";
        }
        StringBuilder subwhere = new StringBuilder();
        subwhere.append(" (");
        for (Node n : criteria.getCriteria()) {
            if (subwhere.length() > 2) {
                subwhere.append((Logician.AND.equals(criteria.getLogician()) ? " AND" : " OR"));
            }
            if (n instanceof Criteria) {
                subwhere.append(parseCriteria(target, (Criteria) n));
            } else if (n instanceof Criterion) {
                subwhere.append(mapToCondition(target, (Criterion) n));
            }
        }
        subwhere.append(")");
        return subwhere.toString();
    }

    private String mapToCondition(String target, Criterion c) {
        if(c.getField().size() == 1)
            return String.format(" \"%s\".\"%s\" %s ?", target , c.getField().getFirst(), getSqlOperator(c.getOperator()), c.getValue());
        return String.format(" \"%s\".\"%s\" %s ?", c.getField().get(c.getField().size() - 2) , c.getField().getLast(), getSqlOperator(c.getOperator()), c.getValue());
    }

    private String getSqlOperator(Operator operator) {
        switch (operator) {
            case EQ: return "=";
            case NE: return "!=";
            case LT: return "<";
            case GT: return ">";
            default: throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }

    private String buildSelect(String target, List<List<String>> select, QueryMeta meta) {
        if (select == null || select.isEmpty()) {
            return defaultSelect(target, meta);
        }
        return "SELECT " + select
                .stream()
                .map((List<String> path) -> {
                    if(path.size() == 1)
                        return String.format("\"%s\".\"%s\"", target , path.getFirst());
                    return String.format("\"%s\".\"%s\" AS \"%s\"", path.get(path.size() - 2) , path.getLast(), path.get(path.size() - 2) + StringUtils.capitalize(path.getLast()));
                })
                .collect(Collectors.joining(", "));
    }

    private String defaultSelect(String target, QueryMeta meta) {
        List<String> parts = meta.getTables().get(target).getColumns().values().stream()
                .filter(c -> Boolean.TRUE.equals(c.getReadable()))
                .map(c -> String.format("\"%s\".\"%s\" AS \"%s\"", target, c.getName(), c.getAlias()))
                .toList();
        return parts.isEmpty() ? "SELECT 1" : "SELECT " + String.join(", ", parts);
    }

    private String buildFrom(List<List<String>> select, Criteria where, QueryMeta meta, Map<String, String> aliasesMapping, String target) {
        String from = " FROM \"" + aliasesMapping.get(target) + "\" AS \"" + target + "\"";

        List<List<String>> sel = select != null ? select : List.of();
        boolean isJoinsRequired = sel.stream().anyMatch(s -> s != null && s.size() > 1) ||
                (where != null && anyCompositeCriterion(where));

        if (!isJoinsRequired) {
            return from;
        }

        String joins = Stream.concat(
                (where != null ? getCompositeCriterion(where) : List.<String>of()).stream(),
                getCompositeSelect(sel).stream())
                .distinct()
                .map(rel -> meta.getTables().get(target).getRelations().get(rel))
                .map(r -> String.format(" LEFT JOIN \"%s\" AS \"%s\" ON \"%s\".\"%s\" = \"%s\".\"%s\"",
                        r.getChildTable(),
                        r.getName(),
                        aliasesMapping.get(target),
                        r.getJoinCurrentColumn() != null ? r.getJoinCurrentColumn() : r.getOneColumn(),
                        r.getName(),
                        r.getJoinChildColumn() != null ? r.getJoinChildColumn() : r.getManyColumn()))
                .collect(Collectors.joining(" "));

        return from + joins;
    }

    private List<String> getCompositeSelect(List<List<String>> select) {
        if (select == null) return List.of();
        return select.stream()
                .filter(path -> path != null && path.size() > 1)
                .map(path -> path.get(0))
                .distinct()
                .toList();
    }

    private boolean anyCompositeCriterion(Criteria where) {
        for (Node n: where.getCriteria()) {
            if(n instanceof Criteria) {
                if(anyCompositeCriterion((Criteria)n)) {
                    return true;
                }
            } else if (n instanceof Criterion) {
                Criterion c = (Criterion)n;
                if(c.getField().size() > 1)
                    return true;
            }
        }
        return false;
    }

    private List<String> getCompositeCriterion(Criteria where) {
        List<String> compositeCriteria = new ArrayList<>();
        for (Node n: where.getCriteria()) {
            if(n instanceof Criteria) {
                compositeCriteria.addAll(getCompositeCriterion((Criteria) n));
            } else if (n instanceof Criterion) {
                Criterion c = (Criterion)n;
                LinkedList<String> list = new LinkedList<>(c.getField());
                while(list.size() > 1) {
                    compositeCriteria.add(list.removeFirst());
                }
            }
        }
        return compositeCriteria;
    }

    private List<Object> mapCriteriaToParams(Criteria where, QueryMeta meta, String target, Map<String, String> aliasMapping) {
        List<Object> params = new ArrayList<>();
        for (Node node : where.getCriteria()) {
            if (node instanceof Criteria) {
                params.addAll(mapCriteriaToParams((Criteria) node, meta, target, aliasMapping));
            } else if (node instanceof Criterion) {
                Criterion c = (Criterion) node;

                ColumnMeta cm;

                if (c.getField().size() == 1)
                    cm = meta.getTables().get(target).getColumns().get(c.getField().get(0));
                else {
                    RelationMeta rel = meta.getTables().get(target).getRelations().get(c.getField().get(0));
                    TableMeta relTable = rel != null ? resolveTableByPhysicalName(meta, rel.getChildTable()) : null;
                    cm = relTable != null ? relTable.getColumns().get(c.getField().getLast()) : null;
                }

                params.add(convertValue(c.getValue(), cm.getDataType()));
            }
        }
        return params;
    }

    private Object convertValue(Object value, String dataType) {
        if (value == null) return null;
        String lower = dataType.toLowerCase();
        if (lower.contains("timestamp")) {
            return value instanceof String ? Timestamp.from(Instant.parse((String) value)) : value;
        }
        if (lower.contains("date") && !lower.contains("timestamp")) {
            return value instanceof String ? LocalDate.parse((String) value) : value;
        }
        if (lower.contains("int") || lower.contains("bigint") || lower.contains("smallint") || lower.contains("serial")) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (lower.contains("boolean") || lower.contains("bool")) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }
        return value;
    }
}
