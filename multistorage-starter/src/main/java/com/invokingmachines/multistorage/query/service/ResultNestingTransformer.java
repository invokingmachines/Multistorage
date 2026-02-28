package com.invokingmachines.multistorage.query.service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;

public final class ResultNestingTransformer {

    private ResultNestingTransformer() {}

    public static List<Map<String, Object>> nestRelationFields(List<Map<String, Object>> rows,
                                                                List<List<String>> expandedSelect) {
        if (expandedSelect == null || expandedSelect.isEmpty())
            return rows;
        List<String> relationPrefixes = relationPrefixesFrom(expandedSelect);
        List<List<String>> relationChains = relationChainsFrom(expandedSelect);
        if (!relationChains.isEmpty()) {
            return nestRecursive(rows, relationChains);
        }
        if (relationPrefixes.isEmpty()) return rows;
        return nestFlat(rows, relationPrefixes);
    }

    private static List<String> relationPrefixesFrom(List<List<String>> expandedSelect) {
        if (expandedSelect == null) return List.of();
        return expandedSelect.stream()
                .filter(path -> path != null && path.size() > 1)
                .map(path -> path.get(0))
                .distinct()
                .toList();
    }

    private static List<List<String>> relationChainsFrom(List<List<String>> expandedSelect) {
        if (expandedSelect == null) return List.of();
        return expandedSelect.stream()
                .filter(path -> path != null && path.size() >= 2)
                .map(path -> (List<String>) new ArrayList<>(path.subList(0, path.size() - 1)))
                .distinct()
                .sorted(comparingInt(List::size))
                .toList();
    }

    private static List<Map<String, Object>> nestRecursive(List<Map<String, Object>> rows, List<List<String>> relationChains) {
        List<String> prefixesByDepth = relationChains.stream()
                .map(c -> c.get(c.size() - 1))
                .toList();
        List<RowSplit> splits = rows.stream()
                .map(row -> splitRowWithChains(row, prefixesByDepth))
                .toList();
        Map<String, List<RowSplit>> byRoot = splits.stream()
                .filter(s -> !s.keysByDepth.isEmpty())
                .collect(Collectors.groupingBy(s -> s.keysByDepth.get(0)));
        return byRoot.values().stream()
                .map(group -> buildRootWithChildren(group, prefixesByDepth))
                .collect(Collectors.toList());
    }

    private static Map<String, Object> buildRootWithChildren(List<RowSplit> group, List<String> prefixesByDepth) {
        Map<String, Object> root = new LinkedHashMap<>(group.get(0).root);
        if (!prefixesByDepth.isEmpty()) {
            root.put(prefixesByDepth.get(0), buildChildrenAtDepth(group, 0, prefixesByDepth));
        }
        return root;
    }

    private static List<Object> buildChildrenAtDepth(List<RowSplit> rows, int depth, List<String> prefixesByDepth) {
        if (depth >= prefixesByDepth.size()) return List.of();
        String prefix = prefixesByDepth.get(depth);
        int keyIndex = depth + 1;
        Map<String, List<RowSplit>> byKey = rows.stream()
                .filter(s -> s.keysByDepth.size() > keyIndex)
                .collect(Collectors.groupingBy(s -> s.keysByDepth.get(keyIndex)));
        return byKey.entrySet().stream()
                .map(e -> {
                    Map<String, Object> obj = new LinkedHashMap<>(e.getValue().get(0).nestedByPrefix.getOrDefault(prefix, new LinkedHashMap<>()));
                    if (isEmptyNested(obj)) return null;
                    if (depth + 1 < prefixesByDepth.size()) {
                        obj.put(prefixesByDepth.get(depth + 1), buildChildrenAtDepth(e.getValue(), depth + 1, prefixesByDepth));
                    }
                    return (Object) obj;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static RowSplit splitRowWithChains(Map<String, Object> row, List<String> prefixesByDepth) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nestedByPrefix = new LinkedHashMap<>();
        for (String key : row.keySet()) {
            Object value = row.get(key);
            String matched = findRelationPrefix(key, prefixesByDepth);
            if (matched != null) {
                String fieldName = uncapitalize(key.substring(matched.length()));
                nestedByPrefix.computeIfAbsent(matched, p -> new LinkedHashMap<>()).put(fieldName, value);
            } else {
                root.put(key, value);
            }
        }
        List<String> keysByDepth = new ArrayList<>();
        keysByDepth.add(mapKey(root));
        for (String p : prefixesByDepth) {
            Map<String, Object> nested = nestedByPrefix.get(p);
            if (nested == null) break;
            keysByDepth.add(keysByDepth.get(keysByDepth.size() - 1) + "|" + mapKey(nested));
        }
        return new RowSplit(root, nestedByPrefix, keysByDepth);
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
            String prefix = findRelationPrefix(key, relationPrefixes);
            if (prefix != null) {
                String fieldName = uncapitalize(key.substring(prefix.length()));
                nested.put(prefix + "." + fieldName, value);
            } else {
                root.put(key, value);
            }
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

    private static String findRelationPrefix(String key, List<String> relationPrefixes) {
        return relationPrefixes.stream()
                .filter(p -> key.startsWith(p) && key.length() > p.length() && Character.isUpperCase(key.charAt(p.length())))
                .findFirst()
                .orElse(null);
    }

    private static String uncapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isEmptyNested(Map<String, Object> m) {
        return m == null || m.values().stream().allMatch(Objects::isNull);
    }

    private record Split(String rootKey, Map<String, Object> root, Map<String, Map<String, Object>> nestedByPrefix) {}

    private record RowSplit(Map<String, Object> root, Map<String, Map<String, Object>> nestedByPrefix, List<String> keysByDepth) {}

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
                        .map(m -> m.getOrDefault(prefix, Map.of()))
                        .filter(n -> !isEmptyNested(n))
                        .map(n -> (Map<String, Object>) new LinkedHashMap<>(n))
                        .toList();
                result.put(prefix, items);
            }
            return result;
        }
    }
}
