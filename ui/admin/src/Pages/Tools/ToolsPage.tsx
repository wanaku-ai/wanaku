import { PageSkeleton } from '../../components/PageSkeleton';
import React, { useCallback, useEffect, useState } from 'react';
import { useTools } from '../../hooks/api/use-tools';
import { ToolEntry } from '../../models';
import { ToolsTable } from './ToolsTable';
import { unwrapData } from '../../utils/api-response';
import { useErrorNotification } from '../../hooks/error-notifications';
import { ErrorNotification } from '../../components/ErrorNotification';

export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const { listTools, setToolEnabled } = useTools();

  const updateTools = useCallback(async () => {
    return listTools().then((result: any) => {
      const data = unwrapData<ToolEntry[]>(result);
      if (result.status !== 200 || !Array.isArray(data)) {
        setErrorMessage('Failed to fetch tools. Please try again later.');
        setFetchedData([]);
      } else {
        setFetchedData(data);
      }

      setIsLoading(false);
    });
  }, [listTools]);

  useEffect(() => {
    updateTools();
  }, [updateTools]);

  if (isLoading) return <PageSkeleton title="Tools" />;

  const handleSetToolEnabled = async (toolName: string, enabled: boolean) => {
    try {
      await setToolEnabled(toolName, enabled);
      await updateTools();
    } catch {
      setErrorMessage(`Failed to ${enabled ? 'enable' : 'disable'} tool: ${toolName}`);
    }
  };

  return (
    <div>
      {errorMessage && (
        <ErrorNotification errorMessage={errorMessage} onClose={() => setErrorMessage(null)} />
      )}
      <h1 className="title">Tools</h1>
      <p className="description">
        A tool enables LLMs to execute tasks beyond their inherent capabilities by utilizing these
        tools. Each tool is uniquely identified by a name and defined with an input schema outlining
        the expected parameters. Tools are auto-discovered from forwarded MCP servers. Configure
        forwarded MCP servers from the Forwards page.
      </p>
      <div id="page-content">
        {fetchedData && (
          <ToolsTable fetchedData={fetchedData} onSetEnabled={handleSetToolEnabled} />
        )}
      </div>
    </div>
  );
};
