# Extensible Web UI Plugin Architecture

## Purpose

This document defines the high-level architecture and development guidelines for making the application's web UI extensible through plugins.

The main application consists of:

- a **Rust backend**;
- a **Carbon-based web UI**;
- a **plugin host/runtime** in the web UI;
- an optional **backend proxy/service-resolution layer** in the Rust application.

Plugins may:

- contribute pages, navigation entries, actions, panels, or other UI elements to the main application;
- render their own UI inside explicit extension points exposed by the host;
- call one or more REST APIs;
- use host-provided capabilities such as navigation, notifications, authentication-aware HTTP, and configuration.

The plugin UI and the plugin backend are independent deployables. A plugin backend may use any implementation technology, including Quarkus, Rust, Spring Boot, Go, or another platform.

---

## 1. Core Architectural Principles

### 1.1 The plugin contract is a browser API, not a framework API

The host must expose a stable plugin API that is independent of the host UI implementation details.

Plugins must not depend directly on internal Carbon component state, internal routing implementation, private stores, or undocumented DOM structure.

Prefer:

```ts
host.navigation.add(...)
host.pages.register(...)
host.http.get(...)
host.notifications.show(...)
```

Avoid:

```ts
document.querySelector("#internal-menu").append(...)
```

The host's implementation may use Carbon internally, but the public plugin contract must remain stable even if the host UI is later refactored.

### 1.2 Use ES modules as the primary plugin format

The default UI plugin format should be a JavaScript/TypeScript ES module.

A typical plugin package contains:

```text
plugin/
├── plugin.json
├── plugin.js
├── plugin.css
└── assets/
```

WebAssembly may be used internally by a plugin, but it should be treated as an implementation detail rather than the platform contract.

For example:

```text
plugin.js
  └── loads plugin.wasm
```

The host should only care that the plugin implements the supported ES module contract.

### 1.3 Plugins must use explicit extension points

Plugins should not receive unrestricted access to the host UI.

The host should define supported extension points such as:

- navigation entries;
- pages/routes;
- toolbar actions;
- dashboard widgets;
- detail-page tabs;
- context-menu actions;
- settings pages;
- notifications;
- command palette entries.

Each extension point should have a stable API and lifecycle.

### 1.4 Plugin UI and backend are separate deployables

A plugin UI must not assume that its backend is colocated with the UI artifact.

The plugin backend may be deployed:

- in the same Kubernetes cluster;
- in another cluster;
- behind a gateway;
- on another domain;
- as an external SaaS API.

The UI plugin should therefore refer to logical services rather than physical hostnames whenever possible.

### 1.5 Prefer host-mediated HTTP access

The preferred model is:

```text
Plugin UI
   |
   | host.http.get("customer-api", "/customers")
   v
Host Plugin API
   |
   | authentication
   | authorization
   | routing
   | tracing
   | tenant/context propagation
   v
Rust backend / gateway
   |
   | reverse proxy / service resolution
   v
Plugin backend
```

This avoids hard-coded backend hostnames and centralizes cross-cutting concerns.

---

## 2. High-Level Architecture

```text
┌──────────────────────────────────────────────────────────────┐
│                        Main Application                      │
│                                                              │
│  Carbon Web UI                                               │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Plugin Runtime                                         │  │
│  │                                                        │  │
│  │  - plugin discovery                                    │  │
│  │  - manifest validation                                 │  │
│  │  - lifecycle management                                │  │
│  │  - extension-point registration                        │  │
│  │  - capability enforcement                              │  │
│  │  - compatibility checks                                │  │
│  └──────────────────────────┬─────────────────────────────┘  │
│                             │                                │
│                             v                                │
│                    Plugin ES Modules                         │
│                             │                                │
│                             │ host.http                      │
└─────────────────────────────┼────────────────────────────────┘
                              │
                              v
┌──────────────────────────────────────────────────────────────┐
│                       Rust Backend                           │
│                                                              │
│  - authentication                                            │
│  - authorization                                             │
│  - plugin configuration                                      │
│  - service resolution                                        │
│  - HTTP proxying                                             │
│  - tracing / telemetry                                       │
│                                                              │
└─────────────────────────────┬────────────────────────────────┘
                              │
                              v
                 ┌─────────────────────────┐
                 │ Plugin / External APIs  │
                 │                         │
                 │ Quarkus                 │
                 │ Rust                    │
                 │ Spring Boot             │
                 │ SaaS APIs               │
                 │ etc.                    │
                 └─────────────────────────┘
```

---

## 3. Plugin Manifest

Every plugin should provide a machine-readable manifest.

Recommended name:

```text
plugin.json
```

Example:

```json
{
  "id": "customer-management",
  "name": "Customer Management",
  "version": "1.4.0",
  "entrypoint": "./plugin.js",
  "styles": ["./plugin.css"],
  "requires": {
    "hostApi": ">=1.0 <2.0",
    "services": [
      {
        "id": "customer-api",
        "version": "v1"
      }
    ]
  },
  "permissions": [
    "navigation",
    "pages",
    "notifications",
    "service:customer-api:read",
    "service:customer-api:write"
  ]
}
```

The manifest should contain logical requirements only.

Do not hard-code environment-specific backend hostnames into the plugin manifest unless the API is intentionally external and public.

---

## 4. Plugin Lifecycle

Each plugin should implement a small lifecycle contract.

Example:

```ts
export interface Plugin {
  activate(host: PluginHost): void | Promise<void>;
  deactivate?(): void | Promise<void>;
}
```

A plugin entry point may look like:

```ts
export async function activate(host) {
  host.navigation.add({
    id: "customers",
    label: "Customers",
    route: "/customers"
  });

  host.pages.register({
    route: "/customers",
    mount(container) {
      return mountCustomerPage(container, host);
    }
  });
}

export async function deactivate() {
  // release listeners, subscriptions, timers, and other resources
}
```

The host should own plugin loading and unloading.

A plugin must clean up all resources it creates during deactivation.

---

## 5. Plugin Host API

The host API should be small, capability-oriented, versioned, and framework-independent.

A conceptual API may look like:

```ts
interface PluginHost {
  version: string;

  navigation: NavigationAPI;
  pages: PageAPI;
  toolbar: ToolbarAPI;
  notifications: NotificationAPI;
  http: HttpAPI;
  config: ConfigurationAPI;
}
```

### Navigation

```ts
interface NavigationAPI {
  add(entry: {
    id: string;
    label: string;
    route: string;
    icon?: string;
  }): Disposable;
}
```

### Pages

```ts
interface PageAPI {
  register(page: {
    route: string;
    mount(container: HTMLElement): void | Disposable;
  }): Disposable;
}
```

### Notifications

```ts
interface NotificationAPI {
  show(message: {
    title?: string;
    text: string;
    kind?: "info" | "success" | "warning" | "error";
  }): void;
}
```

### HTTP

```ts
interface HttpAPI {
  get<T>(service: string, path: string): Promise<T>;
  post<T>(service: string, path: string, body?: unknown): Promise<T>;
  put<T>(service: string, path: string, body?: unknown): Promise<T>;
  delete<T>(service: string, path: string): Promise<T>;
}
```

The exact implementation is application-specific, but the public API should remain deliberately small.

---

## 6. UI Rendering Guidelines

### 6.1 The host owns the shell

The main application owns:

- global navigation;
- top-level routing;
- global theme;
- authentication UX;
- global notifications;
- application chrome;
- top-level error handling.

Plugins should contribute content inside host-controlled extension points.

### 6.2 Plugins own their mounted subtree

When a plugin page is mounted, the host provides a container:

```ts
mount(container: HTMLElement)
```

The plugin may render its UI inside that container.

The plugin must not assume that it owns the entire document.

### 6.3 Carbon consistency

Because the host application uses Carbon, plugin authors should preferably use the same Carbon design system when building first-party plugins.

However, the plugin API should not require access to host-internal Carbon component instances.

Where practical, publish a supported UI package for plugin authors, for example:

```text
@application/plugin-ui
```

This package may expose:

- supported Carbon component wrappers;
- theme utilities;
- spacing and typography tokens;
- common loading and error states;
- shared form patterns.

### 6.4 CSS isolation

Plugins should avoid global CSS.

Prefer one or more of:

- CSS modules;
- strict plugin-specific class prefixes;
- Shadow DOM where appropriate;
- host-provided style primitives.

A plugin must not redefine global selectors such as:

```css
body { ... }
button { ... }
.bx--some-global-class { ... }
```

unless explicitly supported by the plugin platform.

---

## 7. Backend Service Resolution

Plugins should use logical service identifiers.

Example:

```ts
await host.http.get("customer-api", "/customers");
```

The UI plugin does not know whether the backend is physically located at:

```text
http://customer-service.namespace.svc:8080
```

or:

```text
https://customer-api.prod.example.com
```

The platform resolves the logical name.

### Recommended request path

```text
Browser
  |
  | /api/plugins/customer-management/customers
  v
Rust backend
  |
  | resolves logical service
  v
customer-api
  |
  v
Plugin backend
```

The Rust backend may act as a reverse proxy for plugin services.

This provides a single browser origin and reduces CORS complexity.

---

## 8. Authentication and Authorization

Plugins should not directly manage primary application credentials.

The host HTTP capability should inject authentication information when appropriate.

For example:

```text
Plugin
   |
   | host.http.get(...)
   v
Host
   |
   | adds access token
   | adds tenant context
   | adds correlation ID
   v
Plugin backend
```

Permissions declared in `plugin.json` should be checked before activation.

Example:

```json
{
  "permissions": [
    "service:customer-api:read",
    "service:customer-api:write"
  ]
}
```

The host should not expose a capability to a plugin unless the plugin has been granted the corresponding permission.

Frontend permission checks are not a substitute for backend authorization. Plugin backends must independently validate authorization for every protected operation.

---

## 9. Plugin Backend Guidelines

Plugin backends are independent services.

They should:

- expose stable REST APIs;
- publish an OpenAPI specification when possible;
- implement their own authorization checks;
- support distributed tracing/correlation IDs;
- avoid relying on the physical location of the UI plugin;
- maintain backward compatibility for supported API versions.

The plugin UI should consume the backend through an API contract rather than shared implementation details.

Recommended:

```text
OpenAPI
  |
  +-- backend validation/tests
  |
  +-- generated TypeScript client or typed API model
      |
      v
   Plugin UI
```

---

## 10. Testing Strategy

Testing should be possible without running the full application.

### 10.1 Plugin unit tests

Provide a mockable test host.

Example:

```ts
const host = createTestHost();

await activate(host);

expect(host.navigation.entries()).toContainEqual({
  id: "customers",
  label: "Customers",
  route: "/customers"
});
```

### 10.2 Plugin test kit

Publish a reusable test package, for example:

```text
@application/plugin-testkit
```

It should verify:

- manifest validity;
- successful activation;
- successful deactivation;
- duplicate extension IDs;
- route validity;
- permission declarations;
- resource cleanup;
- supported host API usage;
- compatibility with supported API versions.

### 10.3 Fake development host

Provide a small development application that can load a plugin independently from the real product.

It should allow plugin developers to test:

- navigation contribution;
- pages;
- light/dark themes;
- permissions;
- mocked backend responses;
- backend failures;
- latency;
- authentication states;
- multiple host API versions.

### 10.4 Browser integration tests

Use a browser test framework to run:

```text
Fake Host + Real Plugin + Mock Backend
```

and:

```text
Fake Host + Real Plugin + Real Plugin Backend
```

### 10.5 Full-system tests

Keep a smaller set of tests using:

```text
Real Host + Real Plugin + Real Plugin Backend
```

These should validate critical end-to-end workflows rather than every UI detail.

---

## 11. Compatibility and Versioning

The plugin host API must be explicitly versioned.

Example:

```json
{
  "requires": {
    "hostApi": ">=1.1 <2.0"
  }
}
```

The host must validate compatibility before loading the plugin.

Avoid silently changing existing behavior within a host API version.

Prefer additive evolution:

```text
1.0
  + navigation
  + pages

1.1
  + notifications

1.2
  + toolbar
```

Breaking changes should require a new major version.

The test kit should allow plugins to run compatibility tests against every supported host API version.

---

## 12. Plugin Discovery and Installation

The application may support one or more discovery mechanisms:

- static configuration;
- server-side plugin registry;
- filesystem or deployment configuration;
- remote plugin catalog.

A plugin installation record should associate:

```text
plugin identity
       |
       +-- UI artifact location
       |
       +-- logical services
       |
       +-- granted permissions
       |
       +-- configuration
```

Example server-side configuration:

```yaml
plugins:
  - id: customer-management
    manifest: https://plugins.example.com/customer/1.4.0/plugin.json
    services:
      customer-api:
        target: http://customer-service:8080
    permissions:
      - navigation
      - pages
      - service:customer-api:read
```

This environment-specific configuration belongs to the platform, not the plugin bundle.

---

## 13. Security Guidelines

Treat plugins as separate trust boundaries.

The host should:

- validate plugin manifests;
- enforce supported host API versions;
- grant only declared and approved capabilities;
- restrict backend access to approved logical services;
- validate service paths where appropriate;
- apply authorization server-side;
- apply CSP and integrity controls where appropriate;
- log plugin activation and relevant API operations;
- avoid exposing internal application state directly.

For trusted first-party plugins, an ES module loaded into the main page may be sufficient.

For untrusted third-party plugins, stronger isolation may be required, such as:

```text
sandboxed iframe
    +
message-based host API
```

Do not assume that WebAssembly alone provides DOM or browser security isolation.

---

## 14. Error Handling

A plugin failure should not make the entire host UI unusable.

The plugin runtime should isolate plugin lifecycle failures and present a controlled error state.

Example:

```text
Plugin failed to load

Customer Management could not be started.

[Retry]
```

The host should catch:

- manifest failures;
- module loading failures;
- activation failures;
- route rendering failures;
- backend request failures;
- compatibility failures.

Plugins should use host-provided error and notification primitives where available.

---

## 15. Observability

Plugin-originated operations should be observable across frontend and backend boundaries.

Prefer propagating:

- correlation IDs;
- trace IDs;
- plugin ID;
- plugin version;
- user/session context where appropriate.

Conceptually:

```text
Plugin UI
   |
   | plugin=customer-management
   | trace-id=...
   v
Rust Backend
   |
   v
Plugin Backend
```

This makes debugging plugin/backend interactions significantly easier.

---

## 16. Recommended Repository Responsibilities

### Main application repository

The main application should own:

```text
plugin-api/
plugin-runtime/
plugin-testkit/
plugin-development-host/
plugin-manifest-schema/
service-resolution/
```

Responsibilities include:

- defining the plugin contract;
- maintaining compatibility;
- loading plugins;
- providing host capabilities;
- proxying/resolving backend services;
- enforcing permissions;
- providing the plugin development environment.

### Plugin repository

A plugin repository should typically contain:

```text
ui/
  plugin.json
  src/
  tests/

backend/            # optional and independent deployable
  ...
```

The UI should depend only on public plugin SDK packages and documented REST API contracts.

The backend should not depend on internal host UI implementation details.

---

## 17. Recommended Initial Scope

For the first version, keep the platform deliberately small.

Recommended initial capabilities:

```text
Plugin lifecycle
  activate
  deactivate

UI
  navigation.add
  pages.register

Host services
  http.get/post/put/delete
  notifications.show

Platform
  manifest
  permissions
  host API versioning
  development host
  test kit
```

Avoid adding highly generic extension mechanisms until concrete use cases require them.

For example, prefer:

```ts
host.navigation.add(...)
```

over a generic API such as:

```ts
host.extend("anything", {...})
```

Explicit contracts are easier to version, test, document, and secure.

---

## 18. Decision Summary

The recommended architecture is:

```text
                 Main Application

          Rust Backend + Carbon Web UI
                    |
                    v
            Stable Plugin API
                    |
                    v
               ES Module Plugin
                    |
          +---------+----------+
          |                    |
          v                    v
     UI extensions       host.http(...)
                               |
                               v
                        Rust backend proxy
                               |
                               v
                     Plugin / External REST API
```

Key decisions:

1. **Use ES modules as the default plugin packaging model.**
2. **Keep WebAssembly optional and internal to individual plugins.**
3. **Expose explicit extension points rather than arbitrary DOM manipulation.**
4. **Keep the plugin UI independent from its backend deployable.**
5. **Use logical service identifiers instead of backend hostnames.**
6. **Route REST access through a host-provided HTTP capability when possible.**
7. **Centralize authentication, authorization context, routing, and observability in the host/platform.**
8. **Publish a plugin SDK, test kit, and fake development host.**
9. **Version the host plugin API and test compatibility explicitly.**
10. **Keep the first plugin API small and evolve it through concrete extension requirements.**

The most important boundary is:

> A plugin should depend on the public plugin API and its backend API contracts, never on the private implementation details of the main application.

