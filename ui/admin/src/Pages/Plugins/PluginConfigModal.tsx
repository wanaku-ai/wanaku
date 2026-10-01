import { Modal, TextInput } from '@carbon/react';
import React, { useState } from 'react';
import type { PluginManifest } from '../../plugins/types';

interface PluginConfigModalProps {
  plugin: PluginManifest;
  onSave: (pluginId: string, services: Record<string, { target: string }>) => Promise<void>;
  onRequestClose: () => void;
  isSaving?: boolean;
}

export const PluginConfigModal: React.FC<PluginConfigModalProps> = ({
  plugin,
  onSave,
  onRequestClose,
  isSaving = false,
}) => {
  const serviceRequirements = plugin.requires?.services || [];
  const [serviceTargets, setServiceTargets] = useState<Record<string, string>>(() => {
    const initial: Record<string, string> = {};
    for (const svc of serviceRequirements) {
      initial[svc.id] = '';
    }
    return initial;
  });

  const handleTargetChange = (serviceId: string, value: string) => {
    setServiceTargets((prev) => ({
      ...prev,
      [serviceId]: value,
    }));
  };

  const handleSubmit = async () => {
    const formatted: Record<string, { target: string }> = {};
    for (const [svcId, target] of Object.entries(serviceTargets)) {
      if (target.trim()) {
        formatted[svcId] = { target: target.trim() };
      }
    }
    await onSave(plugin.id, formatted);
  };

  return (
    <Modal
      open
      modalHeading={`Configure Services: ${plugin.name}`}
      primaryButtonText={isSaving ? 'Saving...' : 'Save Configuration'}
      primaryButtonDisabled={isSaving}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <p style={{ marginBottom: '0.5rem' }}>
          Configure target URLs for backend services required by this plugin:
        </p>
        {serviceRequirements.length === 0 ? (
          <p style={{ fontStyle: 'italic', color: 'var(--cds-text-secondary, #525252)' }}>
            This plugin does not declare any service requirements.
          </p>
        ) : (
          serviceRequirements.map((svc) => (
            <TextInput
              key={svc.id}
              id={`service-target-${svc.id}`}
              labelText={`Service: ${svc.id} (v${svc.version})`}
              placeholder="e.g. http://localhost:8080"
              value={serviceTargets[svc.id] || ''}
              onChange={(e) => handleTargetChange(svc.id, e.target.value)}
            />
          ))
        )}
      </div>
    </Modal>
  );
};
