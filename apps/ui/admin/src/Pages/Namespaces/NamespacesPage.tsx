import {ToastNotification} from "@carbon/react";
import React, {useCallback, useEffect, useState} from "react";
import {Namespace} from "../../models";
import {NamespaceTable} from "./NamespacesTable";
import {NamespaceModal} from "./NamespaceModal";
import {useNamespaces} from "../../hooks/api/use-namespaces";

export const NamespacesPage: React.FC = () => {
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openedNamespace, setOpenedNamespace] = useState<Namespace>();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listNamespaces, createNamespace, updateNamespace, removeNamespace } = useNamespaces();

  const refreshNamespaces = useCallback(async () => {
    return listNamespaces().then((result) => {
      if (result.status !== 200 || !Array.isArray(result.data.data)) {
        setErrorMessage("Failed to fetch namespaces. Please try again later.");
        setNamespaces([]);
      } else {
        setNamespaces(result.data.data);
      }
      setIsLoading(false);
    });
  }, [listNamespaces]);

  useEffect(() => {
    refreshNamespaces();
  }, [refreshNamespaces]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10_000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  function handleModalClose(): void {
    setOpenedNamespace(undefined);
    setIsModalOpen(false);
  }

  function handleModalSubmit(namespace: Namespace): void {
    if (openedNamespace) {
      handleUpdate(namespace);
    } else {
      handleCreate(namespace);
    }
  }

  const handleCreate = async (namespace: Namespace) => {
    try {
      await createNamespace(namespace);
      setIsModalOpen(false);
      setErrorMessage(null);
      await refreshNamespaces();
    } catch (error) {
      console.error("Error creating namespace:", error);
      setIsModalOpen(false);
      setErrorMessage("Error creating namespace. The name or path may already be in use.");
    }
  };

  const handleUpdate = async (namespace: Namespace) => {
    try {
      await updateNamespace(namespace);
      setErrorMessage(null);
      await refreshNamespaces();
    } catch (error) {
      console.error("Error updating namespace:", error);
      setErrorMessage("Error updating namespace.");
    } finally {
      handleModalClose();
    }
  };

  const handleDelete = async (namespace: Namespace) => {
    try {
      if (!namespace.id) return;
      await removeNamespace(namespace.id);
      await refreshNamespaces();
    } catch (error) {
      console.error("Error deleting namespace:", error);
      setErrorMessage(`Failed to delete namespace: ${namespace.name || namespace.path}`);
    }
  };

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
        <NamespaceTable
          namespaces={namespaces}
          onAdd={() => setIsModalOpen(true)}
          onEdit={(namespace: Namespace) => { setOpenedNamespace(namespace); setIsModalOpen(true); }}
          onDelete={handleDelete}
        />
        {isModalOpen && (
          <NamespaceModal
            namespace={openedNamespace}
            onRequestClose={handleModalClose}
            onSubmit={handleModalSubmit}
          />
        )}
      </div>
    </div>
  );
};
