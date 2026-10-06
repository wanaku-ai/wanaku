import { InlineNotification, Modal, TextInput } from '@carbon/react';
import React, { useState } from 'react';
import type { PluginManifest } from '../../plugins/types';

interface PluginConfigModalProps {
  plugin: PluginManifest;
  onSave: (pluginId: string, services: Record<string, { target: string }>) => Promise<void>;
  onRequestClose: () => void;
  isSaving?: boolean;
  errorMessage?: string | null;
}

const INVALID_URL_TEXT = 'Must be a valid HTTP or HTTPS address, e.g. http://localhost:8080';

function isHttpUrlValid(url: string): boolean {
  try {
    return /^https?:\/\//.test(url) && new URL(url).hostname !== '';
  } catch {
    return false;
  }
}

function isTargetInvalid(target: string): boolean {
  const trimmed = target.trim();
  return trimmed !== '' && !isHttpUrlValid(trimmed);
}

export const PluginConfigModal: React.FC<PluginConfigModalProps> = ({
  plugin,
  onSave,
  onRequestClose,
  isSaving = false,
  errorMessage,
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

  const hasInvalidTarget = Object.values(serviceTargets).some(isTargetInvalid);

  const handleSubmit = async () => {
    if (hasInvalidTarget) {
      return;
    }
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
      primaryButtonDisabled={isSaving || hasInvalidTarget}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      {errorMessage && (
        <InlineNotification
          kind="error"
          title="Failed to save configuration"
          subtitle={errorMessage}
          hideCloseButton
        />
      )}
      <div className="stack-vertical">
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
              invalid={isTargetInvalid(serviceTargets[svc.id] || '')}
              invalidText={INVALID_URL_TEXT}
              onChange={(e) => handleTargetChange(svc.id, e.target.value)}
            />
          ))
        )}
      </div>
    </Modal>
  );
};
