import { ComposedModal, ModalHeader, ModalBody } from "@carbon/react";
import React from "react";
import type { PluginManifest } from "../../plugins/types";

interface PluginDetailModalProps {
  plugin: PluginManifest;
  onRequestClose: () => void;
}

export const PluginDetailModal: React.FC<PluginDetailModalProps> = ({ plugin, onRequestClose }) => {
  return (
    <ComposedModal open onClose={onRequestClose}>
      <ModalHeader title={`${plugin.name} v${plugin.version}`} />
      <ModalBody>
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div>
            <strong>ID:</strong> {plugin.id}
          </div>
          <div>
            <strong>Entrypoint:</strong> {plugin.entrypoint}
          </div>
          <div>
            <strong>Styles:</strong>{" "}
            {plugin.styles && plugin.styles.length > 0 ? plugin.styles.join(", ") : "None"}
          </div>
          <div>
            <strong>Host API:</strong> {plugin.requires?.hostApi || "Not specified"}
          </div>
          <div>
            <strong>Required Services:</strong>
            {plugin.requires?.services && plugin.requires.services.length > 0 ? (
              <ul style={{ marginTop: "0.5rem", paddingLeft: "1.5rem" }}>
                {plugin.requires.services.map((service, idx) => (
                  <li key={idx}>
                    {service.id} v{service.version}
                  </li>
                ))}
              </ul>
            ) : (
              " None"
            )}
          </div>
          <div>
            <strong>Permissions:</strong>
            {plugin.permissions && plugin.permissions.length > 0 ? (
              <ul style={{ marginTop: "0.5rem", paddingLeft: "1.5rem" }}>
                {plugin.permissions.map((permission, idx) => (
                  <li key={idx}>{permission}</li>
                ))}
              </ul>
            ) : (
              " None"
            )}
          </div>
        </div>
      </ModalBody>
    </ComposedModal>
  );
};
