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
        path: Links.LLMChat,
        lazy: async () => import("./Pages/LLMChat"),
      },
    ],
  },
]);
