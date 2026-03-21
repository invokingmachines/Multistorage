package com.invokingmachines.multistorage.sample.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        classes = com.invokingmachines.multistorage.sample.SampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(classes = {E2ETestConfig.class})
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractE2ETest {

    public static final String E2E_TENANT_CODE = "demo";
    public static final String E2E_TENANT_SCHEMA = "_1_sample";
    public static final String API_PREFIX = "/api";

    static void registerDatasourceProperties(DynamicPropertyRegistry r, PostgreSQLContainer<?> postgres) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("spring.jpa.open-in-view", () -> "false");
        r.add("spring.liquibase.enabled", () -> "false");
        r.add("multistorage.web.api-tenant-prefix", () -> "/api");
        r.add("multistorage.web.api-prefix", () -> "/api/{tenantId}");
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static String quotedTenantSchema() {
        return "\"" + E2E_TENANT_SCHEMA.replace("\"", "\"\"") + "\"";
    }

    protected static String tenantTable(String tableName) {
        return quotedTenantSchema() + "." + tableName;
    }

    @BeforeEach
    void clearBusinessTables() {
        String qs = quotedTenantSchema();
        jdbc.execute("TRUNCATE TABLE "
                + qs + ".child_meta, "
                + qs + ".child, "
                + qs + ".parent "
                + "RESTART IDENTITY CASCADE");

        jdbc.update("INSERT INTO " + qs + ".parent(name) VALUES (?)", "Parent 1");
        jdbc.update("INSERT INTO " + qs + ".parent(name) VALUES (?)", "Parent 2");
    }

    @SuppressWarnings("unchecked")
    protected ResponseEntity<Object> postSearch(String entity, Object body) {
        return rest.exchange(
                tenantEntityPath(entity, "search"),
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<Object>() {}
        );
    }

    protected String tenantEntityPath(String entity, String... suffix) {
        StringBuilder b = new StringBuilder(API_PREFIX)
                .append('/')
                .append(E2E_TENANT_CODE)
                .append("/data/")
                .append(entity);
        for (String s : suffix) {
            b.append('/').append(s);
        }
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> asSearchResult(ResponseEntity<Object> r) {
        Object body = r.getBody();
        if (body instanceof List) return (List<Map<String, Object>>) body;
        if (body instanceof Map map && map.containsKey("content")) return (List<Map<String, Object>>) map.get("content");
        return List.of();
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> asPagedSearchResult(ResponseEntity<Object> r) {
        Object body = r.getBody();
        return body instanceof Map ? (Map<String, Object>) body : Map.of();
    }

    protected ResponseEntity<Map<String, Object>> postUpsert(String entity, Object body) {
        return rest.exchange(
                tenantEntityPath(entity),
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected ResponseEntity<Map<String, Object>> deleteEntity(String entity, Object id) {
        return rest.exchange(
                tenantEntityPath(entity, String.valueOf(id)),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected ResponseEntity<String> postAdmin(String path, Object body) {
        return rest.exchange(
                API_PREFIX + "/" + E2E_TENANT_CODE + "/meta" + path,
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                String.class
        );
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
}
