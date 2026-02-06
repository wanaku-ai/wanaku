import React, { useState, useEffect } from "react";
import { ToastNotification } from "@carbon/react";
import { AddDataStoreModal } from "./AddDataStoreModal";
import { ViewDataStoreModal } from "./ViewDataStoreModal";
import { DataStoresTable } from "./DataStoresTable";
import { useDataStores } from "../../hooks/api/use-data-stores";
import type { DataStore } from "../../models";

export const DataStoresPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<DataStore[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [viewDataStore, setViewDataStore] = useState<DataStore | null>(null);
  const { listDataStores, addDataStore, deleteDataStore } = useDataStores();

  // Fetch data on mount
  useEffect(() => {
    listDataStores()
      .then((result) => {
        setFetchedData((result.data.data as DataStore[]) || []);
        setIsLoading(false);
      })
      .catch((error) => {
        console.error("Error fetching data stores:", error);
        setErrorMessage("Failed to load data stores");
        setIsLoading(false);
      });
  }, [listDataStores]);

  // Auto-dismiss error messages
  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 10000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  const handleAddDataStore = async (newDataStore: DataStore) => {
    try {
      await addDataStore(newDataStore);
      setIsAddModalOpen(false);
      setErrorMessage(null);

      // Refresh the list
      listDataStores().then((result) => {
        setFetchedData((result.data.data as DataStore[]) || []);
      });
    } catch (error) {
      console.error("Error adding data store:", error);
      setErrorMessage("Error adding data store. Please try again.");
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteDataStore({ id });

      // Refresh the list
      listDataStores().then((result) => {
        setFetchedData((result.data.data as DataStore[]) || []);
      });
    } catch (error) {
      console.error("Error deleting data store:", error);
      setErrorMessage(`Failed to delete data store`);
    }
  };

  const handleDownload = (dataStore: DataStore) => {
    if (!dataStore.data) {
      setErrorMessage("No data to download");
      return;
    }

    try {
      // Decode base64 to binary
      const binaryString = atob(dataStore.data);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      // Create blob and download
      const blob = new Blob([bytes]);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = dataStore.name || "download";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error("Error downloading file:", error);
      setErrorMessage("Failed to download file. Data may be corrupted.");
    }
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
          style={{ position: "fixed", top: "3rem", right: "1rem", zIndex: 9999 }}
        />
      )}
      <h1 className="title">Data Stores</h1>
      <p className="description">
        Manage stored data files. Upload files as base64-encoded data stores
        that can be retrieved and downloaded later.
      </p>
      <div id="page-content">
        <DataStoresTable
          dataStores={fetchedData}
          onDelete={handleDelete}
          onAdd={() => setIsAddModalOpen(true)}
          onDownload={handleDownload}
          onView={(dataStore) => setViewDataStore(dataStore)}
        />
      </div>
      {isAddModalOpen && (
        <AddDataStoreModal
          onRequestClose={() => setIsAddModalOpen(false)}
          onSubmit={handleAddDataStore}
        />
      )}
      {viewDataStore && (
        <ViewDataStoreModal
          dataStore={viewDataStore}
          onRequestClose={() => setViewDataStore(null)}
        />
      )}
    </div>
  );
};
