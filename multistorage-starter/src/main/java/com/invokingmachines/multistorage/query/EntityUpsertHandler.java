package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.pipeline.RequestPipeline;
import com.invokingmachines.multistorage.util.NamingUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class EntityUpsertHandler {

    private static final Pattern ENTITY_PATH = Pattern.compile("^/multistorage/api/([^/]+)$");

    private final RequestPipeline requestPipeline;

    public EntityUpsertHandler(RequestPipeline requestPipeline) {
        this.requestPipeline = requestPipeline;
    }

    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<?> upsert(HttpServletRequest request, @RequestBody(required = false) Object body) {
        String pathSegment = extractEntity(request.getRequestURI());
        String entityAlias = NamingUtils.fromPathSegment(pathSegment);
        Map<String, Object> entity = body instanceof Map ? (Map<String, Object>) body : null;
        if (entity == null) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> result = requestPipeline.executeUpsert(entity, entityAlias);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    private String extractEntity(String uri) {
        var matcher = ENTITY_PATH.matcher(uri);
        return matcher.find() ? matcher.group(1) : "";
    }
}
