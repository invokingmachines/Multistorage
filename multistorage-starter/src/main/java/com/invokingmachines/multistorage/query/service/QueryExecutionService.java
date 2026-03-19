package com.invokingmachines.multistorage.query.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import lombok.RequiredArgsConstructor;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> execute(CompiledQuery query) {
        return query.getQuery()
                .fetch()
                .getValues(0, JSONB.class)
                .stream()
                .map(jsonb -> {
                    if (jsonb == null) return Map.<String, Object>of();
                    try {
                        return objectMapper.readValue(jsonb.data(), MAP_REF);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse JSONB result", e);
                    }
                })
                .toList();
    }

    public long executeCount(CompiledQuery countQuery) {
        Long value = countQuery.getQuery().fetchOne(0, Long.class);
        return value != null ? value : 0L;
    }
}
