package com.invokingmachines.multistorage.meta.controller;

import com.invokingmachines.multistorage.dto.api.MetaTableDto;
import com.invokingmachines.multistorage.dto.api.MetaTableRequest;
import com.invokingmachines.multistorage.meta.service.MetaTableCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${multistorage.web.api-prefix}/meta/tables")
@Tag(name = "Meta Tables", description = "Configure meta_table (name, alias). Table identified by name or alias.")
@RequiredArgsConstructor
public class MetaTableController {

    private final MetaTableCrudService service;

    @GetMapping
    @Operation(summary = "List all meta tables")
    public List<MetaTableDto> list() {
        return service.findAll();
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get meta table by name")
    public MetaTableDto getByName(@PathVariable String name) {
        return service.getByName(name);
    }

    @GetMapping("/by-alias/{alias}")
    @Operation(summary = "Get meta table by alias")
    public MetaTableDto getByAlias(@PathVariable String alias) {
        return service.getByAlias(alias);
    }

    @PostMapping
    @Operation(summary = "Upsert meta table: find by name, create if missing else update")
    public MetaTableDto upsert(@RequestBody MetaTableRequest request) {
        return service.upsert(request);
    }

    @DeleteMapping("/by-name/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meta table by name")
    public void deleteByName(@PathVariable String name) {
        service.deleteByName(name);
    }

    @DeleteMapping("/by-alias/{alias}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meta table by alias")
    public void deleteByAlias(@PathVariable String alias) {
        service.deleteByAlias(alias);
    }
}
