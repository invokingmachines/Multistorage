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
    @Operation(summary = "List relations where from-table is the given table (name or alias)")
    public List<MetaRelationDto> listByFromTable(@PathVariable String tableRef) {
        return service.findByFromTable(tableRef);
    }

    @GetMapping("/tables/{tableRef}/relations/{alias}")
    @Operation(summary = "Get relation by from-table and alias")
    public MetaRelationDto get(@PathVariable String tableRef, @PathVariable String alias) {
        return service.getByFromTableAndAlias(tableRef, alias);
    }

    @PostMapping("/relations")
    @Operation(summary = "Upsert meta relation: find by fromTable + alias, create if missing else update.")
    public MetaRelationDto upsert(@RequestBody MetaRelationRequest request) {
        return service.upsert(request);
    }

    @DeleteMapping("/tables/{tableRef}/relations/{alias}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meta relation")
    public void delete(@PathVariable String tableRef, @PathVariable String alias) {
        service.delete(tableRef, alias);
    }
}
