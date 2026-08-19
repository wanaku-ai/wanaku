import {ToastNotification,} from "@carbon/react";
import React, {useCallback, useEffect, useState} from "react";
import {usePrompts} from "../../hooks/api/use-prompts";
import {PromptEntry} from "../../models";
import {PromptsTable} from "./PromptsTable";
import {unwrapData} from "../../utils/api-response";

export const PromptsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<PromptEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listPrompts, removePrompt } = usePrompts();

  const updatePrompts = useCallback(async () => {
    return listPrompts().then((result: any) => {
      const data = unwrapData<PromptEntry[]>(result);
      if (result.status !== 200 || !Array.isArray(data)) {
        setErrorMessage("Failed to fetch prompts. Please try again later.");
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

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10_000);

      return () => {
        clearTimeout(timer);
      };
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  const handleDeletePrompt = async (promptName?: string) => {
    try {
      await removePrompt(promptName!);
      await updatePrompts();
    } catch {
      setErrorMessage(`Failed to delete prompt: ${promptName}`);
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
      <h1 className="title">Prompts</h1>
      <p className="description">
        Prompts are reusable templates that can leverage multiple tools and provide
        example interactions for LLMs. Each prompt contains messages, arguments, and
        optional tool references.
        Prompts are auto-discovered from forwarded MCP servers. Configure forwarded MCP servers from the Forwards page.
      </p>
      <div id="page-content">
        {fetchedData && (
          <PromptsTable
            fetchedData={fetchedData}
            onDelete={handleDeletePrompt}
          />
        )}
      </div>
    </div>
  );
};
