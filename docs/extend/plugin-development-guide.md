# Wanaku Plugin Development Guide

Plugins extend the Wanaku admin UI by adding pages, navigation entries, and backend service integration without modifying the core application. This guide shows you how to build one.

This guide is for plugin developers and platform operators. It explains the runtime contract, manifest, host API, backend mappings, local tests, and cleanup rules.

## Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Plugin Structure](#plugin-structure)
- [Manifest Reference](#manifest-reference-pluginjson)
- [Plugin Lifecycle](#plugin-lifecycle)
- [PluginHost API Reference](#pluginhost-api-reference)
- [Backend Configuration](#backend-configuration)
- [Complete Example](#complete-example)
- [Using Carbon Design System](#using-carbon-design-system)
- [Testing Locally](#testing-locally)
- [Packaging a Plugin](#packaging-a-plugin)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)

## Overview

Plugins are ES modules loaded at runtime. A plugin can:

- Add navigation entries to the sidebar
- Register new pages under custom routes
- Call backend services through a same-origin proxy

The supported plugin contract is the host API. Plugins must keep DOM changes inside their page container. They must not read internal host state or call service URLs outside the configured proxy.

Plugins currently run as same-origin JavaScript. Wanaku does not sandbox them or enforce manifest permissions. Install only trusted plugins. The host API contract does not expose React internals or Carbon component instances.

## Quick Start

Five steps to see a plugin running:

1. **Create a plugin directory** under your plugins path (e.g., `/tmp/plugins/my-plugin/`). The directory name must be equal to the manifest `id`.
2. **Create `plugin.json`** with id, name, version, and entrypoint
3. **Create `plugin.js`** with an `activate` export
4. **Start the server with `--plugins-path /tmp/plugins`**
5. **Open the admin UI** — your plugin page appears in the navigation

The following example is the smallest working plugin:

**plugin.json:**

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "entrypoint": "plugin.js"
}
```

**plugin.js:**

```javascript
export async function activate(host) {
  host.navigation.add({
    id: "my-page",
    label: "My Page",
    route: "/my-page"
  });

  host.pages.register({
    route: "/my-page",
    mount(container) {
      container.innerHTML = "<h2>Hello from plugin</h2>";
    }
  });
}

export function deactivate() {}
```

Open the admin UI. The "My Page" link appears in the **Extensions** menu of the sidebar.

## Plugin Structure

A plugin lives in its own directory under the plugins path. The plugins path is the value of `--plugins-path`. When you do not set `--plugins-path`, the plugins path is `<WANAKU_PERSIST_PATH>/plugins` (for example, `~/.wanaku/server/plugins`, or `/data/registry/plugins` in the container image). When persistence is off (`WANAKU_PERSIST_BACKEND=none`) and you do not set `--plugins-path`, the server has no plugins path and loads no plugins.

The directory name must be equal to the `id` in `plugin.json`. The server lists every valid manifest that it finds, but it serves plugin files from `<plugins-path>/<id>/`. If the names are different, the plugin appears in the list, but each file request returns 404 and the plugin does not load.

The typical layout:

```text
my-plugin/
├── plugin.json        # Manifest (required)
├── plugin.js          # Entry point (required)
├── plugin.css         # Optional styles
└── assets/            # Optional images, fonts, etc.
    └── logo.svg
```

The manifest (`plugin.json`) tells the host what files to load. The entry point exports an `activate()` function. Styles and assets are optional — the host loads stylesheets declared in the manifest and serves assets at `/plugins/{pluginId}/{path}`.

The server sets the `Content-Type` from the file extension. It knows `html`, `js`, `mjs`, `css`, `json`, `svg`, `png`, `jpg`, `jpeg`, `gif`, `webp`, `ico`, `woff`, `woff2`, `ttf`, and `wasm`. Other files use `application/octet-stream`.

## Manifest Reference (plugin.json)

The manifest is a JSON file with these fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique plugin identifier (kebab-case, no spaces) |
| `name` | string | yes | Human-readable name shown in the Installed Plugins page and the server log |
| `version` | string | yes | Semver version (e.g., "1.2.3") |
| `entrypoint` | string | yes | Path to the JavaScript module (relative to plugin dir) |
| `styles` | string[] | no | CSS files to load (relative to plugin dir) |
| `requires.hostApi` | string | no | Required host API version (semver range, e.g., ">=1.0 <2.0"). Informational only: the host does not check it. |
| `requires.services` | object[] | no | Backend services this plugin needs (see Backend Configuration) |
| `permissions` | string[] | no | Declared capabilities (not enforced yet, reserved for future use) |

Example with all fields:

```json
{
  "id": "customer-management",
  "name": "Customer Management",
  "version": "1.4.0",
  "entrypoint": "plugin.js",
  "styles": ["plugin.css"],
  "requires": {
    "hostApi": ">=1.0 <2.0",
    "services": [
      {"id": "customer-api", "version": "1.0"}
    ]
  },
  "permissions": [
    "navigation",
    "pages",
    "notifications"
  ]
}
```

The manifest must be valid JSON. Missing `id`, `name`, `version`, or `entrypoint` will prevent the plugin from loading. The server reads manifests only at startup. Restart the server after you add a plugin directory or change `plugin.json`.

## Plugin Lifecycle

Plugins have one lifecycle hook that the host calls: `activate(host)`.

### activate(host)

Called when the plugin loads. The `host` parameter is the `PluginHost` object — your gateway to all platform capabilities. Use this function to register navigation entries, pages, and set up any runtime state.

Returns `void` or `Promise<void>`. Errors thrown here prevent the plugin from loading. The host logs the error to the browser console but continues loading other plugins.

The host loads the plugins one at a time when the admin UI starts. It waits for each `activate()` to finish before it loads the next plugin. It renders the admin UI only after all plugins are active. Thus, a slow `activate()` delays the complete admin UI. Do not wait for network calls in `activate()`. Load data in `mount()` instead.

The host builds the navigation and the routes once, after all `activate()` calls finish. Register navigation entries and pages inside `activate()`, before it returns. A `navigation.add()` or `pages.register()` call after that time has no effect.

### deactivate()

The `PluginModule` type declares an optional `deactivate()` export, but the host does not call it. A browser page reload discards all plugin state. Release resources that belong to a page in the `Disposable` that `mount()` returns.

## PluginHost API Reference

The `PluginHost` object has five capabilities. The registration methods return a `Disposable` — an object with a `dispose()` method. The host reads registrations only when `activate()` returns. A later `dispose()` call does not change the navigation or the routes.

### host.version

A string identifying the host API version (currently `"1.0"`). Use this to log compatibility info or implement fallback behavior for different host versions.

```javascript
console.log(`Running on host API ${host.version}`);
```

### host.navigation.add(entry)

Adds a navigation entry to the sidebar. The host puts plugin entries in a sidebar menu with the `section` name. The default section is **Extensions**. The `order` value controls the position of the entry. Lower values appear first. If multiple plugins use the same value, the host uses registration order.

**Parameters:**

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique identifier for this nav entry |
| `label` | string | yes | Text shown in the sidebar |
| `route` | string | yes | Route to navigate to (must start with `/`) |
| `icon` | string | no | Icon name (not used) |
| `section` | string | no | Sidebar menu for the entry (default: `"Extensions"`) |
| `order` | number | no | Sort order (default: 0) |

**Returns:** `Disposable`

**Example:**

```javascript
host.navigation.add({
  id: "customers",
  label: "Customers",
  route: "/customers",
  order: 100
});
```

### host.pages.register(page)

Registers a page that renders when the route matches. The host calls your `mount(container)` function with an `HTMLElement` — you own everything inside that element. Render your UI however you like: vanilla DOM manipulation, a framework, whatever.

**Parameters:**

| Field | Type | Description |
|---|---|---|
| `route` | string | Route pattern (must start with `/`) |
| `mount` | function | `(container: HTMLElement) => void \| Disposable` |

The `mount` function receives a container element. You can return:

- `undefined` — the host does not release anything when the route unmounts
- A `Disposable` object — the host calls `dispose()` when the route unmounts

**Returns:** `Disposable`

**Example:**

```javascript
host.pages.register({
  route: "/customers",
  mount(container) {
    container.innerHTML = `
      <div class="customer-page">
        <h2>Customers</h2>
        <p>Customer list here...</p>
      </div>
    `;
    
    return {
      dispose() {
        container.innerHTML = "";
      }
    };
  }
});
```

If you use a framework such as React, call its mount function inside `mount()`. Return a disposable that unmounts the page. The host does not share its React instance with plugins. Bundle your own React and ReactDOM, and compile the JSX with a build step. Refer to [Bundling Approach](#bundling-approach).

```javascript
import { createRoot } from "react-dom/client";
import { CustomerPage } from "./CustomerPage.jsx";

host.pages.register({
  route: "/customers",
  mount(container) {
    const root = createRoot(container);
    root.render(<CustomerPage />);
    
    return {
      dispose() {
        root.unmount();
      }
    };
  }
});
```

### host.http.get / post / put / delete

Makes HTTP requests to backend services. These methods route through the Rust backend at `/api/plugins/{pluginId}/{serviceId}/{path}`. The backend resolves the logical service ID to a physical backend URL (configured in `wanaku.yaml` or the management API).

**Why use this instead of `fetch()`?**

- CORS is handled (single origin)
- The backend URL is configuration, not hardcoded in your plugin

**Authentication:** the host does not add authentication headers. The proxy forwards only the request method, path, query, body, and `Accept` header to the backend service. It sends a request body with `Content-Type: application/json`. It does not forward cookies or `Authorization`. If the backend service needs credentials, configure them on the service side.

A 401 response from the backend service has the same effect as an expired admin UI session. The host reloads the admin UI. If a second 401 occurs within 10 seconds, the call fails with `Authentication redirect loop detected`. Do not return 401 from a plugin backend service for reasons other than an expired Wanaku session.

**Signatures:**

```typescript
host.http.get<T>(service: string, path: string): Promise<T>
host.http.post<T>(service: string, path: string, body?: unknown): Promise<T>
host.http.put<T>(service: string, path: string, body?: unknown): Promise<T>
host.http.delete<T>(service: string, path: string): Promise<T>
```

**Parameters:**

- `service` — logical service identifier (e.g., `"customer-api"`, `"chat"`)
- `path` — the path to append (must start with `/`)
- `body` — JSON-serializable object (for POST/PUT)

**Returns:** `Promise<T>`. The value depends on the response:

- A JSON response with a top-level `data` key: the value of `data`. Other top-level keys are discarded. For example, `{"data": [...], "total": 42}` returns only the array.
- Other JSON responses: the parsed JSON.
- A response that is not JSON: the response text.
- A response with a status that is not 2xx: the promise rejects with an `ApiError`. Its `message` is the `error` field of the JSON body, or `Request failed with status <code>`. Its `status` is the HTTP status.

**Example:**

```javascript
try {
  const customers = await host.http.get("customer-api", "/customers");
  console.log("Loaded", customers.length, "customers");
} catch (err) {
  console.error("Failed to load customers:", err);
}

await host.http.post("customer-api", "/customers", {
  name: "Acme Corp",
  email: "contact@acme.example"
});
```

The backend routes this as:

```text
GET /api/plugins/my-plugin/customer-api/customers
  → resolves "customer-api" service
  → proxies to http://customer-service:8080/customers
```

You configure the mapping in the backend (see Backend Configuration below).

### host.notifications.show(message)

**Not implemented:** the host writes the message to the browser console with `console.info`. No toast appears in the UI. Show messages inside your page container until the host implements notifications.

**Parameters:**

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | no | Bold heading |
| `text` | string | yes | Notification body |
| `kind` | string | no | `"info"`, `"success"`, `"warning"`, or `"error"` (default: `"info"`) |

**Example:**

```javascript
host.notifications.show({
  title: "Customer Created",
  text: "Acme Corp has been added to the system.",
  kind: "success"
});

host.notifications.show({
  text: "Failed to connect to backend.",
  kind: "error"
});
```

## Backend Configuration

To call backend services via `host.http`, you need to map logical service IDs to physical URLs. Two options:

### Option 1: wanaku.yaml

Add a `plugins` section to the Wanaku bootstrap configuration. The server reads this file from `--wanaku-config` (default: `wanaku.yaml` in the working directory). The server reads only `id` and `services.<name>.target`. It ignores other keys.

```yaml
plugins:
  - id: my-plugin
    services:
      customer-api:
        target: http://customer-service:8080
      chat:
        target: http://localhost:11434
```

The backend reads this on startup and registers the mappings.

### Option 2: Admin UI or Management API

Open **Installed Plugins** in the admin UI and select **Configure** for the plugin. The **Configure Services** dialog sets the target URL of each service. The dialog calls `PUT /api/v1/plugins/{pluginId}/config`:

```bash
curl -X PUT http://localhost:8080/api/v1/plugins/my-plugin/config \
  -H 'Content-Type: application/json' \
  -d '{"services": {"customer-api": {"target": "http://customer-service:8080"}}}'
```

The new mappings apply immediately. A save replaces all mappings for that plugin. When persistence is on, the server saves the mappings in `<WANAKU_PERSIST_PATH>/plugins-config.json`. At startup, the saved mappings override the `wanaku.yaml` mappings for the same plugin and service.

**Key point:** The plugin does not know the backend URL. The platform resolves it. The same plugin can run in development, staging, and production without a code change.

## Complete Example

The following example shows the `hello-world` plugin in [`examples/hello-world`](../../examples/hello-world/):

**plugin.json:**

```json
{
  "id": "hello-world",
  "name": "Hello World",
  "version": "1.0.0",
  "entrypoint": "./plugin.js",
  "requires": {
    "hostApi": ">=1.0 <2.0"
  },
  "permissions": [
    "navigation",
    "pages"
  ]
}
```

**plugin.js:**

```javascript
// The activate function is called when the plugin loads.
// It receives the PluginHost object.
export async function activate(host) {
  // Register a navigation entry in the sidebar.
  // This appears as "Hello Plugin" in the nav.
  host.navigation.add({
    id: "hello",
    label: "Hello Plugin",
    route: "/hello",
    order: 100,
  });

  // Register the page that renders at /hello.
  // The mount function receives an HTMLElement container.
  host.pages.register({
    route: "/hello",
    mount(container) {
      // Render whatever you want inside the container.
      // This example uses plain HTML.
      container.innerHTML = `
        <div style="padding: 2rem;">
          <h2>Hello from Plugin!</h2>
          <p>This page was contributed by the <strong>hello-world</strong> plugin.</p>
          <p>Host API version: <code>${host.version}</code></p>
        </div>
      `;
      
      // Return a disposable to clean up when the route unmounts.
      return {
        dispose() {
          container.innerHTML = "";
        },
      };
    },
  });
}

// The host does not call deactivate() at this time.
export function deactivate() {
  // Nothing to clean up in this example.
}
```

To run it:

```bash
cargo run -- --plugins-path /path/to/examples
```

Run this command from the Wanaku repository root. The server loads each subdirectory of `examples` that has a `plugin.json`. Open `http://localhost:8080/admin/` (or your admin UI URL). Open the **Extensions** menu in the sidebar and click "Hello Plugin".

## Using Carbon Design System

The admin UI uses IBM Carbon Design System. Use Carbon components in first-party plugins for visual consistency. The plugin API does not require or enforce Carbon.

### Bundling Approach

Plugins are ES modules that load at runtime. Bundle npm dependencies with a tool such as esbuild, Rollup, or Vite.

**Example with esbuild:**

```bash
npm init -y
npm install @carbon/web-components
npm install --save-dev esbuild
```

**src/plugin.js:**

```javascript
import "@carbon/web-components/es/components/button/index.js";

export async function activate(host) {
  host.pages.register({
    route: "/demo",
    mount(container) {
      container.innerHTML = `
        <cds-button>Click Me</cds-button>
      `;
    }
  });
}

export function deactivate() {}
```

**Build:**

```bash
npx esbuild src/plugin.js --bundle --format=esm --outfile=plugin.js
```

Now `plugin.js` includes the Carbon button component. The browser can load it as a single ES module.

**Caveat:** Carbon React components require React and ReactDOM. The host does not share its React instance. Include these dependencies in the plugin bundle. This increases the plugin size.

**Example with React and esbuild:**

```bash
npm install react react-dom
```

**src/plugin.jsx:**

```javascript
import { createRoot } from "react-dom/client";

function HelloPage() {
  return <h2>Hello from React</h2>;
}

export async function activate(host) {
  host.navigation.add({ id: "react-hello", label: "React Hello", route: "/react-hello" });
  host.pages.register({
    route: "/react-hello",
    mount(container) {
      const root = createRoot(container);
      root.render(<HelloPage />);
      return { dispose: () => root.unmount() };
    }
  });
}
```

**Build:**

```bash
npx esbuild src/plugin.jsx --bundle --format=esm --jsx=automatic --define:process.env.NODE_ENV='"production"' --outfile=plugin.js
```

## Testing Locally

Step-by-step process to test a plugin:

1. Create the plugin directory:

   ```bash
   mkdir -p /tmp/plugins/my-plugin
   ```

2. Write the manifest:

   ```bash
   cat > /tmp/plugins/my-plugin/plugin.json <<EOF
   {
     "id": "my-plugin",
     "name": "My Plugin",
     "version": "1.0.0",
     "entrypoint": "plugin.js"
   }
   EOF
   ```

3. Write the plugin code:

   ```bash
   cat > /tmp/plugins/my-plugin/plugin.js <<'EOF'
   export async function activate(host) {
     host.navigation.add({
       id: "test",
       label: "Test Page",
       route: "/test"
     });
     
     host.pages.register({
       route: "/test",
       mount(container) {
         container.innerHTML = "<h2>It works!</h2>";
       }
     });
   }
   
   export function deactivate() {}
   EOF
   ```

4. Run the server from the Wanaku repository root:

   ```bash
   cargo run -- --plugins-path /tmp/plugins
   ```

   The first build also builds the admin UI and needs Node.js and Yarn. Refer to [Contributing](../contributing/index.md). With a released server binary, run `wanaku-server --plugins-path /tmp/plugins` instead.

5. Open `http://localhost:8080/admin/`.

6. Open the **Extensions** menu in the sidebar and select **Test Page**. The page shows **It works!**.

**Reload:** The server reads plugin files from disk on each request. After you edit `plugin.js` or a stylesheet, reload the browser page. After you add a plugin directory or change `plugin.json`, restart the server.

## Packaging a Plugin

The installer (the **Install** action in **Plugin Catalog** and **Installed Plugins**, or `POST /api/v1/plugins/install`) accepts only ZIP archives. Put `plugin.json` at the root of the archive, or in one top-level folder. The installer extracts the files into `<plugins-path>/<id>/`.

Create the archive from the plugin directory:

```bash
cd /tmp/plugins/my-plugin
zip -r ../my-plugin-1.0.0.zip .
```

To test the install, serve the archive over HTTP:

```bash
cd /tmp/plugins
python3 -m http.server 9090
```

Then install it with the management API:

```bash
curl -X POST http://localhost:8080/api/v1/plugins/install \
  -H 'Content-Type: application/json' \
  -d '{"id": "my-plugin", "version": "1.0.0", "url": "http://localhost:9090/my-plugin-1.0.0.zip"}'
```

The `id` must be equal to the `id` in `plugin.json`. Otherwise, the install fails with HTTP 400. The installer does not check `version`. A `.tar.gz` file or another format fails with HTTP 422 `invalid zip archive`. If the server cannot download the archive, the install fails with HTTP 502. If the server has no plugins path, the install fails with HTTP 400. Refer to [Plugin Structure](#plugin-structure).

The server adds the installed plugin to the plugin list immediately. Reload the admin UI to load it.

## Troubleshooting

The admin UI does not show plugin errors. Look in the server log and in the browser console.

| Symptom | Cause | Where to look |
|---|---|---|
| The plugin is not in **Installed Plugins**. | The manifest is not valid JSON, or `id` or `entrypoint` is missing. The server skips the plugin. | Server log: `failed to parse plugin manifest` or `plugin manifest missing required fields`. |
| The plugin is listed, but its menu entry does not appear. | The directory name is not equal to `id`, so the module request returns 404. Or the module import or `activate()` failed. | Browser console: `Failed to load plugin "<id>"`. |
| The menu entry appears, but the page is empty. | `mount()` threw an error. | Browser console: `Plugin "<id>" mount failed`. |
| `host.http` calls return 404 `service ... not found for plugin ...`. | No service mapping exists for this plugin and service. | Refer to [Backend Configuration](#backend-configuration). |
| **Plugin Catalog** shows an error. | One catalog entry is not valid. The server rejects the complete catalog with HTTP 502. | Server log: `failed to load plugin catalog`. |

## Best Practices

### Clean Up Resources in the Page Disposable

The host does not call `deactivate()`. Create timers, event listeners, and subscriptions in `mount()`, and release them in the `Disposable` that `mount()` returns. The host calls `dispose()` when the user leaves the page.

**Bad:**

```javascript
export async function activate(host) {
  setInterval(() => console.log("ping"), 1000);
  // Timer runs until the browser page reloads
}
```

**Good:**

```javascript
host.pages.register({
  route: "/status",
  mount(container) {
    const timer = setInterval(() => console.log("ping"), 1000);
    return {
      dispose() {
        clearInterval(timer);
      }
    };
  }
});
```

### Use host.http Instead of Raw fetch

Always use `host.http` for backend calls. It handles CORS and service resolution.

**Bad:**

```javascript
await fetch("http://customer-service:8080/customers");
// Hard-coded URL, breaks in different environments
// CORS issues if backend is on a different origin
```

**Good:**

```javascript
await host.http.get("customer-api", "/customers");
// Resolves to the right backend for this environment
// Routes through the host (same origin, no CORS issues)
```

### Prefix CSS Classes with Plugin ID

The host and other plugins share the same document. Avoid CSS class name collisions by prefixing your classes.

**Bad:**

```css
.button { background: red; }
```

Now every button on the page is red.

**Good:**

```css
.my-plugin-button { background: red; }
```

Only your plugin's buttons are red.

Or use CSS modules if your bundler supports them.

### Keep DOM Access Inside the Container

The `mount(container)` function gives the plugin a container element. Change only elements inside this container.

**Bad:**

```javascript
mount(container) {
  document.body.appendChild(createModal());
  // Now the modal outlives the route
  // The host cannot clean it up
}
```

**Good:**

```javascript
mount(container) {
  const modal = createModal();
  container.appendChild(modal);
  
  return {
    dispose() {
      modal.remove();
    }
  };
}
```

### Register Everything in activate()

The host reads navigation entries and pages once, after all plugins finish `activate()`. Decide in `activate()` which entries to register. A `dispose()` call or a registration after `activate()` returns does not change the sidebar or the routes.

---

**That's the guide.** You now know how to build, configure, and test a plugin for Wanaku. Start with the Quick Start example, experiment with the host APIs, and check the [`examples/hello-world`](../../examples/hello-world/) directory for a working reference implementation.
