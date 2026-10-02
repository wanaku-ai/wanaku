import { useState, type ChangeEvent } from 'react';
import { InlineNotification, Modal, Stack, TextArea, TextInput } from '@carbon/react';
import type { AgentEntry, AgentView } from '../../models';
import { NamespaceSelect } from '../Namespaces/NamespaceSelect';

interface AgentModalProps {
  agent?: AgentView;
  onClose: () => void;
  onSave: (entry: AgentEntry) => Promise<void>;
}

function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return (
      ['http:', 'https:'].includes(url.protocol) &&
      !url.username &&
      !url.password &&
      !value.includes('#')
    );
  } catch {
    return false;
  }
}

export function AgentModal({ agent, onClose, onSave }: AgentModalProps) {
  const [name, setName] = useState(agent?.name ?? '');
  const [namespace, setNamespace] = useState(agent?.namespace ?? 'default');
  const [address, setAddress] = useState(agent?.address ?? '');
  const [description, setDescription] = useState(agent?.description ?? '');
  const [cardAddress, setCardAddress] = useState(agent?.cardAddress ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const validName = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(name);
  const validAddress = isHttpUrl(address);
  const validCard = !cardAddress || isHttpUrl(cardAddress);

  async function submit() {
    setSaving(true);
    setError(undefined);
    try {
      await onSave({
        name,
        namespace,
        address,
        description,
        cardAddress: cardAddress || undefined,
      });
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save the agent.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open
      modalHeading={agent ? 'Edit A2A agent' : 'Add A2A agent'}
      primaryButtonText={saving ? 'Saving…' : agent ? 'Save' : 'Add'}
      secondaryButtonText="Cancel"
      primaryButtonDisabled={saving || !validName || !validAddress || !validCard}
      onRequestSubmit={() => void submit()}
      onRequestClose={() => {
        if (!saving) onClose();
      }}
    >
      <Stack gap={6}>
        {error && (
          <InlineNotification
            kind="error"
            title="Agent was not saved"
            subtitle={error}
            hideCloseButton
          />
        )}
        <TextInput
          id="agent-name"
          labelText="Name"
          helperText="Use up to 63 lowercase letters, numbers, or hyphens. Start and end with a letter or number."
          value={name}
          disabled={Boolean(agent) || saving}
          invalid={Boolean(name) && !validName}
          invalidText="Enter a valid agent name."
          onChange={(event: ChangeEvent<HTMLInputElement>) => setName(event.target.value)}
          required
        />
        {agent ? (
          <TextInput id="agent-namespace" labelText="Namespace" value={namespace} disabled />
        ) : (
          <NamespaceSelect
            id="agent-namespace"
            labelText="Namespace"
            value={namespace}
            onChange={(selected) => setNamespace(selected.name)}
            required
          />
        )}
        <TextInput
          id="agent-address"
          labelText="Upstream A2A endpoint"
          helperText="Enter the HTTP or HTTPS JSON-RPC endpoint of the upstream agent."
          value={address}
          disabled={saving}
          invalid={Boolean(address) && !validAddress}
          invalidText="Enter an HTTP or HTTPS URL without embedded credentials."
          onChange={(event: ChangeEvent<HTMLInputElement>) => setAddress(event.target.value)}
          required
        />
        <TextArea
          id="agent-description"
          labelText="Description"
          value={description}
          disabled={saving}
          onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setDescription(event.target.value)}
        />
        <TextInput
          id="agent-card-address"
          labelText="Agent card URL (optional)"
          helperText="Leave empty to use the upstream agent's standard discovery path."
          value={cardAddress}
          disabled={saving}
          invalid={!validCard}
          invalidText="Enter an HTTP or HTTPS URL without embedded credentials."
          onChange={(event: ChangeEvent<HTMLInputElement>) => setCardAddress(event.target.value)}
        />
      </Stack>
    </Modal>
  );
}
