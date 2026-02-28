package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.query.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EntitySearchHandler {

    private static final Pattern ENTITY_PATH = Pattern.compile("^/multistorage/api/([^/]+)/search$");

    private final SearchService searchService;

    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> search(HttpServletRequest request, @RequestBody Query query) {
        String entity = extractEntity(request.getRequestURI());
        List<Map<String, Object>> result = searchService.search(entity, query);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    private String extractEntity(String uri) {
        var matcher = ENTITY_PATH.matcher(uri);
        return matcher.find() ? matcher.group(1) : "";
    }
}
