import { ToastNotification } from "@carbon/react";
import { useEffect, useState } from "react";
import {
  listInstallations,
  createInstallation,
  updateInstallation,
  deleteInstallation,
  launchInstallation,
  stopInstallation,
  getInstallationStatus
} from "../../hooks/api/use-installations";
import { InstallationModal } from "./InstallationModal.tsx";
import { InstallationsTable } from "./InstallationsTable.tsx";
import { ProcessStatus } from "./installation-types";

const InstallationsPage = () => {
  const [installations, setInstallations] = useState<any[]>([]);
  const [statusMap, setStatusMap] = useState<Record<string, ProcessStatus>>({});
  const [isModalOpen, setModalOpen] = useState(false);
  const [openedInstallation, setOpenedInstallation] = useState<any>();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function fetchInstallations() {
    try {
      const response = await listInstallations();
      if (response.data?.data) {
        const installs = response.data.data as any[];
        setInstallations(installs);
        await fetchStatuses(installs);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to fetch installations");
    }
  }

  async function fetchStatuses(installs: any[]) {
    const statuses: Record<string, ProcessStatus> = {};
    for (const inst of installs) {
      if (inst.id) {
        try {
          const response = await getInstallationStatus(inst.id);
          if (response.data?.data) {
            statuses[inst.id] = response.data.data as ProcessStatus;
          }
        } catch {
          // Ignore status fetch errors
        }
      }
    }
    setStatusMap(statuses);
  }

  useEffect(() => {
    fetchInstallations();
  }, []);

  function refreshAfterSubmit() {
    closeModal();
    fetchInstallations();
  }

  function closeModal() {
    setOpenedInstallation(undefined);
    setModalOpen(false);
  }

  function handleAdd() {
    setModalOpen(true);
  }

  function handleEdit(installation: any) {
    setOpenedInstallation(installation);
    setModalOpen(true);
  }

  function handleSubmit(installation: { id?: string; name: string; data: string; labels: Record<string, string> }) {
    if (openedInstallation) {
      handleUpdate(installation);
    } else {
      handleCreate(installation);
    }
  }

  async function handleCreate(installation: { name: string; data: string; labels: Record<string, string> }) {
    try {
      const response = await createInstallation(installation);
      if (response.status !== 200) {
        const errorData = response.data as { error?: { message?: string } } | null;
        setErrorMessage(errorData?.error?.message || "Failed to create installation");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred");
    } finally {
      refreshAfterSubmit();
    }
  }

  async function handleUpdate(installation: { id?: string; name: string; data: string; labels: Record<string, string> }) {
    if (!installation.id) {
      setErrorMessage("Installation ID is required for update");
      return;
    }
    try {
      const response = await updateInstallation(installation.id, {
        name: installation.name,
        data: installation.data,
        labels: installation.labels
      });
      if (response.status !== 200) {
        const errorData = response.data as { error?: { message?: string } } | null;
        setErrorMessage(errorData?.error?.message || "Failed to update installation");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred");
    } finally {
      refreshAfterSubmit();
    }
  }

  async function handleDelete(id: string) {
    try {
      const response = await deleteInstallation(id);
      if (response.status === 200) {
        fetchInstallations();
      } else {
        setErrorMessage("Failed to delete installation");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while deleting installation");
    }
  }

  async function handleLaunch(id: string) {
    try {
      const response = await launchInstallation(id);
      if (response.status === 200) {
        fetchInstallations();
      } else {
        setErrorMessage("Failed to launch installation");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while launching installation");
    }
  }

  async function handleStop(id: string) {
    try {
      const response = await stopInstallation(id);
      if (response.status === 200) {
        fetchInstallations();
      } else {
        setErrorMessage("Failed to stop installation");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while stopping installation");
    }
  }

  return (
    <div>
      <h1 className="title">Installations</h1>
      <p className="description">
        Configure and manage capability installations for local development.
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
        {installations && (
          <InstallationsTable
            installations={installations}
            statusMap={statusMap}
            onAdd={handleAdd}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onLaunch={handleLaunch}
            onStop={handleStop}
          />
        )}
      </div>
      {isModalOpen && (
        <InstallationModal
          installation={openedInstallation}
          onRequestClose={closeModal}
          onSubmit={handleSubmit}
        />
      )}
    </div>
  );
};

export const Component = InstallationsPage;
