import { RefreshHandle, ResourcesTable } from './ResourcesTable';
import React, { useCallback, useRef } from 'react';
import { useResources } from '../../hooks/api/use-resources';
import { getErrorMessage } from '../../utils/error';
import { ErrorNotification } from '../../components/ErrorNotification';
import { useErrorNotification } from '../../hooks/error-notifications';

export const ResourcesPage: React.FC = () => {
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const { setResourceEnabled } = useResources();
  const resourceTableRef = useRef<RefreshHandle>({ refresh: () => {} });

  const refreshResources = useCallback(() => {
    resourceTableRef.current.refresh();
  }, []);

  const handleSetResourceEnabled = useCallback(
    async (resourceName: string, enabled: boolean) => {
      try {
        await setResourceEnabled(resourceName, enabled);
      } catch (error) {
        setErrorMessage(
          `Error ${enabled ? 'enabling' : 'disabling'} resource: ${getErrorMessage(error)}`,
        );
      } finally {
        refreshResources();
      }
    },
    [setResourceEnabled, setErrorMessage, refreshResources],
  );

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
          onSetEnabled={handleSetResourceEnabled}
          onError={setErrorMessage}
          ref={resourceTableRef}
        />
      </div>
    </div>
  );
};
