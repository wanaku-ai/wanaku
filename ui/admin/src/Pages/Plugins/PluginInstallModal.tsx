import { Modal, TextInput } from '@carbon/react';
import React, { useState } from 'react';
import type { InstallPluginRequest } from '../../models';

interface PluginInstallModalProps {
  onInstall: (req: InstallPluginRequest) => Promise<void>;
  onRequestClose: () => void;
  isInstalling?: boolean;
}

export const PluginInstallModal: React.FC<PluginInstallModalProps> = ({
  onInstall,
  onRequestClose,
  isInstalling = false,
}) => {
  const [id, setId] = useState('');
  const [version, setVersion] = useState('');
  const [url, setUrl] = useState('');

  const handleSubmit = async () => {
    if (!id.trim() || !version.trim() || !url.trim()) return;
    await onInstall({
      id: id.trim(),
      version: version.trim(),
      url: url.trim(),
    });
  };

  const isInvalid = !id.trim() || !version.trim() || !url.trim();

  return (
    <Modal
      open
      modalHeading="Install Plugin"
      primaryButtonText={isInstalling ? 'Installing...' : 'Install'}
      primaryButtonDisabled={isInvalid || isInstalling}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <div className="stack-vertical">
        <TextInput
          id="plugin-id"
          labelText="Plugin ID"
          placeholder="e.g. wanaku-barn"
          value={id}
          onChange={(e) => setId(e.target.value)}
        />
        <TextInput
          id="plugin-version"
          labelText="Version"
          placeholder="e.g. 0.3.0"
          value={version}
          onChange={(e) => setVersion(e.target.value)}
        />
        <TextInput
          id="plugin-url"
          labelText="Download URL"
          placeholder="e.g. https://example.com/plugins/wanaku-barn-0.3.0.zip"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
      </div>
    </Modal>
  );
};
