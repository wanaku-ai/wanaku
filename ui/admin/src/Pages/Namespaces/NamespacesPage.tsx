import {InlineNotification} from "@carbon/react";
import {PageSkeleton} from "../../components/PageSkeleton";
import React, {useCallback, useEffect, useState} from "react";
import {NamespaceEntry} from "../../models";
import {NamespaceTable} from "./NamespacesTable";
import {NamespaceModal} from "./NamespaceModal";
import {useNamespaces} from "../../hooks/api/use-namespaces";
import {sortedNamespaces} from "./namespaces"

export const NamespacesPage: React.FC = () => {
  const [namespaces, setNamespaces] = useState<NamespaceEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openedNamespace, setOpenedNamespace] = useState<NamespaceEntry>();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listNamespaces, createNamespace, updateNamespace, removeNamespace } = useNamespaces();

  const refreshNamespaces = useCallback(async () => {
    return listNamespaces().then((result) => {
      if (result.status !== 200 || !Array.isArray(result.data)) {
        setErrorMessage("Failed to fetch namespaces. Please try again later.");
        setNamespaces([]);
      } else {
        setNamespaces(sortedNamespaces(result.data));
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

  if (isLoading) return <PageSkeleton title="Namespaces" />;

  function handleModalClose(): void {
    setOpenedNamespace(undefined);
    setIsModalOpen(false);
  }

  function handleModalSubmit(namespace: NamespaceEntry): void {
    if (openedNamespace) {
      handleUpdate(namespace);
    } else {
      handleCreate(namespace);
    }
  }

  const handleCreate = async (namespace: NamespaceEntry) => {
    try {
      await createNamespace(namespace);
      setIsModalOpen(false);
      setErrorMessage(null);
      await refreshNamespaces();
    } catch {
      setIsModalOpen(false);
      setErrorMessage("Error creating namespace. The path may already be in use or is invalid.");
    }
  };

  const handleUpdate = async (namespace: NamespaceEntry) => {
    try {
      await updateNamespace(namespace);
      setErrorMessage(null);
      await refreshNamespaces();
    } catch {
      setErrorMessage("Error updating namespace.");
    } finally {
      handleModalClose();
    }
  };

  const handleDelete = async (namespace: NamespaceEntry) => {
    try {
      if (!namespace.name) return;
      await removeNamespace(namespace.name);
      await refreshNamespaces();
    } catch {
      setErrorMessage(`Failed to delete namespace: ${namespace.name}`);
    }
  };

  return (
    <div>
      {errorMessage && (
        <InlineNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          lowContrast
          hideCloseButton={false}
        />
      )}
      <h1 className="title">Namespaces</h1>
      <p className="description">
        Namespaces help organize and isolate tools and resources, preventing LLM context bloat.
        Each namespace acts as a separate container accessible via its own MCP endpoint path.
      </p>
      <div id="page-content">
        <NamespaceTable
          namespaces={namespaces}
          onAdd={() => setIsModalOpen(true)}
          onEdit={(namespace: NamespaceEntry) => { setOpenedNamespace(namespace); setIsModalOpen(true); }}
          onDelete={handleDelete}
        />
        {isModalOpen && (
          <NamespaceModal
            namespaces={namespaces}
            openedNamespace={openedNamespace}
            onRequestClose={handleModalClose}
            onSubmit={handleModalSubmit}
          />
        )}
      </div>
    </div>
  );
};
