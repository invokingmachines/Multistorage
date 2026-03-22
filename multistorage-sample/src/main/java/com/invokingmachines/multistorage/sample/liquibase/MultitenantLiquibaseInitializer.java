package com.invokingmachines.multistorage.sample.liquibase;

import com.invokingmachines.multistorage.liquibase.SchemaLiquibaseRunner;
import com.invokingmachines.multistorage.sample.multitenancy.MultitenantDataSource;
import com.invokingmachines.multistorage.sample.multitenancy.TenantContext;
import com.invokingmachines.multistorage.sample.multitenancy.TenantSchemaRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(0)
@Profile("!test")
@RequiredArgsConstructor
public class MultitenantLiquibaseInitializer implements ApplicationRunner {

    private static final String PUBLIC_CHANGELOG = "classpath:db/changelog/public/db.changelog-public-master.yaml";
    private static final String STARTER_META_CHANGELOG = "db/changelog/multistorage-meta-master.yaml";
    private static final String TENANT_BUSINESS_CHANGELOG = "classpath:db/changelog/tenant/db.changelog-tenant-business-master.yaml";

    private final DataSource dataSource;
    private final TenantSchemaRegistry tenantSchemaRegistry;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DataSource raw = MultitenantDataSource.unwrapDelegate(dataSource);

        try {
            TenantContext.clear();
            SchemaLiquibaseRunner.run(dataSource, PUBLIC_CHANGELOG, "public", "public");
        } finally {
            TenantContext.clear();
        }

        List<TenantRow> tenants = loadTenants(raw);
        for (TenantRow tenant : tenants) {
            String schema = TenantSchemaRegistry.schemaNameForTenantId(tenant.id());
            createSchemaIfMissing(raw, schema);
        }

        for (TenantRow tenant : tenants) {
            try {
                TenantContext.setTenantCode(tenant.code());
                String schema = TenantSchemaRegistry.schemaNameForTenantId(tenant.id());
                SchemaLiquibaseRunner.run(dataSource, STARTER_META_CHANGELOG, schema, schema);
                SchemaLiquibaseRunner.run(dataSource, TENANT_BUSINESS_CHANGELOG, schema, schema);
                log.info("Liquibase applied for tenant {} schema {}", tenant.code(), schema);
            } finally {
                TenantContext.clear();
            }
        }
        tenantSchemaRegistry.refresh();
    }

    private static void createSchemaIfMissing(DataSource ds, String schema) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            String escaped = schema.replace("\"", "\"\"");
            st.execute("CREATE SCHEMA IF NOT EXISTS \"" + escaped + "\"");
        }
    }

    private static List<TenantRow> loadTenants(DataSource ds) throws Exception {
        List<TenantRow> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, code FROM public.tenant ORDER BY id")) {
            while (rs.next()) {
                rows.add(new TenantRow(rs.getLong(1), rs.getString(2)));
            }
        }
        return rows;
    }

    private record TenantRow(long id, String code) {
    }
}
