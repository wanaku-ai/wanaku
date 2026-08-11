# Wanaku Praxis Plugin Development Guide

Plugins extend the Wanaku Praxis admin UI by adding pages, navigation entries, and backend service integration without modifying the core application. This guide shows you how to build one.

## Overview

Plugins are ES modules loaded at runtime. A plugin can:

- Add navigation entries to the sidebar
- Register new pages under custom routes
- Call backend services through an authenticated proxy
- Show notifications to users

What plugins can't do: access DOM outside their container, reach into internal host state, or bypass the host API. The plugin contract is a stable browser API, not access to React internals or Carbon component instances.

## Quick Start

Five steps to see a plugin running:

1. **Create a plugin directory** under your plugins path (e.g., `/data/plugins/my-plugin/`)
2. **Create `plugin.json`** with id, name, version, and entrypoint
3. **Create `plugin.js`** with `activate` and `deactivate` exports
4. **Set `WANAKU_PLUGINS_PATH=/data/plugins`** and restart the server
5. **Open the admin UI** — your plugin page appears in the navigation

Here's the smallest working plugin:

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

Restart the server. The "My Page" link appears in the sidebar.

## Plugin Structure

A plugin lives in its own directory under `WANAKU_PLUGINS_PATH`. The typical layout:

```
my-plugin/
├── plugin.json        # Manifest (required)
├── plugin.js          # Entry point (required)
├── plugin.css         # Optional styles
└── assets/            # Optional images, fonts, etc.
    └── logo.svg
```

The manifest (`plugin.json`) tells the host what files to load. The entry point exports `activate()` and `deactivate()` functions. Styles and assets are optional — the host loads stylesheets declared in the manifest and serves assets at `/plugins/{pluginId}/{path}`.

## Manifest Reference (plugin.json)

The manifest is a JSON file with these fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique plugin identifier (kebab-case, no spaces) |
| `name` | string | yes | Human-readable name shown in error messages |
| `version` | string | yes | Semver version (e.g., "1.2.3") |
| `entrypoint` | string | yes | Path to the JavaScript module (relative to plugin dir) |
| `styles` | string[] | no | CSS files to load (relative to plugin dir) |
| `requires.hostApi` | string | no | Required host API version (semver range, e.g., ">=1.0 <2.0") |
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

The manifest must be valid JSON. Missing `id`, `name`, `version`, or `entrypoint` will prevent the plugin from loading.

## Plugin Lifecycle

Plugins have two lifecycle hooks:

### activate(host)

Called when the plugin loads. The `host` parameter is the `PluginHost` object — your gateway to all platform capabilities. Use this function to register navigation entries, pages, and set up any runtime state.

Returns `void` or `Promise<void>`. Errors thrown here prevent the plugin from loading. The host logs the error but continues loading other plugins.

### deactivate()

Called when the plugin is unloaded (currently only on page refresh). Clean up timers, event listeners, subscriptions, or other resources here.

Returns `void` or `Promise<void>`. If you skip this export, the host assumes there's nothing to clean up.

**Critical:** A plugin that doesn't clean up resources in `deactivate()` will leak memory or leave orphaned timers. If you register anything in `activate()`, dispose it in `deactivate()`.

## PluginHost API Reference

The `PluginHost` object has five capabilities. All registration methods return a `Disposable` — an object with a `dispose()` method you can call to remove the registration early (before `deactivate()` is called).

### host.version

A string identifying the host API version (currently `"1.0"`). Use this to log compatibility info or implement fallback behavior for different host versions.

```javascript
console.log(`Running on host API ${host.version}`);
```

### host.navigation.add(entry)

Adds a navigation entry to the sidebar. The entry appears in the order specified by `order` (lower numbers first). If multiple plugins use the same order, they're sorted by registration order.

**Parameters:**

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique identifier for this nav entry |
| `label` | string | yes | Text shown in the sidebar |
| `route` | string | yes | Route to navigate to (must start with `/`) |
| `icon` | string | no | Icon name (reserved for future use) |
| `section` | string | no | Grouping hint (reserved for future use) |
| `order` | number | no | Sort order (default: 0) |

**Returns:** `Disposable`

**Example:**

```javascript
const navDisposable = host.navigation.add({
  id: "customers",
  label: "Customers",
  route: "/customers",
  order: 100
});

// Later, remove the nav entry:
// navDisposable.dispose();
```

### host.pages.register(page)

Registers a page that renders when the route matches. The host calls your `mount(container)` function with an `HTMLElement` — you own everything inside that element. Render your UI however you like: vanilla DOM manipulation, a framework, whatever.

**Parameters:**

| Field | Type | Description |
|---|---|---|
| `route` | string | Route pattern (must start with `/`) |
| `mount` | function | `(container: HTMLElement) => void \| Disposable` |

The `mount` function receives a container element. You can return:
- `undefined` — the host assumes you'll clean up in `deactivate()`
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

If you're using a framework like React, call your framework's mount function inside `mount()` and return a disposable that unmounts:

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
- Authentication headers are injected automatically
- CORS is handled (single origin)
- The backend URL is configuration, not hardcoded in your plugin
- Errors trigger redirect to login when auth expires

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

**Returns:** `Promise<T>` — the parsed JSON response

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

```
GET /api/plugins/my-plugin/customer-api/customers
  → resolves "customer-api" service
  → proxies to http://customer-service:8080/customers
```

You configure the mapping in the backend (see Backend Configuration below).

### host.notifications.show(message)

Displays a toast notification at the top-right of the screen. Notifications auto-dismiss after a few seconds.

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

### Option 1: Environment Variable + YAML Config

Set `WANAKU_PLUGINS_PATH` and create a `wanaku.yaml` file with a `plugins` section:

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

### Option 2: Management API

You can dynamically register service mappings via the management API (not yet implemented — this is a placeholder for future capability).

**Key point:** The plugin doesn't know the backend URL. The platform resolves it. This keeps plugins environment-agnostic — the same plugin works in dev (localhost), staging (k8s cluster), and prod (different cluster) without code changes.

## Complete Example

Here's the `hello-world` plugin from the examples directory, annotated:

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

// The deactivate function is called when the plugin unloads.
// Clean up timers, listeners, subscriptions, etc. here.
export function deactivate() {
  // Nothing to clean up in this example.
}
```

To run it:

```bash
export WANAKU_PLUGINS_PATH=/path/to/examples
cargo run
```

Open `http://localhost:8080` (or your admin UI URL). Click "Hello Plugin" in the sidebar.

## Using Carbon Design System

The admin UI uses IBM Carbon Design System. If you're building a first-party plugin, use Carbon components for visual consistency. This isn't required — the plugin API doesn't enforce it — but it makes your plugin look like part of the platform.

### Bundling Approach

Since plugins are ES modules loaded at runtime, you can't use npm dependencies directly in the browser. You need to bundle your plugin with a tool like esbuild, Rollup, or Vite.

**Example with esbuild:**

```bash
npm install @carbon/web-components
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
esbuild src/plugin.js --bundle --format=esm --outfile=plugin.js
```

Now `plugin.js` includes the Carbon button component. The browser can load it as a single ES module.

**Caveat:** Carbon React components won't work in a plain ES module context — you'd need to bundle React and ReactDOM too, which inflates the plugin size. For React-based plugins, consider using a framework like Vite that can produce optimized ES module builds with code splitting.

## Testing Locally

Step-by-step process to test a plugin:

1. **Create the plugin directory:**
   ```bash
   mkdir -p /tmp/plugins/my-plugin
   cd /tmp/plugins/my-plugin
   ```

2. **Write the manifest:**
   ```bash
   cat > plugin.json <<EOF
   {
     "id": "my-plugin",
     "name": "My Plugin",
     "version": "1.0.0",
     "entrypoint": "plugin.js"
   }
   EOF
   ```

3. **Write the plugin code:**
   ```bash
   cat > plugin.js <<EOF
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

4. **Set the environment variable:**
   ```bash
   export WANAKU_PLUGINS_PATH=/tmp/plugins
   ```

5. **Run the server:**
   ```bash
   cargo run
   ```

6. **Open the admin UI:**
   Navigate to `http://localhost:8080`. Click "Test Page" in the sidebar. You should see "It works!".

**Hot reload:** The server doesn't watch for plugin changes. After editing a plugin, restart the server.

## Best Practices

### Clean Up Resources in deactivate()

If you create timers, event listeners, subscriptions, or other stateful resources in `activate()`, dispose them in `deactivate()`. The host can't do this for you.

**Bad:**
```javascript
export async function activate(host) {
  setInterval(() => console.log("ping"), 1000);
}

export function deactivate() {
  // Timer keeps running — memory leak
}
```

**Good:**
```javascript
let timer;

export async function activate(host) {
  timer = setInterval(() => console.log("ping"), 1000);
}

export function deactivate() {
  clearInterval(timer);
}
```

### Use host.http Instead of Raw fetch

Always use `host.http` for backend calls. It handles authentication, CORS, and service resolution.

**Bad:**
```javascript
await fetch("http://customer-service:8080/customers");
// Hard-coded URL, breaks in different environments
// No auth headers, fails if backend requires authentication
// CORS issues if backend is on a different origin
```

**Good:**
```javascript
await host.http.get("customer-api", "/customers");
// Resolves to the right backend for this environment
// Automatically includes auth headers
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

### Don't Access DOM Outside Your Container

The `mount(container)` function gives you a container element. You own everything inside it. Don't reach outside.

**Bad:**
```javascript
mount(container) {
  document.body.appendChild(createModal());
  // Now the modal outlives the route
  // The host can't clean it up
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

### Use the Disposable Pattern for All Registrations

Every `host.navigation.add()` and `host.pages.register()` call returns a `Disposable`. Store it and call `dispose()` when you're done.

**Why?** If you need to unregister something before the plugin unloads (e.g., a dynamic nav entry based on user permissions), you can:

```javascript
const disposable = host.navigation.add({ id: "admin", label: "Admin", route: "/admin" });

// Later, when the user logs out:
disposable.dispose();
```

The nav entry disappears immediately. You don't have to wait for the plugin to unload.

---

**That's the guide.** You now know how to build, configure, and test a plugin for Wanaku Praxis. Start with the Quick Start example, experiment with the host APIs, and check the `examples/hello-plugin/` directory for a working reference implementation.
