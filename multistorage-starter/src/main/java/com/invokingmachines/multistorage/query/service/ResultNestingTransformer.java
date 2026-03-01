package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;

public final class ResultNestingTransformer {

    private ResultNestingTransformer() {}

    public static List<Map<String, Object>> nestRelationFields(List<Map<String, Object>> rows,
                                                                List<List<String>> expandedSelect,
                                                                QueryMeta meta,
                                                                String target) {
        String tableName = QueryCompiler.resolveTargetToTableName(meta, target);
        List<String> relationPrefixes = relationPrefixesFrom(expandedSelect);
        List<List<String>> relationChains = relationChainsFrom(expandedSelect);
        if (!relationChains.isEmpty()) {
            return nestRecursive(rows, relationChains, meta, tableName);
        }
        if (relationPrefixes.isEmpty()) return rows;
        return nestFlat(rows, relationPrefixes);
    }

    private static List<String> relationsAtDepth(List<List<String>> relationChains, int depth) {
        return relationChains.stream()
                .filter(c -> c.size() > depth)
                .map(c -> c.get(depth))
                .distinct()
                .toList();
    }

    private static List<String> childRelations(List<List<String>> relationChains, int depth, String relationName) {
        return relationChains.stream()
                .filter(c -> c.size() > depth + 1 && relationName.equals(c.get(depth)))
                .map(c -> c.get(depth + 1))
                .distinct()
                .toList();
    }

    private static List<String> relationPrefixesFrom(List<List<String>> expandedSelect) {
        return expandedSelect.stream()
                .filter(path -> path.size() > 1)
                .map(path -> path.get(0))
                .distinct()
                .toList();
    }

    private static List<List<String>> relationChainsFrom(List<List<String>> expandedSelect) {
        return expandedSelect.stream()
                .filter(path -> path.size() >= 2)
                .map(path -> (List<String>) new ArrayList<>(path.subList(0, path.size() - 1)))
                .distinct()
                .sorted(comparingInt(List::size))
                .toList();
    }

    private static List<Map<String, Object>> nestRecursive(List<Map<String, Object>> rows, List<List<String>> relationChains, QueryMeta meta, String tableName) {
        List<String> allPrefixes = relationChains.stream().flatMap(List::stream).distinct().toList();
        List<RowSplit> splits = rows.stream()
                .map(row -> splitRowWithChains(row, allPrefixes))
                .toList();
        Map<String, List<RowSplit>> byRoot = splits.stream()
                .collect(Collectors.groupingBy(s -> s.rootKey));
        List<String> rootRels = relationsAtDepth(relationChains, 0);
        return byRoot.values().stream()
                .map(group -> buildRootWithChildren(group, rootRels, relationChains, meta, tableName))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> buildRootWithChildren(List<RowSplit> group, List<String> rootRels, List<List<String>> relationChains, QueryMeta meta, String rootTableName) {
        Map<String, Object> root = new LinkedHashMap<>(group.get(0).root);
        for (String rel : rootRels) {
            root.put(rel, buildValueForRelation(group, 0, rel, List.of(), relationChains, meta, rootTableName));
        }
        return root;
    }

    private static Object buildValueForRelation(List<RowSplit> rows, int depth, String relationName, List<String> path,
                                                 List<List<String>> relationChains, QueryMeta meta, String rootTableName) {
        TableMeta fromTable = QueryCompiler.resolveTableByRelationChainStatic(meta, rootTableName, path);
        RelationMeta rel = fromTable.getRelations().get(relationName);
        boolean many = rel.isOneToMany();
        String childTableName = rel.getToTable();
        Map<String, List<RowSplit>> byKey = rows.stream()
                .collect(Collectors.groupingBy(r -> mapKey(r.nestedByPrefix.get(relationName))));
        List<Object> list = byKey.entrySet().stream()
                .map(e -> {
                    Map<String, Object> obj = new LinkedHashMap<>(e.getValue().get(0).nestedByPrefix.get(relationName));
                    List<String> childPath = new ArrayList<>(path);
                    childPath.add(relationName);
                    for (String childRel : childRelations(relationChains, depth, relationName)) {
                        obj.put(childRel, buildValueForRelation(e.getValue(), depth + 1, childRel, childPath, relationChains, meta, rootTableName));
                    }
                    return (Object) obj;
                })
                .toList();
        return many ? list : list.get(0);
    }

    private static RowSplit splitRowWithChains(Map<String, Object> row, List<String> allPrefixes) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nestedByPrefix = new LinkedHashMap<>();
        for (String key : row.keySet()) {
            Object value = row.get(key);
            tryFindRelationPrefix(key, allPrefixes).ifPresentOrElse(
                    p -> nestedByPrefix.computeIfAbsent(p, x -> new LinkedHashMap<>()).put(uncapitalize(key.substring(p.length())), value),
                    () -> root.put(key, value));
        }
        String rootKey = mapKey(root);
        return new RowSplit(root, nestedByPrefix, rootKey);
    }

    private static String mapKey(Map<String, Object> m) {
        return m.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
    }

    private static List<Map<String, Object>> nestFlat(List<Map<String, Object>> rows, List<String> relationPrefixes) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Split split = splitRow(row, relationPrefixes);
            groups.computeIfAbsent(split.rootKey, k -> new Group(split.root))
                    .addNested(split.nestedByPrefix);
        }
        return groups.values().stream()
                .map(g -> g.toResult(relationPrefixes))
                .collect(Collectors.toList());
    }

    private static Split splitRow(Map<String, Object> row, List<String> relationPrefixes) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        for (String key : row.keySet()) {
            Object value = row.get(key);
            tryFindRelationPrefix(key, relationPrefixes).ifPresentOrElse(
                    p -> nested.put(p + "." + uncapitalize(key.substring(p.length())), value),
                    () -> root.put(key, value));
        }
        String rootKey = mapKey(root);
        Map<String, Map<String, Object>> nestedByPrefix = groupNestedByPrefix(nested);
        return new Split(rootKey, root, nestedByPrefix);
    }

    private static Map<String, Map<String, Object>> groupNestedByPrefix(Map<String, Object> nested) {
        Map<String, Map<String, Object>> byPrefix = new LinkedHashMap<>();
        nested.forEach((k, v) -> {
            int dot = k.indexOf('.');
            String prefix = k.substring(0, dot);
            String field = k.substring(dot + 1);
            byPrefix.computeIfAbsent(prefix, x -> new LinkedHashMap<>()).put(field, v);
        });
        return byPrefix;
    }

    private static Optional<String> tryFindRelationPrefix(String key, List<String> relationPrefixes) {
        return relationPrefixes.stream()
                .filter(p -> key.startsWith(p) && key.length() > p.length() && Character.isUpperCase(key.charAt(p.length())))
                .findFirst();
    }

    private static String findRelationPrefix(String key, List<String> relationPrefixes) {
        return tryFindRelationPrefix(key, relationPrefixes)
                .orElseThrow(() -> new IllegalArgumentException("No relation prefix found for key: " + key));
    }

    private static String uncapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isEmptyNested(Map<String, Object> m) {
        return m.isEmpty();
    }

    private record Split(String rootKey, Map<String, Object> root, Map<String, Map<String, Object>> nestedByPrefix) {}

    private record RowSplit(Map<String, Object> root, Map<String, Map<String, Object>> nestedByPrefix, String rootKey) {}

    private static class Group {
        final Map<String, Object> root;
        final List<Map<String, Map<String, Object>>> nestedList = new ArrayList<>();

        Group(Map<String, Object> root) {
            this.root = root;
        }

        void addNested(Map<String, Map<String, Object>> nested) {
            nestedList.add(nested);
        }

        Map<String, Object> toResult(List<String> relationPrefixes) {
            Map<String, Object> result = new LinkedHashMap<>(root);
            for (String prefix : relationPrefixes) {
                List<Map<String, Object>> items = nestedList.stream()
                        .map(m -> m.get(prefix))
                        .filter(n -> !isEmptyNested(n))
                        .map(n -> (Map<String, Object>) new LinkedHashMap<>(n))
                        .toList();
                result.put(prefix, items);
            }
            return result;
        }
    }
}
