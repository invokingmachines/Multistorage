package com.invokingmachines.multistorage.meta.controller;

import com.invokingmachines.multistorage.dto.api.MetaFeatureDto;
import com.invokingmachines.multistorage.meta.service.MetaFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${multistorage.web.api-prefix}/meta/features")
@Tag(name = "Meta Features", description = "Optional Liquibase feature bundles referenced by classpath path in meta_feature.")
@RequiredArgsConstructor
public class MetaFeatureController {

    private final MetaFeatureService service;

    @GetMapping
    @Operation(summary = "List meta_feature rows (optional feature definitions)")
    public List<MetaFeatureDto> list() {
        return service.findAll();
    }

    @PostMapping("/{code}/enable")
    @Operation(summary = "Run feature changelog for current schema and set enabled")
    public ResponseEntity<Void> enable(@PathVariable String code) {
        try {
            service.enableByCode(code);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            if (e.getCause() != null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            return ResponseEntity.badRequest().build();
        }
    }
}
