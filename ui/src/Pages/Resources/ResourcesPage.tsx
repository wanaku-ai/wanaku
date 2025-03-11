import {
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Button,
  ToastNotification,
  Modal,
  TextInput,
  Select,
  SelectItem,
} from "@carbon/react";
import React, { useState, useEffect } from "react";
import { ResourceReference } from "../../models";
import { useResources } from "../../hooks/api/use-resources";
import { TrashCan } from "@carbon/icons-react";

export const ResourcesPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ResourceReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const { listResources, exposeResource, removeResource } = useResources();

  useEffect(() => {
    listResources().then((result) => {
      setFetchedData(result.data.data!);
      setIsLoading(false);
    });
  }, [listResources]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  const handleAddResource = async (newResource: ResourceReference) => {
    try {
      await exposeResource(newResource);
      setIsAddModalOpen(false);
      setErrorMessage(null);
      listResources().then((result) => {
        setFetchedData(result.data.data!);
      });
    } catch (error) {
      console.error("Error adding resource:", error);
      setIsAddModalOpen(false);
      setErrorMessage("Error adding resource: The resource name must be unique");
    }
  };

  const onDelete = async (resourceName?: string) => {
    try {
      await removeResource({ resource: resourceName });
      listResources().then((result) => {
        setFetchedData(result.data.data!);
      });
    } catch (error) {
      console.error("Error deleting resource:", error);
      setErrorMessage(`Failed to delete resource: ${resourceName}`);
    }
  };

  const headers = ["Name", "Location", "Type", "Description", "Actions"];
  let resourcesList = <></>;

  if (fetchedData) {
    resourcesList = (
      <Grid>
        <Column lg={12} md={8} sm={4}>
          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              alignItems: "center",
            }}
          >
            <Button onClick={() => setIsAddModalOpen(true)}>Add Resource</Button>
          </div>
          <Table aria-label="Resources table">
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
              {fetchedData.map((row: ResourceReference) => (
                <TableRow key={row.name}>
                  <TableCell>{row.name}</TableCell>
                  <TableCell>{row.location}</TableCell>
                  <TableCell>{row.type}</TableCell>
                  <TableCell>{row.description}</TableCell>
                  <TableCell>
                    <Button
                      kind="ghost"
                      renderIcon={TrashCan}
                      hasIconOnly
                      iconDescription="Delete"
                      onClick={() => onDelete(row.name)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Column>
      </Grid>
    );
  }

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
      <h1 className="title">Resources</h1>
      <p className="description">
        Resources are a fundamental primitive in MCP that allow servers to
        expose data and content to LLM clients
      </p>
      <div style={{ background: "#161616", paddingTop: "2rem" }}>
        {resourcesList}
      </div>
      {isAddModalOpen && (
        <AddResourceModal
          onRequestClose={() => setIsAddModalOpen(false)}
          onSubmit={handleAddResource}
        />
      )}
    </div>
  );
};

interface AddResourceModalProps {
  onRequestClose: () => void;
  onSubmit: (newResource: ResourceReference) => void;
}

const AddResourceModal: React.FC<AddResourceModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [resourceName, setResourceName] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState("");
  const [resourceType, setResourceType] = useState("http");

  const handleSubmit = () => {
    onSubmit({
      name: resourceName,
      description,
      location,
      type: resourceType,
    });
  };

  return (
    <Modal
      open={true}
      modalHeading="Add a Resource"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <TextInput
        id="resource-name"
        labelText="Resource Name"
        placeholder="e.g. example-resource"
        value={resourceName}
        onChange={(e) => setResourceName(e.target.value)}
      />
      <TextInput
        id="resource-description"
        labelText="Description"
        placeholder="e.g. Description of the resource"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <TextInput
        id="resource-location"
        labelText="Location"
        placeholder="e.g. /path/to/resource"
        value={location}
        onChange={(e) => setLocation(e.target.value)}
      />
      <Select
        id="resource-type"
        labelText="Type"
        defaultValue="file"
        value={resourceType}
        onChange={(e) => setResourceType(e.target.value)}
      >
        <SelectItem value="file" text="Local file" />
        <SelectItem value="aws2-s3" text="AWS S3" />
        <SelectItem value="ftp" text="FTP" />
      </Select>
    </Modal>
  );
};
