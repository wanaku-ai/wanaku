import {ToastNotification} from "@carbon/react"
import {ResourceModal} from "./ResourceModal"
import {RefreshHandle, ResourcesTable} from "./ResourcesTable"
import React, {useRef, useState} from "react"
import {ResourceEntry} from "../../models"
import {useResources} from "../../hooks/api/use-resources"
import {getErrorMessage} from "../../utils/error"


export const ResourcesPage: React.FC = () => {

  const [errorMessage, setErrorMessage] = useState<string>()
  const [isModalOpen, setModalOpen] = useState(false)
  const [openedResource, setOpenedResource] = useState<ResourceEntry>()
  const { updateResource, removeResource } = useResources()
  const resourceTableRef = useRef<RefreshHandle>({ refresh: () => {} })

  function handleModalCancel() {
    setOpenedResource(undefined)
    setModalOpen(false)
  }

  async function handleUpdateResource(resource: ResourceEntry) {
    try {
      await updateResource(openedResource!.name!, resource)
    } catch (error) {
      setErrorMessage(`Error updating resource: ${getErrorMessage(error)}`)
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
      setErrorMessage(`Error deleting resource: ${getErrorMessage(error)}`)
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
        expose data and content to LLM clients.
        Resources are auto-discovered from forwarded MCP servers. Configure forwarded MCP servers from the Forwards page.
      </p>
      <div id="page-content">
        <ResourcesTable
          onEdit={(resource) => {
            setOpenedResource(resource)
            setModalOpen(true)
          }}
          onDelete={handleDeleteResource}
          onError={(msg) => setErrorMessage(msg)}
          ref={resourceTableRef}
        />
      </div>
      {isModalOpen && openedResource && (
        <ResourceModal
          openedResource={openedResource}
          onSubmit={handleUpdateResource}
          onCancel={handleModalCancel}
          onError={(msg) => setErrorMessage(msg)}
        />
      )}
    </div>
  )
}
