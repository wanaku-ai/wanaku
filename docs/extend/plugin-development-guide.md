---
title: Build an Admin UI plugin
description: Build a Wanaku Admin UI plugin as an ES module, add pages and navigation, test it locally, and package it as a ZIP archive for install.
---

# Build an Admin UI plugin

Plugins extend the Wanaku admin UI by adding pages, navigation entries, and backend service integration without modifying the core application. This guide shows you how to build one.

This guide is for plugin developers and platform operators. It explains the runtime contract, the plugin structure, local tests, packaging, and cleanup rules. The [Plugin reference](plugin-reference.md) lists the manifest fields, the host API, and the backend service mappings.

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

## Plugin Lifecycle

Plugins have one lifecycle hook that the host calls: `activate(host)`.

### activate(host)

Called when the plugin loads. The `host` parameter is the `PluginHost` object — your gateway to all platform capabilities. Use this function to register navigation entries, pages, and set up any runtime state.

Returns `void` or `Promise<void>`. Errors thrown here prevent the plugin from loading. The host logs the error to the browser console but continues loading other plugins.

The host loads the plugins one at a time when the admin UI starts. It waits for each `activate()` to finish before it loads the next plugin. It renders the admin UI only after all plugins are active. Thus, a slow `activate()` delays the complete admin UI. Do not wait for network calls in `activate()`. Load data in `mount()` instead.

The host builds the navigation and the routes once, after all `activate()` calls finish. Register navigation entries and pages inside `activate()`, before it returns. A `navigation.add()` or `pages.register()` call after that time has no effect.

### deactivate()

The `PluginModule` type declares an optional `deactivate()` export, but the host does not call it. A browser page reload discards all plugin state. Release resources that belong to a page in the `Disposable` that `mount()` returns.

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
| `host.http` calls return 404 `service ... not found for plugin ...`. | No service mapping exists for this plugin and service. | Refer to [Backend configuration](plugin-reference.md#backend-configuration). |
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

## Next steps

- [`examples/hello-world`](https://github.com/wanaku-ai/wanaku/blob/main/examples/hello-world): a working plugin to start from.
- [Plugin reference](plugin-reference.md): read the manifest fields, the host API, and the backend configuration.
- [Plugin Catalog](plugin-catalog.md): publish your plugin to a catalog.
- [Admin UI plugin architecture](extensible-web-ui-plugin-guidelines.md): read the design rules for the plugin platform.
