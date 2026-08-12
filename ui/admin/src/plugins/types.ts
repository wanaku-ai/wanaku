export interface PluginManifest {
  id: string;
  name: string;
  version: string;
  entrypoint: string;
  styles?: string[];
  requires?: {
    hostApi?: string;
    services?: Array<{ id: string; version: string }>;
  };
  permissions?: string[];
}

export interface PluginModule {
  activate(host: PluginHost): void | Promise<void>;
  deactivate?(): void | Promise<void>;
}

export interface Disposable {
  dispose(): void;
}

export interface PluginHost {
  version: string;
  navigation: NavigationAPI;
  pages: PageAPI;
  http: HttpAPI;
  notifications: NotificationAPI;
}

export interface NavigationAPI {
  add(entry: { id: string; label: string; route: string; icon?: string; section?: string; order?: number }): Disposable;
}

export interface PageAPI {
  register(page: { route: string; mount(container: HTMLElement): void | Disposable }): Disposable;
}

export interface HttpAPI {
  get<T = unknown>(service: string, path: string): Promise<T>;
  post<T = unknown>(service: string, path: string, body?: unknown): Promise<T>;
  put<T = unknown>(service: string, path: string, body?: unknown): Promise<T>;
  delete<T = unknown>(service: string, path: string): Promise<T>;
}

export interface NotificationAPI {
  show(message: { title?: string; text: string; kind?: "info" | "success" | "warning" | "error" }): void;
}
