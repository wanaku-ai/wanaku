# Tasks: Service Catalog

**Input**: Design documents from `/specs/001-service-catalog/`
**Prerequisites**: plan.md, spec.md, data-model.md, contracts/service-catalog-api.yaml, research.md, quickstart.md

**Tests**: Included — plan.md specifies JUnit 5 + Mockito (backend) and Vitest (frontend) test coverage.

**Organization**: Tasks grouped by user story. US3 (Edit) is out of scope per design decision #6 in research.md (UI is list/search/delete only; creation is via CLI).

**Multi-repo note**: This feature spans three repositories:
- **wanaku** (main) — backend API, CLI, UI, core utilities
- **wanaku-capabilities-java-sdk** — DataStore model changes, downloader
- **camel-integration-capability** — CamelToolMain CLI option

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US4, US5)
- Exact file paths included in descriptions

---

## Phase 1: Setup

**Purpose**: OpenAPI contract and project scaffolding

- [x] T001 Add service catalog endpoints to OpenAPI spec in wanaku-router/wanaku-router-backend/src/main/webui/openapi.yaml per contracts/service-catalog-api.yaml

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core model changes, parser, service interface, and backend API that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### SDK Repository (wanaku-capabilities-java-sdk)

- [ ] T002 [P] Create FileType enum (ROUTES, RULES, DEPENDENCIES, CATALOG, OTHER) in capabilities-api/src/main/java/ai/wanaku/capabilities/sdk/api/types/FileType.java
- [ ] T003 Add optional fileType field (getter/setter, default null) to DataStore entity in capabilities-api/src/main/java/ai/wanaku/capabilities/sdk/api/types/DataStore.java

### Main Repository (wanaku)

- [x] T004 [P] Create ServiceCatalogIndex parser — reads index.properties from ZIP InputStream, validates required properties (catalog.name, catalog.services, route/rule entries), validates referenced files exist in ZIP, exposes typed accessors, protects against ZIP path traversal — in core/core-services-api/src/main/java/ai/wanaku/core/services/api/ServiceCatalogIndex.java
- [x] T005 [P] Create ServiceCatalogService interface with list, get, deploy, remove methods in core/core-services-api/src/main/java/ai/wanaku/core/services/api/ServiceCatalogService.java
- [x] T006 Implement ServiceCatalogBean — delegates to DataStoreRepository with fileType=CATALOG filtering, uses ServiceCatalogIndex to parse ZIP metadata for list/get, validates ZIP on deploy — in wanaku-router/wanaku-router-backend/src/main/java/ai/wanaku/backend/api/v1/servicecatalog/ServiceCatalogBean.java
- [x] T007 Implement ServiceCatalogResource REST endpoints (GET /list, GET /get, POST /deploy, DELETE /remove) in wanaku-router/wanaku-router-backend/src/main/java/ai/wanaku/backend/api/v1/servicecatalog/ServiceCatalogResource.java

**Checkpoint**: Foundation ready — backend API operational, user story implementation can begin

---

## Phase 3: User Story 1 — View Service Catalog (Priority: P1) 🎯 MVP

**Goal**: Administrators can navigate to the service catalog page and see a list of all deployed services with name, icon, description, and system count. Includes search filtering and empty state.

**Independent Test**: Navigate to the service catalog page, verify configured services display with correct metadata. Verify search filters results. Verify empty state appears when no services exist.

### Implementation for User Story 1

- [x] T008 [P] [US1] Add SERVICE_CATALOG entry to Links enum in wanaku-router/ui/admin/src/router/links.models.ts
- [x] T009 [P] [US1] Create use-service-catalog API hook (listServiceCatalogs with search param, getServiceCatalog, deployServiceCatalog, removeServiceCatalog) in wanaku-router/ui/admin/src/hooks/api/use-service-catalog.ts
- [x] T010 [US1] Create ServiceCatalogTable component — Carbon DataTable with columns: name, icon, description, system count; search input for filtering — in wanaku-router/ui/admin/src/Pages/ServiceCatalog/ServiceCatalogTable.tsx
- [x] T011 [US1] Create ServiceCatalogPage — state management, loading indicators, error toasts, empty state prompt — in wanaku-router/ui/admin/src/Pages/ServiceCatalog/ServiceCatalogPage.tsx
- [x] T012 [P] [US1] Create barrel export in wanaku-router/ui/admin/src/Pages/ServiceCatalog/index.ts
- [x] T013 [US1] Add lazy-loaded route for ServiceCatalogPage in wanaku-router/ui/admin/src/router.tsx
- [x] T014 [US1] Add Service Catalog navigation entry in wanaku-router/ui/admin/src/components/SideNav.tsx

**Checkpoint**: Service catalog page is visible in navigation, lists all deployed services with search. MVP is functional.

---

## Phase 4: User Story 2 — Create a New Service via CLI (Priority: P2)

**Goal**: Administrators can scaffold, configure, and deploy a service catalog package using CLI commands (`wanaku service init`, `expose`, `deploy`).

**Independent Test**: Run `wanaku service init --name=test --services=sys1`, verify directory structure is created with index.properties and skeleton files. Run `wanaku service expose --path=.`, verify rules are generated from route YAML. Run `wanaku service deploy --path=.`, verify ZIP is uploaded to data store with fileType=CATALOG and appears in the catalog list API.

### Implementation for User Story 2

- [x] T015 [US2] Create Service parent command (Picocli @Command, extends BaseCommand pattern) in cli/src/main/java/ai/wanaku/cli/main/commands/service/Service.java
- [x] T016 [US2] Implement ServiceInit subcommand — --name and --services options, creates directory structure with index.properties, skeleton route/rules/dependencies files per system — in cli/src/main/java/ai/wanaku/cli/main/commands/service/ServiceInit.java
- [x] T017 [US2] Implement ServiceExpose subcommand — --path and optional --namespace options, reads index.properties, parses Camel route YAML to extract route IDs, generates wanaku-rules.yaml exposing routes as MCP tools — in cli/src/main/java/ai/wanaku/cli/main/commands/service/ServiceExpose.java
- [x] T018 [US2] Implement ServiceDeploy subcommand — --path option, validates index.properties, creates ZIP archive with java.util.zip, Base64-encodes, uploads to /api/v1/service-catalog/deploy with fileType=CATALOG — in cli/src/main/java/ai/wanaku/cli/main/commands/service/ServiceDeploy.java

**Checkpoint**: Full CLI workflow operational: init → expose → deploy. Services appear in catalog list.

---

## Phase 5: User Story 4 — Delete a Service (Priority: P4)

**Goal**: Administrators can delete a service from the catalog via the UI with a confirmation prompt.

**Independent Test**: Select a service in the catalog table, click delete, confirm in the dialog, verify the service is removed from the list and a success notification is shown. Cancel deletion and verify service remains.

### Implementation for User Story 4

- [x] T019 [US4] Add delete action button to ServiceCatalogTable rows with confirmation modal (Carbon Modal) and success/error toast notifications — calls removeServiceCatalog from use-service-catalog hook — in wanaku-router/ui/admin/src/Pages/ServiceCatalog/ServiceCatalogTable.tsx

**Checkpoint**: Users can delete services from the UI. CRUD via CLI+UI is complete (Create via CLI, Read+Delete via UI).

---

## Phase 6: User Story 5 — View System Details Within a Service (Priority: P5)

**Goal**: Administrators can inspect system-level details within a service, including route file references, rules, and optional dependencies.

**Independent Test**: Navigate to service catalog, expand a service with multiple systems, verify each system shows its route file reference, rules file reference, optional dependencies file, and system description.

### Implementation for User Story 5

- [x] T020 [US5] Add expandable row detail to ServiceCatalogTable — fetch service detail via getServiceCatalog, display per-system information (name, routesFile, rulesFile, dependenciesFile) in Carbon ExpandableRow — in wanaku-router/ui/admin/src/Pages/ServiceCatalog/ServiceCatalogTable.tsx

**Checkpoint**: Full catalog visibility. Users can view service overview and drill into system details.

---

## Phase 7: SDK Downloader Update (wanaku-capabilities-java-sdk repo)

**Purpose**: Enable Camel capabilities to consume service catalog ZIP packages at runtime

- [ ] T021 [P] Add SERVICE_CATALOG value to ResourceType enum in capabilities-runtimes/capabilities-runtimes-camel/capabilities-runtimes-camel-common/src/main/java/ai/wanaku/capabilities/sdk/runtimes/camel/common/downloader/ResourceType.java
- [ ] T022 [P] Create ServiceCatalogDownloader — downloads ZIP from DataStore, extracts to data directory, reads index.properties, registers routes/rules/dependencies paths per system in downloadedResources map — in capabilities-runtimes/capabilities-runtimes-camel/capabilities-runtimes-camel-common/src/main/java/ai/wanaku/capabilities/sdk/runtimes/camel/common/downloader/ServiceCatalogDownloader.java
- [ ] T023 Update DownloaderFactory to dispatch SERVICE_CATALOG type to ServiceCatalogDownloader in capabilities-runtimes/capabilities-runtimes-camel/capabilities-runtimes-camel-common/src/main/java/ai/wanaku/capabilities/sdk/runtimes/camel/common/downloader/DownloaderFactory.java
- [ ] T024 Add addServiceCatalogRef method to ResourceListBuilder in capabilities-runtimes/capabilities-runtimes-camel/capabilities-runtimes-camel-common/src/main/java/ai/wanaku/capabilities/sdk/runtimes/camel/common/downloader/ResourceListBuilder.java

---

## Phase 8: Camel Integration Update (camel-integration-capability repo)

**Purpose**: Add --service-catalog CLI option as alternative to individual --routes-ref/--rules-ref/--dependencies

- [ ] T025 Add --service-catalog option to CamelToolMain — accepts datastore:// or file:// URIs, downloads/extracts ZIP, reads index, loads each system's routes/rules/dependencies, mutually exclusive with --routes-ref/--rules-ref — in camel-integration-capability-runtimes/camel-integration-capability-main/src/main/java/ai/wanaku/camel/integration/capability/CamelToolMain.java

---

## Phase 9: Tests

**Purpose**: Unit and integration tests for backend, CLI, and parser

- [x] T026 [P] Write ServiceCatalogIndex parser tests — valid index parsing, missing required properties, invalid ZIP, path traversal rejection, multi-system catalogs — in core/core-services-api/src/test/java/ai/wanaku/core/services/api/ServiceCatalogIndexTest.java
- [x] T027 [P] Write ServiceCatalogResource API tests — list (empty, populated, search), get (found, not found), deploy (valid, invalid ZIP), remove (found, not found) — in wanaku-router/wanaku-router-backend/src/test/java/ai/wanaku/backend/api/v1/servicecatalog/ServiceCatalogResourceTest.java
- [x] T028 [P] Write ServiceInitTest — verify directory structure creation, index.properties content, skeleton file generation — in cli/src/test/java/ai/wanaku/cli/main/commands/service/ServiceInitTest.java
- [x] T029 [P] Write ServiceExposeTest — verify rules YAML generation from route YAML, namespace support, error on missing routes — in cli/src/test/java/ai/wanaku/cli/main/commands/service/ServiceExposeTest.java
- [x] T030 [P] Write ServiceDeployTest — verify ZIP creation, Base64 encoding, API upload invocation, validation errors — in cli/src/test/java/ai/wanaku/cli/main/commands/service/ServiceDeployTest.java

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Security hardening, end-to-end validation, cleanup

- [x] T031 [P] Verify ZIP path traversal protection — ensure ServiceCatalogIndex rejects entries with `..` or absolute paths
- [x] T032 [P] Validate error messages are actionable and consistent with existing UI patterns (toasts, CLI output via WanakuPrinter)
- [ ] T033 Run quickstart.md end-to-end validation: init → expose → deploy → view in UI → delete

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup; SDK changes (T002-T003) must precede main repo backend (T006-T007); **BLOCKS all user stories**
- **US1 (Phase 3)**: Depends on Foundational (Phase 2) — specifically T007 (backend API)
- **US2 (Phase 4)**: Depends on Foundational (Phase 2) — specifically T007 (deploy endpoint)
- **US4 (Phase 5)**: Depends on US1 (Phase 3) — extends table component with delete
- **US5 (Phase 6)**: Depends on US1 (Phase 3) — extends table component with detail view
- **SDK Downloader (Phase 7)**: Depends on T002-T003 (FileType/DataStore); independent of UI/CLI stories
- **Camel Integration (Phase 8)**: Depends on SDK Downloader (Phase 7)
- **Tests (Phase 9)**: Depends on corresponding implementation phases
- **Polish (Phase 10)**: Depends on all prior phases

### User Story Dependencies

- **US1 (P1)**: Depends on Foundational only — no cross-story dependencies
- **US2 (P2)**: Depends on Foundational only — can run in parallel with US1
- **US4 (P4)**: Depends on US1 (extends the table UI)
- **US5 (P5)**: Depends on US1 (extends the table UI)

### Within Each User Story

- Models/interfaces before services
- Services before REST endpoints
- API hooks before UI components
- Table before Page
- Page before route/navigation

### Parallel Opportunities

- T002, T004, T005 can all run in parallel (different files, different repos)
- T008, T009, T012 can run in parallel within US1 (different files)
- T015-T018: T016, T017 can run in parallel after T015 (independent subcommands)
- T021, T022 can run in parallel (different files in SDK repo)
- T026-T030 can all run in parallel (independent test files)
- **US1 and US2 can run in parallel** after Foundational phase completes
- **SDK Downloader (Phase 7) can run in parallel** with US1/US2/US4/US5

---

## Parallel Example: User Story 1

```bash
# After Foundational completes, launch these in parallel:
Task: "Add SERVICE_CATALOG entry to Links enum in wanaku-router/ui/admin/src/router/links.models.ts"
Task: "Create use-service-catalog API hook in wanaku-router/ui/admin/src/hooks/api/use-service-catalog.ts"
Task: "Create barrel export in wanaku-router/ui/admin/src/Pages/ServiceCatalog/index.ts"

# Then sequentially:
Task: "Create ServiceCatalogTable component" (depends on hook)
Task: "Create ServiceCatalogPage" (depends on table)
Task: "Add route in router.tsx" (depends on page + links enum)
Task: "Add SideNav entry" (depends on links enum)
```

## Parallel Example: Cross-Story

```bash
# After Foundational completes, these story phases can run in parallel:
# Developer A: US1 (View — UI)
# Developer B: US2 (Create — CLI)
# Developer C: SDK Downloader (Phase 7)

# Then sequentially after US1:
# US4 (Delete) and US5 (View Details) — both extend the same table component
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002-T007)
3. Complete Phase 3: US1 View (T008-T014)
4. **STOP and VALIDATE**: Navigate to service catalog page, verify list/search/empty state
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Backend API operational
2. US1 (View) → Catalog page visible, searchable → **MVP!**
3. US2 (Create via CLI) → Full init/expose/deploy workflow
4. US4 (Delete) → Cleanup capability from UI
5. US5 (View Details) → System-level inspection
6. SDK Downloader + Camel Integration → Runtime consumption
7. Tests + Polish → Production-ready

### Parallel Team Strategy

With multiple developers:
1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (UI view) → then US4 + US5
   - Developer B: US2 (CLI commands) → then Tests
   - Developer C: SDK Downloader → then Camel Integration
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story
- US3 (Edit) is **out of scope** per research.md decision #6 — can be added as future enhancement
- SDK and Camel integration repos require separate branches/PRs
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
