package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.api.MetaDiscoveryDto;
import com.invokingmachines.multistorage.query.service.MetaDiscoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/multistorage/api/search/discovery")
@Tag(name = "Discovery", description = "Metadata available to the user: tables and columns")
@RequiredArgsConstructor
public class MetaDiscoveryController {

    private final MetaDiscoveryService metaDiscoveryService;

    @GetMapping
    @Operation(summary = "List metadata", description = "Without table: all tables. With table (name or alias): only that table. Each table has columns and relations.")
    public MetaDiscoveryDto discovery(@RequestParam(required = false) String table) {
        return metaDiscoveryService.getDiscovery(table);
    }
}
