package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.*;
import com.invokingmachines.multistorage.query.service.MetaAliasMapper;
import com.invokingmachines.multistorage.query.service.ValueConverter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class QueryCompiler {

    private final MetaAliasMapper metaAliasMapper;
    private final ValueConverter valueConverter;

    public QueryCompiler(MetaAliasMapper metaAliasMapper, ValueConverter valueConverter) {
        this.metaAliasMapper = metaAliasMapper;
        this.valueConverter = valueConverter;
    }

    public CompiledQuery compile(Query query, QueryMeta meta, String target) {
        if (query.getWhere() == null || isEmptyWhere(query.getWhere()))
            query.setWhere(new Criteria(Logician.AND, List.of()));

        Map<String, String> aliasesMapping = metaAliasMapper.buildAliasesMapping(meta);
        String tableName = aliasesMapping.getOrDefault(target, resolveTargetToTableName(meta, target));
        if (!meta.getTables().containsKey(tableName)) {
            tableName = resolveTargetToTableName(meta, target);
        }
        List<List<String>> select = query.getSelect() != null && !query.getSelect().isEmpty() ? query.getSelect() : List.of(List.of("*"));
        List<List<String>> expandedSelect = expandSelect(select, meta, tableName, aliasesMapping);

        return CompiledQuery.builder()
                .sql(buildSql(expandedSelect, query, meta, aliasesMapping, target, tableName))
                .parameters(mapCriteriaToParams(query.getWhere(), meta, tableName, aliasesMapping))
                .expandedSelect(expandedSelect)
                .build();
    }

    private static boolean isEmptyWhere(Criteria c) {
        return c.getCriteria().isEmpty();
    }

    private String buildSql(List<List<String>> expandedSelect, Query query, QueryMeta meta, Map<String, String> aliasesMapping, String target, String tableName) {
        String select = buildSelect(target, expandedSelect, meta, tableName);
        String from = buildFrom(expandedSelect, query.getWhere(), meta, aliasesMapping, target, tableName);
        return select + from + buildWhere(target, query.getWhere(), aliasesMapping) + ";";
    }

    private List<List<String>> expandSelect(List<List<String>> select, QueryMeta meta, String tableName, Map<String, String> aliasesMapping) {
        String resolvedTableName = resolveTargetToTableName(meta, tableName);
        if (!meta.getTables().containsKey(resolvedTableName)) {
            throw new IllegalArgumentException("Table not found in meta: " + tableName + " (resolved: " + resolvedTableName + ")");
        }
        tableName = resolvedTableName;
        List<List<String>> result = new ArrayList<>();
        TableMeta rootTable = meta.getTables().get(tableName);
        for (List<String> path : select) {
            if (path.size() == 1 && "*".equals(path.getFirst())) {
                rootTable.getColumns().values().stream()
                        .map(c -> List.of(c.getName())).forEach(result::add);
            } else if (path.size() >= 2 && "*".equals(path.get(path.size() - 1))) {
                String relationAlias = path.get(path.size() - 2);
                String toTableName = aliasesMapping.get(relationAlias);
                if (toTableName == null) {
                    throw new IllegalArgumentException("Relation not found in aliases: " + relationAlias);
                }
                TableMeta toTable = meta.getTables().get(toTableName);
                if (toTable == null) {
                    throw new IllegalArgumentException("Table not found in meta: " + toTableName);
                }
                List<String> prefix = path.subList(0, path.size() - 1);
                toTable.getColumns().values().stream()
                        .map(c -> Stream.concat(prefix.stream(), Stream.of(c.getName())).toList())
                        .forEach(result::add);
            } else {
                if (path.size() == 1) {
                    String colRef = path.getFirst();
                    String colName = aliasesMapping.getOrDefault(colRef, colRef);
                    result.add(List.of(colName));
                } else {
                    String colRef = path.getLast();
                    String colName = aliasesMapping.getOrDefault(colRef, colRef);
                    List<String> prefix = path.subList(0, path.size() - 1);
                    result.add(Stream.concat(prefix.stream(), Stream.of(colName)).toList());
                }
            }
        }
        return result;
    }


    static TableMeta resolveTableByRelationChainStatic(QueryMeta meta, String tableName, List<String> chain) {
        if (chain.isEmpty()) {
            TableMeta t = meta.getTables().get(tableName);
            if (t == null) throw new IllegalArgumentException("Table not found: " + tableName);
            return t;
        }
        TableMeta current = meta.getTables().get(tableName);
        if (current == null) throw new IllegalArgumentException("Table not found: " + tableName);
        for (String rel : chain) {
            RelationMeta r = current.getRelations().get(rel);
            if (r == null) throw new IllegalArgumentException("Relation not found: " + rel + " in table " + current.getName());
            current = meta.getTables().get(r.getToTable());
            if (current == null) throw new IllegalArgumentException("Table not found: " + r.getToTable());
        }
        return current;
    }

    public static String resolveTargetToTableName(QueryMeta meta, String target) {
        if (meta.getTables().containsKey(target)) return target;
        return meta.getTables().values().stream()
                .filter(t -> target.equals(t.getAlias()))
                .map(TableMeta::getName)
                .findFirst()
                .or(() -> meta.getTables().values().stream()
                        .filter(t -> target.equalsIgnoreCase(t.getName()) || (t.getAlias() != null && target.equalsIgnoreCase(t.getAlias())))
                        .map(TableMeta::getName)
                        .findFirst())
                .orElse(target);
    }

    private String buildWhere(String target, Criteria criteria, Map<String, String> aliasesMapping) {
        return " WHERE" + parseCriteria(target, criteria, aliasesMapping);
    }

    private String parseCriteria(String target, Criteria criteria, Map<String, String> aliasesMapping) {
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
                subwhere.append(parseCriteria(target, (Criteria) n, aliasesMapping));
            } else if (n instanceof Criterion) {
                subwhere.append(mapToCondition(target, (Criterion) n, aliasesMapping));
            }
        }
        subwhere.append(")");
        return subwhere.toString();
    }

    private String mapToCondition(String target, Criterion c, Map<String, String> aliasesMapping) {
        String sqlAlias = c.getField().size() == 1 ? target : c.getField().get(c.getField().size() - 2);
        String columnRef = c.getField().getLast();
        String columnName = aliasesMapping.getOrDefault(columnRef, columnRef);
        return String.format(" \"%s\".\"%s\" %s ?", sqlAlias, columnName, getSqlOperator(c.getOperator()));
    }

    private String getSqlOperator(Operator operator) {
        switch (operator) {
            case EQ:
                return "=";
            case NE:
                return "!=";
            case LT:
                return "<";
            case GT:
                return ">";
            default:
                throw new IllegalArgumentException("Unsupported operator: " + operator);
        }
    }

    private String buildSelect(String target, List<List<String>> select, QueryMeta meta, String tableName) {
        Map<String, String> aliasesMapping = metaAliasMapper.buildAliasesMapping(meta);
        return select.isEmpty() ? defaultSelect(target, meta, tableName) : "SELECT " + select
                .stream()
                .map((List<String> path) -> {
                    if (path.size() == 1) {
                        String physical = path.getFirst();
                        TableMeta root = meta.getTables().get(tableName);
                        ColumnMeta cm = root != null ? root.getColumns().get(physical) : null;
                        String outKey = cm != null && cm.getAlias() != null && !cm.getAlias().isBlank() ? cm.getAlias() : physical;
                        return String.format("\"%s\".\"%s\" AS \"%s\"", target, physical, outKey);
                    }
                    String relAlias = path.get(path.size() - 2);
                    String physical = path.getLast();
                    String toTableName = aliasesMapping.get(relAlias);
                    TableMeta child = toTableName != null ? meta.getTables().get(toTableName) : null;
                    ColumnMeta cm = child != null ? child.getColumns().get(physical) : null;
                    String outField = cm != null && cm.getAlias() != null && !cm.getAlias().isBlank() ? cm.getAlias() : physical;
                    return String.format("\"%s\".\"%s\" AS \"%s\"", relAlias, physical, relAlias + StringUtils.capitalize(outField));
                })
                .collect(Collectors.joining(", "));
    }

    private String defaultSelect(String target, QueryMeta meta, String tableName) {
        List<String> parts = meta.getTables().get(tableName).getColumns().values().stream()
                .map(c -> String.format("\"%s\".\"%s\" AS \"%s\"", target, c.getName(), c.getAlias()))
                .toList();
        return parts.isEmpty() ? "SELECT 1" : "SELECT " + String.join(", ", parts);
    }

    private String buildFrom(List<List<String>> select, Criteria where, QueryMeta meta, Map<String, String> aliasesMapping, String target, String tableName) {
        String physicalName = meta.getTables().get(tableName).getName();
        String from = " FROM \"" + physicalName + "\" AS \"" + target + "\"";
        List<List<String>> chains = collectChains(select, where);
        if (chains.isEmpty()) return from;
        List<Join> joins = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();
        for (List<String> chain : chains) {
            for (Join j : toJoins(meta, target, tableName, chain)) {
                String alias = j.joinAlias();
                if (seenAliases.add(alias)) joins.add(j);
            }
        }
        return from + joins.stream().map(Join::toSql).collect(Collectors.joining(" "));
    }

    private List<List<String>> collectChains(List<List<String>> select, Criteria where) {
        List<List<String>> fromSelect = select.stream().filter(p -> p.size() >= 2).map(p -> List.copyOf(p.subList(0, p.size() - 1))).toList();
        List<List<String>> fromWhere = getChainsFromWhere(where);
        return Stream.concat(fromSelect.stream(), fromWhere.stream())
                .distinct()
                .sorted(Comparator.comparingInt(List::size))
                .toList();
    }

    private List<List<String>> getChainsFromWhere(Criteria where) {
        List<List<String>> out = new ArrayList<>();
        for (Node n : where.getCriteria()) {
            if (n instanceof Criteria) out.addAll(getChainsFromWhere((Criteria) n));
            else if (n instanceof Criterion) {
                List<String> f = ((Criterion) n).getField();
                if (f.size() > 1) out.add(List.copyOf(f.subList(0, f.size() - 1)));
            }
        }
        return out;
    }

    private record Join(String tableName, String joinAlias, String leftAlias, String leftColumn, String rightColumn) {
        String toSql() {
            return String.format(" LEFT JOIN \"%s\" AS \"%s\" ON \"%s\".\"%s\" = \"%s\".\"%s\"",
                    tableName, joinAlias, leftAlias, leftColumn, joinAlias, rightColumn);
        }
    }

    private List<Join> toJoins(QueryMeta meta, String currentTableAlias, String currentTableName, List<String> chain) {
        String first = chain.get(0);
        TableMeta table = meta.getTables().get(currentTableName);
        if (table == null) throw new IllegalArgumentException("Table not found: " + currentTableName);
        RelationMeta r = table.getRelations().get(first);
        if (r == null) throw new IllegalArgumentException("Relation not found: " + first + " in table " + currentTableName);
        Join join = new Join(r.getToTable(), r.getAlias(), currentTableAlias, r.getFromColumn(), r.getToColumn());
        List<Join> out = new ArrayList<>();
        out.add(join);
        if (chain.size() > 1) {
            out.addAll(toJoins(meta, r.getAlias(), r.getToTable(), chain.subList(1, chain.size())));
        }
        return out;
    }

    private List<Object> mapCriteriaToParams(Criteria where, QueryMeta meta, String tableName, Map<String, String> aliasMapping) {
        List<Object> params = new ArrayList<>();
        for (Node node : where.getCriteria()) {
            if (node instanceof Criteria) {
                params.addAll(mapCriteriaToParams((Criteria) node, meta, tableName, aliasMapping));
            } else if (node instanceof Criterion) {
                Criterion c = (Criterion) node;
                List<String> field = c.getField();
                TableMeta table = field.size() == 1
                        ? meta.getTables().get(tableName)
                        : resolveTableByRelationChainStatic(meta, tableName, field.subList(0, field.size() - 1));
                String columnRef = field.get(field.size() - 1);
                String columnName = aliasMapping.getOrDefault(columnRef, columnRef);
                ColumnMeta cm = table != null ? table.getColumns().get(columnName) : null;
                params.add(valueConverter.toJdbcValue(c.getValue(), cm != null ? cm.getDataType() : null));
            }
        }
        return params;
    }

}
