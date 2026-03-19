package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.query.dto.normalized.NormalizedQuery;
import com.invokingmachines.multistorage.query.dto.normalized.NormalizedSelect;
import com.invokingmachines.multistorage.dto.query.Criteria;
import com.invokingmachines.multistorage.dto.query.Criterion;
import com.invokingmachines.multistorage.dto.query.Node;
import com.invokingmachines.multistorage.dto.query.Operator;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectLimitStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.*;


@Component
@RequiredArgsConstructor
public class QueryCompiler {

    private final DSLContext dsl;

    public CompiledQuery compile(NormalizedQuery nq, QueryMeta meta) {
        String rootTableName = nq.getRootTableName();
        String rootSqlAlias = nq.getRootSqlAlias();

        Table<?> rootTable = DSL.table(DSL.name(meta.getTables().get(rootTableName).getName())).as(rootSqlAlias);
        Condition where = toCondition(nq, meta, rootTableName, List.of(), nq.getWhere());
        Field<JSONB> json = buildJsonForSelect(nq.getSelect(), meta);

        SelectConditionStep<?> base = dsl.select(json.as("data")).from(rootTable).where(where);
        SelectLimitStep<?> ordered = nq.getSortBy() == null
                ? base
                : (nq.isSortDesc()
                        ? base.orderBy(DSL.field(DSL.name(rootSqlAlias, nq.getSortBy())).desc())
                        : base.orderBy(DSL.field(DSL.name(rootSqlAlias, nq.getSortBy())).asc()));
        var q = nq.isPaged() ? ordered.limit(nq.getLimit()).offset(nq.getOffset()) : ordered;

        return CompiledQuery.builder()
                .query(q)
                .build();
    }

    public CompiledQuery compileCount(NormalizedQuery nq, QueryMeta meta) {
        String rootTableName = nq.getRootTableName();
        String rootSqlAlias = nq.getRootSqlAlias();

        Table<?> rootTable = DSL.table(DSL.name(meta.getTables().get(rootTableName).getName())).as(rootSqlAlias);
        Condition where = toCondition(nq, meta, rootTableName, List.of(), nq.getWhere());
        Select<?> count = dsl.selectCount().from(rootTable).where(where);
        return CompiledQuery.builder()
                .query(count)
                .build();
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

    private Condition toCondition(NormalizedQuery nq, QueryMeta meta, String rootTableName, List<String> relationPath, Criteria where) {
        if (where == null || where.getCriteria() == null || where.getCriteria().isEmpty()) return DSL.trueCondition();
        return mapCriteria(where, n -> switch (n) {
            case Criteria c -> toCondition(nq, meta, rootTableName, relationPath, c);
            case Criterion c -> toCondition(nq, meta, rootTableName, relationPath, c);
            default -> DSL.trueCondition();
        });
    }

    private static Condition mapCriteria(Criteria c, java.util.function.Function<Node, Condition> mapper) {
        List<Condition> parts = c.getCriteria().stream().map(mapper).toList();
        return switch (c.getLogician()) {
            case OR -> parts.stream().reduce(DSL.falseCondition(), Condition::or);
            case NOT -> parts.stream().reduce(DSL.trueCondition(), Condition::and).not();
            case AND -> parts.stream().reduce(DSL.trueCondition(), Condition::and);
        };
    }

    private Condition toCondition(NormalizedQuery nq, QueryMeta meta, String rootTableName, List<String> relationPath, Criterion c) {
        if (c == null || c.getField() == null || c.getField().isEmpty()) return DSL.trueCondition();
        List<String> fieldPath = c.getField();
        String columnRef = fieldPath.get(fieldPath.size() - 1);
        List<String> relations = fieldPath.size() > 1 ? fieldPath.subList(0, fieldPath.size() - 1) : List.of();

        Object jdbcValue = c.getValue();

        String fromAlias = nq.sqlAliasFor(relationPath);
        return relations.isEmpty()
                ? opToCondition(DSL.field(DSL.name(fromAlias, columnRef)), c.getOperator(), jdbcValue)
                : existsForChain(nq, meta, rootTableName, relationPath, relations, columnRef, c.getOperator(), jdbcValue);
    }

    private Condition existsForChain(NormalizedQuery nq,
                                    QueryMeta meta,
                                    String rootTableName,
                                    List<String> fromRelationPath,
                                    List<String> relations,
                                    String columnName,
                                    Operator operator,
                                    Object jdbcValue) {
        String relKey = relations.getFirst();
        TableMeta fromTable = resolveTableByRelationChainStatic(meta, rootTableName, fromRelationPath);
        RelationMeta rel = fromTable != null ? fromTable.getRelations().get(relKey) : null;
        if (rel == null) throw new IllegalArgumentException("Relation not found: " + relKey + " in table " + (fromTable != null ? fromTable.getName() : rootTableName));

        List<String> toRelationPath = new ArrayList<>(fromRelationPath);
        toRelationPath.add(relKey);
        String fromAlias = nq.sqlAliasFor(fromRelationPath);
        String toAlias = nq.sqlAliasFor(toRelationPath);
        String toTableName = rel.getToTable();
        Table<?> toTable = DSL.table(DSL.name(meta.getTables().get(toTableName).getName())).as(toAlias);
        Condition link = DSL.field(DSL.name(toAlias, rel.getToColumn())).eq(DSL.field(DSL.name(fromAlias, rel.getFromColumn())));

        if (relations.size() == 1) {
            Condition leaf = opToCondition(DSL.field(DSL.name(toAlias, columnName)), operator, jdbcValue);
            return DSL.exists(dsl.selectOne().from(toTable).where(link.and(leaf)));
        }

        Condition inner = existsForChain(nq, meta, rootTableName, toRelationPath, relations.subList(1, relations.size()), columnName, operator, jdbcValue);
        return DSL.exists(dsl.selectOne().from(toTable).where(link.and(inner)));
    }

    private static Condition opToCondition(Field<Object> f, Operator op, Object v) {
        return switch (op) {
            case EQ -> v == null ? f.isNull() : f.eq(v);
            case NE -> v == null ? f.isNotNull() : f.ne(v);
            case GT -> f.gt(v);
            case GTE -> f.ge(v);
            case LT -> f.lt(v);
            case LTE -> f.le(v);
            case LIKE -> f.like(Objects.toString(v, ""));
            case ILIKE -> DSL.condition("{0} ILIKE {1}", f, DSL.val(Objects.toString(v, "")));
            case IN -> (v instanceof Collection<?> c) ? f.in(c) : f.in(v);
            case NIN -> (v instanceof Collection<?> c) ? f.notIn(c) : f.notIn(v);
            case NULL -> f.isNull();
            case NOT_NULL -> f.isNotNull();
            case BETWEEN -> (v instanceof List<?> l && l.size() >= 2) ? f.between(l.get(0)).and(l.get(1)) : DSL.trueCondition();
        };
    }

    private Field<JSONB> buildJsonForSelect(NormalizedSelect select, QueryMeta meta) {
        String tableName = select.getTableName();
        String sqlAlias = select.getSqlAlias();
        TableMeta table = meta.getTables().get(tableName);
        if (table == null) throw new IllegalArgumentException("Table not found in meta: " + tableName);

        List<Field<?>> args = new ArrayList<>();

        select.getColumns().forEach(c -> {
            args.add(DSL.inline(c.getOutputKey()).cast(String.class));
            args.add(DSL.field(DSL.name(sqlAlias, c.getPhysicalName())));
        });

        select.getRelations().forEach((relAlias, childSel) -> {
            RelationMeta rel = table.getRelations().get(relAlias);
            if (rel == null) return;

            Field<JSONB> childJson = buildJsonForSelect(childSel, meta);
            String childPhysical = meta.getTables().get(rel.getToTable()).getName();
            Table<?> childTable = DSL.table(DSL.name(childPhysical)).as(childSel.getSqlAlias());

            Condition joinCond = DSL.field(DSL.name(childSel.getSqlAlias(), rel.getToColumn()))
                    .eq(DSL.field(DSL.name(sqlAlias, rel.getFromColumn())));

            Field<JSONB> value = rel.isOneToMany()
                    ? oneToManyJson(childJson, childTable, joinCond)
                    : manyToOneJson(childJson, childTable, joinCond);

            args.add(DSL.inline(relAlias).cast(String.class));
            args.add(value);
        });

        if (args.isEmpty()) {
            return DSL.field("'{}'::jsonb", JSONB.class);
        }
        return DSL.function("jsonb_build_object", JSONB.class, args.toArray(Field[]::new));
    }

    private Field<JSONB> manyToOneJson(Field<JSONB> childJson, Table<?> childTable, Condition joinCond) {
        return dsl.select(childJson)
                .from(childTable)
                .where(joinCond)
                .limit(1)
                .asField();
    }

    private Field<JSONB> oneToManyJson(Field<JSONB> childJson, Table<?> childTable, Condition joinCond) {
        var rows = dsl.select(childJson.as("x"))
                .from(childTable)
                .where(joinCond)
                .asTable("t");
        Field<JSONB> x = DSL.field(DSL.name("t", "x"), JSONB.class);
        Field<JSONB> agg = DSL.field("coalesce(jsonb_agg({0}), '[]'::jsonb)", JSONB.class, x);
        return dsl.select(agg).from(rows).asField();
    }

}
