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

    static void registerDatasourceProperties(DynamicPropertyRegistry r, PostgreSQLContainer<?> postgres) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("spring.jpa.open-in-view", () -> "false");
        r.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void clearBusinessTables() {
        jdbc.execute("TRUNCATE TABLE child_meta RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE child RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE parent RESTART IDENTITY CASCADE");

        // seed minimal baseline per test
        jdbc.update("INSERT INTO parent(name) VALUES (?)", "Parent 1");
        jdbc.update("INSERT INTO parent(name) VALUES (?)", "Parent 2");
    }

    @SuppressWarnings("unchecked")
    protected ResponseEntity<Object> postSearch(String entity, Object body) {
        return rest.exchange(
                "/multistorage/api/" + entity + "/search",
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<Object>() {}
        );
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> asSearchResult(ResponseEntity<Object> r) {
        return (List<Map<String, Object>>) r.getBody();
    }

    protected ResponseEntity<Map<String, Object>> postUpsert(String entity, Object body) {
        return rest.exchange(
                "/multistorage/api/" + entity,
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected ResponseEntity<Map<String, Object>> deleteEntity(String entity, Object id) {
        return rest.exchange(
                "/multistorage/api/" + entity + "/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    protected ResponseEntity<String> postAdmin(String path, Object body) {
        return rest.exchange(
                "/multistorage/admin/meta" + path,
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

