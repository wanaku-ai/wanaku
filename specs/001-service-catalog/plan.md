# Implementation Plan: Service Catalog

**Branch**: `001-service-catalog` | **Date**: 2026-02-23 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-service-catalog/spec.md`

## Summary

Add a service catalog system that packages Camel routes, Wanaku rules, and dependencies into deployable ZIP archives stored in the data store. Requires: DataStore entity modification (file type enum), new CLI commands (`wanaku service init/expose/deploy`), a backend API for catalog operations, an admin UI page (list/search/delete), and updates to the Camel Integration Capability SDK to consume catalog packages.

## Technical Context

**Language/Version**: Java 21 (Quarkus), TypeScript 5.7 (React 19)
**Primary Dependencies**: Quarkus, Picocli, Carbon React, Orval, java.util.zip
**Storage**: Infinispan (via existing DataStore)
**Testing**: JUnit 5 + Mockito (backend), Vitest (frontend)
**Target Platform**: Linux/macOS/Windows (JVM + native)
**Project Type**: web (backend + frontend + CLI + SDK)
**Performance Goals**: Catalog list/search within existing page load envelope
**Constraints**: Backward compatible with existing DataStore entries and Camel capability CLI options
**Scale/Scope**: Dozens to low hundreds of catalog entries

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality First | PASS | Follows existing patterns; no new dependencies beyond `java.util.zip` (JDK built-in) |
| II. Testing Standards | PASS | Plan includes unit tests for catalog parsing, CLI commands, API endpoints, and UI components |
| III. User Experience Consistency | PASS | CLI follows `wanaku <noun> <verb>` pattern; UI follows Carbon DataTable pattern; error messages are actionable |
| IV. Performance Requirements | PASS | ZIP parsing is local; no new network hops; DataStore queries use existing label filtering |
| V. Security by Default | PASS | ZIP contents validated before extraction (path traversal protection); no secrets in catalog files; existing auth applies to API |

**Post-Phase 1 Re-check**: All gates still pass. The design adds no new external dependencies, follows existing patterns, and maintains backward compatibility.

## Project Structure

### Documentation (this feature)

```text
specs/001-service-catalog/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── service-catalog-api.yaml
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
# Wanaku main repository
core/
└── core-services-api/
    └── src/main/java/ai/wanaku/core/services/api/
        └── ServiceCatalogService.java          # New service interface

wanaku-router/
├── wanaku-router-backend/
│   └── src/main/java/ai/wanaku/backend/api/v1/
│       └── servicecatalog/
│           ├── ServiceCatalogResource.java     # REST endpoints
│           └── ServiceCatalogBean.java         # Business logic
│   └── src/main/webui/openapi.yaml             # Updated OpenAPI spec
│   └── src/test/java/.../servicecatalog/
│       └── ServiceCatalogResourceTest.java     # API tests
└── ui/admin/src/
    ├── Pages/ServiceCatalog/
    │   ├── ServiceCatalogPage.tsx               # Main page
    │   ├── ServiceCatalogTable.tsx              # DataTable
    │   └── index.ts                            # Router export
    ├── hooks/api/
    │   └── use-service-catalog.ts              # API hook
    ├── router.tsx                              # Updated routes
    ├── router/links.models.ts                  # Updated links enum
    └── components/SideNav.tsx                  # Updated navigation

cli/
└── src/main/java/ai/wanaku/cli/main/commands/
    └── service/
        ├── Service.java                        # Parent command
        ├── ServiceInit.java                    # Init subcommand
        ├── ServiceExpose.java                  # Expose subcommand
        └── ServiceDeploy.java                  # Deploy subcommand
    └── src/test/java/.../service/
        ├── ServiceInitTest.java
        ├── ServiceExposeTest.java
        └── ServiceDeployTest.java

# Wanaku Capabilities Java SDK (separate repo)
capabilities-api/
└── src/main/java/ai/wanaku/capabilities/sdk/api/types/
    ├── DataStore.java                          # Modified: add fileType field
    └── FileType.java                           # New enum

capabilities-runtimes/capabilities-runtimes-camel/
└── capabilities-runtimes-camel-common/src/main/java/.../downloader/
    ├── ResourceType.java                       # Modified: add SERVICE_CATALOG
    ├── ServiceCatalogDownloader.java           # New: extracts ZIP, loads index
    ├── DownloaderFactory.java                  # Modified: handle catalog type
    └── ResourceListBuilder.java                # Modified: add catalog ref

# Camel Integration Capability (separate repo)
camel-integration-capability-runtimes/
└── camel-integration-capability-main/src/main/java/.../
    └── CamelToolMain.java                      # Modified: add --service-catalog option
```

**Structure Decision**: Multi-repo web application. Changes span the main Wanaku repo (backend API, CLI, UI), the capabilities SDK repo (DataStore model, downloader), and the Camel Integration Capability repo (CLI option).

## Design Details

### Component 1: DataStore FileType Enum

**Repo**: wanaku-capabilities-java-sdk

Add `FileType` enum to `capabilities-api`:
```java
public enum FileType {
    ROUTES, RULES, DEPENDENCIES, CATALOG, OTHER
}
```

Modify `DataStore.java`: add optional `fileType` field with getter/setter. No migration needed — existing entries have `fileType=null`.

### Component 2: Service Catalog Index Parser

**Repo**: wanaku main (new utility class)

Create `ServiceCatalogIndex` class that:
- Parses `index.properties` from a ZIP `InputStream`
- Validates required properties (`catalog.name`, `catalog.services`, route/rules entries)
- Validates referenced files exist in the ZIP
- Exposes typed accessors for catalog metadata and system definitions
- Protects against ZIP path traversal attacks

### Component 3: Backend API

**Repo**: wanaku main

New REST resource at `/api/v1/service-catalog` with endpoints:
- `GET /list?search=` — list all catalogs (DataStore entries with `fileType=CATALOG`), parse each ZIP's index for metadata
- `GET /get?name=` — get catalog detail by name with parsed system info
- `POST /deploy` — validate and store ZIP
- `DELETE /remove?name=` — delete catalog entry

Business logic in `ServiceCatalogBean` delegates to `DataStoreRepository` with label/fileType filtering.

### Component 4: CLI Commands

**Repo**: wanaku main

`wanaku service init --name=X --services=A,B`:
- Creates directory structure with skeleton files
- Generates `index.properties` with all required entries

`wanaku service expose --path=. [--namespace=ns]`:
- Reads `index.properties`
- For each system, reads the Camel route YAML
- Extracts route IDs from YAML
- Generates/updates the corresponding `wanaku-rules.yaml` exposing each route as an MCP tool
- If `--namespace` provided, sets namespace in generated rules

`wanaku service deploy --path=.`:
- Reads and validates `index.properties`
- Creates ZIP archive from directory contents
- Base64-encodes the ZIP
- Uploads to data store via REST API with `fileType=CATALOG`

### Component 5: Admin UI Page

**Repo**: wanaku main

Service Catalog page with:
- Carbon DataTable listing services (name, icon, description, system count)
- Search input filtering by name/description
- Delete action with confirmation dialog
- Empty state prompt
- Loading indicators and error toasts
- No create/edit — services are managed via CLI

### Component 6: SDK Downloader Update

**Repo**: wanaku-capabilities-java-sdk

Add `ServiceCatalogDownloader` that:
- Downloads ZIP from DataStore
- Extracts to data directory
- Reads `index.properties`
- For each system, registers routes/rules/dependencies paths in `downloadedResources` map
- Handles multi-system catalogs (multiple route/rule pairs)

Update `ResourceType` enum with `SERVICE_CATALOG` value.
Update `ResourceListBuilder` with `addServiceCatalogRef()`.
Update `DownloaderFactory` to handle catalog type.

### Component 7: Camel Integration Capability Update

**Repo**: camel-integration-capability

Add `--service-catalog` option to `CamelToolMain`:
- Accepts `datastore://name.service.zip` or `file:///path/to/name.service.zip`
- Downloads/extracts ZIP
- Reads index, loads each system's routes and rules
- Loads dependencies if present
- Mutually exclusive with `--routes-ref`/`--rules-ref` (one mode or the other)

## Complexity Tracking

No constitution violations. All components follow existing patterns.
