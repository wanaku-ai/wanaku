import { RefreshHandle, ResourcesTable } from './ResourcesTable';
import React, { useRef } from 'react';
import { useResources } from '../../hooks/api/use-resources';
import { getErrorMessage } from '../../utils/error';
import { ErrorNotification } from '../../components/ErrorNotification';
import { useErrorNotification } from '../../hooks/error-notifications';

export const ResourcesPage: React.FC = () => {
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const { removeResource } = useResources();
  const resourceTableRef = useRef<RefreshHandle>({ refresh: () => {} });

  async function handleDeleteResource(resourceName: string) {
    try {
      await removeResource(resourceName);
    } catch (error) {
      setErrorMessage(`Error deleting resource: ${getErrorMessage(error)}`);
    } finally {
      refreshResources();
    }
  }

  function refreshResources() {
    resourceTableRef.current.refresh();
  }

  return (
    <div>
      {errorMessage && (
        <ErrorNotification errorMessage={errorMessage} onClose={() => setErrorMessage(null)} />
      )}
      <h1 className="title">Resources</h1>
      <p className="description">
        Resources are a fundamental primitive in MCP that allow servers to expose data and content
        to LLM clients. Resources are auto-discovered from forwarded MCP servers. Configure
        forwarded MCP servers from the Forwards page.
      </p>
      <div id="page-content">
        <ResourcesTable
          onDelete={handleDeleteResource}
          onError={(msg) => setErrorMessage(msg)}
          ref={resourceTableRef}
        />
      </div>
    </div>
  );
};
