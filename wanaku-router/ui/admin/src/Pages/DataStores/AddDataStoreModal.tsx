import React, { useState, useRef } from "react";
import { Modal, TextInput } from "@carbon/react";
import type { DataStore } from "../../models";

interface AddDataStoreModalProps {
  onRequestClose: () => void;
  onSubmit: (dataStore: DataStore) => void;
}

export const AddDataStoreModal: React.FC<AddDataStoreModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [errorText, setErrorText] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (files && files.length > 0) {
      const file = files[0];
      setSelectedFile(file);
      // Auto-fill name if not already set
      if (!name) {
        setName(file.name);
      }
      setErrorText("");
    }
  };

  const handleSubmit = async () => {
    if (!selectedFile) {
      setErrorText("Please select a file to upload");
      return;
    }

    try {
      // Read file as ArrayBuffer
      const arrayBuffer = await selectedFile.arrayBuffer();
      const bytes = new Uint8Array(arrayBuffer);

      // Convert to base64
      let binary = "";
      for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
      }
      const base64Data = btoa(binary);

      const dataStore: DataStore = {
        name: name || selectedFile.name,
        data: base64Data,
      };

      onSubmit(dataStore);
    } catch (error) {
      console.error("Error reading file:", error);
      setErrorText("Error reading file. Please try again.");
    }
  };

  return (
    <Modal
      open={true}
      modalHeading="Add Data Store"
      primaryButtonText="Upload"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
    >
      <div style={{ marginBottom: "1rem" }}>
        <TextInput
          id="datastore-name"
          labelText="Name (optional)"
          placeholder="e.g. config.yaml"
          value={name}
          onChange={(e) => setName(e.target.value)}
          helperText="Leave empty to use filename"
        />
      </div>

      <div style={{ marginBottom: "1rem" }}>
        <label
          htmlFor="file-input"
          style={{
            display: "block",
            marginBottom: "0.5rem",
            fontSize: "0.875rem",
            fontWeight: 400,
            color: "var(--cds-text-primary)",
          }}
        >
          Select File
        </label>
        <input
          ref={fileInputRef}
          id="file-input"
          type="file"
          onChange={handleFileChange}
          style={{
            display: "block",
            width: "100%",
            padding: "0.5rem",
            border: "1px solid var(--cds-border-strong)",
            borderRadius: "0",
            backgroundColor: "var(--cds-field)",
          }}
        />
        {selectedFile && (
          <div style={{ marginTop: "0.5rem", fontSize: "0.875rem", color: "var(--cds-text-secondary)" }}>
            Selected: {selectedFile.name} ({(selectedFile.size / 1024).toFixed(2)} KB)
          </div>
        )}
      </div>

      {errorText && (
        <div
          style={{
            color: "var(--cds-text-error)",
            fontSize: "0.875rem",
            marginTop: "0.5rem",
          }}
        >
          {errorText}
        </div>
      )}
    </Modal>
  );
};
