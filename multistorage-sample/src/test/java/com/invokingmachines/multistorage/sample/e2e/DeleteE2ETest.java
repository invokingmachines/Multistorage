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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class DeleteE2ETest extends AbstractE2ETest {

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
    void delete_existingRow_removesIt() {
        seedOneChild();
        var r = deleteEntity(E2ETestConfig.T_CHILD, 1);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("deleted", true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child") + " WHERE id = 1", Integer.class)).isEqualTo(0);
    }

    @Test
    void delete_nonExistingRow_doesNotFail() {
        var r = deleteEntity(E2ETestConfig.T_CHILD, 999);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("deleted", true);
    }

    @Test
    void delete_numericStringId_isParsedAndWorks() {
        seedOneChild();
        var r = deleteEntity(E2ETestConfig.T_CHILD, "1");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child"), Integer.class)).isEqualTo(0);
    }

    @Test
    void delete_childMetaRow_works() {
        seedOneChild();
        jdbc.update("INSERT INTO " + tenantTable("child_meta") + "(child_id, meta_value) VALUES (?,?)", 1L, "m1");
        var r = deleteEntity(E2ETestConfig.T_CHILD_META, 1);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("child_meta") + " WHERE id = 1", Integer.class)).isEqualTo(0);
    }

    @Test
    void delete_unknownEntityEndpoint_is404() {
        var r = deleteEntity("no_such_entity", 1);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void seedOneChild() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        jdbc.update("INSERT INTO " + tenantTable("child") + "(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 1", Timestamp.from(now), Timestamp.from(now));
    }
}

