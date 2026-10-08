import { useState, type ChangeEvent } from 'react';
import { Button, InlineNotification, Modal, Stack, TextArea, TextInput } from '@carbon/react';
import type { AgentEntry, AgentView } from '../../models';
import { isHttpUrl } from '../../utils/url';
import { NamespaceSelect } from '../Namespaces/NamespaceSelect';

interface AgentModalProps {
  agent?: AgentView;
  onClose: () => void;
  onSave: (entry: AgentEntry) => Promise<void>;
}

export function AgentModal({ agent, onClose, onSave }: AgentModalProps) {
  const [name, setName] = useState(agent?.name ?? '');
  const [namespace, setNamespace] = useState(agent?.namespace ?? 'default');
  const [address, setAddress] = useState(agent?.address ?? '');
  const [description, setDescription] = useState(agent?.description ?? '');
  const [cardOverride, setCardOverride] = useState<string | null>(
    agent ? (agent.cardAddress ?? '') : null,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const validName = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(name);
  const validAddress = isHttpUrl(address);
  const cardAddress =
    cardOverride ?? (validAddress ? new URL('/.well-known/agent-card.json', address).href : '');
  const validCard = !cardAddress || isHttpUrl(cardAddress);
  const canSubmit = !saving && validName && validAddress && validCard;

  async function submit() {
    if (!canSubmit) return;
    setSaving(true);
    setError(undefined);
    try {
      await onSave({
        name,
        namespace,
        address: new URL(address).href,
        description,
        // Automatic discovery must follow later endpoint changes.
        cardAddress: cardOverride ? new URL(cardOverride).href : undefined,
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
      primaryButtonDisabled={!canSubmit}
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
          invalidText="Enter a valid HTTP or HTTPS URL without credentials or a fragment."
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
          helperText="Edit the suggested URL if needed. Leave empty to use the upstream agent's standard discovery path."
          value={cardAddress}
          disabled={saving}
          invalid={!validCard}
          invalidText="Enter a valid HTTP or HTTPS URL without credentials or a fragment."
          onChange={(event: ChangeEvent<HTMLInputElement>) => setCardOverride(event.target.value)}
        />
        <Button
          kind="ghost"
          size="sm"
          type="button"
          disabled={saving || !cardAddress}
          onClick={() => setCardOverride('')}
        >
          Clear agent card URL
        </Button>
      </Stack>
    </Modal>
  );
}
