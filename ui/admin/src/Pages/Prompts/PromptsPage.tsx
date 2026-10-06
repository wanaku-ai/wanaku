import { PageSkeleton } from '../../components/PageSkeleton';
import React, { useCallback, useEffect, useState } from 'react';
import { usePrompts } from '../../hooks/api/use-prompts';
import { PromptEntry } from '../../models';
import { PromptsTable } from './PromptsTable';
import { unwrapData } from '../../utils/api-response';
import { useErrorNotification } from '../../hooks/error-notifications';
import { ErrorNotification } from '../../components/ErrorNotification';

export const PromptsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<PromptEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const { listPrompts, setPromptEnabled } = usePrompts();

  const updatePrompts = useCallback(async () => {
    return listPrompts().then((result: any) => {
      const data = unwrapData<PromptEntry[]>(result);
      if (result.status !== 200 || !Array.isArray(data)) {
        setErrorMessage('Failed to fetch prompts. Please try again later.');
        setFetchedData([]);
      } else {
        setFetchedData(data);
      }

      setIsLoading(false);
    });
  }, [listPrompts]);

  useEffect(() => {
    updatePrompts();
  }, [updatePrompts]);

  const handleSetPromptEnabled = useCallback(
    async (promptName: string, enabled: boolean) => {
      try {
        await setPromptEnabled(promptName, enabled);
        await updatePrompts();
      } catch {
        setErrorMessage(`Failed to ${enabled ? 'enable' : 'disable'} prompt: ${promptName}`);
      }
    },
    [setPromptEnabled, updatePrompts, setErrorMessage],
  );

  if (isLoading) return <PageSkeleton title="Prompts" />;

  return (
    <div>
      {errorMessage && (
        <ErrorNotification errorMessage={errorMessage} onClose={() => setErrorMessage(null)} />
      )}
      <h1 className="title">Prompts</h1>
      <p className="description">
        Prompts are reusable templates that can leverage multiple tools and provide example
        interactions for LLMs. Each prompt contains messages, arguments, and optional tool
        references. Prompts are auto-discovered from forwarded MCP servers. Configure forwarded MCP
        servers from the Forwards page.
      </p>
      <div id="page-content">
        {fetchedData && (
          <PromptsTable fetchedData={fetchedData} onSetEnabled={handleSetPromptEnabled} />
        )}
      </div>
    </div>
  );
};
