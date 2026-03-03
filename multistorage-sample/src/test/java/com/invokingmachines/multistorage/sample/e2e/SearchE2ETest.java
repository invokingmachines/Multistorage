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
public class SearchE2ETest extends AbstractE2ETest {

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
    void search_emptyWhere_returnsAllRows() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("*")),
                "where", Map.of("logician", "AND", "criteria", List.of())
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asSearchResult(r)).hasSize(3);
    }

    @Test
    void search_eqWhere_filters() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("name")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 2")
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = asSearchResult(r);
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("name")).isEqualTo("Child 2");
    }

    @Test
    void search_gtWhere_filters() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("id")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("id"), "operator", "GT", "value", 1)
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asSearchResult(r)).extracting(m -> (Number) m.get("id"))
                .allSatisfy(n -> assertThat(n.longValue()).isGreaterThan(1L));
    }

    @Test
    void search_whereOnRelationField_joinsAndFilters() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("name")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of(E2ETestConfig.R_CHILD_TO_PARENT, "name"), "operator", "EQ", "value", "Parent 2")
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = asSearchResult(r);
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("name")).isEqualTo("Child 3");
    }

    @Test
    void search_selectStar_returnsAliases() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("*")),
                "where", Map.of("logician", "AND", "criteria", List.of())
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> list = asSearchResult(r);
        assertThat(list.get(0)).containsKeys("id", "name", "createdAt", "updatedAt", "parentId");
    }

    @Test
    void search_selectSpecificFieldsByAlias_works() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("createdAt"), List.of("name")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 1")
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = asSearchResult(r);
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).containsKeys("createdAt", "name");
    }

    @Test
    void search_selectRelationStar_returnsNestedObject() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("*"), List.of(E2ETestConfig.R_CHILD_TO_PARENT, "*")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 1")
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = asSearchResult(r);
        assertThat(body).hasSize(1);
        assertThat(body.get(0)).containsKey(E2ETestConfig.R_CHILD_TO_PARENT);
        assertThat(body.get(0).get(E2ETestConfig.R_CHILD_TO_PARENT)).isInstanceOf(Map.class);
    }

    @Test
    void search_complexAndOrCriteria_works() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("name")),
                "where", Map.of("logician", "OR", "criteria", List.of(
                        Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 1"),
                        Map.of("logician", "AND", "criteria", List.of(
                                Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 3")
                        ))
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asSearchResult(r)).extracting(m -> (String) m.get("name"))
                .containsExactlyInAnyOrder("Child 1", "Child 3");
    }

    @Test
    void search_unknownField_failsValidation() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("unknown")),
                "where", Map.of("logician", "AND", "criteria", List.of())
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void search_unknownRelation_failsValidation() {
        seedChildren();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("name")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("nope", "name"), "operator", "EQ", "value", "x")
                ))
        ));
        assertThat(r.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void search_selectDeepNestedRelationStar_works() {
        seedChildren();
        seedChildMeta();
        var r = postSearch(E2ETestConfig.T_CHILD, Map.of(
                "select", List.of(List.of("name"), List.of(E2ETestConfig.R_CHILD_TO_CHILD_META, "*")),
                "where", Map.of("logician", "AND", "criteria", List.of(
                        Map.of("field", List.of("name"), "operator", "EQ", "value", "Child 1")
                ))
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = asSearchResult(r);
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get(E2ETestConfig.R_CHILD_TO_CHILD_META)).isInstanceOf(List.class);
    }

    private void seedChildren() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        jdbc.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 1", Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                1L, "Child 2", Timestamp.from(now.plusSeconds(60)), Timestamp.from(now.plusSeconds(60)));
        jdbc.update("INSERT INTO child(parent_id, name, created_at, updated_at) VALUES (?,?,?,?)",
                2L, "Child 3", Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(120)));
    }

    private void seedChildMeta() {
        jdbc.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m1");
        jdbc.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 1L, "m2");
        jdbc.update("INSERT INTO child_meta(child_id, meta_value) VALUES (?,?)", 2L, "m3");
    }
}

