import { Add, Upload } from "@carbon/icons-react";
import {
  Button,
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tile,
  Modal,
  TextInput,
  Select,
  SelectItem,
  TextArea,
  ToastNotification,
} from "@carbon/react";
import { FunctionComponent, useState, useEffect } from "react";
import { WanakuLinks } from "../../router/links.models";

interface ToolType {
  name: string;
  type: string;
  description: string;
  uri: string;
  inputSchema: {
    type: string;
    properties: {
      count: {
        type: string;
        description: string;
      };
    };
    required: string[];
  };
}

export const ToolsPage: FunctionComponent = () => {
  const [fetchedData, setFetchedData] = useState<ToolType[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await fetch(WanakuLinks.ListTools);
        const data = await response.json();
        setFetchedData(data);
      } catch (error) {
        console.error("Error fetching data:", error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchData();
  }, []);
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);
  if (isLoading) return <div>Loading...</div>;
  const testData: ToolType[] = [
    {
      name: "meow-facts",
      description: "Retrieve random facts about cats",
      uri: "https://meowfacts.herokuapp.com?count={count}",
      type: "http",
      inputSchema: {
        type: "object",
        properties: {
          count: {
            type: "int",
            description: "The count of facts to retrieve",
          },
        },
        required: ["count"],
      },
    },
    {
      name: "dog-facts",
      description: "Retrieve random facts about dogs",
      uri: "https://dogapi.dog/api/v2/facts?limit={count}",
      type: "http",
      inputSchema: {
        type: "object",
        properties: {
          count: {
            type: "int",
            description: "The count of facts to retrieve",
          },
        },
        required: ["count"],
      },
    },
  ];

  const data = fetchedData || testData;

  console.log(data);
  const headers = ["Name", "Type", "Description", "URI", "Input Schema"];
  let ToolsList = <></>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const formatInputSchema = (inputSchema: any) => {
    return (
      Object.entries(inputSchema.properties)
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .map(([key, value]: [string, any]) => {
          return `${key}: ${value.type} - ${value.description}`;
        })
        .join("\n")
    );
  };
  if (data) {
    ToolsList = (
      <Grid>
        <Column lg={12} md={8} sm={4}>
          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              alignItems: "center",
            }}
          >
            <Button
              kind="secondary"
              renderIcon={Upload}
              onClick={() => setIsImportModalOpen(true)}
            >
              Import Toolset
            </Button>
            <Button renderIcon={Add} onClick={() => setIsModalOpen(true)}>
              Add Tool
            </Button>
          </div>
          <Table aria-label="Tools table">
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader id={header} key={header}>
                    {header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((row: ToolType) => (
                <TableRow key={row.name}>
                  <TableCell>{row.name}</TableCell>
                  <TableCell>{row.type}</TableCell>
                  <TableCell>{row.description}</TableCell>
                  <TableCell style={{ wordWrap: "break-word" }}>
                    {row.uri}
                  </TableCell>
                  <TableCell style={{ fontSize: "14px" }}>
                    {formatInputSchema(row.inputSchema)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Column>
        <Column sm={4}>
          <Tile></Tile>
        </Column>
      </Grid>
    );
  }

  const handleAddTool = async () => {
    const name = (document.getElementById("tool-name") as HTMLInputElement)
      .value;
    const description = (
      document.getElementById("tool-description") as HTMLInputElement
    ).value;
    const uri = (document.getElementById("tool-uri") as HTMLInputElement).value;
    const type = (document.getElementById("tool-type") as HTMLSelectElement)
      .value;
    const inputSchema = JSON.parse(
      (document.getElementById("input-schema") as HTMLInputElement).value
    );

    const newTool = {
      name,
      description,
      uri,
      type,
      inputSchema,
    };

    try {
      const response = await fetch("/api/v1/tools/add", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(newTool),
      });

      if (!response.ok) {
        const errorData = await response.json();
        setErrorMessage(errorData.message || "Failed to add tool");
        return;
      }

      setIsModalOpen(false);
      setErrorMessage(null);
      // Optionally, refetch the tools list to update the table
      const updatedData = await fetch(WanakuLinks.ListTools);
      const data = await updatedData.json();
      setFetchedData(data);
    } catch (error) {
      console.error("Error adding tool:", error);
      setErrorMessage("Error adding tool: The tool name must be unique");
      setIsImportModalOpen(false);
    }
  };

  const handleImportToolset = async () => {
    const toolsetJson = (
      document.getElementById("toolset-json") as HTMLTextAreaElement
    ).value;
    const toolset = JSON.parse(toolsetJson);
    setErrorMessage(null);

    for (const tool of toolset) {
      const response = await fetch(WanakuLinks.AddTool, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(tool),
      });

      if (!response.ok) {
        setErrorMessage(`Failed to add tool: ${tool.name}`);
      }
    }

    setIsImportModalOpen(false);
    // Optionally, refetch the tools list to update the table
    const updatedData = await fetch(WanakuLinks.ListTools);
    const data = await updatedData.json();
    setFetchedData(data);
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
      {ToolsList}
      <Modal
        open={isModalOpen}
        modalHeading="Add a Tool"
        primaryButtonText="Add"
        secondaryButtonText="Cancel"
        onRequestClose={() => setIsModalOpen(false)}
        onRequestSubmit={handleAddTool}
      >
        <TextInput
          id="tool-name"
          labelText="Tool Name"
          placeholder="e.g. meow-facts"
        />
        <TextInput
          id="tool-description"
          labelText="Description"
          placeholder="e.g. Retrieve random facts about cats"
        />
        <TextInput
          id="tool-uri"
          labelText="URI"
          placeholder="e.g. https://meowfacts.herokuapp.com?count={count}"
        />
        <Select id="tool-type" defaultValue="http" labelText="Type">
          <SelectItem value="http" text="HTTP" />
          <SelectItem value="kafka" text="Kafka" />
          <SelectItem
            value="camel-route"
            text="Camel Route (for prototyping)"
          />
        </Select>
        <TextInput
          id="input-schema"
          labelText="Input Schema"
          placeholder='e.g. {"type": "object", "properties": {"count": {"type": "int", "description": "The count of facts to retrieve"}}, "required": ["count"]}'
        />
      </Modal>
      <Modal
        open={isImportModalOpen}
        modalHeading="Import Toolset"
        primaryButtonText="Import"
        secondaryButtonText="Cancel"
        onRequestClose={() => setIsImportModalOpen(false)}
        onRequestSubmit={handleImportToolset}
      >
        <TextArea
          id="toolset-json"
          labelText="Toolset JSON"
          placeholder="Paste your JSON array here"
          rows={10}
        />
      </Modal>
    </div>
  );
};
