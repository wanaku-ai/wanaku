import { ToastNotification } from "@carbon/react";
import React, { useState, useEffect, useCallback } from "react";
import { Namespace } from "../../models";
import { NamespaceTable } from "./NamespacesTable";
import { listNamespaces } from "../../hooks/api/use-namespaces";

export const NamespacesPage: React.FC = () => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Use useCallback to memoize the fetch function
  const loadNamespaces = useCallback(async () => {
    try {
      setIsLoading(true);
      const result = await listNamespaces(null);
      setNamespaces(result.data.data as Namespace[]);
    } catch (error) {
      setErrorMessage('Failed to load namespaces');
    } finally {
      setIsLoading(false);
    }
  }, []); // Empty dependency array since getCachedNamespacesList handles its own caching

  useEffect(() => {
    loadNamespaces();
  }, [loadNamespaces]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  return (
    <div>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ float: "right" }}
        />
      )}
      <h1 className="title">Namespaces</h1>
      <p className="description">
        Namespaces help organize and isolate tools and resources, preventing LLM context bloat and improving deployment efficiency. 
        Wanaku provides up to 10 namespace slots (ns-0 to ns-9) plus a default namespace for general use. 
        Each namespace acts as a separate logical container to ensure tools don't interfere with each other.
      </p>
      <div id="page-content">
        <NamespaceTable namespaces={namespaces} />
      </div>
    </div>
  );
};