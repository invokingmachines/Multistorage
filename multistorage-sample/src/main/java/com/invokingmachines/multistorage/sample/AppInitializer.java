package com.invokingmachines.multistorage.sample;

import com.invokingmachines.multistorage.dto.db.Table;
import com.invokingmachines.multistorage.meta.service.DatabaseMetadataManagerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("liquibase")
public class AppInitializer {

    private final DatabaseMetadataManagerService databaseMetadataManagerService;

    @PostConstruct
    public void scanDatabase() {
        log.info("Starting database scan...");
        Map<String, Table> tables = databaseMetadataManagerService.scanDatabase();
        log.info("Database scan completed. Found {} tables:", tables.size());
        tables.forEach((name, table) -> {
            log.info("Table: {} (schema: {}, columns: {}, relations: {})",
                    name, table.getSchema(), table.getColumns().size(), table.getRelations().size());
        });
    }
}
