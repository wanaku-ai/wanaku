import {ToastNotification} from "@carbon/react";
import {ResourceModal} from "./ResourceModal.tsx";
import {ResourcesTable} from "./ResourcesTable";
import React, {useEffect, useState} from "react";
import {ResourceReference} from "../../models";
import {useResources} from "../../hooks/api/use-resources";

export const ResourcesPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ResourceReference[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openedResource, setOpenedResource] = useState<ResourceReference>()
  const { listResources, exposeResource, updateResource, removeResource } = useResources();

  useEffect(() => {
    listResources().then((result) => {
      setFetchedData(result.data.data as ResourceReference[]);
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

  function reloadResources() {
    listResources().then((result) => {
      setFetchedData(result.data.data as ResourceReference[]);
    });
  }

  function handleModalClose() {
    setOpenedResource(undefined);
    setIsModalOpen(false);
  }

  function handleModalSubmit(resource: ResourceReference) {
    if (openedResource) {
      handleUpdateResource(resource)
    } else {
      handleAddResource(resource)
    }
  }

  const handleAddResource = async (newResource: ResourceReference) => {
    try {
      await exposeResource(newResource);
      setErrorMessage(null);
      reloadResources()
    } catch (error) {
      console.error("Error adding resource:", error);
      setErrorMessage("Error adding resource: The resource name must be unique");
    } finally {
      handleModalClose()
    }
  };

  const handleUpdateResource = async (resource: ResourceReference) => {
    try {
      await updateResource(resource);
      setErrorMessage(null);
      reloadResources()
    } catch (error) {
      console.error("Error updating resource:", error);
    } finally {
      handleModalClose()
    }
  }

  const onDelete = async (resourceName?: string) => {
    try {
      await removeResource({ resource: resourceName });
      listResources().then((result) => {
        setFetchedData(result.data.data as ResourceReference[]);
      });
    } catch (error) {
      console.error("Error deleting resource:", error);
      setErrorMessage(`Failed to delete resource: ${resourceName}`);
    }
  };

  const handleAddClick = () => {
    setIsModalOpen(true);
  };

  function handleEditClick(resource: ResourceReference): void {
    setOpenedResource(resource)
    setIsModalOpen(true);
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
      <div id="page-content">
        {fetchedData && (
          <ResourcesTable
            resources={fetchedData}
            onDelete={onDelete}
            onAdd={handleAddClick}
            onEdit={handleEditClick}
          />
        )}
      </div>
      {isModalOpen && (
        <ResourceModal
          resource={openedResource}
          onRequestClose={handleModalClose}
          onSubmit={handleModalSubmit}
        />
      )}
    </div>
  );
};
