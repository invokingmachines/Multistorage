package com.invokingmachines.multistorage.sample.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class UpsertE2ETest extends AbstractE2ETest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("multistorage")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        registerDatasourceProperties(r, POSTGRES);
    }

    @Test
    void upsert_insertParent_returnsSavedEntity() {
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of("name", "Parent X"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("name", "Parent X").containsKey("id");
    }

    @Test
    void upsert_updateParentById_updatesRow() {
        jdbc.update("INSERT INTO " + tenantTable("parent") + "(name) VALUES (?)", "Parent Old");
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of("id", 3, "name", "Parent New"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("name", "Parent New").containsEntry("id", 3);
        assertThat(jdbc.queryForObject("SELECT name FROM " + tenantTable("parent") + " WHERE id = 3", String.class)).isEqualTo("Parent New");
    }

    @Test
    void upsert_insertChild_withNestedParent_manyToOneCascade_persistsBoth() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "name", "Child X",
                "createdAt", "2024-01-15T10:00:00Z",
                "updatedAt", "2024-01-15T10:00:00Z",
                E2ETestConfig.R_CHILD_TO_PARENT, Map.of("name", "Parent Nested")
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("name", "Child X").containsKey("id");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("parent") + " WHERE name = 'Parent Nested'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child") + " WHERE name = 'Child X'", Integer.class)).isEqualTo(1);
    }

    @Test
    void upsert_insertParent_withChildren_oneToManyCascade_persistsChildren() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of(
                "name", "Parent With Kids",
                E2ETestConfig.R_PARENT_TO_CHILD, List.of(
                        Map.of("name", "Kid 1", "createdAt", now.toString(), "updatedAt", now.toString()),
                        Map.of("name", "Kid 2", "createdAt", now.toString(), "updatedAt", now.toString())
                )
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child") + " WHERE name IN ('Kid 1','Kid 2')", Integer.class)).isEqualTo(2);
    }

    @Test
    void upsert_insertChild_withChildMeta_oneToManyCascade_persistsGrandChildren() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "parentId", 1,
                "name", "Child With Meta",
                "createdAt", now.toString(),
                "updatedAt", now.toString(),
                E2ETestConfig.R_CHILD_TO_CHILD_META, List.of(
                        Map.of("metaValue", "m1"),
                        Map.of("metaValue", "m2")
                )
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child_meta") + " WHERE meta_value IN ('m1','m2')", Integer.class)).isEqualTo(2);
    }

    @Test
    void upsert_whenCascadeNone_parentToChild_nestedWriteFails() {
        setCascade(E2ETestConfig.R_PARENT_TO_CHILD, "NONE");
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of(
                "name", "Parent Bad",
                E2ETestConfig.R_PARENT_TO_CHILD, List.of(
                        Map.of("name", "Kid", "createdAt", now.toString(), "updatedAt", now.toString())
                )
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
        setCascade(E2ETestConfig.R_PARENT_TO_CHILD, "PERSIST_MERGE");
    }

    @Test
    void upsert_whenCascadeNone_childToParent_nestedWriteFails() {
        setCascade(E2ETestConfig.R_CHILD_TO_PARENT, "NONE");
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "name", "Child Bad",
                "createdAt", now.toString(),
                "updatedAt", now.toString(),
                E2ETestConfig.R_CHILD_TO_PARENT, Map.of("name", "Parent Bad")
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
        setCascade(E2ETestConfig.R_CHILD_TO_PARENT, "PERSIST_MERGE");
    }

    @Test
    void upsert_timestampWithOffset_convertsToJdbcTimestamp() {
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "parentId", 1,
                "name", "Child TS",
                "createdAt", "2024-01-15T10:00:00+00:00",
                "updatedAt", "2024-01-15T10:00:00+00:00"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child") + " WHERE name = 'Child TS'", Integer.class)).isEqualTo(1);
    }

    @Test
    void upsert_numericAsString_converts() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "parentId", "1",
                "name", "Child Num",
                "createdAt", now.toString(),
                "updatedAt", now.toString()
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM " + tenantTable("child") + " WHERE name = 'Child Num'", Long.class)).isEqualTo(1L);
    }

    @Test
    void upsert_responseIsNestedTree_afterRefetch() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of(
                "name", "Parent Tree",
                E2ETestConfig.R_PARENT_TO_CHILD, List.of(
                        Map.of(
                                "name", "Kid Tree",
                                "createdAt", now.toString(),
                                "updatedAt", now.toString(),
                                E2ETestConfig.R_CHILD_TO_CHILD_META, List.of(Map.of("metaValue", "mx"))
                        )
                )
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey(E2ETestConfig.R_PARENT_TO_CHILD);
        assertThat(r.getBody().get(E2ETestConfig.R_PARENT_TO_CHILD)).isInstanceOf(List.class);
    }

    @Test
    void upsert_unknownField_failsValidation() {
        var r = postUpsert(E2ETestConfig.T_PARENT, Map.of("unknown", "x"));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void upsert_updateChild_updatesTimestamps() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        jdbc.update("INSERT INTO " + tenantTable("child") + "(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child U", Timestamp.from(now), Timestamp.from(now));
        var r = postUpsert(E2ETestConfig.T_CHILD, Map.of(
                "id", 1,
                "name", "Child U2",
                "updatedAt", "2024-01-15T12:00:00Z"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT name FROM " + tenantTable("child") + " WHERE id = 1", String.class)).isEqualTo("Child U2");
    }

    private void setCascade(String relationAlias, String cascade) {
        jdbc.update("UPDATE " + tenantTable("meta_relation") + " SET cascade_type = ? WHERE alias = ?", cascade, relationAlias);
    }
}

