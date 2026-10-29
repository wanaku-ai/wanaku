# Plugin Catalog

The Plugin Catalog connects the Wanaku admin UI to a growing ecosystem of plugins. A catalog is a JSON document with explicit plugin metadata and archive URLs. Publishers can host plugin archives independently of Wanaku and independently of the catalog.

## Catalog sources

Wanaku embeds the default catalog from [`features/plugins/plugin-catalog.json`](../features/plugins/plugin-catalog.json). The default catalog contains Wanaku Barn and Deployment Debugger. Deployment Debugger connects directly to MCP, A2A, and inference listeners and requires no Barn backend service. Each entry has a short plugin description. GitHub release pages and release notes are not catalog sources.

The server selects one source at startup, in this order:

1. `WANAKU_PLUGIN_CATALOG_PATH`: a local JSON file.
2. `WANAKU_PLUGIN_CATALOG_URL`: an HTTP or HTTPS JSON document.
3. The embedded catalog.

A custom source replaces the complete default catalog. It does not merge with the default catalog. Include the default entries in your custom document if you need them.

The server reads the selected local file or remote document on each catalog request. Changes to these documents do not require a restart. Changes to the embedded catalog require a new build. Source environment variable changes require a restart.

The catalog size limit is 1 MiB. A catalog request has a 30-second limit. A remote download has a 15-second limit. An unavailable or invalid source produces HTTP 502. The server does not use a fallback source.

Catalog requests do not download plugin archives. The server downloads an archive only when the user selects **Install**.

## Add a plugin

1. Create a ZIP plugin archive as described in [Packaging a Plugin](plugin-development-guide.md#packaging-a-plugin). The installer accepts only ZIP archives.
2. Publish the archive at an HTTP or HTTPS URL.
3. Add an entry to a catalog JSON array.
4. Set the entry `id` and `version` to the values in the archive `plugin.json` file. The installer rejects an archive whose `plugin.json` `id` is different from the entry `id`. It does not check `version`.
5. Copy the required host API and service versions from the plugin manifest.
6. Write a short description of the plugin.
7. Configure the catalog source.

Use one entry per plugin ID. Select the supported version for that entry. The following example contains two independent plugins. These are example entries, not plugins in the default catalog.

```json
[
  {
    "id": "service-tools",
    "name": "Service Tools",
    "version": "1.2.3",
    "description": "Manage service connections in the admin UI.",
    "publisher": "Example Publisher",
    "license": "Apache-2.0",
    "dependencies": [],
    "requires": {
      "hostApi": ">=1.0 <2.0",
      "services": [{"id": "service-tools-api", "version": "1.0"}]
    },
    "metadata": {"homepage": "https://publisher.example/service-tools"},
    "url": "https://downloads.publisher.example/service-tools-1.2.3.zip"
  },
  {
    "id": "reports",
    "name": "Reports",
    "version": "2.0.0",
    "description": "View service activity reports.",
    "publisher": "Another Publisher",
    "license": "MIT",
    "dependencies": [],
    "requires": {"hostApi": ">=1.0 <2.0", "services": []},
    "metadata": {"homepage": "https://another.example/reports"},
    "url": "https://another.example/releases/reports-2.0.0.zip"
  }
]
```

Each entry must contain `id`, `name`, `version`, and `url`. The ID must be a single path segment. The URL must use HTTP or HTTPS. Optional fields are `description`, `publisher`, `license`, `dependencies`, `requires`, and `metadata`. Each dependency has `id` and `version`. The `requires` field uses the plugin manifest format. The `metadata` field is a JSON object.

The admin UI only shows `dependencies` and `requires`. The installer does not install dependencies and does not check versions.

If one entry is not valid, the server rejects the complete catalog with HTTP 502. The server log shows the cause after `failed to load plugin catalog`.

Required backend services are separate deployments. Installing a UI plugin does not install its backend services. Deploy each required service. Configure its URL in the plugin configuration dialog.

## Install a plugin

The install needs a plugins path. The server uses `--plugins-path`, or `<WANAKU_PERSIST_PATH>/plugins` when persistence is on. Without a plugins path, the install fails with HTTP 400.

1. Open **Plugin Catalog** in the admin UI.
2. Select **Install** for the plugin. The server downloads the archive and extracts it into `<plugins-path>/<id>/`.
3. If the plugin requires backend services, set their URLs in the **Configure Services** dialog. The mappings apply immediately.
4. Reload the admin UI in the browser. The host loads plugins when the admin UI starts.

To install a plugin that is not in the catalog, open **Installed Plugins** and select **Install Plugin**. Enter the plugin ID, the version, and the archive download URL.

## Use a custom catalog

Save your document as `/etc/wanaku/plugin-catalog.json`. Set the local source before you start Wanaku:

```bash
export WANAKU_PLUGIN_CATALOG_PATH=/etc/wanaku/plugin-catalog.json
cargo run
```

To use a remote document, leave `WANAKU_PLUGIN_CATALOG_PATH` unset. Set the remote source before you start Wanaku:

```bash
export WANAKU_PLUGIN_CATALOG_URL=https://catalog.example/wanaku-plugins.json
cargo run
```

Open **Plugin Catalog** in the admin UI. Confirm that your entries appear. Use **Info** to inspect metadata and required services.

## Contribute to the default catalog

Add your entry to `features/plugins/plugin-catalog.json`. Keep the array valid JSON. The unit tests in `features/plugins/src/catalog.rs` check the number of embedded entries (`embedded_catalog_has_curated_barn_metadata`). Update the expected count, and add a test for your entry. Submit a pull request to Wanaku with the archive URL, plugin metadata, and required service information. The archive can use a publisher-controlled host. A change to the default catalog becomes available in builds that include that change.
