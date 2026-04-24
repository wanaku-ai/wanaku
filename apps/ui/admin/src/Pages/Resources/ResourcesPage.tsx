import {ToastNotification} from "@carbon/react"
import {ResourceModal} from "./ResourceModal"
import {RefreshHandle, ResourcesTable} from "./ResourcesTable"
import React, {useRef, useState} from "react"
import {ResourceReference} from "../../models"
import {useResources} from "../../hooks/api/use-resources"


export const ResourcesPage: React.FC = () => {
  
  const [errorMessage, setErrorMessage] = useState<string>()
  const [isModalOpen, setModalOpen] = useState(false)
  const [openedResource, setOpenedResource] = useState<ResourceReference>()
  const { exposeResource, updateResource, removeResource } = useResources()
  const resourceTableRef = useRef<RefreshHandle>({ refresh: () => {} })
  
  async function handleModalSubmit(resource: ResourceReference) {
    if (openedResource) {
      await handleUpdateResource(resource)
    } else {
      await handleAddResource(resource)
    }
  }
  
  function handleModalCancel() {
    setOpenedResource(undefined)
    setModalOpen(false)
  }

  async function handleAddResource(resource: ResourceReference) {
    try {
      await exposeResource(resource)
    } catch (error) {
      setErrorMessage(`Error adding resource: ${error}`)
    } finally {
      setModalOpen(false)
      refreshResources()
    }
  }

  async function handleUpdateResource(resource: ResourceReference) {
    try {
      await updateResource(resource)
    } catch (error) {
      setErrorMessage(`Error updating resource: ${error}`)
    } finally {
      setOpenedResource(undefined)
      setModalOpen(false)
      refreshResources()
    }
  }

  async function handleDeleteResource(resourceName: string) {
    try {
      await removeResource(resourceName)
    } catch (error) {
      setErrorMessage(`Error deleting resource: ${error}`)
    } finally {
      refreshResources()
    }
  }
  
  function refreshResources() {
    resourceTableRef.current.refresh()
  }

  return (
    <div>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(undefined)}
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
        <ResourcesTable
          onAdd={() => setModalOpen(true)}
          onEdit={(resource) => {
            setOpenedResource(resource)
            setModalOpen(true)
          }}
          onDelete={handleDeleteResource}
          ref={resourceTableRef}
        />
      </div>
      {isModalOpen && (
        <ResourceModal
          openedResource={openedResource}
          onSubmit={handleModalSubmit}
          onCancel={handleModalCancel}
        />
      )}
    </div>
  )
}
