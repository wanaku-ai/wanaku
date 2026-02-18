import React from "react";
import {Modal, Tag} from "@carbon/react";
import type {InputSchema, Property} from "../../models";

interface InputSchemaModalProps {
  inputSchema?: InputSchema;
  toolName?: string;
  open: boolean;
  onClose: () => void;
}

export const InputSchemaModal: React.FC<InputSchemaModalProps> = ({
  inputSchema,
  toolName,
  open,
  onClose,
}) => {
  const properties = inputSchema?.properties || {};
  const requiredFields = inputSchema?.required || [];
  const entries = Object.entries(properties) as [string, Property][];

  return (
    <Modal
      open={open}
      passiveModal
      modalHeading={`Input Schema: ${toolName || "Tool"}`}
      onRequestClose={onClose}
      size="md"
    >
      {entries.length === 0 ? (
        <p style={{ color: "var(--cds-text-secondary)", fontStyle: "italic" }}>
          No input parameters defined.
        </p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
          {entries.map(([name, prop]) => {
            const isRequired = requiredFields.includes(name);
            return (
              <div
                key={name}
                style={{
                  padding: "1rem",
                  backgroundColor: "var(--cds-layer-01)",
                  border: "1px solid var(--cds-border-subtle)",
                  borderRadius: "4px",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.5rem",
                    marginBottom: prop.description ? "0.5rem" : 0,
                  }}
                >
                  <span style={{ fontWeight: 600, fontSize: "0.875rem" }}>{name}</span>
                  {prop.type && (
                    <Tag size="sm" type="blue">
                      {prop.type}
                    </Tag>
                  )}
                  {isRequired && (
                    <Tag size="sm" type="red">
                      Required
                    </Tag>
                  )}
                </div>
                {prop.description && (
                  <p
                    style={{
                      margin: 0,
                      fontSize: "0.8125rem",
                      color: "var(--cds-text-secondary)",
                    }}
                  >
                    {prop.description}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
};
