package com.invokingmachines.multistorage.data;

import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.pipeline.RequestPipeline;
import com.invokingmachines.multistorage.data.dto.SearchResult;
import com.invokingmachines.multistorage.util.NamingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map.Entry;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EntityOperationsHandler {

    private final RequestPipeline requestPipeline;

    public ResponseEntity<?> search(String entity, Query query) {
        String entityAlias = NamingUtils.fromPathSegment(entity);
        SearchResult result = requestPipeline.executeSearch(query, entityAlias);
        Object body = result.hasPagination() ? result : result.getContent();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    public ResponseEntity<?> upsert(String entity, Object body) {
        String entityAlias = NamingUtils.fromPathSegment(entity);
        Map<String, Object> flatEntity = body instanceof Map<?, ?> m
                ? m.entrySet().stream()
                .filter(e -> e.getKey() instanceof String)
                .collect(Collectors.toMap(e -> (String) e.getKey(), Entry::getValue))
                : null;
        if (flatEntity == null) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> result = requestPipeline.executeUpsert(flatEntity, entityAlias);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    public ResponseEntity<Map<String, Object>> delete(String entity, String id) {
        String entityAlias = NamingUtils.fromPathSegment(entity);
        requestPipeline.executeDelete(parseId(id), entityAlias);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("deleted", true));
    }

    private Object parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }
}
