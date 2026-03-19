package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.pipeline.RequestPipeline;
import com.invokingmachines.multistorage.query.dto.SearchResult;
import com.invokingmachines.multistorage.util.NamingUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map.Entry;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class EntityController {

    private final RequestPipeline requestPipeline;

    @Operation(hidden = true)
    @PostMapping(
            path = "/multistorage/api/{entity}/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> search(@PathVariable("entity") String entity,
                                      @RequestBody Query query) {
        String entityAlias = NamingUtils.fromPathSegment(entity);
        SearchResult result = requestPipeline.executeSearch(query, entityAlias);
        Object body = result.hasPagination() ? result : result.getContent();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @Operation(hidden = true)
    @PostMapping(
            path = "/multistorage/api/{entity}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> upsert(@PathVariable("entity") String entity,
                                      @RequestBody(required = false) Object body) {
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

    @Operation(hidden = true)
    @DeleteMapping(
            path = "/multistorage/api/{entity}/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("entity") String entity,
                                                         @PathVariable("id") String id) {
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

