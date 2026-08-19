import { ComposedModal, ModalHeader, ModalBody, Tag } from "@carbon/react";
import React from "react";
import { ForwardEntry } from "../../models";

interface ForwardDetailModalProps {
  forward: ForwardEntry;
  onRequestClose: () => void;
}

export const ForwardDetailModal: React.FC<ForwardDetailModalProps> = ({
  forward,
  onRequestClose,
}) => {
  const si = forward.serverInfo;
  const labels = forward.labels;

  return (
    <ComposedModal open onClose={onRequestClose}>
      <ModalHeader title={forward.name ?? "Forward Details"} />
      <ModalBody>
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div>
            <strong>Status:</strong>{" "}
            <Tag
              type={forward.available === true ? "green" : "red"}
              size="sm"
            >
              {forward.available === true ? "Available" : "Unavailable"}
            </Tag>
          </div>
          {forward.statusMessage && (
            <div>
              <strong>Status Detail:</strong>{" "}
              <span style={{ color: "var(--cds-text-error, #da1e28)" }}>
                {forward.statusMessage}
              </span>
            </div>
          )}
          <div>
            <strong>Address:</strong> {forward.address}
          </div>
          <div>
            <strong>Namespace:</strong> {forward.namespace ?? "default"}
          </div>

          <h4 style={{ marginBottom: 0 }}>Server Info</h4>
          {si?.serverName ? (
            <>
              <div>
                <strong>Name:</strong> {si.serverName}
              </div>
              <div>
                <strong>Version:</strong> {si.version ?? "Unknown"}
              </div>
              {si.description && (
                <div>
                  <strong>Description:</strong> {si.description}
                </div>
              )}
              {si.websiteUrl && (
                <div>
                  <strong>Website:</strong>{" "}
                  <a href={si.websiteUrl} target="_blank" rel="noopener noreferrer">
                    {si.websiteUrl}
                  </a>
                </div>
              )}
              <div>
                <strong>Capabilities:</strong>{" "}
                {si.capabilities && si.capabilities.length > 0
                  ? si.capabilities.map((cap) => (
                      <Tag key={cap} type="blue" size="sm" style={{ marginLeft: "0.25rem" }}>
                        {cap}
                      </Tag>
                    ))
                  : "None"}
              </div>
              <div>
                <strong>Extensions:</strong>{" "}
                {si.extensions && si.extensions.length > 0
                  ? si.extensions.map((ext) => (
                      <Tag key={ext} type="purple" size="sm" style={{ marginLeft: "0.25rem" }}>
                        {ext}
                      </Tag>
                    ))
                  : "None"}
              </div>
              {si.instructions && (
                <div>
                  <strong>Instructions:</strong>
                  <p style={{ marginTop: "0.25rem", whiteSpace: "pre-wrap" }}>
                    {si.instructions}
                  </p>
                </div>
              )}
            </>
          ) : (
            <div style={{ color: "var(--cds-text-secondary, #525252)" }}>
              No server info available. The upstream MCP server may not report identity during initialization.
            </div>
          )}

          {labels && Object.keys(labels).length > 0 && (
            <>
              <h4 style={{ marginBottom: 0 }}>Labels</h4>
              <div>
                {Object.entries(labels).map(([key, value]) => (
                  <Tag key={key} type="gray" size="sm" style={{ marginRight: "0.25rem" }}>
                    {key}={value}
                  </Tag>
                ))}
              </div>
            </>
          )}
        </div>
      </ModalBody>
    </ComposedModal>
  );
};
