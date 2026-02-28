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
            return expandStar(meta.getTables().get(target), null);
        }
        List<List<String>> result = new ArrayList<>();
        for (List<String> path : select) {
            if (path == null || path.isEmpty()) continue;
            if (path.size() == 1 && "*".equals(path.getFirst())) {
                result.addAll(expandStar(meta.getTables().get(target), null));
            } else if (path.size() >= 2 && "*".equals(path.get(path.size() - 1))) {
                List<String> chain = path.subList(0, path.size() - 1);
                TableMeta table = resolveTableByRelationChain(meta, target, chain);
                if (table != null)
                    result.addAll(expandStar(table, chain));
            } else {
                result.add(path);
            }
        }
        return result;
    }

    private TableMeta resolveTableByRelationChain(QueryMeta meta, String target, List<String> chain) {
        return resolveTableByRelationChainStatic(meta, target, chain);
    }

    static TableMeta resolveTableByRelationChainStatic(QueryMeta meta, String target, List<String> chain) {
        if (chain == null || chain.isEmpty()) return meta.getTables().get(target);
        TableMeta current = meta.getTables().get(target);
        if (current == null) return null;
        for (String rel : chain) {
            RelationMeta r = current.getRelations().get(rel);
            if (r == null) return null;
            current = resolveTableByPhysicalNameStatic(meta, r.getChildTable());
            if (current == null) return null;
        }
        return current;
    }

    private TableMeta resolveTableByPhysicalName(QueryMeta meta, String physicalName) {
        return resolveTableByPhysicalNameStatic(meta, physicalName);
    }

    static TableMeta resolveTableByPhysicalNameStatic(QueryMeta meta, String physicalName) {
        return meta.getTables().values().stream()
                .filter(t -> physicalName.equals(t.getName()))
                .findFirst()
                .orElse(meta.getTables().get(physicalName));
    }

    private List<List<String>> expandStar(TableMeta table, List<String> relationPrefix) {
        return table.getColumns().values().stream()
                .filter(c -> Boolean.TRUE.equals(c.getReadable()))
                .map(c -> relationPrefix == null || relationPrefix.isEmpty()
                        ? List.of(c.getAlias())
                        : Stream.concat(relationPrefix.stream(), Stream.of(c.getAlias())).toList())
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
        List<List<String>> chains = collectChains(select, where);
        if (chains.isEmpty()) return from;
        return from + buildJoinSteps(target, chains).stream()
                .map(step -> formatJoin(step, meta, target))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private List<List<String>> collectChains(List<List<String>> select, Criteria where) {
        List<List<String>> fromSelect = select != null
                ? select.stream().filter(p -> p != null && p.size() >= 2).map(p -> List.copyOf(p.subList(0, p.size() - 1))).toList()
                : List.of();
        List<List<String>> fromWhere = getChainsFromWhere(where);
        return Stream.concat(fromSelect.stream(), fromWhere.stream())
                .distinct()
                .sorted(Comparator.comparingInt(List::size))
                .toList();
    }

    private List<List<String>> getChainsFromWhere(Criteria where) {
        if (where == null) return List.of();
        List<List<String>> out = new ArrayList<>();
        for (Node n : where.getCriteria()) {
            if (n instanceof Criteria) out.addAll(getChainsFromWhere((Criteria) n));
            else if (n instanceof Criterion) {
                List<String> f = ((Criterion) n).getField();
                if (f != null && f.size() > 1) out.add(List.copyOf(f.subList(0, f.size() - 1)));
            }
        }
        return out;
    }

    private List<String> buildJoinSteps(String target, List<List<String>> chains) {
        List<String> steps = new ArrayList<>();
        for (List<String> chain : chains) {
            String fromAlias = target;
            for (String toRel : chain) {
                steps.add(fromAlias + "|" + toRel);
                fromAlias = toRel;
            }
        }
        return steps.stream().distinct().toList();
    }

    private String formatJoin(String step, QueryMeta meta, String target) {
        int pipe = step.indexOf('|');
        String fromAlias = step.substring(0, pipe);
        String toRel = step.substring(pipe + 1);
        RelationMeta r = fromAlias.equals(target)
                ? meta.getTables().get(target).getRelations().get(toRel)
                : Optional.ofNullable(meta.getTables().get(target).getRelations().get(fromAlias))
                        .flatMap(rel -> Optional.ofNullable(resolveTableByPhysicalName(meta, rel.getChildTable())))
                        .map(t -> t.getRelations().get(toRel))
                        .orElse(null);
        if (r == null) return null;
        String curr = r.getJoinCurrentColumn() != null ? r.getJoinCurrentColumn() : r.getOneColumn();
        String child = r.getJoinChildColumn() != null ? r.getJoinChildColumn() : r.getManyColumn();
        return String.format(" LEFT JOIN \"%s\" AS \"%s\" ON \"%s\".\"%s\" = \"%s\".\"%s\"",
                r.getChildTable(), r.getName(), fromAlias, curr, r.getName(), child);
    }

    private List<Object> mapCriteriaToParams(Criteria where, QueryMeta meta, String target, Map<String, String> aliasMapping) {
        List<Object> params = new ArrayList<>();
        for (Node node : where.getCriteria()) {
            if (node instanceof Criteria) {
                params.addAll(mapCriteriaToParams((Criteria) node, meta, target, aliasMapping));
            } else if (node instanceof Criterion) {
                Criterion c = (Criterion) node;
                List<String> field = c.getField();
                TableMeta table = field.size() == 1 ? meta.getTables().get(target) : resolveTableByRelationChain(meta, target, field.subList(0, field.size() - 1));
                ColumnMeta cm = table != null ? table.getColumns().get(field.get(field.size() - 1)) : null;
                params.add(convertValue(c.getValue(), cm != null ? cm.getDataType() : "varchar"));
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
