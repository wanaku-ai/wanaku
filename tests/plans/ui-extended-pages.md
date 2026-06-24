# Test Plan: Extended UI Page Tests

## Overview

This test plan describes Playwright e2e tests for Wanaku admin UI pages that are not covered by the basic UI test suite. It targets: Forwards, Data Stores, Namespaces, Service Catalog, Targets (Capabilities), Tool Call Debugger, and cross-page Navigation.

The basic UI tests (`basic-ui-test.md`) cover Dashboard, Tools, Resources, and Prompts. This plan extends coverage to the remaining pages.

Every scenario is described in Playwright terms (navigate, click, fill, assert) and is fully automatable.

## Prerequisites

### Required tools

| Tool | Minimum version | Verify command |
|------|-----------------|----------------|
| `node` | 18+ | `node --version` |
| `yarn` | 1.22+ | `yarn --version` |
| `npx` | any | `npx --version` |
| `curl` | any | `curl --version` |

### Prerequisite check

```bash
node --version || { echo "FAIL: node not found"; exit 1; }
yarn --version || { echo "FAIL: yarn not found"; exit 1; }
npx --version || { echo "FAIL: npx not found"; exit 1; }
curl --version > /dev/null || { echo "FAIL: curl not found"; exit 1; }
echo "PASS: all prerequisites met"
```

### Environment variables

```bash
export WANAKU_ROUTER_URL="${WANAKU_ROUTER_URL:-http://localhost:8080}"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `WANAKU_ROUTER_URL` | `http://localhost:8080` | Base URL of the Wanaku router (no trailing slash) |

### Remote instance requirements

The router at `WANAKU_ROUTER_URL` must:
- Be running and healthy (`/q/health/ready` returns 200)
- Have auth disabled (`WANAKU_HTTP_AUTH=none`) or the test user must be authenticated
- Serve the admin UI at `/admin/`

---

## Phase 0: Verify Router Connectivity

### Test 0.1: Router health check

```bash
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${WANAKU_ROUTER_URL}/q/health/ready")
if [ "${HTTP_STATUS}" -eq 200 ]; then
  echo "PASS: router healthy at ${WANAKU_ROUTER_URL}"
else
  echo "FAIL: router not reachable (HTTP ${HTTP_STATUS})"
  exit 1
fi
```

### Test 0.2: Install dependencies and Playwright browser

```bash
cd tests/e2e/ui
yarn install && npx playwright install chromium
if [ $? -eq 0 ]; then
  echo "PASS: dependencies and browser installed"
else
  echo "FAIL: installation failed"
  exit 1
fi
```

---

## Phase 1: Forwards Page

**Route:** `#/forwards`
**Page title:** "Forwards"
**Page description contains:** "A list of forwards registered in the system"

### Test 1.1: Forwards page loads with title and description

**Spec file:** `forwards.spec.ts`

1. Navigate to `#/forwards` via `BasePage.navigateTo('/forwards')`.
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` text is `"Forwards"`.
4. Assert `p.description` text contains `"forwards registered"`.
5. Assert the DataTable container is visible.

### Test 1.2: Empty state is displayed when no forwards exist

1. Navigate to `#/forwards`.
2. If no forwards exist, assert empty state text `"Start by adding forwards"` is visible in the table body.

### Test 1.3: Add a forward via modal

1. Navigate to `#/forwards`.
2. Click the `"Add Forward"` toolbar button.
3. Assert modal opens with heading `"Add a Forward"`.
4. Fill `#forward-name` with a unique name (e.g., `e2e-forward-<timestamp>`).
5. Fill `#forward-address` with `http://localhost:9090`.
6. Optionally select a namespace from the `NamespaceSelect` dropdown.
7. Assert primary button (`"Add"`) is enabled.
8. Click the primary button to submit.
9. Assert modal closes.
10. Assert a table row containing the forward name appears.

### Test 1.4: Edit a forward via modal

1. Seed a forward via API (`POST /api/v1/forwards`).
2. Navigate to `#/forwards`.
3. Locate the row with the seeded forward name.
4. Click the `"Edit"` icon button (ghost button with Edit icon) in that row.
5. Assert modal opens with heading `"Edit forward"`.
6. Assert `#forward-name` is pre-filled with the existing name.
7. Assert `#forward-address` is pre-filled with the existing address.
8. Change `#forward-address` to `http://localhost:9999`.
9. Click the `"Save"` primary button.
10. Assert modal closes.
11. Assert the row now shows the updated address.

### Test 1.5: Delete a forward

1. Seed a forward via API.
2. Navigate to `#/forwards`.
3. Locate the row with the seeded forward name.
4. Click the `"Delete"` icon button (ghost button with TrashCan icon) in that row.
5. Assert the row with that forward name disappears from the table.

### Test 1.6: Refresh a forward

1. Seed a forward via API.
2. Navigate to `#/forwards`.
3. Locate the row with the seeded forward name.
4. Click the `"Refresh"` icon button (ghost button with Renew icon) in that row.
5. Assert no error notification appears.
6. Assert the row is still present.

### Test 1.7: Primary button is disabled when required fields are empty

1. Navigate to `#/forwards`.
2. Click `"Add Forward"`.
3. Assert the primary button (`"Add"`) is disabled.
4. Fill only `#forward-name` -- assert primary button is still disabled.
5. Clear `#forward-name`, fill only `#forward-address` -- assert primary button is still disabled.
6. Fill both fields -- assert primary button is enabled.
7. Cancel the modal.

**Cleanup:** Each test must delete any forwards it created, either via `afterEach` API cleanup or inline deletion.

---

## Phase 2: Data Stores Page

**Route:** `#/data-stores`
**Page title:** "Data Stores"
**Page description contains:** "Manage stored data files"

### Test 2.1: Data Stores page loads with title and description

**Spec file:** `data-stores.spec.ts`

1. Navigate to `#/data-stores`.
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` text is `"Data Stores"`.
4. Assert `p.description` text contains `"Manage stored data files"`.

### Test 2.2: Empty state message when no data stores exist

1. Navigate to `#/data-stores`.
2. If no data stores exist, assert the table body shows `"No data stores found"`.

### Test 2.3: Add a data store via file upload modal

1. Navigate to `#/data-stores`.
2. Click the `"Add Data Store"` toolbar button.
3. Assert modal opens with heading `"Add Data Store"`.
4. Assert `#datastore-name` input is present with placeholder `"e.g. config.yaml"`.
5. Assert the file input (`#file-input`) is present.
6. Use Playwright's `setInputFiles` on the file input to upload a small test file (e.g., a text file with content `"e2e-test-data"`).
7. Assert the `"Selected: ..."` indicator appears showing the file name and size.
8. Assert the name field auto-fills with the file name (if it was empty).
9. Click `"Upload"` primary button.
10. Assert modal closes.
11. Assert a table row appears with the data store name.
12. Assert the row shows columns: ID, Name, Data (Base64 truncated).

### Test 2.4: View a data store via modal

1. Seed a data store via API.
2. Navigate to `#/data-stores`.
3. Click the `"View"` icon button for the seeded data store.
4. Assert a passive modal opens with heading containing `"View Data Store:"` and the data store name.
5. Assert the modal displays `ID`, `Name`, and `Contents` sections.
6. Assert the decoded content is visible in a `<pre>` block.
7. Close the modal.

### Test 2.5: Delete a data store

1. Seed a data store via API.
2. Navigate to `#/data-stores`.
3. Click the `"Delete"` icon button for the seeded data store.
4. Assert the row disappears from the table.

### Test 2.6: Submit without selecting a file shows error

1. Navigate to `#/data-stores`.
2. Click `"Add Data Store"`.
3. Click `"Upload"` without selecting a file.
4. Assert the error text `"Please select a file to upload"` appears inside the modal.
5. Cancel the modal.

**Cleanup:** Each test must delete any data stores it created via API in `afterEach`.

---

## Phase 3: Namespaces Page

**Route:** `#/namespaces`
**Page title:** "Namespaces"
**Page description contains:** "Namespaces help organize and isolate tools and resources"

### Test 3.1: Namespaces page loads with title and description

**Spec file:** `namespaces.spec.ts`

1. Navigate to `#/namespaces`.
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` text is `"Namespaces"`.
4. Assert `p.description` text contains `"Namespaces help organize"`.

### Test 3.2: Default namespaces are present

1. Navigate to `#/namespaces`.
2. Assert the table has columns: ID, Name, Path, Status, Actions.
3. Assert at least one row with path `"default"` exists.
4. Assert rows with protected paths (`"default"`, `"public"`, `"wanaku-internal"`) have disabled Edit and Delete buttons.

### Test 3.3: Create a namespace via modal

1. Navigate to `#/namespaces`.
2. Click the `"Create Namespace"` toolbar button.
3. Assert modal opens with heading `"Create Namespace"`.
4. Fill `#namespace-name` with a unique name (e.g., `e2e-ns-<timestamp>`).
5. Fill `#namespace-path` with a unique path (e.g., `ns-e2e-<timestamp>`).
6. Assert primary button (`"Create"`) is enabled.
7. Click `"Create"`.
8. Assert modal closes.
9. Assert a table row with the namespace name and path appears.
10. Assert the status column shows `"Allocated"` for the new namespace.

### Test 3.4: Edit a namespace via modal

1. Create a namespace via API or via the UI (from Test 3.3).
2. Navigate to `#/namespaces`.
3. Click the `"Edit"` icon button for the created namespace.
4. Assert modal opens with heading `"Edit Namespace"`.
5. Assert `#namespace-path` is disabled (path cannot be changed after creation).
6. Change `#namespace-name` to a new value.
7. Click `"Save"`.
8. Assert modal closes.
9. Assert the row now shows the updated name.

### Test 3.5: Delete a namespace

1. Create a namespace (non-protected).
2. Navigate to `#/namespaces`.
3. Click the `"Delete"` icon button for the created namespace.
4. Assert the row disappears or reverts to `"Available"` status.

### Test 3.6: Duplicate name validation

1. Navigate to `#/namespaces`.
2. Note the name of an existing allocated namespace.
3. Click `"Create Namespace"`.
4. Type the existing namespace name into `#namespace-name`.
5. Assert the field shows invalid state with text containing `"already in use"`.
6. Assert the primary button is disabled.
7. Cancel the modal.

### Test 3.7: Duplicate path validation

1. Navigate to `#/namespaces`.
2. Click `"Create Namespace"`.
3. Type an existing path (e.g., `"default"`) into `#namespace-path`.
4. Assert the field shows invalid state with text containing `"already in use"`.
5. Assert the primary button is disabled.
6. Cancel the modal.

### Test 3.8: Expandable row shows labels

1. If a namespace has labels, assert the expand chevron is present.
2. Click the expand button.
3. Assert the expanded row shows a `"Labels:"` heading and `<li>` items.

**Cleanup:** Each test must delete any namespaces it created via API in `afterEach`.

---

## Phase 4: Service Catalog Page

**Route:** `#/service-catalog`
**Page title:** "Service Catalog"
**Page description contains:** "View and manage deployed service catalogs"

### Test 4.1: Service Catalog page loads with tabs

**Spec file:** `service-catalog.spec.ts`

1. Navigate to `#/service-catalog`.
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` text is `"Service Catalog"`.
4. Assert `p.description` text contains `"View and manage"`.
5. Assert three tabs are visible: `"Service Catalogs"`, `"Service Templates"`, `"Toolset Repositories"`.
6. Assert the `"Service Catalogs"` tab is active by default.

### Test 4.2: Service Catalogs tab -- empty state

1. Navigate to `#/service-catalog`.
2. If no catalogs exist, assert the empty tile displays `"No service catalogs found"`.
3. Assert the CLI hint `"wanaku service init"` is shown.

### Test 4.3: Service Catalogs tab -- search bar present

1. Navigate to `#/service-catalog`.
2. Assert the search input with placeholder `"Search service catalogs..."` is visible.

### Test 4.4: Service Catalogs tab -- catalog card structure (if catalogs exist)

1. Navigate to `#/service-catalog`.
2. If catalogs exist, assert each card tile shows:
   - Catalog name in an `h4`.
   - Description text.
   - Service tags (blue `Tag` components).
   - A `"Deploy"` button.
   - A `"View details"` / `"Hide details"` toggle button.
   - A delete icon button.

### Test 4.5: Service Catalogs tab -- expand card details

1. If a catalog exists, click `"View details"` on the first card.
2. Assert the expanded section shows system details with routes and rules file names.
3. Click `"Hide details"`.
4. Assert the details section is hidden.

### Test 4.6: Service Catalogs tab -- delete confirmation modal

1. If a catalog exists, click the delete icon button on a card.
2. Assert a danger modal opens with heading `"Delete Service Catalog"`.
3. Assert the modal body contains the catalog name and `"cannot be undone"`.
4. Click `"Cancel"`.
5. Assert the modal closes and the card is still present.

### Test 4.7: Service Catalogs tab -- deploy wizard

1. If a catalog exists, click `"Deploy"` on a card.
2. Assert the `ComposedModal` wizard opens with title containing `"Deploy:"`.
3. Assert a `ProgressIndicator` shows two steps: `"Deployment Model"` and `"Instructions"`.
4. Assert three radio buttons are shown: `"Local (java -jar)"`, `"Docker / Podman"`, `"Kubernetes / OpenShift"`.
5. Assert `"Local (java -jar)"` is selected by default.
6. Click `"Next"`.
7. Wait for loading to finish.
8. Assert step 2 shows deployment instructions with a `CodeSnippet`.
9. Click `"Back"` and assert step 1 is shown again.
10. Click `"Next"` again, then `"Done"`.
11. Assert the wizard closes.

### Test 4.8: Service Templates tab -- loads

1. Navigate to `#/service-catalog`.
2. Click the `"Service Templates"` tab.
3. Wait for `Loading...` to disappear.
4. Assert either template cards are visible or the empty tile shows `"No service templates found"`.

### Test 4.9: Service Templates tab -- search bar present

1. Click the `"Service Templates"` tab.
2. Assert the search input with placeholder `"Search service templates..."` is visible.

### Test 4.10: Service Templates tab -- template card structure (if templates exist)

1. If templates exist, assert each card shows:
   - Template name in an `h4`.
   - Description text.
   - Service tags (purple `Tag` components).
   - A `"Parameterized"` green tag if `hasProperties` is true.
   - A `"Create Service Catalog"` button.
   - A `"View details"` toggle button.

### Test 4.11: Service Templates tab -- instantiate wizard

1. If a template exists, click `"Create Service Catalog"` on a card.
2. Assert the `ComposedModal` wizard opens with title containing `"Create Service Catalog from Template"`.
3. Assert `#service-name` and `#service-system` text inputs are present.
4. If the template has properties, assert property input fields are rendered with labels.
5. Click `"Cancel"`.
6. Assert the wizard closes.

### Test 4.12: Toolset Repositories tab -- loads

1. Navigate to `#/service-catalog`.
2. Click the `"Toolset Repositories"` tab.
3. Wait for loading to finish.
4. Assert either repository cards are visible or the empty tile shows `"No toolset repositories registered"`.

### Test 4.13: Toolset Repositories tab -- add repository modal

1. Click the `"Toolset Repositories"` tab.
2. Click `"Add Repository"`.
3. Assert modal opens with heading `"Add Toolset Repository"`.
4. Assert inputs: `#repo-name`, `#repo-url`, `#repo-branch`, `#repo-description` are present.
5. Assert primary button is disabled when name or URL is empty.
6. Fill `#repo-name` with `e2e-repo-<timestamp>`.
7. Fill `#repo-url` with `https://github.com/example/repo`.
8. Assert primary button is enabled.
9. Click `"Cancel"`.
10. Assert modal closes.

### Test 4.14: Toolset Repositories tab -- delete confirmation

1. If a repository exists, click the delete icon button.
2. Assert a danger modal opens with heading `"Delete Toolset Repository"`.
3. Assert the body mentions the repository name and notes that previously imported tools are not removed.
4. Click `"Cancel"`.
5. Assert the modal closes.

**Note:** Tests 4.4-4.7 and 4.10-4.11 require pre-existing catalogs or templates. If the router has none, these tests should be skipped gracefully using `test.skip()` with a descriptive message.

---

## Phase 5: Targets (Capabilities) Page

**Route:** `#/capabilities`
**Page title:** "Capabilities"
**Page description contains:** "connected to Wanaku"

### Test 5.1: Targets page loads with title and description

**Spec file:** `targets.spec.ts`

1. Navigate to `#/capabilities`.
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` text is `"Capabilities"`.
4. Assert `p.description` text contains `"connected to Wanaku"`.

### Test 5.2: Table structure is correct

1. Navigate to `#/capabilities`.
2. Assert the DataTable has headers: `"Service"`, `"Service Type"`, `"Host"`, `"Port"`, `"Status"`, `"Last Seen"`, `"Reason"`.
3. Assert the table has the correct `aria-label` of `"Targets table"`.

### Test 5.3: Empty state when no capabilities are registered

1. If no capability services are running, assert the empty state component is visible in the table body.

### Test 5.4: Capability rows display status (if capabilities exist)

1. If capabilities are registered, assert each row shows:
   - A service name.
   - A service type value.
   - A host value.
   - A port value.
   - A status value (one of: `"Healthy"`, `"Down"`, `"Pending"`, or similar capitalized format).
   - A last-seen timestamp.

### Test 5.5: No error notification on page load

1. Navigate to `#/capabilities`.
2. Assert no error toast notification (`.cds--toast-notification--error`) is visible.

**Note:** The Targets page is read-only -- there are no add/edit/delete operations. SSE connectivity is tested implicitly: if the page loads and shows status values, SSE was established. Explicit SSE testing (mocking EventSource events) is out of scope for this plan but could be added as a follow-up.

---

## Phase 6: Tool Call Debugger Page

**Route:** `#/tool-calls`
**Page title:** "Tool Call Debugger"
**Page description contains:** "Real-time monitoring and debugging"

### Test 6.1: Tool Call Debugger page loads with title and description

**Spec file:** `tool-calls.spec.ts`

1. Navigate to `#/tool-calls`.
2. Assert `h1.title` text is `"Tool Call Debugger"`.
3. Assert `p.description` text contains `"Real-time monitoring and debugging"`.

### Test 6.2: Filter controls are present

1. Navigate to `#/tool-calls`.
2. Assert two `Search` inputs are visible:
   - One with placeholder `"Filter by Connection ID"`.
   - One with placeholder `"Filter by Tool Name"`.
3. Assert a `<select>` dropdown for error category filtering is present with option `"All Error Categories"`.

### Test 6.3: Action buttons are present

1. Navigate to `#/tool-calls`.
2. Assert the `"Clear Events"` button (danger style with TrashCan icon) is visible.
3. Assert the `"Export to JSON"` button (tertiary style with Download icon) is visible.
4. Assert the auto-scroll toggle is visible with label `"Auto-scroll to latest"`.

### Test 6.4: Empty state when no events exist

1. Navigate to `#/tool-calls`.
2. Assert the empty message `"No tool call events yet"` is visible.
3. Assert the event count text shows `"Showing 0 of 0 events"`.

### Test 6.5: Clear events button works

1. Navigate to `#/tool-calls`.
2. Click `"Clear Events"`.
3. Assert the empty state message is still visible (no crash, no error).

### Test 6.6: Auto-scroll toggle works

1. Navigate to `#/tool-calls`.
2. Locate the toggle `#auto-scroll-toggle`.
3. Assert it is toggled on by default.
4. Click the toggle to turn it off.
5. Assert it is now toggled off.
6. Click again to re-enable.
7. Assert it is toggled on.

**Note:** Testing actual SSE event rendering requires a running tool invocation or SSE mock. This is out of scope for this plan. If SSE events are present, the accordion-based event detail view should be verified as a follow-up.

---

## Phase 7: Navigation

### Test 7.1: Sidebar links navigate to correct pages

**Spec file:** `navigation.spec.ts`

For each sidebar link, click it and verify the correct page loads:

| Sidebar Label | Expected Route | Expected Page Title |
|---------------|----------------|---------------------|
| Home | `#/` | `Dashboard` |
| Tools | `#/tools` | `Tools` |
| Resources | `#/resources` | `Resources` |
| Prompts | `#/prompts` | `Prompts` |
| Capabilities | `#/capabilities` | `Capabilities` |
| Namespaces | `#/namespaces` | `Namespaces` |
| Forwards | `#/forwards` | `Forwards` |
| Service Catalog | `#/service-catalog` | `Service Catalog` |

Developer submenu items (require expanding the `"Developer"` menu first):

| Sidebar Label | Expected Route | Expected Page Title |
|---------------|----------------|---------------------|
| Tool Call Debugger | `#/tool-calls` | `Tool Call Debugger` |
| Data Stores | `#/data-stores` | `Data Stores` |

1. For each entry above, click the sidebar link (expanding the Developer menu if needed).
2. Wait for `Loading...` to disappear.
3. Assert `h1.title` matches the expected page title.
4. Assert the URL hash matches the expected route.

### Test 7.2: Developer submenu expands and collapses

1. Locate the `"Developer"` SideNavMenu item.
2. Click to expand it.
3. Assert subitems `"LLMChat"`, `"Code Execution"`, `"Tool Call Debugger"`, `"Data Stores"` are visible.
4. Click to collapse it.
5. Assert subitems are hidden.

---

## Page Object and Helper Guidance

### New page objects to create

| Page Object | File | Base Class |
|-------------|------|------------|
| `ForwardsPage` | `pages/forwards.page.ts` | `BasePage` |
| `DataStoresPage` | `pages/data-stores.page.ts` | `BasePage` |
| `NamespacesPage` | `pages/namespaces.page.ts` | `BasePage` |
| `ServiceCatalogPage` | `pages/service-catalog.page.ts` | `BasePage` |
| `TargetsPage` | `pages/targets.page.ts` | `BasePage` |
| `ToolCallsPage` | `pages/tool-calls.page.ts` | `BasePage` |

### New API helpers to add

Extend `ApiHelper` in `helpers/api-helpers.ts` with:

- `addForward(forward)` -- `POST /api/v1/forwards`
- `deleteForward(name)` -- `DELETE /api/v1/forwards/{name}`
- `addDataStore(dataStore)` -- `POST /api/v1/data-stores`
- `deleteDataStore(id)` -- `DELETE /api/v1/data-stores/{id}`
- `createNamespace(namespace)` -- `POST /api/v1/namespaces`
- `deleteNamespace(id)` -- `DELETE /api/v1/namespaces/{id}`

### New test data helpers to add

Extend `helpers/test-data.ts` with:

- `forwardData()` -- returns `{ name, address }` with unique suffix
- `dataStoreData()` -- returns `{ name }` with unique suffix
- `namespaceData()` -- returns `{ name, path }` with unique suffix

---

## Test Summary

| Phase | Test ID | Test Name | Priority |
|-------|---------|-----------|----------|
| 0 | 0.1 | Router health check | Critical |
| 0 | 0.2 | Install dependencies and browser | Critical |
| 1 | 1.1 | Forwards page loads with title and description | High |
| 1 | 1.2 | Empty state when no forwards exist | Medium |
| 1 | 1.3 | Add a forward via modal | Critical |
| 1 | 1.4 | Edit a forward via modal | Critical |
| 1 | 1.5 | Delete a forward | Critical |
| 1 | 1.6 | Refresh a forward | Medium |
| 1 | 1.7 | Primary button disabled when fields empty | Medium |
| 2 | 2.1 | Data Stores page loads with title and description | High |
| 2 | 2.2 | Empty state when no data stores exist | Medium |
| 2 | 2.3 | Add a data store via file upload | Critical |
| 2 | 2.4 | View a data store via modal | High |
| 2 | 2.5 | Delete a data store | Critical |
| 2 | 2.6 | Submit without file shows error | Medium |
| 3 | 3.1 | Namespaces page loads with title and description | High |
| 3 | 3.2 | Default namespaces are present | High |
| 3 | 3.3 | Create a namespace via modal | Critical |
| 3 | 3.4 | Edit a namespace via modal | Critical |
| 3 | 3.5 | Delete a namespace | Critical |
| 3 | 3.6 | Duplicate name validation | Medium |
| 3 | 3.7 | Duplicate path validation | Medium |
| 3 | 3.8 | Expandable row shows labels | Medium |
| 4 | 4.1 | Service Catalog page loads with tabs | High |
| 4 | 4.2 | Service Catalogs tab -- empty state | Medium |
| 4 | 4.3 | Service Catalogs tab -- search bar | Medium |
| 4 | 4.4 | Service Catalogs tab -- card structure | High |
| 4 | 4.5 | Service Catalogs tab -- expand details | Medium |
| 4 | 4.6 | Service Catalogs tab -- delete confirmation | High |
| 4 | 4.7 | Service Catalogs tab -- deploy wizard | High |
| 4 | 4.8 | Service Templates tab -- loads | High |
| 4 | 4.9 | Service Templates tab -- search bar | Medium |
| 4 | 4.10 | Service Templates tab -- card structure | High |
| 4 | 4.11 | Service Templates tab -- instantiate wizard | High |
| 4 | 4.12 | Toolset Repositories tab -- loads | High |
| 4 | 4.13 | Toolset Repositories tab -- add modal | High |
| 4 | 4.14 | Toolset Repositories tab -- delete confirmation | Medium |
| 5 | 5.1 | Targets page loads with title and description | High |
| 5 | 5.2 | Table structure is correct | High |
| 5 | 5.3 | Empty state when no capabilities | Medium |
| 5 | 5.4 | Capability rows display status | High |
| 5 | 5.5 | No error notification on load | Medium |
| 6 | 6.1 | Tool Call Debugger loads with title and description | High |
| 6 | 6.2 | Filter controls are present | High |
| 6 | 6.3 | Action buttons are present | Medium |
| 6 | 6.4 | Empty state when no events | Medium |
| 6 | 6.5 | Clear events button works | Medium |
| 6 | 6.6 | Auto-scroll toggle works | Medium |
| 7 | 7.1 | Sidebar links navigate to correct pages | Critical |
| 7 | 7.2 | Developer submenu expands and collapses | Medium |
