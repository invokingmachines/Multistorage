# Multistorage

Spring Boot starter that turns any application with a PostgreSQL database into a **dynamic entity API** driven by metadata stored in DB (`meta_table`, `meta_column`, `meta_relation`).

**Why:** get an API on top of an existing schema without writing controllers/DTOs for every table, keep a single JSON contract for frontends/integrations, and customize behavior via validators and pre/post processors.

---

## Current features

- **Dynamic entity endpoints** — For each business table described by metadata:
  - `POST /multistorage/api/{entity}/search`
  - `POST /multistorage/api/{entity}` (upsert)
  - `DELETE /multistorage/api/{entity}/{id}`
- **Request pipeline** — all operations go through:
  - receive request → validate request → preProcess handlers → operation → mapping to response → postProcess handlers
- **Validation** — one validator per operation type:
  - **Search**: checks that all referenced fields/relations exist in metadata
  - **Upsert**: checks that all fields exist; nested updates are allowed only when relation cascade != `NONE`
  - **Delete**: checks that target table exists
  - You can override/extend validation by providing your own `RequestValidator<?>` beans.
- **Cascade for nested upsert** — `meta_relation.cascade_type` controls whether nested entities can be persisted/updated:
  - `NONE`, `PERSIST`, `MERGE`, `PERSIST_MERGE`
- **Query → SQL** — search is compiled into parameterized SQL with JOINs via relation chains and typed parameters.
- **Nested response** — DB returns flat rows, API returns a tree:
  - one-to-many → array
  - many-to-one → single object
  - Search and upsert responses are returned in a nested structure.
- **Discovery** — `GET /multistorage/api/search/discovery?table=...` returns metadata for the client (tables, columns, relations).
- **Metadata Admin API** — CRUD for metadata: `/multistorage/admin/meta/...` (tables, columns, relations). Liquibase creates meta schema on startup.
- **Metadata customization** — `MetaCustomizer` bean can modify `QueryMeta` before compilation.
- **OpenAPI** — endpoints under `/multistorage/api/**` are included in Swagger; operations are generated per entity and include request/response examples.

**Stack:** Spring Boot 4, WebMvc, Data JPA, Liquibase, PostgreSQL, springdoc-openapi.

---

## Quickstart

### 1) Add dependency

```xml
<dependency>
  <groupId>com.invokingmachines</groupId>
  <artifactId>multistorage-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

You also need: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `liquibase-core`, `postgresql`, and optionally `springdoc-openapi-starter-webmvc-ui`.

### 2) Run and open Swagger

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

### 3) Seed metadata

You can seed metadata in two ways:
- **Admin API** (recommended for manual setup): create `meta_table`, then `meta_column`, then `meta_relation`.
- **Programmatic DB scan** (example in `multistorage-sample`): call `DatabaseMetadataManagerService.scanDatabase()`; if `MetaSyncService` is present it will sync scanned tables/columns/relations into meta tables.

Admin API base path: `/multistorage/admin/meta` (tables / columns / relations).

### 4) Call entity endpoints

Search:

```bash
curl -X POST "http://localhost:8080/multistorage/api/<entity>/search" \
  -H "Content-Type: application/json" \
  -d '{"select":[["*"]],"where":{"logician":"AND","criteria":[]}}'
```

Upsert:

```bash
curl -X POST "http://localhost:8080/multistorage/api/<entity>" \
  -H "Content-Type: application/json" \
  -d '{"name":"example"}'
```

Delete:

```bash
curl -X DELETE "http://localhost:8080/multistorage/api/<entity>/1"
```

---

## API

### Search

`POST /multistorage/api/{entity}/search`

Body (`Query`):
- **select** — list of paths (`["*"]`, `["relation", "*"]`, `["a","b","field"]`)
- **where** — criteria tree (`logician`, `criteria[]`, leaf criterion with `field[]`, `operator`, `value`)

Example:

```json
{
  "select": [["*"], ["childToParent", "*"]],
  "where": {
    "logician": "AND",
    "criteria": [
      { "field": ["childToParent", "name"], "operator": "EQ", "value": "Parent 7" }
    ]
  }
}
```

Response: array of entities with nested relations (tree).

### Upsert (create/update)

`POST /multistorage/api/{entity}`

Body: JSON object (entity). Keys can be column **name** or **alias**. Relation keys are relation aliases from `meta_relation.alias`.

Nested objects/arrays are supported when cascade allows it.

Response: saved entity in nested form (tree). Internally the starter persists and then refetches the entity by id and applies the nesting transformer, so API always returns tree-structured data.

### Delete

`DELETE /multistorage/api/{entity}/{id}`

Response:

```json
{ "deleted": true }
```

---

## Customization hooks

### Request validators

Provide your own `RequestValidator<T>` beans. Operation types:
- `SEARCH` (`T = Query`)
- `UPSERT` (`T = Map<String,Object>`)
- `DELETE` (`T = Object` / id)

If multiple validators exist for the same `OperationType`, the one with higher priority can be selected using Spring `@Order` (defaults exist in the starter).

### PreProcess / PostProcess

Register beans:
- `PreProcessHandler<T>`
- `PostProcessHandler<T, R>`

They receive `(request, meta, targetTableName)` and optionally `(response)` and can enforce app-specific behavior (auth, multi-tenancy, defaults, masking, auditing).

---

## Metadata constraints (important)

To keep resolution deterministic, manual metadata updates validate alias uniqueness:
- **Meta table**: `alias` must not conflict with any existing table `name`.
- **Meta column**: `alias` must not conflict with any existing column `name` within the same table.
- **Meta relation**: relation `alias` must not conflict with any column `name` within the same from-table.

The rest of the system assumes aliases are unique and do not conflict with names.

---

## Configuration

### multistorage.scan.ignore-tables

Additional table names to ignore during DB scan. Defaults always include:

- `databasechangelog`
- `databasechangeloglock`
- `meta_table`
- `meta_column`
- `meta_relation`

Example:

```yaml
multistorage:
  scan:
    ignore-tables:
      - flyway_schema_history
      - audit_log
```

---

## Notes / limitations

- **Primary key**: current JDBC persistor assumes primary key column name is `id` and uses `RETURNING "id"` on insert.
- **Delete**: current implementation deletes only the root row; it does not automatically cascade deletes to children.
- **Security**: exposing a dynamic entity API is powerful. In real apps you typically want to add auth/ACL and restrict meta via `MetaCustomizer` / validators.

---

## Database and metadata

- Configure PostgreSQL and Liquibase in your application.
- The starter brings meta migrations (`db/changelog/meta/`) that create:
  - `meta_table`, `meta_column`, `meta_relation`
  - including `meta_relation.cascade_type` for nested upsert cascade control
- Register your business tables/columns/relations in metadata (Admin API or your own seeds).

### 3. Auto-configuration

The starter provides `MultistorageAutoConfiguration` (component scan for `meta`, `pipeline`, `query`, `config`, JPA entities and meta repositories, and `MultistorageScanProperties`).

### 4. Metadata customization

Implement and register a bean:

```java
@Component
public class TenantMetaCustomizer implements MetaCustomizer {
  @Override
  public QueryMeta customize(QueryMeta meta, MetaRequest request) {
    return meta;
  }
}
```

At the moment the starter uses `MetaRequest.builder().build()`; if you need principal/tenant context — extend handlers/pipeline to pass request context into `MetaProvider.getMeta(...)`.

---

## Planned

- **Read by id** endpoint (GET) for entity rows
- **Pagination/sorting** for search
- **Authorization hooks** for tables/columns/operations
- **Audit/logging** for entity upsert/delete
