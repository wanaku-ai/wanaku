import {
  ToastNotification
} from "@carbon/react"
import {useEffect, useState} from "react"
import {addForward, listForwards, refreshForward, removeForward} from "../../hooks/api/use-forwards"
import {ForwardEntry} from "../../models"
import {ForwardDetailModal} from "./ForwardDetailModal.tsx"
import {ForwardModal} from "./ForwardModal.tsx"
import {ForwardsTable} from "./ForwardsTable.tsx"

const ForwardsPage = () => {

  const [forwards, setForwards] = useState<ForwardEntry[]>([])
  const [isModalOpen, setModalOpen] = useState(false)
  const [detailForward, setDetailForward] = useState<ForwardEntry>()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function fetchForwards() {
    listForwards().then((response) => {
      if (response.data) {
        setForwards(response.data as ForwardEntry[])
      }
    })
  }

  useEffect(() => {
    fetchForwards()
  }, [])

  function refreshAfterSubmit() {
    setModalOpen(false)
    fetchForwards()
  }

  function handleDetailButton(forward: ForwardEntry) {
    setDetailForward(forward)
  }

  function handleAddButton() {
    setModalOpen(true)
  }

  async function handleSubmit(forward: ForwardEntry) {
    try {
      const response = await addForward(forward)
      if (response.status !== 200) {
        const errorData = response.data as unknown as { error?: { message?: string } } | null
        setErrorMessage(errorData?.error?.message || "Failed to add forward")
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred")
    } finally {
      refreshAfterSubmit()
    }
  }

  async function handleDeleteForward(forward: ForwardEntry) {
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

  async function handleRefreshForward(forward: ForwardEntry) {
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
            onDetail={handleDetailButton}
            onDelete={handleDeleteForward}
            onRefresh={handleRefreshForward}
          />
        )}
      </div>
      {detailForward && (
        <ForwardDetailModal
          forward={detailForward}
          onRequestClose={() => setDetailForward(undefined)}
        />
      )}
      {isModalOpen && (
        <ForwardModal
          onRequestClose={() => setModalOpen(false)}
          onSubmit={handleSubmit}
        />
      )}
    </div>
  )
}

export const Component = ForwardsPage
