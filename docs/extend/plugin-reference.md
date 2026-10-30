---
title: Admin UI plugin reference
description: Reference for Wanaku Admin UI plugins, with the plugin.json manifest fields, the PluginHost API, and the backend service mapping configuration.
---

# Admin UI plugin reference

This page lists the contract between an Admin UI plugin and the Wanaku host. For a step-by-step guide, read [Build an Admin UI plugin](plugin-development-guide.md).

## Manifest (plugin.json)

The manifest is a JSON file with these fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique plugin identifier (kebab-case, no spaces) |
| `name` | string | yes | Human-readable name shown in the Installed Plugins page and the server log |
| `version` | string | yes | Semver version (e.g., "1.2.3") |
| `entrypoint` | string | yes | Path to the JavaScript module (relative to plugin dir) |
| `styles` | string[] | no | CSS files to load (relative to plugin dir) |
| `requires.hostApi` | string | no | Required host API version (semver range, e.g., ">=1.0 <2.0"). Informational only: the host does not check it. |
| `requires.services` | object[] | no | Backend services this plugin needs (see [Backend configuration](#backend-configuration)) |
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

## PluginHost API

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

If you use a framework such as React, call its mount function inside `mount()`. Return a disposable that unmounts the page. The host does not share its React instance with plugins. Bundle your own React and ReactDOM, and compile the JSX with a build step. Refer to [Bundling Approach](plugin-development-guide.md#bundling-approach).

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

You configure the mapping in the backend (see [Backend configuration](#backend-configuration)).

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

## Backend configuration

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

## Next steps

- [Build an Admin UI plugin](plugin-development-guide.md): create, test, and package a plugin.
- [Management API](../reference/management-api.md): read the plugin routes.
- [Plugin Catalog](plugin-catalog.md): install plugins from a catalog.
