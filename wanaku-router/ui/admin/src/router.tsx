import { createHashRouter } from "react-router-dom";
import App from "./App";
import { ErrorPage } from "./Pages/Error";
import { Links } from "./router/links.models";

export const router = createHashRouter([
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
      // {
      //   path: Links.LLMChat,
      //   lazy: async () => import("./Pages/LLMChat"),
      // },
      {
        path: Links.CodeExecution,
        lazy: async () => import("./Pages/CodeExecution"),
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
        path: Links.DataStores,
        lazy: async () => import("./Pages/DataStores"),
      },
    ],
  },
]);
