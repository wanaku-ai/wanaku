---
title: Admin UI development
description: "Develop the Wanaku Admin UI: the React and Carbon stack, page patterns, the generated API client, styling, and the Playwright E2E tests."
---

# Contributing: Admin UI

This guide covers development of the admin UI for contributors. For end-user documentation on using the admin UI, see the [Getting Started](../get-started/quickstart.md) guide.

The admin UI is a React 19 and TypeScript frontend embedded in the server binary through `rust_embed`. Open `http://localhost:8080/admin/` to use the graphical interface to the management API. The server redirects `/` to `/admin/`.

The UI is not a separate deployment. The management API server provides the compiled static assets from the Rust binary. One binary serves the API and UI on one port.

## Tech Stack

- **Framework:** React 19.2, TypeScript 5.7
- **Build tool:** Vite 6
- **Component library:** IBM Carbon Design System (`@carbon/react`)
- **Icons:** `@carbon/icons-react`
- **Routing:** `react-router-dom` v7, hash-based (`createHashRouter`)
- **Styling:** SCSS with Carbon theme tokens (`$g10` light / `$g100` dark), adjusted to match the wanaku.ai website
- **API client:** Orval 8, generated from the Rust server OpenAPI spec (`openapi.json`)
- **Package manager:** Yarn (classic, not Berry)

## Project Structure

```text
ui/admin/
├── src/
│   ├── api/                    # Orval-generated API client (DO NOT EDIT)
│   ├── assets/                 # Static assets (images, fonts)
│   ├── components/             # Shared layout components
│   │   ├── Header.tsx          # Top navigation bar
│   │   ├── SideNav.tsx         # Left sidebar
│   │   ├── Content.tsx         # Main content wrapper
│   │   └── ErrorBoundary.tsx   # Error boundary wrapper
│   ├── constants/              # Shared constants
│   ├── hooks/api/              # Custom hooks wrapping API functions (use-tools.ts, ...)
│   ├── models/                 # Orval-generated TypeScript types (DO NOT EDIT)
│   ├── Pages/                  # Page components (capital P)
│   │   ├── Tools/
│   │   │   ├── ToolsPage.tsx   # Page component
│   │   │   ├── ToolsTable.tsx  # Other page components
│   │   │   ├── index.ts        # Re-exports from router-exports.tsx
│   │   │   └── router-exports.tsx  # Exports the route element for lazy loading
│   │   ├── Resources/
│   │   ├── EmptyTableState.tsx # Shared TableEmptyState row
│   │   └── ...
│   ├── navigation/             # Core navigation entries
│   │   └── core-nav-items.ts   # Built-in navigation entries
│   ├── router/                 # Route constants
│   │   └── links.models.ts     # const enum Links
│   ├── utils/                  # Utility functions
│   ├── test/                   # Unit test support (browser stubs, import resolver)
│   ├── custom-fetch.ts         # Fetch wrapper with error handling
│   ├── router.tsx              # Hash-based router setup
│   ├── App.tsx                 # Root component
│   └── index.scss              # Global Carbon theme setup
├── public/                     # Static files (copied to dist/)
├── openapi.json                # Generated OpenAPI spec (committed, not gitignored)
├── orval.config.ts             # Orval API client generator config
├── package.json
├── tsconfig.json
├── vite.config.ts
└── yarn.lock
```

## Development Workflow

### 1. Install Dependencies

Use Node.js 22.18.0 or later. Orval 8 requires this version.

```bash
cd ui/admin
yarn install
```

### 2. Run Dev Server

```bash
yarn run dev
```

This starts Vite's dev server on `http://localhost:5173/admin/` with hot module replacement (HMR). Changes to `.tsx`/`.scss` files reload instantly.

The dev server does not proxy API calls. `vite.config.ts` has no `server.proxy` setting. The UI sends API calls to `VITE_API_URL` when it is set. Otherwise, it sends them to its own origin, and the API calls fail on the Vite server.

`vite.config.ts` injects `VITE_API_URL` as a JavaScript expression. Put the URL in double quotes inside the value:

```bash
VITE_API_URL='"http://localhost:8080"' yarn run dev
```

The browser then makes cross-origin requests to the management API. The management API sets only `Access-Control-Allow-Origin` (from `WANAKU_CORS_ORIGIN`) and does not answer `OPTIONS` preflight requests. Thus, `GET` requests can work, but JSON `POST` and `PUT` requests fail. To test the full UI against a running server, use the `WANAKU_UI_PATH` workflow in [Embed in Server](#4-embed-in-server).

### 3. Build for Production

```bash
yarn run build
```

This runs:

1. **Orval:** Regenerates API client from committed `openapi.json`
2. **TypeScript:** Type-checks all `.tsx` files
3. **Vite:** Bundles and minifies to `dist/`

Output:

```text
dist/
├── index.html
├── assets/
│   ├── index-<hash>.js
│   └── index-<hash>.css
└── ...
```

### 4. Embed in Server

The server binary embeds `ui/admin/dist/` at compile time through `rust_embed`. Refer to the `AdminUi` struct in [`server/src/management/ui.rs`](https://github.com/wanaku-ai/wanaku/blob/main/server/src/management/ui.rs). The `ui` Cargo feature (on by default) enables the embedded UI.

The server serves the UI under `/admin/`. When you open `http://localhost:8080/admin/`, the server serves files from the embedded bundle.

**Important:** Complete these steps after you change the UI:

1. `yarn run build` to update `dist/`
2. `cargo build` to re-embed `dist/` into the binary

For local dev, use `WANAKU_UI_PATH` to serve from filesystem:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/admin/dist
cargo run
```

Then open `http://localhost:8080/admin/`. The UI and the API have the same origin. Now you can iterate on the UI (`yarn run build`) without rebuilding the server.

## Code Conventions

### Source Formatting

Prettier 3.5.3 formats the handwritten JavaScript and TypeScript files in `ui/admin/src/`.
The settings are in `ui/admin/.prettierrc.json`.
The generated files in `src/api/` and `src/models/` are excluded by `.prettierignore`.
Orval formats these generated files with the pinned Prettier version and its default settings.

Run these commands from `ui/admin`.

Format the source files:

```bash
yarn run format
```

Check the source files before you submit a pull request:

```bash
yarn run format:check
```

The formatting check runs in CI for pull requests.
It fails if a source file does not match the Prettier settings.
Run `yarn run format` to correct the format.
ESLint checks code quality with `yarn run lint`.

### Carbon Components Only

Never use raw HTML elements for interactive UI. Use `@carbon/react` components:

**Bad:**

```tsx
<button onClick={handleClick}>Submit</button>
```

**Good:**

```tsx
import { Button } from '@carbon/react';

<Button onClick={handleClick}>Submit</Button>
```

**Bad:**

```tsx
<table>
  <tr><td>Name</td><td>Value</td></tr>
</table>
```

**Good:**

```tsx
import { DataTable, Table, TableHead, TableRow, TableHeader, TableBody, TableCell } from '@carbon/react';

<DataTable rows={rows} headers={headers}>
  {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
    <Table {...getTableProps()}>
      <TableHead>
        <TableRow>
          {headers.map(header => (
            <TableHeader {...getHeaderProps({ header })}>{header.header}</TableHeader>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map(row => (
          <TableRow {...getRowProps({ row })}>
            {row.cells.map(cell => <TableCell key={cell.id}>{cell.value}</TableCell>)}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )}
</DataTable>
```

### Page Structure

Each page folder has these files at minimum. A page can have more component files, for example a table and a modal.

**1. `<PageName>Page.tsx`** — the page component

```tsx
export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolEntry[]>([]);
  // ... component logic
  return <div>...</div>;
};
```

**2. `index.ts`** — re-exports from `router-exports.tsx`

```tsx
export * from './router-exports';
```

**3. `router-exports.tsx`** — exports the route element for lazy loading

```tsx
import { ToolsPage } from './ToolsPage';

export const element = <ToolsPage />;
```

**Why?** The router loads each page with the React Router `lazy` property:

```tsx
{
  path: Links.Tools,
  lazy: async () => import('./Pages/Tools'),
}
```

React Router uses the exported module as route properties. The export name must be `element`. React Router ignores other names, such as `ToolsElement`, and the page renders nothing. This pattern keeps the router config clean and enables code-splitting.

### Route Constants

Never hardcode URLs. Define them in `src/router/links.models.ts`:

```tsx
export const enum Links {
  Home = '/',
  Tools = '/tools',
  Resources = '/resources',
  Prompts = '/prompts',
}
```

Use in components:

```tsx
import { Links } from '../router/links.models';
import { Link } from 'react-router-dom';

<Link to={Links.Tools}>View Tools</Link>
```

### API Hooks

Wrap Orval-generated API functions with custom hooks in `src/hooks/api/`:

**Orval-generated (DO NOT EDIT):**

```tsx
// src/api/wanaku-router-api.ts
export const listTools = async (options?: Parameters<typeof customFetch>[1]): Promise<listToolsResponse> => {
  return customFetch<listToolsResponse>(getListToolsUrl(), { ...options, method: 'GET' });
};
```

**Custom hook:**

Hook file names use kebab case, for example `use-tools.ts`.

```tsx
// src/hooks/api/use-tools.ts
import { useCallback } from 'react';
import { listTools as apiListTools, listToolsResponse } from '../../api/wanaku-router-api';

export const useTools = () => {
  const listTools = useCallback((options?: RequestInit): Promise<listToolsResponse> => {
    return apiListTools(options);
  }, []);

  return { listTools };
};
```

`customFetch` returns `{status, data, headers}` where `data` is the unwrapped payload (e.g., `ToolEntry[]`). The server's `{"data": ..., "error": ...}` envelope is stripped by `customFetch`.

### Notifications

Use Carbon `ToastNotification` with auto-dismiss:

```tsx
import { ToastNotification } from '@carbon/react';

const [showNotification, setShowNotification] = useState(false);

// Trigger notification
setShowNotification(true);

// In JSX
{showNotification && (
  <ToastNotification
    title="Success"
    subtitle="Tool created successfully"
    kind="success"
    timeout={3000}
    onClose={() => setShowNotification(false)}
  />
)}
```

Never use `alert()` or `console.log()` for user feedback.

### Error Handling

Wrap page content in `ErrorBoundary`. `ErrorBoundary` is a default export:

```tsx
import ErrorBoundary from '../../components/ErrorBoundary';

export const MyPage = () => {
  return (
    <ErrorBoundary>
      <div>
        {/* page content */}
      </div>
    </ErrorBoundary>
  );
};
```

The error boundary catches React errors and shows a Carbon `InlineNotification` instead of crashing the app.

### Empty States

Use the shared `TableEmptyState` component from `src/Pages/EmptyTableState.tsx`. It renders a table row, so put it in the `TableBody`:

```tsx
import { TableEmptyState } from '../EmptyTableState';

<TableBody>
  {rows.length === 0 ? (
    <TableEmptyState
      colSpan={headers.length}
      title="No tools discovered yet"
      body="Register a forwarded MCP server to auto-discover tools"
    />
  ) : (
    rows.map(row => <TableRow key={row.id}>{/* cells */}</TableRow>)
  )}
</TableBody>
```

### Search and Pagination

The Tools, Resources, and Prompts tables have a search field and pagination. The filter and the page split occur in the browser. The management API returns the full list.

Use the `useSearchPagination` hook from `src/hooks/search-pagination.ts` to add the same behavior to a table. The hook takes the list and a function. The function returns the text values that the search term can match. The search does not use letter case.

Define the function outside the component. The hook memoizes the filtered list, so the function must keep the same reference across renders.

```tsx
import { Pagination, TableToolbarSearch } from '@carbon/react';
import { PAGE_SIZES, useSearchPagination } from '../../hooks/search-pagination';

const toolSearchableText = (tool: ToolEntry) => [tool.name, tool.description];

const { searchTerm, setSearchTerm, page, pageSize, onPageChange, pageItems, filteredCount } =
  useSearchPagination(tools, toolSearchableText);
```

Build the table rows from `pageItems`. Connect `searchTerm` and `setSearchTerm` to a `TableToolbarSearch`. Give `page`, `pageSize`, `PAGE_SIZES`, `filteredCount`, and `onPageChange` to a Carbon `Pagination` below the table. The default page size is 25. A new search term resets the table to page 1.

When the list has items but no item matches the search term, show a `TableEmptyState` that tells the user that no item matches.

## Styling

The UI uses Carbon theme tokens, not hardcoded colors.

**Bad:**

```scss
.my-component {
  background-color: #f4f4f4;
  color: #161616;
}
```

**Good:**

```scss
@use '@carbon/react/scss/theme';

.my-component {
  background-color: theme.$layer-01;
  color: theme.$text-primary;
}
```

**Theme tokens:**

- `$layer-01`, `$layer-02`, `$layer-03` — background layers
- `$text-primary`, `$text-secondary` — text colors
- `$interactive-01`, `$interactive-02` — buttons, links
- `$border-subtle`, `$border-strong` — borders

**Website look:**

The UI follows the wanaku.ai website, not the default Carbon look. `src/index.scss` sets these overrides:

- The Inter typeface replaces IBM Plex. The `@fontsource-variable/inter` package supplies the font files, so the UI does not load fonts from the internet.
- The `--cds-*` custom properties set the website neutral colors and the brand blue (`#5b8def`).
- Buttons, tiles, and modals have rounded corners.

To change a color, change the `--cds-*` custom property in `src/index.scss`. Do not set colors in page styles.

## Router Configuration

The app uses hash-based routing (URLs start with `#/`) to avoid 404s when serving from the embedded bundle.

**Why hash routing?**

The server does not rewrite URLs. With browser routing, a refresh of `/tools` sends `GET /tools` to the server. It does not load `/` before client-side routing.

Hash routing keeps all requests to `GET /` (which serves `index.html`), and the router handles `#/tools` in JavaScript.

**Router setup:**

`buildRouter(pluginPages)` in [`ui/admin/src/router.tsx`](https://github.com/wanaku-ai/wanaku/blob/main/ui/admin/src/router.tsx) creates the hash router. It adds one lazy route for each core page and one route for each plugin page.

## API Client (Orval)

The UI uses Orval to generate a TypeScript client from the Rust server's OpenAPI spec. This keeps the API client in sync with the server.

### OpenAPI Spec Generation

The OpenAPI spec is generated from the Rust server's `utoipa` annotations and committed as `ui/admin/openapi.json`. When the Rust API changes (new endpoints, new fields on types), regenerate the spec and the TypeScript client:

```bash
cd ui/admin
yarn run generate-api
```

This runs the `wanaku-openapi` example from `server/Cargo.toml` with `--no-default-features`. It writes `openapi.json` and then runs Orval. The `--no-default-features` flag skips the UI build step in `build.rs`. This prevents a circular dependency: the UI build needs the spec, and the spec needs the server. Commit `openapi.json` and the regenerated `src/api/` and `src/models/` files.

### Build Scripts

| Script | What it does |
|---|---|
| `yarn build` | Orval (reads committed `openapi.json`) + TypeScript + Vite |
| `yarn dev` | Orval + Vite dev server with HMR |
| `yarn generate-api` | Regenerates `openapi.json` from Rust server + Orval |

`yarn build` and `yarn dev` do **not** run cargo — they read the committed `openapi.json`. Only `yarn generate-api` invokes the Rust toolchain.

### Configuration

[`ui/admin/orval.config.ts`](https://github.com/wanaku-ai/wanaku/blob/main/ui/admin/orval.config.ts) configures Orval:

- The input is `./openapi.json`. An input transformer removes the `{"data": ...}` envelope from the success response schemas of some tags.
- The output is a `fetch` client in `./src/api/wanaku-router-api.ts`, with the models in `./src/models`.
- The `customFetch` function in `./src/custom-fetch.ts` is the mutator for all requests.
- An `afterAllFilesWrite` hook formats the generated files with Prettier.

Orval overwrites `src/api/wanaku-router-api.ts` and `src/models/`. Never edit these files manually.

### Response Handling

The Rust management API wraps all responses in `{"data": ..., "error": ...}`. The `customFetch` function in `src/custom-fetch.ts` unwraps this automatically, so API hooks and pages access `response.data` directly (not `response.data.data`).

### Error Responses

`customFetch` rejects an unsuccessful response with an `ApiError`. `ApiError` extends `Error`, so code that reads `error.message` continues to work. `ApiError` has these properties:

- `status`: the HTTP status.
- `data`: the `data` member of the error envelope, as `unknown`. It is `undefined` when the body is empty, is not JSON, or has no `data`.
- `message`: the envelope `error`, or a `reason_code` in `data`. If the body has neither, the message is `Request failed with status <status>`.

A malformed or non-JSON error body does not change the status.

Do not compare `error.message` with server text to find a state. Examine `error.status` with `instanceof ApiError` in the domain hook, and give the page an explicit result. For example, `getActivePolicy` in `src/hooks/api/use-action-policies.ts` returns `{ state: 'absent' }` for a 404 from the active-policy endpoint. A 404 for a policy revision stays an error. `actionPolicyErrorKind` maps a failed action-policy request to `not_found`, `conflict` (409), `validation` (422), `invalid_request` (400), or `unexpected`.

The authentication redirect responses do not give an `ApiError`. Refer to [Authentication](#authentication).

The base URL is dynamic. The UI uses `VITE_API_URL` when it is set. Otherwise, it uses `window.location.origin`. This supports localhost and other backend addresses.

## Authentication

The admin UI is protected by oauth2-proxy when auth is enabled. Users authenticate via oauth2-proxy's browser-based cookie flow — no client-side OIDC logic in the React app.

**How it works:**

1. User visits `http://localhost:4180/admin/` (oauth2-proxy management port)
2. oauth2-proxy checks for a valid session cookie
3. If no cookie, oauth2-proxy redirects to Keycloak's login page
4. User authenticates with Keycloak
5. Keycloak redirects back to oauth2-proxy with an auth code
6. oauth2-proxy exchanges the code for a token and sets a session cookie
7. oauth2-proxy proxies the request to the Wanaku management API on port 8080
8. The UI loads, session is established

**Session expiry:**

When the session expires, oauth2-proxy returns HTTP 401 or a redirect. `customFetch` in `src/custom-fetch.ts` sends requests with `redirect: 'manual'`. When it gets a redirect or a 401 response, it reloads the page, and oauth2-proxy starts the login flow. If a second redirect or 401 occurs within 10 seconds, `customFetch` does not reload. It throws `Authentication redirect loop detected — check OIDC configuration`.

**No client-side tokens:**

The UI does not store tokens in `sessionStorage` and does not send `Authorization: Bearer` headers. oauth2-proxy handles all authentication with cookies.

**Testing without auth:**

Run the server with `cargo run`, without oauth2-proxy. Open `http://localhost:8080/admin/`. The UI connects directly and sends unauthenticated requests.

## Adding a New Page

### 1. Create Page Files

```bash
mkdir src/Pages/MyNewPage
touch src/Pages/MyNewPage/MyNewPagePage.tsx
touch src/Pages/MyNewPage/index.ts
touch src/Pages/MyNewPage/router-exports.tsx
```

### 2. Implement Component

**`MyNewPagePage.tsx`:**

```tsx
import { Button } from '@carbon/react';

export const MyNewPagePage = () => {
  return (
    <div>
      <h1>My New Page</h1>
      <Button>Click me</Button>
    </div>
  );
};
```

### 3. Add Router Exports

**`index.ts`:**

```tsx
export * from './router-exports';
```

**`router-exports.tsx`:**

```tsx
import { MyNewPagePage } from './MyNewPagePage';

export const element = <MyNewPagePage />;
```

The export name must be `element`. React Router reads only route properties from the lazy module.

### 4. Register Route

**`src/router/links.models.ts`:**

```tsx
export const enum Links {
  MyNewPage = '/my-new-page',
  // ...
}
```

**`src/router.tsx`:** add the route to the `children` array in `buildRouter`:

```tsx
{
  path: Links.MyNewPage,
  lazy: async () => import('./Pages/MyNewPage'),
}
```

### 5. Add Nav Link

**`src/navigation/core-nav-items.ts`:**

```tsx
export const CORE_NAV_ITEMS: NavItem[] = [
  // Existing entries...
  { id: "my-new-page", label: "My New Page", route: Links.MyNewPage, source: "core", order: 100 },
];
```

Rebuild and the page appears in the UI. Add E2E tests for the page. Refer to [E2E Tests](#e2e-tests).

## Unit Tests

Unit tests use the Node.js test runner (`node:test`). Node.js runs the TypeScript files directly, so the tests do not need more dependencies. Put a unit test next to its source file, with the name `<name>.test.ts`.

Run the unit tests from `ui/admin`:

```bash
yarn run test
```

The `test` script loads `src/test/ts-resolve.mjs`. This hook resolves the extensionless imports in the sources and the generated client. `src/test/browser-stub.ts` replaces `fetch`, `window`, `sessionStorage`, and `VITE_API_URL`. In a test file, import local modules with the `.ts` extension.

Node.js removes the type annotations but does not compile other TypeScript syntax. Do not use `enum` or constructor parameter properties in a module that a unit test loads.

Unit tests do not render React components. Put logic that needs tests in plain functions, for example in a domain hook module. Use the E2E tests for page behavior.

## E2E Tests

The admin UI has Playwright tests in `tests/e2e/ui/`. The `.github/workflows/ui-e2e-tests.yml` workflow runs them for each pull request that changes code.

The test directory has this layout:

- `pages/`: page objects, one for each UI page.
- `helpers/test-data.ts`: test data.
- `helpers/api-helpers.ts`: API calls that prepare the test state.
- `tests/`: test specs (`*.spec.ts`).

A new page must have tests for the page title. If the page supports these actions, add tests for the add modal and for delete.

To run the tests, build the admin UI and the evaluator actions first. Refer to [Contributing](index.md#prerequisites). Then install the test dependencies:

```bash
cd tests/e2e/ui
yarn install --frozen-lockfile
npx playwright install --with-deps chromium
```

The `--with-deps` option also installs the system libraries that Chromium needs on Linux. CI uses the same command. On Linux, this step can ask for `sudo`.

Run the tests:

```bash
npx playwright test
```

`playwright.config.ts` starts the server with `cargo run -- --wanaku-config wanaku.yaml` from the repository root. If a server already runs on `http://localhost:8080`, Playwright uses it. To use a server at a different address, set `WANAKU_ROUTER_URL`.

## Next steps

- [Architecture](../get-started/architecture.md) — how the UI is embedded in the server
- [Wanaku environment variables](../reference/environment-variables.md#admin-ui-override) — `WANAKU_UI_PATH` for local dev
- [Management API](../reference/management-api.md) — API routes the UI consumes
