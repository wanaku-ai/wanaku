import type { PluginHost, Disposable } from "./types";
import type { PageRegistry } from "./page-registry";
import { customFetch } from "../custom-fetch";

interface NavigationStore {
  add(item: { id: string; label: string; route: string; icon?: string; section?: string; order?: number }, source: string): Disposable;
}

interface NotificationStore {
  show(msg: { title?: string; text: string; kind?: "info" | "success" | "warning" | "error" }): void;
}

export function createPluginHost(
  pluginId: string,
  navigationStore: NavigationStore,
  notificationStore: NotificationStore,
  pageRegistry: PageRegistry,
): PluginHost {
  return {
    version: "1.0",
    navigation: {
      add(entry) {
        return navigationStore.add({ ...entry, section: entry.section ?? "Extensions" }, pluginId);
      },
    },
    pages: {
      register(page) {
        return pageRegistry.register(page.route, page.mount, pluginId);
      },
    },
    http: {
      async get<T>(service: string, path: string): Promise<T> {
        const res: Record<string, unknown> = await customFetch(`/api/plugins/${pluginId}/${service}${path}`, { method: "GET" });
        return res.data as T;
      },
      async post<T>(service: string, path: string, body?: unknown): Promise<T> {
        const res: Record<string, unknown> = await customFetch(`/api/plugins/${pluginId}/${service}${path}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: body ? JSON.stringify(body) : undefined,
        });
        return res.data as T;
      },
      async put<T>(service: string, path: string, body?: unknown): Promise<T> {
        const res: Record<string, unknown> = await customFetch(`/api/plugins/${pluginId}/${service}${path}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: body ? JSON.stringify(body) : undefined,
        });
        return res.data as T;
      },
      async delete<T>(service: string, path: string): Promise<T> {
        const res: Record<string, unknown> = await customFetch(`/api/plugins/${pluginId}/${service}${path}`, { method: "DELETE" });
        return res.data as T;
      },
    },
    notifications: {
      show(message) {
        notificationStore.show(message);
      },
    },
  };
}
