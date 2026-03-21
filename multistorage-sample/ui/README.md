# Multistorage Sample UI

Angular sample UI for **multistorage-sample** (uses **multistorage-starter** on the backend). Full API and architecture: repository root [README.md](../../README.md).

## Run

```bash
npm install
npm start
```

Default URL: `http://localhost:4200/`.

The dev server uses [proxy.conf.json](proxy.conf.json) and forwards `/api/**` to `http://localhost:8080`.

## API paths (aligned with backend)

Configuration mirrors `MultistorageWebProperties`:

- **`environment.apiTenantPrefix`** — same idea as `multistorage.web.api-tenant-prefix` (default `/api`). Used for tenant list and as the path prefix the sample resolves before the tenant segment (see backend `TenantPathFilter`).
- **`environment.apiPrefix`** — same pattern as `multistorage.web.api-prefix` (e.g. `/api/{tenantId}`). In requests, `{tenantId}` / `{tenantCode}` is replaced with the active tenant **code**.

The **starter** maps controllers under `api-prefix` only (no `/admin` segment):

| Area | HTTP | Path pattern (after resolving `{tenantId}`) |
|------|------|---------------------------------------------|
| Entity search | `POST` | `{resolved}/data/{entity}/search` |
| Entity upsert | `POST` | `{resolved}/data/{entity}` |
| Entity delete | `DELETE` | `{resolved}/data/{entity}/{id}` |
| Discovery | `GET` | `{resolved}/data/discovery` (optional `?table=...`) |
| Meta tables | `GET` / `POST` | `{resolved}/meta/tables` (and `/by-name/...`, `/by-alias/...`) |
| Meta columns | `GET` / `POST` | `{resolved}/meta/tables/{tableRef}/columns` |
| Meta relations | `GET` | `{resolved}/meta/tables/{tableRef}/relations` |
| Meta relation upsert | `POST` | `{resolved}/meta/relations` |

`GET {apiTenantPrefix}/tenants` is implemented by **multistorage-sample** only (`TenantController`), not the starter JAR. Path-based tenant context (`search_path`, schema per tenant) is also sample-specific.

Discovery omits columns with `readable = false` and returns `searchable` and `editable` for each visible column (see backend `MetaDiscoveryService`).

## Routes

- `/` — tenant picker (`GET /api/tenants`)
- `/:tenantCode/browser/:activeEntity` — generic entity browser (calls entity API under `{resolved}/data/...`)
- `/:tenantCode/browser/:activeEntity/:id` — entity detail
- `/:tenantCode/admin` — metadata admin for that tenant

## What the browser page does

- Loads metadata from the discovery endpoint.
- Builds dynamic table columns from metadata.
- Filter row by type:
  - integer → exact match (`EQ`)
  - date/time → range (`GT` + `LT`)
  - text → `LIKE`
- Per-column sort in header (`none` → `ASC` → `DESC` → `none`).
- Multiselect (top 5): backend search over searchable string fields.
- Request/response debug panels (shared state from the search service).

## What the admin page does

- One page with three editable grids: **MetaTable**, **MetaColumn**, **MetaRelation**.
- A **floating Save** button appears when any row in any grid has unsaved edits (same idea as entity detail). **Save** sends parallel upserts: all dirty tables, all dirty columns, and all dirty relations (separate HTTP calls per changed row; grouped by entity type).
- Input control per field type: boolean → checkbox, datetime → `datetime-local`, number → numeric input, text → text input.
- Upsert uses **`id`** when present → update; without **`id`** → create / match by business key (backend semantics; sending **`id`** avoids accidental inserts on renames).

## Build

```bash
npm run build
```

## Tests

```bash
npm test
```
