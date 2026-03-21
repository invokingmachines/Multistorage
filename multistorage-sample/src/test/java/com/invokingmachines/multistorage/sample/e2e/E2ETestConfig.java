package com.invokingmachines.multistorage.sample.e2e;

import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import com.invokingmachines.multistorage.sample.multitenancy.MultitenantDataSource;
import com.invokingmachines.multistorage.sample.multitenancy.TenantContext;
import com.invokingmachines.multistorage.sample.multitenancy.TenantSchemaRegistry;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

@TestConfiguration
public class E2ETestConfig {

    private static final String STARTER_META_CHANGELOG = "db/changelog/multistorage-meta-master.yaml";
    private static final String TENANT_BUSINESS_CHANGELOG = "classpath:db/changelog/tenant/db.changelog-tenant-business-master.yaml";

    public static final String T_PARENT = "parent";
    public static final String T_CHILD = "child";
    public static final String T_CHILD_META = "child_meta";

    public static final String R_PARENT_TO_CHILD = "parentToChild";
    public static final String R_CHILD_TO_PARENT = "childToParent";
    public static final String R_CHILD_TO_CHILD_META = "childToChildMeta";
    public static final String R_CHILD_META_TO_CHILD = "childMetaToChild";

    private static String quotedSchema() {
        return "\"" + AbstractE2ETest.E2E_TENANT_SCHEMA.replace("\"", "\"\"") + "\"";
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner seedSchemaAndMeta(DataSource dataSource,
                                        JdbcTemplate jdbcTemplate,
                                        MetaTableRepository metaTableRepository,
                                        MetaColumnRepository metaColumnRepository,
                                        MetaRelationRepository metaRelationRepository,
                                        TenantSchemaRegistry tenantSchemaRegistry) {
        return args -> {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quotedSchema() + " CASCADE");
            jdbcTemplate.execute("CREATE SCHEMA " + quotedSchema());
            ensurePublicTenant(jdbcTemplate);
            try {
                runTenantLiquibase(dataSource);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            tenantSchemaRegistry.refresh();
            try {
                TenantContext.setTenantCode(AbstractE2ETest.E2E_TENANT_CODE);
                truncateAll(jdbcTemplate);
                seedMeta(metaTableRepository, metaColumnRepository, metaRelationRepository);
                seedBusinessData(jdbcTemplate);
            } finally {
                TenantContext.clear();
            }
        };
    }

    private static void runTenantLiquibase(DataSource dataSource) throws Exception {
        try {
            TenantContext.setTenantCode(AbstractE2ETest.E2E_TENANT_CODE);
            runSingleLiquibase(dataSource, STARTER_META_CHANGELOG);
            runSingleLiquibase(dataSource, TENANT_BUSINESS_CHANGELOG);
        } finally {
            TenantContext.clear();
        }
    }

    private static void runSingleLiquibase(DataSource dataSource, String changelogClasspath) throws Exception {
        String path = changelogClasspath.startsWith("classpath:")
                ? changelogClasspath.substring("classpath:".length())
                : changelogClasspath;
        try (Connection connection = dataSource.getConnection()) {
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            database.setDefaultSchemaName(AbstractE2ETest.E2E_TENANT_SCHEMA);
            database.setLiquibaseSchemaName(AbstractE2ETest.E2E_TENANT_SCHEMA);
            Liquibase liquibase = new Liquibase(path, new ClassLoaderResourceAccessor(), database);
            liquibase.update("");
        }
    }

    private static void ensurePublicTenant(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS public.tenant (
                  id BIGSERIAL PRIMARY KEY,
                  code TEXT NOT NULL UNIQUE
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO public.tenant (id, code) VALUES (1, ?) ON CONFLICT (id) DO NOTHING",
                AbstractE2ETest.E2E_TENANT_CODE
        );
    }

    private static void truncateAll(JdbcTemplate jdbcTemplate) {
        String qs = quotedSchema();
        jdbcTemplate.execute("TRUNCATE TABLE "
                + qs + ".meta_relation, "
                + qs + ".meta_column, "
                + qs + ".meta_table, "
                + qs + ".child_meta, "
                + qs + ".child, "
                + qs + ".parent "
                + "RESTART IDENTITY CASCADE");
    }

    private static void seedMeta(MetaTableRepository metaTableRepository,
                                 MetaColumnRepository metaColumnRepository,
                                 MetaRelationRepository metaRelationRepository) {
        MetaTableEntity parent = metaTableRepository.save(MetaTableEntity.builder().name(T_PARENT).alias("Parent").build());
        MetaTableEntity child = metaTableRepository.save(MetaTableEntity.builder().name(T_CHILD).alias("Child").build());
        MetaTableEntity childMeta = metaTableRepository.save(MetaTableEntity.builder().name(T_CHILD_META).alias("ChildMeta").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(parent).name("id").alias("id").dataType("bigint").editable(false).build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(parent).name("name").alias("name").dataType("text").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("id").alias("id").dataType("bigint").editable(false).build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("parent_id").alias("parentId").dataType("bigint").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("name").alias("name").dataType("text").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("created_at").alias("createdAt").dataType("timestamptz").editable(false).build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("updated_at").alias("updatedAt").dataType("timestamptz").editable(false).build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("child_type").alias("childType").dataType("text").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(childMeta).name("id").alias("id").dataType("bigint").editable(false).build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(childMeta).name("child_id").alias("childId").dataType("bigint").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(childMeta).name("meta_value").alias("metaValue").dataType("text").build());

        metaRelationRepository.save(MetaRelationEntity.builder()
                .fromTable(parent)
                .toTable(child)
                .fromColumn("id")
                .toColumn("parent_id")
                .oneToMany(true)
                .alias(R_PARENT_TO_CHILD)
                .cascadeType("PERSIST_MERGE")
                .active(true)
                .build());
        metaRelationRepository.save(MetaRelationEntity.builder()
                .fromTable(child)
                .toTable(parent)
                .fromColumn("parent_id")
                .toColumn("id")
                .oneToMany(false)
                .alias(R_CHILD_TO_PARENT)
                .cascadeType("PERSIST_MERGE")
                .active(true)
                .build());

        metaRelationRepository.save(MetaRelationEntity.builder()
                .fromTable(child)
                .toTable(childMeta)
                .fromColumn("id")
                .toColumn("child_id")
                .oneToMany(true)
                .alias(R_CHILD_TO_CHILD_META)
                .cascadeType("PERSIST_MERGE")
                .active(true)
                .build());
        metaRelationRepository.save(MetaRelationEntity.builder()
                .fromTable(childMeta)
                .toTable(child)
                .fromColumn("child_id")
                .toColumn("id")
                .oneToMany(false)
                .alias(R_CHILD_META_TO_CHILD)
                .cascadeType("PERSIST_MERGE")
                .active(true)
                .build());
    }

    private static void seedBusinessData(JdbcTemplate jdbcTemplate) {
        String qs = quotedSchema();
        jdbcTemplate.update("INSERT INTO " + qs + ".parent(name) VALUES (?)", "Parent 1");
        jdbcTemplate.update("INSERT INTO " + qs + ".parent(name) VALUES (?)", "Parent 2");

        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        jdbcTemplate.update("INSERT INTO " + qs + ".child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 1", Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("INSERT INTO " + qs + ".child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 2", Timestamp.from(now.plusSeconds(60)), Timestamp.from(now.plusSeconds(60)));
        jdbcTemplate.update("INSERT INTO " + qs + ".child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                2L, "Child 3", Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(120)));

        jdbcTemplate.update("INSERT INTO " + qs + ".child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m1");
        jdbcTemplate.update("INSERT INTO " + qs + ".child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m2");
        jdbcTemplate.update("INSERT INTO " + qs + ".child_meta(child_id, meta_value) VALUES (?,?)", 2L, "m3");
    }
}
