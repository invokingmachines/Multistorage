package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.pipeline.RequestPipeline;
import com.invokingmachines.multistorage.util.NamingUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Component
public class EntityDeleteHandler {

    private final RequestPipeline requestPipeline;

    public EntityDeleteHandler(RequestPipeline requestPipeline) {
        this.requestPipeline = requestPipeline;
    }

    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<Map<String, Object>> delete(HttpServletRequest request, @PathVariable("id") String id) {
        String pathSegment = extractEntity(request.getRequestURI());
        if (pathSegment == null || pathSegment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String entityAlias = NamingUtils.fromPathSegment(pathSegment);
        requestPipeline.executeDelete(parseId(id), entityAlias);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("deleted", true));
    }

    private String extractEntity(String uri) {
        if (uri == null) return "";
        String[] parts = uri.split("/");
        return parts.length >= 4 ? parts[3] : "";
    }

    private Object parseId(String idStr) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }
}
