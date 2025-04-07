import { ToastNotification } from "@carbon/react";
import { AddResourceModal } from "./AddResourceModal";
import { ResourcesTable } from "./ResourcesTable";
import React, { useState, useEffect } from "react";
import { ResourceReference } from "../../models";
import { useResources } from "../../hooks/api/use-resources";

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

  const handleAddClick = () => {
    setIsAddModalOpen(true);
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
      <h1 className="title">Resources</h1>
      <p className="description">
        Resources are a fundamental primitive in MCP that allow servers to
        expose data and content to LLM clients
      </p>
      <div id="page-content">
        {fetchedData && (
          <ResourcesTable
            resources={fetchedData}
            onDelete={onDelete}
            onAdd={handleAddClick}
          />
        )}
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
