import { ToastNotification } from "@carbon/react";
import { TargetsTable } from "./TargetsTable";
import React, { useState, useEffect } from "react";
import { useTargets } from "../../hooks/api/use-targets";
import { ServiceTargetState } from "./ServiceTargetState";

export const TargetsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ServiceTargetState[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listManagementResources, listManagementTools, listManagementToolsState, listManagementResourcesState } = useTargets();

  const fetch = async () => {
    setIsLoading(true);
    const [tools, resources] = await Promise.all([
      listManagementTools(),
      listManagementResources()
    ]);

    const merged = tools.data.data?.concat(resources.data.data!);

    setFetchedData(merged!);
    setIsLoading(false);

    return merged as ServiceTargetState[];
  }

  const fetchState = async (data: ServiceTargetState[]) => {
    const [tools, resources] = await Promise.all([
      listManagementToolsState(),
      listManagementResourcesState()
    ]);

    const updatedData = data.map((entry) => {
      if (entry.service && tools.data?.data?.[entry.service]) {
        return { ...entry, ...tools.data?.data?.[entry.service][0] };
      } else if (entry.service && resources.data?.data?.[entry.service]) {
        return { ...entry, ...resources.data?.data?.[entry.service][0] };
      }
      return entry;
    });

    setFetchedData(updatedData);
  }

  useEffect(() => {
    fetch().then((list) => fetchState(list));
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
      <h1 className="title">Capabilities</h1>
      <p className="description">
        Capbilties (Downstream Services) connected to Wanaku.
      </p>
      <div id="page-content">
        {fetchedData && (
          <TargetsTable
            targets={fetchedData}
          />
        )}
      </div>
    </div>
  );
};
