import {ToastNotification} from "@carbon/react";
import {TargetsTable} from "./TargetsTable";
import React, {useEffect, useState} from "react";
import {useCapabilities} from "../../hooks/api/use-capabilities";
import {ServiceTargetState} from "./ServiceTargetState";
import {getGetApiV1CapabilitiesNotificationsUrl} from "../../api/wanaku-router-api";
import {ServiceTargetEvent} from "../../models";

export const TargetsPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ServiceTargetState[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { listManagementTools, listManagementToolsState, listManagementResourcesState } = useCapabilities();

  const fetch = async () => {
    setIsLoading(true);
    const result = await listManagementTools();

    setFetchedData(result.data.data!);
    setIsLoading(false);

    return result.data.data as ServiceTargetState[];
  }

  const fetchState = async (data: ServiceTargetState[]) => {
    const [tools, resources] = await Promise.all([
      listManagementToolsState(),
      listManagementResourcesState()
    ]);

    const updatedData = data.map((entry) => {
      if (entry.serviceName && tools.data?.data?.[entry.serviceName]) {
        return { ...entry, ...tools.data?.data?.[entry.serviceName][0] };
      } else if (entry.serviceName && resources.data?.data?.[entry.serviceName]) {
        return { ...entry, ...resources.data?.data?.[entry.serviceName][0] };
      }
      return entry;
    });

    updatedData?.forEach((t: ServiceTargetState) => {
      t.lastSeen = dateFormatter.format(new Date(t.lastSeen ?? new Date()));
    })

    setFetchedData(updatedData);

    return updatedData as ServiceTargetState[];
  }

  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short'
  });

  const setupSSE = (data: ServiceTargetState[]) => {
    const baseUrl = VITE_API_URL || window.location.origin;
    const eventSource = new EventSource(baseUrl + getGetApiV1CapabilitiesNotificationsUrl());
    
    eventSource.onopen = () => {
      console.log('SSE connection opened');
    };

    eventSource.addEventListener('UPDATE', (event) => {
      const serviceTargetEvent = JSON.parse(event.data) as ServiceTargetEvent;

      const updatedData = data.map((entry) => {
        if (entry.id == serviceTargetEvent.id) {
          entry.active = serviceTargetEvent.serviceState?.healthy; // TODO is this assumption true?
          const date = new Date(serviceTargetEvent.serviceState?.timestamp ?? new Date());
          entry.lastSeen = dateFormatter.format(date);
          entry.reason = serviceTargetEvent.serviceState?.reason;
        }

        return entry;
      });

      data = updatedData;
      setFetchedData(updatedData);
    });

    eventSource.addEventListener('PING', (event) => {
      const serviceTargetEvent = JSON.parse(event.data) as ServiceTargetEvent;

      const updatedData = data.map((entry) => {
        if (entry.id == serviceTargetEvent.id) {
          entry.lastSeen = dateFormatter.format(new Date()); // TODO What to do with dates?!
          entry.active = true; // TODO is this assumption true?
        }

        return entry;
      });

      data = updatedData;
      setFetchedData(updatedData);
    });

    eventSource.addEventListener('REGISTER', (event) => {
      const serviceTargetEvent = JSON.parse(event.data) as ServiceTargetEvent;

      // Remove if already present, shuoldn't be, but better safe than sorry
      const updatedData = data.filter((entry) => entry.id != serviceTargetEvent.id);

      updatedData.push(serviceTargetEvent.serviceTarget as ServiceTargetState);

      data = updatedData;
      fetchState(updatedData);
    });

    eventSource.addEventListener('DEREGISTER', (event) => {
      const serviceTargetEvent = JSON.parse(event.data) as ServiceTargetEvent;

      const updatedData = data.filter((entry) => entry.id != serviceTargetEvent.id);

      data = updatedData;
      setFetchedData(updatedData);
    });
    
    eventSource.onerror = (error) => {
      console.error('SSE error:', error);
    };
    
    return eventSource;
  };

  useEffect(() => {
    let eventSource: EventSource | null = null;

    fetch()
      .then((list) => fetchState(list))
      .then((list) => {
        eventSource = setupSSE(list);
      });

    return () => {
      eventSource?.close();
    };
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
