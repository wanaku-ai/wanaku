import {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import {RouterProvider} from "react-router-dom";
import "./index.scss";
import { buildRouter } from './router';
import { PageRegistry } from "./plugins/page-registry";
import { discoverAndActivatePlugins } from "./plugins/runtime";
import { setInitialNavItems } from "./plugins/plugin-state";
import type { NavItem, Disposable } from "./contexts/NavigationContext";
import { CORE_NAV_ITEMS } from "./navigation/core-nav-items";

window.addEventListener("unhandledrejection", (event) => {
  console.error("Unhandled promise rejection:", event.reason);
});

function createNavigationStore() {
  const items = [...CORE_NAV_ITEMS];
  return {
    add(item: Omit<NavItem, "source">, source: string): Disposable {
      const fullItem: NavItem = { ...item, source };
      items.push(fullItem);
      return { dispose: () => { const idx = items.indexOf(fullItem); if (idx >= 0) items.splice(idx, 1); } };
    },
    getItems: () => items,
  };
}

function createNotificationStore() {
  return {
    show(msg: { title?: string; text: string; kind?: "info" | "success" | "warning" | "error" }) {
      console.info(`[plugin notification] ${msg.kind || "info"}: ${msg.text}`);
    },
  };
}

async function bootstrap() {
  const navStore = createNavigationStore();
  const notifStore = createNotificationStore();
  const pageRegistry = new PageRegistry();

  await discoverAndActivatePlugins(navStore, notifStore, pageRegistry);

  setInitialNavItems(navStore.getItems());

  const router = buildRouter(pageRegistry.getPages());

  const root = document.getElementById("root");
  if (!root) return;

  createRoot(root).render(
    <StrictMode>
      <RouterProvider router={router} />
    </StrictMode>
  );
}

bootstrap().catch(err => {
  console.error("Bootstrap failed:", err);
  const router = buildRouter([]);
  const root = document.getElementById("root");
  if (root) {
    createRoot(root).render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>
    );
  }
});
