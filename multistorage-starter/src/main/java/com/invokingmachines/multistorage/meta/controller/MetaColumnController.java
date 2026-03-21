package com.invokingmachines.multistorage.meta.controller;

import com.invokingmachines.multistorage.dto.api.MetaColumnDto;
import com.invokingmachines.multistorage.dto.api.MetaColumnRequest;
import com.invokingmachines.multistorage.meta.service.MetaColumnCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${multistorage.web.api-prefix}/meta/tables/{tableRef}/columns")
@Tag(name = "Meta Columns", description = "Configure meta_column. Table and column identified by name or alias.")
@RequiredArgsConstructor
public class MetaColumnController {

    private final MetaColumnCrudService service;

    @GetMapping
    @Operation(summary = "List columns by table (name or alias)")
    public List<MetaColumnDto> list(@PathVariable String tableRef) {
        return service.findByTable(tableRef);
    }

    @GetMapping("/{columnRef}")
    @Operation(summary = "Get column by table and column (name or alias)")
    public MetaColumnDto get(@PathVariable String tableRef, @PathVariable String columnRef) {
        return service.getByTableAndColumn(tableRef, columnRef);
    }

    @PostMapping
    @Operation(summary = "Upsert meta column: find by table + name, create if missing else update")
    public MetaColumnDto upsert(@PathVariable String tableRef, @RequestBody MetaColumnRequest request) {
        return service.upsert(tableRef, request);
    }

    @DeleteMapping("/{columnRef}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meta column")
    public void delete(@PathVariable String tableRef, @PathVariable String columnRef) {
        service.delete(tableRef, columnRef);
    }
}
