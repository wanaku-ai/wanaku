import type { Disposable } from "./types";

interface PageEntry {
  route: string;
  mount: (container: HTMLElement) => void | Disposable;
  pluginId: string;
}

export class PageRegistry {
  private pages = new Map<string, PageEntry>();

  register(route: string, mount: (el: HTMLElement) => void | Disposable, pluginId: string): Disposable {
    this.pages.set(route, { route, mount, pluginId });
    return { dispose: () => { this.pages.delete(route); } };
  }

  getPages(): PageEntry[] {
    return Array.from(this.pages.values());
  }
}
