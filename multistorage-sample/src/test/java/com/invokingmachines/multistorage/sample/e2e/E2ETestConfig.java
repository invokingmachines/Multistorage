package com.invokingmachines.multistorage.sample.e2e;

import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

@TestConfiguration
public class E2ETestConfig {

    public static final String T_PARENT = "parent";
    public static final String T_CHILD = "child";
    public static final String T_CHILD_META = "child_meta";

    public static final String R_PARENT_TO_CHILD = "parentToChild";
    public static final String R_CHILD_TO_PARENT = "childToParent";
    public static final String R_CHILD_TO_CHILD_META = "childToChildMeta";
    public static final String R_CHILD_META_TO_CHILD = "childMetaToChild";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner seedSchemaAndMeta(JdbcTemplate jdbcTemplate,
                                        MetaTableRepository metaTableRepository,
                                        MetaColumnRepository metaColumnRepository,
                                        MetaRelationRepository metaRelationRepository) {
        return args -> {
            dropBusinessTables(jdbcTemplate);
            createBusinessSchema(jdbcTemplate);
            truncateAll(jdbcTemplate);
            seedMeta(metaTableRepository, metaColumnRepository, metaRelationRepository);
            seedBusinessData(jdbcTemplate);
        };
    }

    private static void dropBusinessTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS child_meta CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS child CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS parent CASCADE");
    }

    private static void createBusinessSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS parent (
                  id BIGSERIAL PRIMARY KEY,
                  name TEXT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS child (
                  id BIGSERIAL PRIMARY KEY,
                  parent_id BIGINT REFERENCES parent(id),
                  name TEXT NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL,
                  updated_at TIMESTAMPTZ NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS child_meta (
                  id BIGSERIAL PRIMARY KEY,
                  child_id BIGINT REFERENCES child(id),
                  meta_value TEXT NOT NULL
                )
                """);
    }

    private static void truncateAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE meta_relation RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE meta_column RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE meta_table RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE child_meta RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE child RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE parent RESTART IDENTITY CASCADE");
    }

    private static void seedMeta(MetaTableRepository metaTableRepository,
                                 MetaColumnRepository metaColumnRepository,
                                 MetaRelationRepository metaRelationRepository) {
        MetaTableEntity parent = metaTableRepository.save(MetaTableEntity.builder().name(T_PARENT).alias("Parent").build());
        MetaTableEntity child = metaTableRepository.save(MetaTableEntity.builder().name(T_CHILD).alias("Child").build());
        MetaTableEntity childMeta = metaTableRepository.save(MetaTableEntity.builder().name(T_CHILD_META).alias("ChildMeta").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(parent).name("id").alias("id").dataType("bigint").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(parent).name("name").alias("name").dataType("text").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("id").alias("id").dataType("bigint").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("parent_id").alias("parentId").dataType("bigint").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("name").alias("name").dataType("text").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("created_at").alias("createdAt").dataType("timestamptz").build());
        metaColumnRepository.save(MetaColumnEntity.builder().table(child).name("updated_at").alias("updatedAt").dataType("timestamptz").build());

        metaColumnRepository.save(MetaColumnEntity.builder().table(childMeta).name("id").alias("id").dataType("bigint").build());
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
        jdbcTemplate.update("INSERT INTO parent(name) VALUES (?)", "Parent 1");
        jdbcTemplate.update("INSERT INTO parent(name) VALUES (?)", "Parent 2");

        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        jdbcTemplate.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 1", Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 2", Timestamp.from(now.plusSeconds(60)), Timestamp.from(now.plusSeconds(60)));
        jdbcTemplate.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                2L, "Child 3", Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(120)));

        jdbcTemplate.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m1");
        jdbcTemplate.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m2");
        jdbcTemplate.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 2L, "m3");
    }
}

