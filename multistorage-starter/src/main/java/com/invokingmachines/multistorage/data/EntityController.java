package com.invokingmachines.multistorage.data;

import com.invokingmachines.multistorage.dto.query.Query;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("${multistorage.web.api-prefix}/data")
public class EntityController {

    private final EntityOperationsHandler entityOperationsHandler;

    @Operation(hidden = true)
    @PostMapping(
            path = "{entity}/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> search(@PathVariable("entity") String entity,
                                      @RequestBody Query query) {
        return entityOperationsHandler.search(entity, query);
    }

    @Operation(hidden = true)
    @PostMapping(
            path = "{entity}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> upsert(@PathVariable("entity") String entity,
                                      @RequestBody(required = false) Object body) {
        return entityOperationsHandler.upsert(entity, body);
    }

    @Operation(hidden = true)
    @DeleteMapping(
            path = "{entity}/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("entity") String entity,
                                                         @PathVariable("id") String id) {
        return entityOperationsHandler.delete(entity, id);
    }
}
