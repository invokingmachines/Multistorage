# Multistorage Sample UI

Angular sample UI for `multistorage-starter`.

## Run

```bash
npm install
npm start
```

Default URL: `http://localhost:4200/`.

The dev server uses proxy config and forwards `/api/**` to `http://localhost:8080`.

API paths: `environment.apiTenantPrefix` (e.g. `/api` for `/api/tenants`) and `environment.apiPrefix` (same as `multistorage.web.api-prefix`, e.g. `/api/{tenantId}`). Tenant-scoped calls use `{apiPrefix}` with `{tenantId}` replaced: meta CRUD under `{resolved}/meta/...`, discovery GET `{resolved}/data/discovery`, entity API `{resolved}/data/{entity}/...`. Placeholders `{tenantId}` / `{tenantCode}` are replaced with the active tenant code.

## Routes

- `/` — tenant picker (`GET /api/tenants`)
- `/:tenantCode/browser/:activeEntity` — generic entity browser (entity API under `{apiPrefix}/data/...` after substitution)
- `/:tenantCode/browser/:activeEntity/:id` — entity detail
- `/:tenantCode/admin` — metadata admin page for that tenant

## What browser page does

- Loads entity metadata from discovery endpoint.
- Builds dynamic table columns from metadata.
- Supports filter row by type:
  - integer -> exact match (`EQ`)
  - date/time -> range (`GT` + `LT`)
  - text -> `LIKE`
- Supports per-column sort toggle in header (`none -> ASC -> DESC -> none`).
- Contains multiselect component (top 5 backend search over all searchable string fields).
- Shows request/response debug panels (single shared state from search service).

## What admin page does

- Single page with 3 editable tables:
  - `MetaTable`
  - `MetaColumn`
  - `MetaRelation`
- Save button is highlighted when row has unsaved changes.
- Input type depends on field type:
  - boolean -> checkbox
  - datetime -> datetime picker
  - number -> numeric input
  - text -> text input
- Saves via admin meta controllers.
- Uses id-based upsert semantics:
  - if `id` is present -> update
  - if `id` is missing -> create

## Build

```bash
npm run build
```

## Tests

```bash
npm test
```
