package com.invokingmachines.multistorage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/{entity}/query")
@Tag(name = "Entities", description = "Entity management API")
public class QueryController {

    @PostConstruct
    public void init() {
        log.info("EntityController initialized");
    }

    @PostMapping
    @Operation(summary = "Get all entities", description = "Returns list of all entities")
    public String getAll(@PathVariable String entity) {
        return "[]";
    }
}
