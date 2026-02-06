import React, { useState, useEffect } from "react";
import { Modal, Loading } from "@carbon/react";
import type { DataStore } from "../../models";

interface ViewDataStoreModalProps {
  dataStore: DataStore;
  onRequestClose: () => void;
}

export const ViewDataStoreModal: React.FC<ViewDataStoreModalProps> = ({
  dataStore,
  onRequestClose,
}) => {
  const [decodedContent, setDecodedContent] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!dataStore.data) {
      setError("No data available");
      setIsLoading(false);
      return;
    }

    try {
      // Decode base64 to text
      const decoded = atob(dataStore.data);
      setDecodedContent(decoded);
      setIsLoading(false);
    } catch (err) {
      console.error("Error decoding data:", err);
      setError("Failed to decode data. The content may be binary or corrupted.");
      setIsLoading(false);
    }
  }, [dataStore.data]);

  const isJsonContent = (content: string): boolean => {
    try {
      JSON.parse(content);
      return true;
    } catch {
      return false;
    }
  };

  const formatContent = (content: string): string => {
    if (isJsonContent(content)) {
      try {
        return JSON.stringify(JSON.parse(content), null, 2);
      } catch {
        return content;
      }
    }
    return content;
  };

  return (
    <Modal
      open={true}
      modalHeading={`View Data Store: ${dataStore.name || dataStore.id || "Unknown"}`}
      passiveModal
      onRequestClose={onRequestClose}
      size="lg"
    >
      <div style={{ marginBottom: "1rem" }}>
        <div style={{ marginBottom: "0.5rem" }}>
          <strong>ID:</strong> {dataStore.id || "N/A"}
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <strong>Name:</strong> {dataStore.name || "N/A"}
        </div>
      </div>

      <div>
        <strong>Contents:</strong>
        {isLoading ? (
          <div style={{ display: "flex", justifyContent: "center", padding: "2rem" }}>
            <Loading description="Loading content..." withOverlay={false} />
          </div>
        ) : error ? (
          <div
            style={{
              color: "#da1e28",
              fontSize: "0.875rem",
              marginTop: "0.5rem",
              padding: "1rem",
              backgroundColor: "#fff1f1",
              border: "1px solid #da1e28",
            }}
          >
            {error}
          </div>
        ) : (
          <pre
            style={{
              marginTop: "0.5rem",
              padding: "1rem",
              backgroundColor: "#f4f4f4",
              border: "1px solid #e0e0e0",
              borderRadius: "4px",
              overflow: "auto",
              maxHeight: "400px",
              fontSize: "0.875rem",
              fontFamily: "monospace",
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
            }}
          >
            {formatContent(decodedContent)}
          </pre>
        )}
      </div>
    </Modal>
  );
};
