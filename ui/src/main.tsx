import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import "./index.scss";
import { router } from './router';

import { NamespaceProvider } from "./contexts/NamespaceContext";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <NamespaceProvider>
      <RouterProvider router={router} />
    </NamespaceProvider>
  </StrictMode>
);

