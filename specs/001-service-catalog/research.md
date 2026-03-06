# Research: Service Catalog

**Feature Branch**: `001-service-catalog`
**Date**: 2026-02-23

## Decision Log

### 1. Service Package Format

**Decision**: A service is a ZIP archive containing an `index.properties` manifest, one or more Camel route YAML files, matching Wanaku rules YAML files, and optional dependency files.

**Rationale**: Keeps all related artifacts together as a single deployable unit. The properties-based manifest is simple to parse and human-readable. ZIP is universally supported in Java via `java.util.zip`.

**Alternatives considered**:
- Tar/gzip: Slightly more complex to handle in Java; ZIP is natively supported
- JSON/YAML manifest: Properties format is simpler and already familiar in Java ecosystem
- OCI image: Over-engineered for configuration artifacts

### 2. Storage Backend

**Decision**: Store service catalog ZIP files in the existing Wanaku DataStore (Infinispan-backed, Base64-encoded).

**Rationale**: Reuses existing infrastructure. The DataStore already supports binary data via Base64 encoding, label-based filtering, and CRUD operations. Adding a file type enum to DataStore entries lets the system distinguish catalog entries from other stored data.

**Alternatives considered**:
- Dedicated database table: Adds persistence complexity without benefit
- Filesystem storage: Doesn't support distributed deployments
- S3/object store: Requires external dependency

### 3. DataStore File Type Enum

**Decision**: Add an optional `fileType` field to the DataStore entity with enum values: `RULES`, `ROUTES`, `DEPENDENCIES`, `CATALOG`, `OTHER`.

**Rationale**: Enables filtering data store entries by purpose. The `CATALOG` type identifies service catalog ZIP packages. Existing entries remain unaffected (field is optional, defaults to `OTHER` or null).

**Alternatives considered**:
- Label-based classification only: Less structured, harder to enforce consistency
- Separate data store per type: Over-engineering; labels + enum is sufficient

### 4. CLI Command Structure

**Decision**: Add `wanaku service` command group with subcommands: `init`, `expose`, `deploy`.

**Rationale**: Follows the established verb-noun pattern (`wanaku <noun> <verb>`). The three commands map to the service lifecycle: scaffold → configure rules → package & upload.

**Alternatives considered**:
- Single monolithic command: Doesn't support iterative development workflow
- Separate `wanaku catalog` command: "service" is the domain term used in the spec

### 5. Camel Integration Capability Update

**Decision**: Add a `--service-catalog` option to `CamelToolMain` as an alternative to individual `--routes-ref`, `--rules-ref`, and `--dependencies` options. The capability downloads the ZIP, extracts it, reads the index, and loads each service's routes/rules/dependencies.

**Rationale**: Simplifies deployment by reducing three+ separate file references to one. Backward compatible — existing individual references still work.

**Alternatives considered**:
- Replace individual references entirely: Breaks backward compatibility
- Proxy via router API: Adds complexity; direct datastore download is simpler

### 6. UI Catalog Page Scope

**Decision**: List, search, and delete operations only. No create/edit in the UI — services are created and deployed via the CLI.

**Rationale**: The user explicitly stated the page should list, search, and delete. Creation is a developer workflow involving local files and the CLI. The UI provides visibility and cleanup capability.

**Alternatives considered**:
- Full CRUD in UI: User explicitly scoped to list/search/delete
- Upload ZIP via UI: Could be added later as enhancement

## Existing Code Patterns

### CLI Pattern
- All commands extend `BaseCommand` (Picocli + Quarkus)
- Service interfaces used via `initService()` REST client builder
- Output via `WanakuPrinter` (styled terminal output)
- Reference: `cli/src/main/java/ai/wanaku/cli/main/commands/tools/Tools.java`

### DataStore Pattern
- Entity: `DataStore extends LabelsAwareEntity<String>` with `id`, `name`, `data` fields
- Repository: `DataStoreRepository extends LabelAwareInfinispanRepository`
- REST: `/api/v1/data-store` with add/update/list/get/remove endpoints
- Data is Base64-encoded for binary safety

### UI Pattern
- React 19 + TypeScript + Carbon Design System
- API hooks in `hooks/api/use-*.ts` wrapping Orval-generated clients
- Page structure: `*Page.tsx` (state+handlers), `*Table.tsx` (Carbon DataTable), `*Modal.tsx` (forms)
- Routes in `router.tsx` with lazy loading
- Navigation in `SideNav.tsx`

### Camel Capability Pattern
- `ResourceType` enum: `ROUTES_REF`, `RULES_REF`, `DEPENDENCY_REF`
- `ResourceListBuilder` constructs download list
- `DownloaderFactory` dispatches to `FileDownloader` or `DataStoreDownloader`
- `ResourceDownloaderCallback` orchestrates downloads on registration
