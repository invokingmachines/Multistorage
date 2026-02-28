# Multistorage

Spring Boot starter that turns any application with a PostgreSQL database into a **CRUD-style API** with a unified request format: metadata (tables, columns, relations) is stored in the DB, search is expressed as JSON (select/where), and results are returned with nested relations resolved.

**Why:** Get an API on top of an existing schema without writing controllers and DTOs for every entity; a single contract for frontends and integrations; room for customization (multi-tenancy, auth, validation, post-processing).

---

## Current features

- **Dynamic Search API** — For each entity (by table alias from metadata), a `POST /multistorage/api/{entity}/search` endpoint is registered. Request body: JSON Query (select, where).
- **Metadata engine** — Tables, columns, and relations are read from storage (`meta_table`, `meta_column`, `meta_relation`). Supports aliases, readable/searchable flags, and bidirectional relations (one/many, inverse name).
- **Query → SQL** — Query is compiled to SQL using metadata: arbitrary-depth JOINs along relation chains, filters on fields at any nesting level, parameterized queries.
- **Select:** Root `["*"]`, relation paths like `["relationName", "*"]`, recursive expansion of `*` to any depth (e.g. `["childToParent", "childToChild_meta", "*"]`).
- **Where:** Criteria with operators (EQ, NE, GT, GTE, LT, LTE, IN, NIN, LIKE, ILIKE, NULL, NOT_NULL, BETWEEN), AND/OR logic, nested criteria, fields along relation chains (e.g. `["childToParent", "name"]`).
- **Nested response** — Flat JDBC rows are turned into a tree: one-to-many → array, many-to-one → single object; multiple branches from root and arbitrary nesting depth are supported.
- **Discovery** — `GET /multistorage/api/search/discovery?table=...` returns metadata for the client: list of tables, selectable and searchable columns (including via relations).
- **Metadata Admin API** — CRUD for tables, columns, and relations: `/multistorage/admin/meta/...` (tables, tables/{ref}/columns, relations). Liquibase migrations create meta tables on startup.
- **Metadata customization** — `MetaCustomizer` bean: alter `QueryMeta` before compilation (e.g. filter tables/columns by tenant or role). Request context is available via `MetaRequest` (principal, context).
- **OpenAPI** — Endpoints under `/multistorage/api/**` are included in the docs; tags and search operations are generated per entity.

**Stack:** Spring Boot 4, WebMvc, Data JPA, Liquibase, PostgreSQL, springdoc-openapi.

---

## Integration

### 1. Dependency

```xml
<dependency>
    <groupId>com.invokingmachines</groupId>
    <artifactId>multistorage-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

You also need: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `liquibase-core`, `postgresql`, and optionally `springdoc-openapi-starter-webmvc-ui`.

### 2. Database and metadata

- Configure PostgreSQL and Liquibase in your application.
- The starter brings its own meta migrations (`db/changelog/meta/`) that create `meta_table`, `meta_column`, `meta_relation`.
- Register your business tables and columns in metadata (via Admin API or your own migrations/seeds). Search endpoints are then exposed per table alias.

### 3. Auto-configuration

The starter registers `MultistorageAutoConfiguration`: component scan for `meta`, `query`, `config` packages, JPA entities and meta repositories, and `MultistorageScanProperties` (prefix `multistorage.scan`, optional `ignoreTables`). To disable: exclude the auto-configuration or omit the dependency.

### 4. Metadata customization

Implement and register a bean:

```java
@Component
public class TenantMetaCustomizer implements MetaCustomizer {
    @Override
    public QueryMeta customize(QueryMeta meta, MetaRequest request) {
        // Restrict tables/columns by tenant, role, etc.
        return meta;
    }
}
```

Search calls do not yet pass `MetaRequest` with the current user/context into the meta provider; you can add that in `EntitySearchHandler` and `MetaProvider.getMeta(request)` if needed.

---

## Search request format

- **select** — List of paths. A path is an array of strings. `["*"]` = all readable columns of the root. `["relationName", "*"]` = all readable columns of that relation; chains like `["a", "b", "*"]` for nested relations.
- **where** — Tree of criteria. Node: `logician` (AND/OR), `criteria` = array of nodes or leaf criteria. Leaf: `field` (array = path to field, including via relations), `operator`, `value` (for BETWEEN use an array of two elements).

Example:

```json
{
  "select": [ ["*"], ["childToParent", "*"], ["childToChild_meta", "*"] ],
  "where": {
    "logician": "AND",
    "criteria": [
      { "field": ["childToParent", "name"], "operator": "EQ", "value": "Parent 7" }
    ]
  }
}
```

Response is a list of maps (objects); nested relations are shaped as objects or arrays by relation type (many-to-one → object, one-to-many → array).

---

## Planned (TODO)

- **Entity CRUD** — Create (POST body), Read by id (GET `/{entity}/{id}`), Update (PUT/PATCH), Delete (DELETE `/{entity}/{id}`) for business table rows, not only metadata.
- **Validation and pre-processing** — Hooks/interfaces before running the query (validate Query, inject context into where, limits).
- **Post-processing** — Transform or mask the result after fetch (e.g. hide fields, resolve references).
- **Multi-tenancy** — Built-in ways to inject tenant id into queries and metadata (e.g. via `MetaCustomizer` and request context).
- **Custom authorization** — Access checks for entities/fields at API level (e.g. with Spring Security, before compile/execute).
- **Pagination and sorting** — limit/offset and order by in Query and generated SQL.
- **API versioning** — Version prefix in paths or headers.
- **Audit and logging** — Optional request/response logging and CRUD audit trail.
