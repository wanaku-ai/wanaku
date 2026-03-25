import {Modal, Select, SelectItem, Stack, TextArea, TextInput, ToastNotification,} from "@carbon/react";
import React, {useCallback, useEffect, useState} from "react";
import {usePrompts} from "../../hooks/api/use-prompts";
import {Namespace, PromptReference} from "../../models";
import {PromptsTable} from "./PromptsTable";
import {listNamespaces} from "../../hooks/api/use-namespaces";

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
    } catch (error) {
      console.error("Error adding prompt:", error);
      setIsAddModalOpen(false);
      setErrorMessage("Error adding prompt: The prompt name must be unique");
    }
  };

  const handleDeletePrompt = async (promptName?: string) => {
    try {
      await removePrompt({ prompt: promptName });
      await updatePrompts();
    } catch (error) {
      console.error("Error deleting prompt:", error);
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
          <AddPromptModal
            onRequestClose={() => setIsAddModalOpen(false)}
            onSubmit={handleAddPrompt}
          />
        )}
      </div>
    </div>
  );
};

interface AddPromptModalProps {
  onRequestClose: () => void;
  onSubmit: (newPrompt: PromptReference) => void;
}

const AddPromptModal: React.FC<AddPromptModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [promptName, setPromptName] = useState("");
  const [description, setDescription] = useState("");
  const [messagesJson, setMessagesJson] = useState("");
  const [argumentsJson, setArgumentsJson] = useState("");
  const [toolReferences, setToolReferences] = useState("");
  const [fetchedNamespaceData, setFetchedNamespaceData] = useState<Namespace[]>([]);
  const [selectedNamespace, setSelectedNamespace] = useState('');

  useEffect(() => {
    listNamespaces().then((result) => {
      setFetchedNamespaceData(result.data.data as Namespace[]);
    });
  }, [listNamespaces]);

  const handleSelectionChange = (event) => {
    setSelectedNamespace(event.target.value);
  };

  const handleSubmit = () => {
    try {
      const messages = messagesJson ? JSON.parse(messagesJson) : [];
      const args = argumentsJson ? JSON.parse(argumentsJson) : [];
      const toolRefs = toolReferences ? toolReferences.split(',').map(t => t.trim()) : [];

      onSubmit({
        name: promptName,
        description,
        messages,
        arguments: args,
        toolReferences: toolRefs,
        namespace: selectedNamespace,
      });
    } catch (error) {
      console.error("Invalid JSON in prompt data:", error);
    }
  };

  return (
    <Modal
      open={true}
      modalHeading="Add a Prompt"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <Stack gap={5}>
        <TextInput
          id="prompt-name"
          labelText="Prompt Name"
          placeholder="e.g. code-reviewer"
          value={promptName}
          onChange={(e) => setPromptName(e.target.value)}
        />
        <TextInput
          id="prompt-description"
          labelText="Description"
          placeholder="e.g. A prompt for reviewing code"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <TextArea
          id="messages-json"
          labelText="Messages (JSON)"
          placeholder='Examples:
Text: {"role": "user", "content": {"type": "text", "text": "Review {{code}}"}}
Image: {"role": "user", "content": {"type": "image", "data": "base64...", "mimeType": "image/png"}}
Audio: {"role": "user", "content": {"type": "audio", "data": "base64...", "mimeType": "audio/wav"}}
Resource: {"role": "user", "content": {"type": "resource", "resource": {"location": "file:///path", "description": "File content", "mimeType": "text/plain"}}}'
          rows={6}
          value={messagesJson}
          onChange={(e) => setMessagesJson(e.target.value)}
        />
        <TextArea
          id="arguments-json"
          labelText="Arguments (JSON, optional)"
          placeholder='e.g. [{"name": "code", "description": "The code to review", "required": true}]'
          rows={3}
          value={argumentsJson}
          onChange={(e) => setArgumentsJson(e.target.value)}
        />
        <TextInput
          id="tool-references"
          labelText="Tool References (comma-separated, optional)"
          placeholder="e.g. code-analyzer, linter"
          value={toolReferences}
          onChange={(e) => setToolReferences(e.target.value)}
        />
        <Select
          id="namespace"
          labelText="Select a Namespace"
          helperText="Choose a Namespace from the list"
          value={selectedNamespace}
          onChange={handleSelectionChange}
        >
          <SelectItem text="Choose an option" value="" />
          {fetchedNamespaceData.map((namespace) => (
            <SelectItem
              key={namespace.id}
              id={namespace.id}
              text={namespace.path || "default"}
              value={namespace.id}
            />
          ))}
        </Select>
      </Stack>
    </Modal>
  );
};
