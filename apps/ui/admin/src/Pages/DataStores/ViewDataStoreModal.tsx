import React, {useEffect, useState} from "react";
import {Loading, Modal} from "@carbon/react";
import type {DataStore} from "../../models";

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
    // Reset state whenever the data changes to avoid showing stale content
    setIsLoading(true);
    setError(null);
    setDecodedContent("");

    if (!dataStore.data) {
      setError("No data available");
      setIsLoading(false);
      return;
    }

    try {
      // Decode base64 to binary, then use TextDecoder for proper UTF-8 handling
      const binaryString = atob(dataStore.data);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const decoded = new TextDecoder("utf-8").decode(bytes);
      setDecodedContent(decoded);
      setIsLoading(false);
    } catch (err) {
      console.error("Error decoding data:", err);
      setError("Failed to decode data. The content may be binary or corrupted.");
      setIsLoading(false);
    }
  }, [dataStore.data]);

  // Format content, pretty-printing JSON if valid (single parse attempt)
  const formatContent = (content: string): string => {
    try {
      const parsed = JSON.parse(content);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return content;
    }
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
              color: "var(--cds-text-error)",
              fontSize: "0.875rem",
              marginTop: "0.5rem",
              padding: "1rem",
              backgroundColor: "var(--cds-notification-background-error)",
              border: "1px solid var(--cds-support-error)",
            }}
          >
            {error}
          </div>
        ) : (
          <pre
            style={{
              marginTop: "0.5rem",
              padding: "1rem",
              backgroundColor: "var(--cds-layer-01)",
              border: "1px solid var(--cds-border-subtle)",
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
