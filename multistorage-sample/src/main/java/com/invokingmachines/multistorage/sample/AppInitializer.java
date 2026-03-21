package com.invokingmachines.multistorage.sample;

import com.invokingmachines.multistorage.dto.db.Table;
import com.invokingmachines.multistorage.meta.service.DatabaseMetadataManagerService;
import com.invokingmachines.multistorage.sample.multitenancy.TenantContext;
import com.invokingmachines.multistorage.sample.multitenancy.TenantSchemaRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@Profile("!test")
public class AppInitializer implements ApplicationRunner {

    private final DatabaseMetadataManagerService databaseMetadataManagerService;
    private final TenantSchemaRegistry tenantSchemaRegistry;

    @Override
    public void run(ApplicationArguments args) {
        tenantSchemaRegistry.listOrdered().forEach(t -> {
            try {
                TenantContext.setTenantCode(t.getCode());
                log.info("Starting database scan for tenant {}...", t.getCode());
                Map<String, Table> tables = databaseMetadataManagerService.scanDatabase();
                log.info("Database scan completed for tenant {}. Found {} tables:", t.getCode(), tables.size());
                tables.forEach((name, table) -> log.info("Table: {} (schema: {}, columns: {}, relations: {})",
                        name, table.getSchema(), table.getColumns().size(), table.getRelations().size()));
            } finally {
                TenantContext.clear();
            }
        });
    }
}
