import {
  Modal,
  Select,
  SelectItem,
  Stack,
  TextArea,
  TextInput,
  ToastNotification,
} from "@carbon/react";
import React, { useCallback, useEffect, useState } from "react";
import { useTools } from "../../hooks/api/use-tools";
import { Namespace, ToolReference } from "../../models";
import { ToolsTable } from "./ToolsTable";
import { useNamespaces } from "../../hooks/api/use-namespaces";

export const ToolsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ToolReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listTools, addTool, removeTool } = useTools();

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
  const { listNamespaces } = useNamespaces();
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
      const schema = JSON.parse(inputSchema);
      onSubmit({
        name: toolName,
        description,
        uri,
        type: toolType,
        inputSchema: schema,
        namespace: selectedNamespace,
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
