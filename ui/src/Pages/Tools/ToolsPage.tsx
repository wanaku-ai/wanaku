import React, { useState, useEffect } from "react";
import {
  ToastNotification,
  Modal,
  TextInput,
  Select,
  SelectItem,
  TextArea,
} from "@carbon/react";
import { useTools } from "../../hooks/api/use-tools";
import { ToolReference } from "../../models";
import { ToolsTable } from "./ToolsTable";

export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listTools, addTool, removeTool } = useTools();

  useEffect(() => {
    listTools().then((result) => {
      setFetchedData(result.data.data!);
      setIsLoading(false);
    });
  }, [listTools]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  const handleAddTool = async (newTool: ToolReference) => {
    try {
      await addTool(newTool);
      setIsAddModalOpen(false);
      setErrorMessage(null);
      listTools().then((result) => {
        setFetchedData(result.data.data!);
      });
    } catch (error) {
      console.error("Error adding tool:", error);
      setIsAddModalOpen(false);
      setErrorMessage("Error adding tool: The tool name must be unique");
    }
  };

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
    listTools().then((result) => {
      setFetchedData(result.data.data!);
    });
  };

  const handleDeleteTool = async (toolName?: string) => {
    try {
      await removeTool({ tool: toolName });
      listTools().then((result) => {
        setFetchedData(result.data.data!);
      });
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
      <div style={{ background: "#161616", paddingTop: "2rem" }}>
        {fetchedData && (
          <ToolsTable
            fetchedData={fetchedData}
            onDelete={handleDeleteTool}
            onImport={() => setIsImportModalOpen(true)}
            onAdd={() => setIsAddModalOpen(true)}
          />
        )}
        {isAddModalOpen && (
          <AddToolModal
            onRequestClose={() => setIsAddModalOpen(false)}
            onSubmit={handleAddTool}
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

interface AddToolModalProps {
  onRequestClose: () => void;
  onSubmit: (newTool: ToolReference) => void;
}

const AddToolModal: React.FC<AddToolModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [toolName, setToolName] = useState("");
  const [description, setDescription] = useState("");
  const [uri, setUri] = useState("");
  const [toolType, setToolType] = useState("http");
  const [inputSchema, setInputSchema] = useState("");

  const handleSubmit = () => {
    try {
      const schema = JSON.parse(inputSchema);
      onSubmit({
        name: toolName,
        description,
        uri,
        type: toolType,
        inputSchema: schema,
      });
    } catch (error) {
      console.error("Invalid JSON in input schema:", error);
    }
  };

  return (
    <Modal
      open={true}
      modalHeading="Add a Tool"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <TextInput
        id="tool-name"
        labelText="Tool Name"
        placeholder="e.g. meow-facts"
        value={toolName}
        onChange={(e) => setToolName(e.target.value)}
      />
      <TextInput
        id="tool-description"
        labelText="Description"
        placeholder="e.g. Retrieve random facts about cats"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <TextInput
        id="tool-uri"
        labelText="URI"
        placeholder="e.g. https://meowfacts.herokuapp.com?count={count}"
        value={uri}
        onChange={(e) => setUri(e.target.value)}
      />
      <Select
        id="tool-type"
        labelText="Type"
        defaultValue="http"
        value={toolType}
        onChange={(e) => setToolType(e.target.value)}
      >
        <SelectItem value="http" text="HTTP" />
        <SelectItem value="kafka" text="Kafka" />
        <SelectItem value="camel-route" text="Camel Route (for prototyping)" />
      </Select>
      <TextInput
        id="input-schema"
        labelText="Input Schema"
        placeholder='e.g. {"type": "object", "properties": {"count": {"type": "int", "description": "The count of facts to retrieve"}}, "required": ["count"]}'
        value={inputSchema}
        onChange={(e) => setInputSchema(e.target.value)}
      />
    </Modal>
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

  const handleSubmit = () => {
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
      <TextArea
        id="toolset-json"
        labelText="Toolset JSON"
        placeholder="Paste your JSON array here"
        rows={10}
        value={toolsetJson}
        onChange={(e) => setToolsetJson(e.target.value)}
      />
    </Modal>
  );
};
