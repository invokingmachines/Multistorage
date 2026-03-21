package com.invokingmachines.multistorage.data.service;

import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Criteria;
import com.invokingmachines.multistorage.dto.query.Criterion;
import com.invokingmachines.multistorage.dto.query.Node;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.data.dto.normalized.NormalizedQuery;
import com.invokingmachines.multistorage.data.dto.normalized.NormalizedSelect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class QueryNormalizer {

    private final MetaAliasMapper metaAliasMapper;
    private final ValueConverter valueConverter;

    public QueryNormalizer(MetaAliasMapper metaAliasMapper, ValueConverter valueConverter) {
        this.metaAliasMapper = metaAliasMapper;
        this.valueConverter = valueConverter;
    }

    public NormalizedQuery normalize(Query query, QueryMeta meta, String targetAliasOrName) {
        Map<String, String> aliases = metaAliasMapper.buildAliasesMapping(meta);
        String rootTableName = QueryCompiler.resolveTargetToTableName(meta, targetAliasOrName);
        AliasContext ctx = new AliasContext();
        String rootSqlAlias = ctx.sqlAliasFor(List.of());

        Criteria where = normalizeWhere(query.getWhere(), meta, rootTableName, aliases, ctx);
        NormalizedSelect select = normalizeSelect(query.getSelect(), meta, rootTableName, aliases, ctx, List.of());
        String sortBy = normalizeSortBy(query.sortBy(), meta, rootTableName);
        boolean sortDesc = query.sortDesc();

        boolean paged = query.hasPagination();
        int limit = paged ? query.effectiveSize() : -1;
        int offset = paged ? query.effectivePage() * query.effectiveSize() : 0;

        return new NormalizedQuery(rootSqlAlias, rootTableName, select, where, ctx.aliasByPath, paged, limit, offset, sortBy, sortDesc);
    }

    private String normalizeSortBy(String sortBy, QueryMeta meta, String rootTableName) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        TableMeta root = meta.getTables().get(rootTableName);
        if (root == null) {
            return null;
        }
        String resolved = metaAliasMapper.resolveColumnName(root, sortBy.strip());
        return resolved != null ? resolved : null;
    }

    private NormalizedSelect normalizeSelect(List<List<String>> select,
                                            QueryMeta meta,
                                            String rootTableName,
                                            Map<String, String> aliases,
                                            AliasContext ctx,
                                            List<String> relationPath) {
        List<List<String>> raw = (select == null || select.isEmpty()) ? List.of(List.of("*")) : select;
        List<List<String>> expanded = expandStar(raw, meta, rootTableName, aliases);

        List<NormalizedSelect.NormalizedColumn> cols = expanded.stream()
                .filter(p -> p.size() == 1)
                .map(p -> columnFor(meta.getTables().get(rootTableName), p.getFirst(), aliases))
                .toList();

        Map<String, List<List<String>>> relToPaths = expanded.stream()
                .filter(p -> p.size() > 1)
                .collect(Collectors.groupingBy(List::getFirst, LinkedHashMap::new, Collectors.toList()));

        Map<String, NormalizedSelect> rels = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> e : relToPaths.entrySet()) {
            String relAlias = e.getKey();
            List<List<String>> childPaths = e.getValue().stream()
                    .map(p -> (List<String>) new ArrayList<>(p.subList(1, p.size())))
                    .toList();
            RelationMeta rel = meta.getTables().get(rootTableName).getRelations().get(relAlias);
            List<String> childRelationPath = new ArrayList<>(relationPath);
            childRelationPath.add(relAlias);
            rels.put(relAlias, normalizeSelect(childPaths, meta, rel.getToTable(), aliases, ctx, childRelationPath));
        }

        return new NormalizedSelect(rootTableName, ctx.sqlAliasFor(relationPath), cols, rels);
    }

    private List<List<String>> expandStar(List<List<String>> select, QueryMeta meta, String rootTableName, Map<String, String> aliases) {
        TableMeta root = meta.getTables().get(rootTableName);
        List<List<String>> out = new ArrayList<>();
        for (List<String> path : select) {
            if (path == null || path.isEmpty()) continue;

            if (path.size() == 1 && "*".equals(path.getFirst())) {
                root.getColumns().values().stream()
                        .filter(c -> !Boolean.FALSE.equals(c.getReadable()))
                        .distinct()
                        .map(ColumnMeta::getName)
                        .map(List::of)
                        .forEach(out::add);
                continue;
            }

            if (path.size() >= 2 && "*".equals(path.getLast())) {
                List<String> chain = path.subList(0, path.size() - 1);
                TableMeta t = QueryCompiler.resolveTableByRelationChainStatic(meta, rootTableName, chain);
                t.getColumns().values().stream()
                        .filter(c -> !Boolean.FALSE.equals(c.getReadable()))
                        .distinct()
                        .map(ColumnMeta::getName)
                        .map(col -> {
                            List<String> p = new ArrayList<>(chain);
                            p.add(col);
                            return (List<String>) p;
                        })
                        .forEach(out::add);
                continue;
            }

            out.add(resolvePathToPhysical(meta, rootTableName, path, aliases));
        }
        return out;
    }

    private List<String> resolvePathToPhysical(QueryMeta meta, String rootTableName, List<String> path, Map<String, String> aliases) {
        if (path.size() == 1) {
            String colRef = path.getFirst();
            String physical = aliases.getOrDefault(colRef, colRef);
            return List.of(physical);
        }
        List<String> prefix = path.subList(0, path.size() - 1);
        String colRef = path.getLast();
        String physical = aliases.getOrDefault(colRef, colRef);
        List<String> out = new ArrayList<>(prefix);
        out.add(physical);
        return out;
    }

    private NormalizedSelect.NormalizedColumn columnFor(TableMeta table, String physicalName, Map<String, String> aliases) {
        ColumnMeta cm = table.getColumns().get(physicalName);
        String outKey = cm != null && cm.getAlias() != null && !cm.getAlias().isBlank() ? cm.getAlias() : physicalName;
        return new NormalizedSelect.NormalizedColumn(physicalName, outKey);
    }

    private Criteria normalizeWhere(Criteria where, QueryMeta meta, String rootTableName, Map<String, String> aliases, AliasContext ctx) {
        if (where == null) return new Criteria(com.invokingmachines.multistorage.dto.query.Logician.AND, List.of());
        if (where.getCriteria() == null) return new Criteria(where.getLogician(), List.of());

        List<Node> nodes = where.getCriteria().stream()
                .map(n -> n instanceof Criteria
                        ? normalizeWhere((Criteria) n, meta, rootTableName, aliases, ctx)
                        : normalizeCriterion((Criterion) n, meta, rootTableName, aliases, ctx))
                .filter(Objects::nonNull)
                .toList();
        return new Criteria(where.getLogician(), nodes);
    }

    private Criterion normalizeCriterion(Criterion c, QueryMeta meta, String rootTableName, Map<String, String> aliases, AliasContext ctx) {
        if (c == null || c.getField() == null || c.getField().isEmpty()) return c;

        List<String> field = c.getField();
        List<String> relChain = field.size() > 1 ? field.subList(0, field.size() - 1) : List.of();
        String colRef = field.getLast();
        String colPhysical = aliases.getOrDefault(colRef, colRef);

        if (!relChain.isEmpty()) {
            ctx.sqlAliasFor(relChain);
        }

        TableMeta table = relChain.isEmpty()
                ? meta.getTables().get(rootTableName)
                : QueryCompiler.resolveTableByRelationChainStatic(meta, rootTableName, relChain);
        ColumnMeta cm = table != null ? table.getColumns().get(colPhysical) : null;
        Object jdbcValue = valueConverter.toJdbcValue(c.getValue(), cm != null ? cm.getDataType() : null);

        List<String> normalizedField = new ArrayList<>(relChain);
        normalizedField.add(colPhysical);
        return new Criterion(c.getQuantifier(), c.getOperator(), jdbcValue, normalizedField);
    }

    private static final class AliasContext {
        private int next = 1;
        private final Map<String, String> aliasByPath = new LinkedHashMap<>();

        String sqlAliasFor(List<String> relationPath) {
            String key = relationPath.isEmpty() ? "" : String.join(".", relationPath);
            return aliasByPath.computeIfAbsent(key, k -> "t" + next++);
        }
    }
}

