package com.invokingmachines.multistorage.compiler;

import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryCompiler {


    public CompiledQuery compile(Query query, QueryMeta meta) {
        return CompiledQuery.builder()
                .sql(buildSql(query, meta))
                .parameters(mapCriteriaToParams(query.getWhere()))
                .build();
    }

    private Map<String, String> mapAlliases(QueryMeta meta) {
        Map<String, String> aliases = new HashMap<>();
        for (TableMeta t: meta.getTables().values()) {
            aliases.put(t.getAlias(), t.getName());
            for (ColumnMeta col: t.getColumns().values()) {
                aliases.put(col.getAlias(), col.getName());
            }

            for (RelationMeta r: t.getRelations().values()) {
                aliases.put(r.getName(), r.getName());
            }
        }
        return aliases;
    }

    private String buildSql(Query query, QueryMeta meta) {
        Map<String, String> aliasesMapping = mapAlliases(meta);
        String select = buildSelect(query.getTarget(), query.getSelect());
        String from = buildFrom(query, meta, aliasesMapping);
        String where = buildWhere(query.getTarget(), query.getWhere());
        return select + from + where + ";";
    }

    private String buildWhere(String target, Criteria criteria) {
        return " WHERE" + parseCriteria(target, criteria);
    }

    private String parseCriteria(String target, Criteria criteria) {
        StringBuilder subwhere = new StringBuilder();


        subwhere.append(" (");


        for (Node n: criteria.getCriteria()) {
            if(subwhere.length() > 2) {
                subwhere.append((Logician.AND.equals(criteria.getLogician()) ? " AND" : " OR"));
            }

            if(n instanceof Criteria) {
                subwhere.append(parseCriteria(target, (Criteria) n));
            } else if (n instanceof Criterion) {
                Criterion c = (Criterion)n;
                subwhere.append(mapToCondition(target, c));
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

    private String buildSelect(String target, List<List<String>> select) {
        return "SELECT " + select
                .stream()
                .map((List<String> path) -> {
                    if(path.size() == 1)
                        return String.format("\"%s\".\"%s\"", target , path.getFirst());
                    return String.format("\"%s\".\"%s\"", path.get(path.size() - 2) , path.getLast());
                })
                .collect(Collectors.joining(", "));
    }

    private String buildFrom(Query query, QueryMeta meta, Map<String, String> aliasesMapping) {
        String from = " FROM \"" + aliasesMapping.get(query.getTarget()) + "\" AS \"" + query.getTarget() + "\"";

        boolean isJoinsRequired = query.getSelect().stream().anyMatch(s-> s.size() > 1) ||
                                  anyCompositeCriterion(query.getWhere());

        if(!isJoinsRequired) {
            return from;
        }

        String joins = Stream.concat(getCompositeCriterion(query.getWhere()).stream(), getCompositeSelect(query.getSelect()).stream())
                .distinct()
                .map(a->meta.getTables().get(query.getTarget()).getRelations().get(a))
                .map(r->String.format(" LEFT JOIN \"%s\" AS \"%s\" ON \"%s\".\"%s\" = \"%s\".\"%s\"",
                        r.getChildTable(),
                        r.getName(),
                        aliasesMapping.get(query.getTarget()),
                        r.getToColumn(),
                        r.getName(),
                        r.getFromColumn()))
                .collect(Collectors.joining(" "));


        return from + joins;
    }

    private List<String> getCompositeSelect(List<List<String>> select) {
        return List.of();
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

    private List<Object> mapCriteriaToParams(Criteria where) {
        List<Object> params = new ArrayList<>();
        for (Node node : where.getCriteria()) {
            if (node instanceof Criteria) {
                params.addAll(mapCriteriaToParams((Criteria) node));
            } else if (node instanceof Criterion) {
                params.add(((Criterion) node).getValue());
            }
        }
        return params;
    }
}
