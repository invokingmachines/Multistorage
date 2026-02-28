package com.invokingmachines.multistorage.meta.controller;

import com.invokingmachines.multistorage.dto.api.MetaRelationDto;
import com.invokingmachines.multistorage.dto.api.MetaRelationRequest;
import com.invokingmachines.multistorage.meta.service.MetaRelationCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/multistorage/admin/meta")
@Tag(name = "Meta Relations", description = "Configure meta_relation. Tables identified by name or alias.")
@RequiredArgsConstructor
public class MetaRelationController {

    private final MetaRelationCrudService service;

    @GetMapping("/tables/{tableRef}/relations")
    @Operation(summary = "List relations by many-table (name or alias)")
    public List<MetaRelationDto> listByManyTable(@PathVariable String tableRef) {
        return service.findByManyTable(tableRef);
    }

    @GetMapping("/tables/{tableRef}/relations/{relationName}")
    @Operation(summary = "Get relation by many-table and relation name")
    public MetaRelationDto get(@PathVariable String tableRef, @PathVariable String relationName) {
        return service.getByManyTableAndName(tableRef, relationName);
    }

    @PostMapping("/relations")
    @Operation(summary = "Upsert meta relation: find by manyTable + name, create if missing else update.")
    public MetaRelationDto upsert(@RequestBody MetaRelationRequest request) {
        return service.upsert(request);
    }

    @DeleteMapping("/tables/{tableRef}/relations/{relationName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meta relation")
    public void delete(@PathVariable String tableRef, @PathVariable String relationName) {
        service.delete(tableRef, relationName);
    }
}
