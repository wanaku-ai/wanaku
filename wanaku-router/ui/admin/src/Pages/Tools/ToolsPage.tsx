import {
  Modal,
  Stack,
  TextArea,
  TextInput,
  ToastNotification,
} from "@carbon/react";
import React, { useCallback, useEffect, useState } from "react";
import { useTools } from "../../hooks/api/use-tools";
import { ToolReference } from "../../models";
import { ToolsTable } from "./ToolsTable";
import { ToolModal } from "./ToolModal"


export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [openedTool, setOpenedTool] = useState<ToolReference>()
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listTools, addTool, updateTool, removeTool } = useTools();

  const updateTools = useCallback(async () => {
    return listTools().then((result) => {
      if (result.status !== 200 || !Array.isArray(result.data.data)) {
        setErrorMessage("Failed to fetch tools. Please try again later.");
        setFetchedData([]);
      } else {
        setFetchedData(result.data.data);
      }

      setIsLoading(false);
    });
  }, [listTools]);

  useEffect(() => {
    updateTools();
  }, [updateTools]);

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

  function handleToolModalSubmit(tool: ToolReference): void {
    if (openedTool) {
      handleUpdateTool(tool)
    } else {
      handleAddTool(tool)
    }
  }

  function handleToolModalClose(): void {
    setOpenedTool(undefined)
    setIsAddModalOpen(false)
  }

  const handleAddTool = async (newTool: ToolReference) => {
    try {
      await addTool(newTool);
      setIsAddModalOpen(false);
      setErrorMessage(null);

      await updateTools();
    } catch (error) {
      console.error("Error adding tool:", error);
      setIsAddModalOpen(false);
      setErrorMessage("Error adding tool: The tool name must be unique");
    }
  };

  const handleUpdateTool = async(tool: ToolReference) => {
    try {
      await updateTool(tool)
      setErrorMessage(null)
      await updateTools();
    } catch (error) {
      console.error("Error updating tool:", error)
    } finally {
      handleToolModalClose()
    }
  }

  const handleImportToolset = async (tools: ToolReference[]) => {
    setErrorMessage(null);
    for (const tool of tools) {
      try {
        await addTool(tool);
      } catch (error) {
        console.error("Error adding tool:", error);
        setErrorMessage(`Failed to add tool: ${tool.name}`);
      }
    }
    setIsImportModalOpen(false);
    await updateTools();
  };

  const handleDeleteTool = async (toolName?: string) => {
    try {
      await removeTool({ tool: toolName });
      await updateTools();
    } catch (error) {
      console.error("Error deleting tool:", error);
      setErrorMessage(`Failed to delete tool: ${toolName}`);
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
      <h1 className="title">Tools</h1>
      <p className="description">
        A tool enables LLMs to execute tasks beyond their inherent capabilities
        by utilizing these tools. Each tool is uniquely identified by a name and
        defined with an input schema outlining the expected parameters.
      </p>
      <div id="page-content">
        {fetchedData && (
          <ToolsTable
            fetchedData={fetchedData}
            onDelete={handleDeleteTool}
            onImport={() => setIsImportModalOpen(true)}
            onAdd={() => setIsAddModalOpen(true)}
            onEdit={(tool: ToolReference) => { setOpenedTool(tool); setIsAddModalOpen(true) }}
          />
        )}
        {isAddModalOpen && (
          <ToolModal
            tool={openedTool}
            onRequestClose={handleToolModalClose}
            onSubmit={handleToolModalSubmit}
          />
        )}
        {isImportModalOpen && (
          <ImportToolsetModal
            onRequestClose={() => setIsImportModalOpen(false)}
            onSubmit={handleImportToolset}
          />
        )}
      </div>
    </div>
  );
};





interface ImportToolsetModalProps {
  onRequestClose: () => void;
  onSubmit: (tools: ToolReference[]) => void;
}

export const ImportToolsetModal: React.FC<ImportToolsetModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [toolsetJson, setToolsetJson] = useState("");
  const [toolsetUrl, setToolsetUrl] = useState("");

  const handleFetchToolset = async () => {
    if (toolsetUrl) {
      try {
        const response = await fetch(toolsetUrl);
        if (!response.ok) {
          throw new Error("Failed to fetch toolset from URL");
        }
        const tools = await response.json();
        setToolsetJson(JSON.stringify(tools, null, 2));
      } catch (error) {
        console.error("Error fetching toolset from URL:", error);
      }
    }
  };

  const handleSubmit = async () => {
    try {
      const tools = JSON.parse(toolsetJson);
      onSubmit(tools);
    } catch (error) {
      console.error("Invalid JSON for toolset:", error);
    }
  };

  return (
    <Modal
      open={true}
      modalHeading="Import Toolset"
      primaryButtonText="Import"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <Stack gap={7}>
        <TextInput
          id="toolset-url"
          labelText="Fetch from Toolset URL"
          placeholder="Enter the URL of the toolset JSON"
          value={toolsetUrl}
          onChange={(e) => setToolsetUrl(e.target.value)}
          onBlur={handleFetchToolset}
        />
        <TextArea
          id="toolset-json"
          labelText="Toolset JSON"
          placeholder="Paste your JSON array here"
          rows={10}
          required
          value={toolsetJson}
          onChange={(e) => setToolsetJson(e.target.value)}
          invalid={!toolsetJson}
          invalidText="This field is required"
        />
      </Stack>
    </Modal>
  );
};
