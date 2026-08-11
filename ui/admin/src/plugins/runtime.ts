import type { PluginManifest, PluginModule } from "./types";
import { createPluginHost } from "./plugin-host";
import type { PageRegistry } from "./page-registry";
import { getUrl } from "../custom-fetch";

interface NavigationStore {
  add(item: { id: string; label: string; route: string; icon?: string; section?: string; order?: number }, source: string): { dispose(): void };
}

interface NotificationStore {
  show(msg: { title?: string; text: string; kind?: "info" | "success" | "warning" | "error" }): void;
}

export async function discoverAndActivatePlugins(
  navigationStore: NavigationStore,
  notificationStore: NotificationStore,
  pageRegistry: PageRegistry,
): Promise<void> {
  let manifests: PluginManifest[];
  try {
    const response = await fetch(getUrl("/api/v1/plugins"));
    if (!response.ok) {
      console.warn("Plugin discovery failed:", response.status);
      return;
    }
    const result = await response.json();
    manifests = result.data ?? [];
  } catch (err) {
    console.warn("Plugin discovery error:", err);
    return;
  }

  for (const manifest of manifests) {
    try {
      if (!manifest.id || !manifest.entrypoint) {
        console.warn("Skipping plugin with missing id or entrypoint");
        continue;
      }

      for (const css of manifest.styles ?? []) {
        const link = document.createElement("link");
        link.rel = "stylesheet";
        link.href = `/plugins/${manifest.id}/${css}`;
        document.head.appendChild(link);
      }

      const moduleUrl = `/plugins/${manifest.id}/${manifest.entrypoint}`;
      const mod: PluginModule = await import(/* @vite-ignore */ moduleUrl);

      const host = createPluginHost(manifest.id, navigationStore, notificationStore, pageRegistry);

      if (typeof mod.activate === "function") {
        await mod.activate(host);
        console.info(`Plugin "${manifest.id}" activated`);
      } else {
        console.warn(`Plugin "${manifest.id}" has no activate() export`);
      }
    } catch (err) {
      console.error(`Failed to load plugin "${manifest.id}":`, err);
    }
  }
}
