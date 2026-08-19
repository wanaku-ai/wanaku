# Contributing: Admin UI

This guide covers development of the admin UI for contributors. For end-user documentation on using the admin UI, see the [Getting Started](./getting-started.md) guide.

The admin UI is a React 19 + TypeScript frontend embedded into the server binary via `rust_embed`. It's accessible at `http://localhost:8080` and provides a graphical interface to the management API.

This isn't a separate deployment. The UI is compiled into the Rust binary as static assets, served directly by the management API server. No nginx, no S3, no CDN. One binary, one port, one URL.

## Tech Stack

- **Framework:** React 19.1, TypeScript 5.7
- **Build tool:** Vite 6
- **Component library:** IBM Carbon Design System (`@carbon/react`)
- **Icons:** `@carbon/icons-react`
- **Routing:** `react-router-dom` v6, hash-based (`createHashRouter`)
- **Styling:** SCSS with Carbon theme tokens (`$g10` light / `$g100` dark)
- **API client:** Orval-generated from Rust server's OpenAPI spec (`openapi.json`)
- **Package manager:** Yarn (classic, not Berry)

## Project Structure

```
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
│   ├── hooks/api/              # Custom hooks wrapping API functions
│   ├── models/                 # Orval-generated TypeScript types (DO NOT EDIT)
│   ├── Pages/                  # Page components (capital P)
│   │   ├── Tools/
│   │   │   ├── Tools.tsx       # Main component
│   │   │   ├── index.ts        # Re-exports from router-exports.tsx
│   │   │   └── router-exports.tsx  # Exports page element for lazy loading
│   │   ├── Resources/
│   │   └── ...
│   ├── router/                 # Route constants and configuration
│   │   ├── links.models.ts     # const enum Links
│   │   └── router.tsx          # Hash-based router setup
│   ├── utils/                  # Utility functions
│   ├── custom-fetch.ts         # Fetch wrapper with error handling
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

```bash
cd ui/admin
yarn install
```

### 2. Run Dev Server

```bash
yarn run dev
```

This starts Vite's dev server on `http://localhost:5173` with hot module replacement (HMR). Changes to `.tsx`/`.scss` files reload instantly.

The dev server proxies API calls to `http://localhost:8080` (configurable in `vite.config.ts`).

### 3. Build for Production

```bash
yarn run build
```

This runs:
1. **Orval:** Regenerates API client from committed `openapi.json`
2. **TypeScript:** Type-checks all `.tsx` files
3. **Vite:** Bundles and minifies to `dist/`

Output:

```
dist/
├── index.html
├── assets/
│   ├── index-<hash>.js
│   └── index-<hash>.css
└── ...
```

### 4. Embed in Server

The server binary embeds `ui/admin/dist/` at compile time via `rust_embed`:

```rust
#[derive(RustEmbed)]
#[folder = "ui/admin/dist/"]
struct AdminUI;
```

When you visit `http://localhost:8080`, the server serves files from the embedded bundle.

**Gotcha:** Changes to the UI require:
1. `yarn run build` to update `dist/`
2. `cargo build` to re-embed `dist/` into the binary

For local dev, use `WANAKU_UI_PATH` to serve from filesystem:

```bash
export WANAKU_UI_PATH=/absolute/path/to/ui/admin/dist
cargo run
```

Now you can iterate on the UI (`yarn run build`) without rebuilding the server.

## Code Conventions

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

### Page Structure: Three-File Pattern

Each page has exactly three files:

**1. `<PageName>.tsx`** — the main component

```tsx
export const Tools = () => {
  const [tools, setTools] = useState<Tool[]>([]);
  // ... component logic
  return <div>...</div>;
};
```

**2. `index.ts`** — re-exports from `router-exports.tsx`

```tsx
export * from './router-exports';
```

**3. `router-exports.tsx`** — exports the page element for lazy loading

```tsx
import { Tools } from './Tools';

export const ToolsElement = <Tools />;
```

**Why?** The router uses lazy loading:

```tsx
{
  path: Links.Tools,
  lazy: async () => import('./Pages/Tools'),
  element: <Suspense fallback={<Loading />}><ToolsElement /></Suspense>
}
```

This pattern keeps the router config clean and enables code-splitting.

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
export const getTools = (): Promise<ToolsResponse> => {
  return customFetch('/api/v1/tools');
};
```

**Custom hook:**

```tsx
// src/hooks/api/useTools.ts
import { useCallback } from 'react';
import { listTools } from '../../api/wanaku-router-api';

export const useTools = () => {
  const fetchTools = useCallback(async () => {
    const result = await listTools();
    return result.data;  // customFetch unwraps the server envelope automatically
  }, []);

  return { fetchTools };
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

Wrap page content in `ErrorBoundary`:

```tsx
import { ErrorBoundary } from '../../components/ErrorBoundary';

export const Tools = () => {
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

Use the shared `EmptyTableState` component:

```tsx
import { EmptyTableState } from '../../components/EmptyTableState';

{tools.length === 0 ? (
  <EmptyTableState
    title="No tools discovered yet"
    subtitle="Register a forwarded MCP server to auto-discover tools"
  />
) : (
  <DataTable rows={tools} headers={headers} />
)}
```

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

See [Carbon Design Tokens](https://carbondesignsystem.com/guidelines/color/tokens/) for the full list.

## Router Configuration

The app uses hash-based routing (URLs start with `#/`) to avoid 404s when serving from the embedded bundle.

**Why hash routing?**

The server doesn't rewrite URLs. If you use browser routing and refresh `/tools`, the server looks for `GET /tools` instead of `GET /` + client-side routing.

Hash routing keeps all requests to `GET /` (which serves `index.html`), and the router handles `#/tools` in JavaScript.

**Router setup:**

```tsx
import { createHashRouter } from 'react-router-dom';

const router = createHashRouter([
  {
    path: Links.Home,
    element: <App />,
    children: [
      { path: Links.Tools, lazy: async () => import('./Pages/Tools') },
      { path: Links.Resources, lazy: async () => import('./Pages/Resources') },
    ]
  }
]);
```

## API Client (Orval)

The UI uses Orval to generate a TypeScript client from the Rust server's OpenAPI spec. This keeps the API client in sync with the server.

### OpenAPI Spec Generation

The OpenAPI spec is generated from the Rust server's `utoipa` annotations and committed as `ui/admin/openapi.json`. When the Rust API changes (new endpoints, new fields on types), regenerate the spec:

```bash
cargo run --example wanaku-openapi --no-default-features > ui/admin/openapi.json
```

The `--no-default-features` flag skips the UI build step in `build.rs`, avoiding a circular dependency (the UI build needs the spec, but the spec needs the server binary).

After regenerating the spec, regenerate the TypeScript client:

```bash
cd ui/admin
yarn run generate-api
```

This runs `cargo run --bin wanaku-openapi` + Orval in one step. Commit both `openapi.json` and the regenerated `src/api/` and `src/models/` files.

### Build Scripts

| Script | What it does |
|---|---|
| `yarn build` | Orval (reads committed `openapi.json`) + TypeScript + Vite |
| `yarn dev` | Orval + Vite dev server with HMR |
| `yarn generate-api` | Regenerates `openapi.json` from Rust server + Orval |

`yarn build` and `yarn dev` do **not** run cargo — they read the committed `openapi.json`. Only `yarn generate-api` invokes the Rust toolchain.

### Configuration

**`orval.config.ts`:**

```typescript
export default {
  wanaku: {
    input: './openapi.json',
    output: {
      target: './src/api/wanaku-router-api.ts',
      schemas: './src/models',
      client: 'fetch',
      mode: 'single',
      override: {
        mutator: {
          path: './src/custom-fetch.ts',
          name: 'customFetch',
        },
      },
    },
  },
};
```

This overwrites `src/api/wanaku-router-api.ts` and `src/models/`. Never edit these files manually.

### Response Handling

The Rust management API wraps all responses in `{"data": ..., "error": ...}`. The `customFetch` function in `src/custom-fetch.ts` unwraps this automatically, so API hooks and pages access `response.data` directly (not `response.data.data`).

The base URL is dynamic — it uses `VITE_API_URL` if set, otherwise `window.location.origin`. This means the UI works against any backend, not just localhost.

## Authentication

The admin UI is protected by oauth2-proxy when auth is enabled. Users authenticate via oauth2-proxy's browser-based cookie flow — no client-side OIDC logic in the React app.

**How it works:**

1. User visits `http://localhost:4181/admin/` (oauth2-proxy management port)
2. oauth2-proxy checks for a valid session cookie
3. If no cookie, oauth2-proxy redirects to Keycloak's login page
4. User authenticates with Keycloak
5. Keycloak redirects back to oauth2-proxy with an auth code
6. oauth2-proxy exchanges the code for a token and sets a session cookie
7. oauth2-proxy proxies the request to Praxis on port 8080
8. The UI loads, session is established

**Session expiry:**

When the session expires, oauth2-proxy returns HTTP 401. The browser is redirected to the login page automatically.

**No client-side tokens:**

Unlike the previous embedded auth approach, the UI does NOT store tokens in sessionStorage or send `Authorization: Bearer` headers. oauth2-proxy handles all auth via cookies.

**Code changes:**

The UI no longer depends on `oidc-client-ts`. Auth redirect handling is removed from `src/custom-fetch.ts`.

**Testing without auth:**

Run Praxis standalone on port 8080 without oauth2-proxy. The UI connects directly and sends unauthenticated requests.

## Adding a New Page

### 1. Create Page Files

```bash
mkdir src/Pages/MyNewPage
touch src/Pages/MyNewPage/MyNewPage.tsx
touch src/Pages/MyNewPage/index.ts
touch src/Pages/MyNewPage/router-exports.tsx
```

### 2. Implement Component

**`MyNewPage.tsx`:**

```tsx
import { Button } from '@carbon/react';

export const MyNewPage = () => {
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
import { MyNewPage } from './MyNewPage';

export const MyNewPageElement = <MyNewPage />;
```

### 4. Register Route

**`src/router/links.models.ts`:**

```tsx
export const enum Links {
  MyNewPage = '/my-new-page',
  // ...
}
```

**`src/router/router.tsx`:**

```tsx
{
  path: Links.MyNewPage,
  lazy: async () => import('../Pages/MyNewPage'),
  element: <Suspense fallback={<Loading />}><MyNewPageElement /></Suspense>
}
```

### 5. Add Nav Link

**`src/components/SideNav.tsx`:**

```tsx
<SideNavItems>
  <SideNavLink to={Links.MyNewPage}>My New Page</SideNavLink>
</SideNavItems>
```

Rebuild and the page appears in the UI.

## Related Docs

- [Architecture](./architecture.md) — how the UI is embedded in the server
- [Configuration](./configuration.md) — `WANAKU_UI_PATH` for local dev
- [Management API](./management-api.md) — API routes the UI consumes
