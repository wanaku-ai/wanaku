import { PageSkeleton } from '../../components/PageSkeleton';
import React, { useCallback, useEffect, useState } from 'react';
import { NamespaceEntry } from '../../models';
import { NamespaceTable } from './NamespacesTable';
import { NamespaceModal } from './NamespaceModal';
import { useNamespaces } from '../../hooks/api/use-namespaces';
import { sortedNamespaces } from './namespaces';
import { useErrorNotification } from '../../hooks/error-notifications';
import { ErrorNotification } from '../../components/ErrorNotification';
import { getInfo } from '../../api/wanaku-router-api';

export const NamespacesPage: React.FC = () => {
  const [namespaces, setNamespaces] = useState<NamespaceEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openedNamespace, setOpenedNamespace] = useState<NamespaceEntry>();
  // `undefined` until the server info arrives, so the table never shows a wrong address first.
  const [mcpPublicUrl, setMcpPublicUrl] = useState<string | null>();
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const { listNamespaces, createNamespace, updateNamespace, removeNamespace } = useNamespaces();

  const refreshNamespaces = useCallback(async () => {
    return listNamespaces().then((result) => {
      if (result.status !== 200 || !Array.isArray(result.data)) {
        setErrorMessage('Failed to fetch namespaces. Please try again later.');
        setNamespaces([]);
      } else {
        setNamespaces(sortedNamespaces(result.data));
      }
      setIsLoading(false);
    });
  }, [listNamespaces, setErrorMessage]);

  useEffect(() => {
    refreshNamespaces();
  }, [refreshNamespaces]);

  useEffect(() => {
    // Without the server info, the table keeps the local default address.
    getInfo()
      .then((result) => setMcpPublicUrl(result.data.mcpPublicUrl ?? null))
      .catch(() => setMcpPublicUrl(null));
  }, []);

  if (isLoading || mcpPublicUrl === undefined) return <PageSkeleton title="Namespaces" />;

  function handleModalClose(): void {
    setOpenedNamespace(undefined);
    setIsModalOpen(false);
  }

  async function handleModalSubmit(namespace: NamespaceEntry): Promise<void> {
    const save = openedNamespace ? updateNamespace : createNamespace;
    try {
      await save(namespace);
      setErrorMessage(null);
      await refreshNamespaces();
    } catch (error) {
      const action = openedNamespace ? 'updating' : 'creating';
      const reason = error instanceof Error ? `: ${error.message}` : '.';
      setErrorMessage(`Error ${action} namespace ${namespace.name}${reason}`);
    } finally {
      handleModalClose();
    }
  }

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
        <ErrorNotification errorMessage={errorMessage} onClose={() => setErrorMessage(null)} />
      )}
      <h1 className="title">Namespaces</h1>
      <p className="description">
        Concrete namespaces organize tools, resources, prompts, and A2A agents. Virtual namespaces
        expose entries from multiple concrete namespaces through one endpoint.
      </p>
      <div id="page-content">
        <NamespaceTable
          namespaces={namespaces}
          mcpPublicUrl={mcpPublicUrl}
          onAdd={() => setIsModalOpen(true)}
          onEdit={(namespace: NamespaceEntry) => {
            setOpenedNamespace(namespace);
            setIsModalOpen(true);
          }}
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
