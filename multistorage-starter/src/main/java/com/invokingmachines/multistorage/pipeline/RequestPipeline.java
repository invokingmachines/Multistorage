package com.invokingmachines.multistorage.pipeline;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Criteria;
import com.invokingmachines.multistorage.dto.query.Criterion;
import com.invokingmachines.multistorage.dto.query.Logician;
import com.invokingmachines.multistorage.dto.query.Operator;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import com.invokingmachines.multistorage.pipeline.operation.EntityPersistor;
import com.invokingmachines.multistorage.pipeline.validation.RequestValidator;
import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.query.service.EntitySelectBuilder;
import com.invokingmachines.multistorage.query.service.QueryCompiler;
import com.invokingmachines.multistorage.query.service.QueryExecutionService;
import com.invokingmachines.multistorage.query.service.ResultNestingTransformer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RequestPipeline {

    private final MetaProvider metaProvider;
    private final List<RequestValidator<?>> validators;
    private final List<PreProcessHandler<?>> preProcessHandlers;
    private final List<PostProcessHandler<?, ?>> postProcessHandlers;
    private final QueryCompiler queryCompiler;
    private final QueryExecutionService queryExecutionService;
    private final EntityPersistor entityPersistor;

    public RequestPipeline(MetaProvider metaProvider,
                           List<RequestValidator<?>> validators,
                           QueryCompiler queryCompiler,
                           QueryExecutionService queryExecutionService,
                           EntityPersistor entityPersistor,
                           @Autowired(required = false) List<PreProcessHandler<?>> preProcessHandlers,
                           @Autowired(required = false) List<PostProcessHandler<?, ?>> postProcessHandlers) {
        this.metaProvider = metaProvider;
        this.validators = validators;
        this.preProcessHandlers = preProcessHandlers != null ? preProcessHandlers : Collections.emptyList();
        this.postProcessHandlers = postProcessHandlers != null ? postProcessHandlers : Collections.emptyList();
        this.queryCompiler = queryCompiler;
        this.queryExecutionService = queryExecutionService;
        this.entityPersistor = entityPersistor;
    }

    private Map<OperationType, RequestValidator<?>> validatorMap;
    private Map<OperationType, List<PreProcessHandler<?>>> preProcessMap;
    private Map<OperationType, List<PostProcessHandler<?, ?>>> postProcessMap;

    @PostConstruct
    void initRegistries() {
        validatorMap = validators.stream()
                .collect(Collectors.toMap(RequestValidator::getOperationType, v -> v, (a, b) -> OrderUtils.getOrder(a.getClass()) <= OrderUtils.getOrder(b.getClass()) ? a : b));
        preProcessMap = preProcessHandlers.stream()
                .collect(Collectors.groupingBy(PreProcessHandler::getOperationType));
        postProcessMap = postProcessHandlers.stream()
                .collect(Collectors.groupingBy(PostProcessHandler::getOperationType));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeSearch(Query request, String targetTableName) {
        QueryMeta fullMeta = metaProvider.getMeta(MetaRequest.builder().build());
        RequestValidator<Query> v = (RequestValidator<Query>) validatorMap.get(OperationType.SEARCH);
        if (v == null) throw new IllegalStateException("No validator for SEARCH");
        QueryMeta meta = v.validate(request, fullMeta, targetTableName);
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);

        List<PreProcessHandler<?>> preList = preProcessMap.getOrDefault(OperationType.SEARCH, Collections.emptyList());
        preList.forEach(h -> ((PreProcessHandler<Query>) h).preProcess(request, meta, tableName));

        CompiledQuery compiled = queryCompiler.compile(request, meta, targetTableName);
        List<Map<String, Object>> rows = queryExecutionService.execute(compiled);
        rows = normalizeRowKeysToAliases(rows, meta, tableName);
        List<Map<String, Object>> response = ResultNestingTransformer.nestRelationFields(rows, compiled.getExpandedSelect(), meta, targetTableName);

        List<PostProcessHandler<?, ?>> postList = postProcessMap.getOrDefault(OperationType.SEARCH, Collections.emptyList());
        postList.forEach(h -> ((PostProcessHandler<Query, List<Map<String, Object>>>) h).postProcess(request, meta, response));

        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> executeUpsert(Map<String, Object> request, String targetTableName) {
        QueryMeta fullMeta = metaProvider.getMeta(MetaRequest.builder().build());
        RequestValidator<Map<String, Object>> v = (RequestValidator<Map<String, Object>>) validatorMap.get(OperationType.UPSERT);
        if (v == null) throw new IllegalStateException("No validator for UPSERT");
        QueryMeta meta = v.validate(request, fullMeta, targetTableName);
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);

        List<PreProcessHandler<?>> preList = preProcessMap.getOrDefault(OperationType.UPSERT, Collections.emptyList());
        preList.forEach(h -> ((PreProcessHandler<Map<String, Object>>) h).preProcess(request, meta, tableName));

        Map<String, Object> flatResponse = entityPersistor.upsert(tableName, request, meta);
        Object savedId = flatResponse.get("id");

        List<List<String>> expandedSelect = EntitySelectBuilder.expandedSelectFromEntity(request, tableName, meta);
        Query refetchQuery = new Query(expandedSelect, new Criteria(Logician.AND, List.of(new Criterion(null, Operator.EQ, savedId, List.of("id")))));
        CompiledQuery refetchCompiled = queryCompiler.compile(refetchQuery, meta, targetTableName);
        List<Map<String, Object>> refetchRows = queryExecutionService.execute(refetchCompiled);
        List<Map<String, Object>> refetchNormalized = normalizeRowKeysToAliases(refetchRows, meta, tableName);
        Map<String, Object> response = refetchNormalized.isEmpty() ? flatResponse
                : ResultNestingTransformer.nestRelationFields(refetchNormalized, refetchCompiled.getExpandedSelect(), meta, targetTableName).get(0);

        List<PostProcessHandler<?, ?>> postList = postProcessMap.getOrDefault(OperationType.UPSERT, Collections.emptyList());
        postList.forEach(h -> ((PostProcessHandler<Map<String, Object>, Map<String, Object>>) h).postProcess(request, meta, response));

        return response;
    }

    @SuppressWarnings("unchecked")
    public void executeDelete(Object id, String targetTableName) {
        QueryMeta fullMeta = metaProvider.getMeta(MetaRequest.builder().build());
        RequestValidator<Object> v = (RequestValidator<Object>) validatorMap.get(OperationType.DELETE);
        if (v == null) throw new IllegalStateException("No validator for DELETE");
        QueryMeta meta = v.validate(id, fullMeta, targetTableName);
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);

        List<PreProcessHandler<?>> preList = preProcessMap.getOrDefault(OperationType.DELETE, Collections.emptyList());
        preList.forEach(h -> ((PreProcessHandler<Object>) h).preProcess(id, meta, tableName));

        entityPersistor.delete(tableName, id, meta);

        Map<String, Object> response = Map.of("deleted", true);
        List<PostProcessHandler<?, ?>> postList = postProcessMap.getOrDefault(OperationType.DELETE, Collections.emptyList());
        postList.forEach(h -> ((PostProcessHandler<Object, Map<String, Object>>) h).postProcess(id, meta, response));
    }

    private static List<Map<String, Object>> normalizeRowKeysToAliases(List<Map<String, Object>> rows, QueryMeta meta, String tableName) {
        TableMeta table = meta.getTables().get(tableName);
        if (table == null || table.getColumns() == null) return rows;
        List<Map.Entry<String, String>> renames = table.getColumns().values().stream()
                .filter(c -> c.getAlias() != null && !c.getAlias().isBlank() && !c.getName().equals(c.getAlias()))
                .map(c -> Map.entry(c.getName(), c.getAlias()))
                .toList();
        if (renames.isEmpty()) return rows;
        return rows.stream()
                .map(row -> {
                    Map<String, Object> out = new java.util.LinkedHashMap<>(row);
                    renames.forEach(e -> {
                        String physical = e.getKey();
                        String alias = e.getValue();
                        if (out.containsKey(physical)) {
                            out.put(alias, out.remove(physical));
                        } else {
                            String physicalLower = physical.toLowerCase();
                            if (out.containsKey(physicalLower)) {
                                out.put(alias, out.remove(physicalLower));
                            }
                        }
                    });
                    return out;
                })
                .collect(Collectors.toList());
    }
}
