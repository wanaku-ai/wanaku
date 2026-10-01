import { Modal, TextInput } from '@carbon/react';
import React, { useState } from 'react';
import { CredentialPurpose, ForwardEntry } from '../../models';
import { NamespaceSelect } from '../Namespaces/NamespaceSelect.tsx';
import { BindingSelect } from './BindingSelect.tsx';

interface ForwardModalProps {
  forward?: ForwardEntry;
  onRequestClose: () => void;
  onSubmit: (newForward: ForwardEntry) => void;
}

export const ForwardModal: React.FC<ForwardModalProps> = ({
  forward,
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState(forward?.name || '');
  const [address, setAddress] = useState(forward?.address || '');
  const [selectedNamespace, setSelectedNamespace] = useState<string | null | undefined>(
    forward?.namespace,
  );
  const [discoveryBinding, setDiscoveryBinding] = useState<string | undefined>(
    forward?.credentialBindings?.[CredentialPurpose.discovery],
  );
  const [invocationBinding, setInvocationBinding] = useState<string | undefined>(
    forward?.credentialBindings?.[CredentialPurpose.invocation],
  );

  const handleSubmit = () => {
    const credentialBindings: Record<string, string> = {};
    if (discoveryBinding) {
      credentialBindings[CredentialPurpose.discovery] = discoveryBinding;
    }
    if (invocationBinding) {
      credentialBindings[CredentialPurpose.invocation] = invocationBinding;
    }

    // Preserve operator-authored labels on edit. Discovery-computed fields
    // (serverInfo, available, statusMessage) are intentionally omitted so the
    // backend recomputes them; resending stale values would persist them on a
    // failed re-discovery.
    const next: ForwardEntry = {
      name,
      address,
      namespace: selectedNamespace,
      credentialBindings,
    };
    if (forward?.labels) {
      next.labels = forward.labels;
    }
    onSubmit(next);
  };

  return (
    <Modal
      open={true}
      modalHeading={forward ? 'Edit forward' : 'Add a Forward'}
      primaryButtonText={forward ? 'Save' : 'Add'}
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
      primaryButtonDisabled={!name || !address}
    >
      <TextInput
        id="forward-name"
        labelText="Forward Name"
        placeholder="e.g. my-forward"
        value={name}
        onChange={(e) => setName(e.target.value)}
        required
      />
      <TextInput
        id="forward-address"
        labelText="Address"
        placeholder="http://host:port"
        value={address}
        onChange={(e) => setAddress(e.target.value)}
        required
      />
      <NamespaceSelect
        id="namespace"
        labelText="Select a Namespace"
        helperText="Choose a Namespace from the list (optional)"
        value={selectedNamespace ?? undefined}
        onChange={(namespace) => setSelectedNamespace(namespace.name)}
      />
      <BindingSelect
        id="forward-discovery-binding"
        labelText="Discovery credential binding"
        helperText="Bindings owned by this forward that allow discovery (optional). Author bindings in wanaku.yaml."
        forwardName={name}
        purpose={CredentialPurpose.discovery}
        value={discoveryBinding}
        onChange={setDiscoveryBinding}
      />
      <BindingSelect
        id="forward-invocation-binding"
        labelText="Invocation credential binding"
        helperText="Bindings owned by this forward that allow invocation (optional). Author bindings in wanaku.yaml."
        forwardName={name}
        purpose={CredentialPurpose.invocation}
        value={invocationBinding}
        onChange={setInvocationBinding}
      />
    </Modal>
  );
};
