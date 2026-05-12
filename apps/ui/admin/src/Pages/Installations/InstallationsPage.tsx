import { ToastNotification } from "@carbon/react";
import { useEffect, useState } from "react";
import { useServiceCatalog } from "../../hooks/api/use-service-catalog";
import {
  launchCapability,
  stopCapability,
  getAllLauncherStatuses
} from "../../hooks/api/use-installations";
import { InstallationsTable, ProcessStatus, CatalogSystem } from "./InstallationsTable";

const InstallationsPage = () => {
  const [systems, setSystems] = useState<CatalogSystem[]>([]);
  const [statusMap, setStatusMap] = useState<Record<string, ProcessStatus>>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listServiceCatalogs, getServiceCatalog } = useServiceCatalog();

  async function fetchCatalogsAndSystems() {
    try {
      const response = await listServiceCatalogs() as any;
      const catalogs = response.data?.data || [];
      const allSystems: CatalogSystem[] = [];

      for (const catalog of catalogs) {
        try {
          const detailResp = await getServiceCatalog(catalog.name) as any;
          const detail = detailResp.data?.data;
          if (detail?.systems) {
            for (const sys of detail.systems) {
              allSystems.push({ catalogName: catalog.name, systemName: sys.name || sys });
            }
          }
        } catch {
          allSystems.push({ catalogName: catalog.name, systemName: catalog.name });
        }
      }

      setSystems(allSystems);
      await fetchStatuses();
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to fetch catalogs");
    }
  }

  async function fetchStatuses() {
    try {
      const response = await getAllLauncherStatuses() as any;
      if (response.data?.data) {
        setStatusMap(response.data.data as Record<string, ProcessStatus>);
      }
    } catch {
      // Launcher may be disabled (NoopLauncher)
    }
  }

  useEffect(() => {
    fetchCatalogsAndSystems();
  }, []);

  async function handleLaunch(catalogName: string, systemName: string) {
    try {
      const response = await launchCapability(catalogName, systemName) as any;
      if (response.status === 200) {
        await fetchStatuses();
      } else {
        setErrorMessage("Failed to launch capability");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while launching");
    }
  }

  async function handleStop(catalogName: string, systemName: string) {
    try {
      const response = await stopCapability(catalogName, systemName) as any;
      if (response.status === 200) {
        await fetchStatuses();
      } else {
        setErrorMessage("Failed to stop capability");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "An error occurred while stopping");
    }
  }

  return (
    <div>
      <h1 className="title">Launcher</h1>
      <p className="description">
        Launch and manage capability processes for deployed service catalogs.
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
        <InstallationsTable
          systems={systems}
          statusMap={statusMap}
          onLaunch={handleLaunch}
          onStop={handleStop}
        />
      </div>
    </div>
  );
};

export const Component = InstallationsPage;
