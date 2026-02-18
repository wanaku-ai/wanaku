import {
  ToastNotification
} from "@carbon/react"
import {useEffect, useState} from "react"
import {addForward, updateForward, listForwards, refreshForward, removeForward} from "../../hooks/api/use-forwards"
import {ForwardReference} from "../../models"
import {ForwardModal} from "./ForwardModal.tsx"
import {ForwardsTable} from "./ForwardsTable.tsx"

const ForwardsPage = () => {

  const [forwards, setForwards] = useState<ForwardReference[]>([])
  const [isModalOpen, setModalOpen] = useState(false)
  const [openedForward, setOpenedForward] = useState<ForwardReference>()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function fetchForwards() {
    listForwards().then((response) => {
      if (response.data?.data) {
        setForwards(response.data.data as ForwardReference[])
      }
    })
  }

  useEffect(() => {
    fetchForwards()
  }, [])
  
  function refreshAfterSubmit() {
    closeModal()
    fetchForwards()
  }

  function closeModal() {
    setOpenedForward(undefined)
    setModalOpen(false)
  }

  function handleAddButton() {
    setModalOpen(true)
  }

  function handleEditButton(forward: ForwardReference) {
    setOpenedForward(forward)
    setModalOpen(true)
  }
  
  function handleSubmit(forward: ForwardReference) {
    if (openedForward) {
      handleUpdateForward(forward)
    } else {
      handleAddForward(forward)
    }
  }

  async function handleAddForward(newForward: ForwardReference){
    try {
      const response = await addForward(newForward)
      if (response.status !== 200) {
        const errorData = response.data as { error?: { message?: string } } | null
        setErrorMessage(errorData?.error?.message || "Failed to add forward")
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred")
    } finally {
      refreshAfterSubmit()
    }
  }

  async function handleUpdateForward(forward: ForwardReference) {
    try {
      const response = await updateForward(forward)
      if (response.status !== 200) {
        const errorData = response.data as { error?: { message?: string } } | null
        setErrorMessage(errorData?.error?.message || "Failed to update forward")
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred")
    } finally {
      refreshAfterSubmit()
    }
  }

  async function handleDeleteForward(forward: ForwardReference) {
    try {
      const response = await removeForward(forward)
        if (response.status === 200) {
          fetchForwards()
        } else {
          setErrorMessage("Failed to delete forward")
        }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while deleting forward")
    }
  }

  async function handleRefreshForward(forward: ForwardReference) {
    try {
      const response = await refreshForward(forward)
      if (response.status === 200) {
        fetchForwards()
      } else {
        setErrorMessage("Failed to refresh forward")
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while refreshing forward")
    }
  }

  return (
    <div>
      <h1 className="title">Forwards</h1>
      <p className="description">
        A list of forwards registered in the system.
      </p>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={5000}
          style={{ float: "right" }}
        />
      )}
      <div id="page-content">
        {forwards && (
          <ForwardsTable
            forwards={forwards}
            onAdd={handleAddButton}
            onEdit={handleEditButton}
            onDelete={handleDeleteForward}
            onRefresh={handleRefreshForward}
          />
        )}
      </div>
      {isModalOpen && (
        <ForwardModal
          forward={openedForward}
          onRequestClose={closeModal}
          onSubmit={handleSubmit}
        />
      )}
    </div>
  )
}

export const Component = ForwardsPage