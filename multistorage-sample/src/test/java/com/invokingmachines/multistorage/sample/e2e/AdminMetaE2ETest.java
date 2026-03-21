package com.invokingmachines.multistorage.sample.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class AdminMetaE2ETest extends AbstractE2ETest {

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
    void admin_upsertTable_createsRow() {
        var r = postAdmin("/tables", Map.of("name", "order_line", "alias", "OrderLine"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("meta_table") + " WHERE name = 'order_line'", Integer.class)).isEqualTo(1);
    }

    @Test
    void admin_upsertColumn_createsRow() {
        var r = postAdmin("/tables/" + E2ETestConfig.T_PARENT + "/columns", Map.of(
                "name", "code",
                "alias", "code",
                "dataType", "text",
                "readable", true,
                "searchable", true
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + tenantTable("meta_column") + " WHERE name = 'code'", Integer.class)).isEqualTo(1);
    }

    @Test
    void admin_upsertRelation_allowsCascadeTypeUpdate() {
        var r = postAdmin("/relations", Map.of(
                "fromTable", "Parent",
                "toTable", "Child",
                "fromColumn", "id",
                "toColumn", "parent_id",
                "oneToMany", true,
                "alias", E2ETestConfig.R_PARENT_TO_CHILD,
                "cascadeType", "NONE",
                "active", true
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT cascade_type FROM " + tenantTable("meta_relation") + " WHERE alias = ?", String.class, E2ETestConfig.R_PARENT_TO_CHILD))
                .isEqualTo("NONE");

        // revert
        postAdmin("/relations", Map.of(
                "fromTable", "Parent",
                "toTable", "Child",
                "fromColumn", "id",
                "toColumn", "parent_id",
                "oneToMany", true,
                "alias", E2ETestConfig.R_PARENT_TO_CHILD,
                "cascadeType", "PERSIST_MERGE",
                "active", true
        ));
    }

    @Test
    void admin_tableAliasConflictWithExistingName_isRejected() {
        var r = postAdmin("/tables", Map.of("name", "new_table", "alias", E2ETestConfig.T_CHILD));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void admin_columnAliasConflictWithExistingColumnName_isRejected() {
        var r = postAdmin("/tables/" + E2ETestConfig.T_CHILD + "/columns", Map.of(
                "name", "x",
                "alias", "name",
                "dataType", "text"
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void admin_relationAliasConflictWithExistingColumnName_isRejected() {
        var r = postAdmin("/relations", Map.of(
                "fromTable", "Child",
                "toTable", "Parent",
                "fromColumn", "parent_id",
                "toColumn", "id",
                "oneToMany", false,
                "alias", "name",
                "cascadeType", "PERSIST_MERGE",
                "active", true
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void admin_updateTable_withoutAliasChange_isAllowed() {
        var r = postAdmin("/tables", Map.of("name", E2ETestConfig.T_PARENT));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

