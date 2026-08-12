import {createHashRouter} from "react-router-dom";
import App from "./App";
import {ErrorPage} from "./Pages/Error";
import {Links} from "./router/links.models";
import { PluginMount } from "./plugins/PluginMount";
import ErrorBoundary from "./components/ErrorBoundary";
import type { Disposable } from "./plugins/types";

interface PluginPage {
  route: string;
  mount: (container: HTMLElement) => void | Disposable;
  pluginId: string;
}

export function buildRouter(pluginPages: PluginPage[]) {
  const pluginRoutes = pluginPages.map(({ route, mount, pluginId }) => ({
    path: route.replace(/^\//, ""),
    element: (
      <ErrorBoundary key={route}>
        <PluginMount mount={mount} pluginId={pluginId} />
      </ErrorBoundary>
    ),
  }));

  return createHashRouter([
    {
      path: Links.Home,
      element: <App />,
      errorElement: <ErrorPage />,
      children: [
        {
          index: true,
          lazy: async () => import("./Pages/Dashboard"),
        },
        {
          path: Links.Tools,
          lazy: async () => import("./Pages/Tools"),
        },
        {
          path: Links.Resources,
          lazy: async () => import("./Pages/Resources"),
        },
        {
          path: Links.Prompts,
          lazy: async () => import("./Pages/Prompts"),
        },
        {
          path: Links.LLMChat,
          lazy: async () => import("./Pages/LLMChat"),
        },
        {
          path: Links.ToolCalls,
          lazy: async () => import("./Pages/ToolCalls"),
        },
        {
          path: Links.Capabilities,
          lazy: async () => import("./Pages/Targets"),
        },
        {
          path: Links.Namespaces,
          lazy: async () => import("./Pages/Namespaces"),
        },
        {
          path: Links.Forwards,
          lazy: async () => import("./Pages/Forwards"),
        },
        {
          path: Links.Evaluators,
          lazy: async () => import("./Pages/Evaluators"),
        },
        {
          path: Links.Plugins,
          lazy: async () => import("./Pages/Plugins"),
        },
        ...pluginRoutes,
      ],
    },
  ]);
}
