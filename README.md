# Multistorage

Spring Boot starter that turns any application with a PostgreSQL database into a **dynamic entity API** driven by metadata stored in DB (`meta_table`, `meta_column`, `meta_relation`).

**Why:** get an API on top of an existing schema without writing controllers/DTOs for every table, keep a single JSON contract for frontends/integrations, and customize behavior via validators and pre/post processors.

---

## Current features

- **Configurable HTTP paths** — `MultistorageWebProperties`: `multistorage.web.api-tenant-prefix` (default `/api`) and `multistorage.web.api-prefix` (default `/api/{tenantId}`). The `{tenantId}` segment is a Spring MVC path variable; its meaning is application-defined (the sample uses tenant **code**). The starter registers controllers only under `api-prefix`:
  - **Entity API**: `POST/DELETE {api-prefix}/data/{entity}/...` (see below).
  - **Discovery**: `GET {api-prefix}/data/discovery` (optional `?table=...`).
  - **Metadata admin**: `{api-prefix}/meta/...` (tables, columns, relations — no `/admin` segment).
  Path-based tenant routing (`search_path`, schema per tenant) lives in **multistorage-sample** (`TenantPathFilter`, `TenantContext`). `GET {api-tenant-prefix}/tenants` is a **sample-only** endpoint for the UI, not part of the starter JAR.
- **Dynamic entity endpoints** — For each business table described by metadata:
  - `POST {api-prefix}/data/{entity}/search`
  - `POST {api-prefix}/data/{entity}` (upsert)
  - `DELETE {api-prefix}/data/{entity}/{id}`
- **Request pipeline** — all operations go through:
  - receive request → validate request → preProcess handlers → operation → mapping to response → postProcess handlers
- **Validation** — one validator per operation type:
  - **Search**: checks that all referenced fields/relations exist in metadata
    - `select` is allowed only for columns with `readable = true`
    - `where` is allowed only for columns with `searchable = true`
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
- **Discovery** — `GET {api-prefix}/data/discovery?table=...` returns metadata for the client (tables, columns, relations).
  - Discovery excludes columns with `readable = false`
  - Discovery returns `searchable` (and `editable`) for each visible column
- **Metadata Admin API** — CRUD under `{api-prefix}/meta/...` (e.g. `/meta/tables`, `/meta/tables/{tableRef}/columns`, list relations at `/meta/tables/{tableRef}/relations`, upsert relation at `POST .../meta/relations`). In the sample, meta rows live in the active tenant PostgreSQL schema (`search_path` / `TenantContext`).
  - Admin DTOs now include `id`
  - Upsert semantics: if `id` is provided -> update, otherwise -> create
- **Metadata customization** — `MetaCustomizer` bean can modify `QueryMeta` before compilation.
- **OpenAPI** — paths follow the same `api-prefix` pattern; operations are generated per entity and include request/response examples.

**Stack:** Spring Boot 4, WebMvc, Spring Data JPA, jOOQ, Liquibase, PostgreSQL, springdoc-openapi.

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

You also need: `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `liquibase-core`, `postgresql`, and optionally `springdoc-openapi-starter-webmvc-ui` (the starter already pulls jOOQ and springdoc).

### 2) Run and open Swagger

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

### 3) Seed metadata

**Production:** treat metadata like any other schema contract — **load `meta_table`, `meta_column`, and `meta_relation` only from versioned migrations** (Liquibase or equivalent), review changes in PRs, and **avoid ad-hoc edits**. In practice: **do not expose** `{api-prefix}/meta/**` in production (remove routes, disable controllers, or gate them behind strict auth and operational process), and **forbid casual manual changes** to meta rows in the database outside controlled releases.

**Development and demos** can use additional options:
- **Liquibase** — same approach as production; preferred whenever you want parity with what ships.
- **Admin API** — handy for local experiments and the sample UI; create `meta_table`, then `meta_column`, then `meta_relation`.
- **Programmatic DB scan** (example in `multistorage-sample` `AppInitializer`): `DatabaseMetadataManagerService.scanDatabase()`; the starter’s `MetaSyncService` bean runs `syncFromScan` after each scan and upserts meta from JDBC metadata (exploratory / bootstrap only — not a replacement for reviewed migrations in prod).

Admin API base path (when enabled): `{api-prefix}/meta` — tables at `/meta/tables`, columns at `/meta/tables/{tableRef}/columns`, relations listed at `/meta/tables/{tableRef}/relations`, relation upsert at `POST /meta/relations`.

### 4) Call entity endpoints

Search:

```bash
curl -X POST "http://localhost:8080/api/demo/data/<entity>/search" \
  -H "Content-Type: application/json" \
  -d '{"select":[["*"]],"where":{"logician":"AND","criteria":[]}}'
```

Upsert:

```bash
curl -X POST "http://localhost:8080/api/demo/data/<entity>" \
  -H "Content-Type: application/json" \
  -d '{"name":"example"}'
```

Delete:

```bash
curl -X DELETE "http://localhost:8080/api/demo/data/<entity>/1"
```

---

## API

### Search

`POST {api-prefix}/data/{entity}/search`

Body (`Query`):
- **select** — list of paths (`["*"]`, `["relation", "*"]`, `["a","b","field"]`)
- **where** — criteria tree (`logician`, `criteria[]`, leaf criterion with `field[]`, `operator`, `value`)
- **sort** — object with `{ "by": "<column name|alias>", "order": "ASC|DESC" }`

Example:

```json
{
  "select": [["*"], ["childToParent", "*"]],
  "sort": { "by": "name", "order": "ASC" },
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

`POST {api-prefix}/data/{entity}`

Body: JSON object (entity). Keys can be column **name** or **alias**. Relation keys are relation aliases from `meta_relation.alias`.

Nested objects/arrays are supported when cascade allows it.

Response: saved entity in nested form (tree). Internally the starter persists and then refetches the entity by id and applies the nesting transformer, so API always returns tree-structured data.

### Delete

`DELETE {api-prefix}/data/{entity}/{id}`

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

### Identity in admin upsert

Admin upsert endpoints (`MetaTableController`, `MetaColumnController`, `MetaRelationController`) support identity by `id`:
- if request contains `id` -> existing row is updated by `id`
- if request does not contain `id` -> row is created (or upserted by legacy business key where applicable)

This is important for rename scenarios (for example relation alias rename): send `id` to avoid accidental insert.

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
- **Security**: exposing a dynamic entity API is powerful. In real apps you typically want auth/ACL, restrict meta via `MetaCustomizer` / validators, and **omit or strictly protect `{api-prefix}/meta/**` in production** (same guidance as in **Seed metadata** under Quickstart).
- **Aliases in API**: clients receive fields using **aliases**, not raw DB names. For best readability, keep DB naming `snake_case` (e.g. `child_meta`, `created_at`) and use API aliases in `PascalCase` for tables (`ChildMeta`) and `camelCase` for columns (`createdAt`).
  This is fully configurable via metadata: `meta_table.alias`, `meta_column.alias`, `meta_relation.alias`.

---

## Database and metadata

- Configure PostgreSQL and Liquibase in your application. The **sample** sets `spring.liquibase.enabled: false` and runs changelogs per schema via `MultitenantLiquibaseInitializer` (public + starter meta + tenant business); a typical consumer app can use standard Spring Liquibase instead.
- The starter ships changelog `db/changelog/multistorage-meta-master.yaml`, which includes changesets under `db/changelog/meta/` and creates:
  - `meta_table`, `meta_column`, `meta_relation`
  - including `meta_relation.cascade_type` and column `editable` for nested upsert / UI behavior
- Register your business tables/columns/relations in metadata (Admin API, `MetaSyncService` after scan, or your own seeds).

### Auto-configuration

The starter provides `MultistorageAutoConfiguration` (component scan for `meta`, `pipeline`, `data`, `openapi`, `config`; JPA entity scan and meta repositories; `MultistorageScanProperties` and `MultistorageWebProperties`).

### Metadata customization

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
- **Authorization hooks** for tables/columns/operations
- **Audit/logging** for entity upsert/delete
