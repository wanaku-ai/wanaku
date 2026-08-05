import {ToastNotification,} from "@carbon/react";
import React, {useCallback, useEffect, useState} from "react";
import {usePrompts} from "../../hooks/api/use-prompts";
import {PromptReference} from "../../models";
import {PromptsTable} from "./PromptsTable";
import {PromptModal} from "./PromptsModal"

export const PromptsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<PromptReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listPrompts, addPrompt, removePrompt } = usePrompts();

  const updatePrompts = useCallback(async () => {
    return listPrompts().then((result) => {
      if (result.status !== 200 || !Array.isArray(result.data.data)) {
        setErrorMessage("Failed to fetch prompts. Please try again later.");
        setFetchedData([]);
      } else {
        setFetchedData(result.data.data);
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

  const handleAddPrompt = async (newPrompt: PromptReference) => {
    try {
      await addPrompt(newPrompt);
      setIsAddModalOpen(false);
      setErrorMessage(null);

      await updatePrompts();
    } catch {
      setIsAddModalOpen(false);
      setErrorMessage("Error adding prompt: The prompt name must be unique");
    }
  };

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
      </p>
      <div id="page-content">
        {fetchedData && (
          <PromptsTable
            fetchedData={fetchedData}
            onDelete={handleDeletePrompt}
            onAdd={() => setIsAddModalOpen(true)}
          />
        )}
        {isAddModalOpen && (
          <PromptModal
            onRequestClose={() => setIsAddModalOpen(false)}
            onSubmit={handleAddPrompt}
          />
        )}
      </div>
    </div>
  );
};
