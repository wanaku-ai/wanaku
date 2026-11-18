import { Modal, MultiSelect, Stack, TextInput, Toggle } from '@carbon/react';
import React, { useState } from 'react';
import type { NamespaceEntry } from '../../models';
import { concreteNamespaces, isVirtualNamespace } from './namespaces';

interface NamespaceModalProps {
  openedNamespace?: NamespaceEntry;
  namespaces: NamespaceEntry[];
  onSubmit: (namespace: NamespaceEntry) => void;
  onRequestClose: () => void;
}

export const NamespaceModal: React.FC<NamespaceModalProps> = ({
  openedNamespace,
  namespaces,
  onSubmit,
  onRequestClose,
}) => {
  const [name, setName] = useState(openedNamespace?.name ?? '');
  const [virtual, setVirtual] = useState(
    openedNamespace ? isVirtualNamespace(openedNamespace) : false,
  );
  const [members, setMembers] = useState<string[]>(openedNamespace?.members ?? []);
  const [nameTouched, setNameTouched] = useState(false);
  const validName = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/.test(name);
  const duplicateName = namespaces.some(
    (namespace) => namespace.name === name && namespace.name !== openedNamespace?.name,
  );
  const memberOptions = concreteNamespaces(namespaces)
    .filter((namespace) => namespace.name !== openedNamespace?.name)
    .map((namespace) => namespace.name);

  const handleSubmit = () => {
    if (!validName || duplicateName) return;
    onSubmit({
      ...openedNamespace,
      name,
      members: virtual ? members : undefined,
    });
  };

  return (
    <Modal
      open
      modalHeading={openedNamespace ? 'Edit Namespace' : 'Create Namespace'}
      primaryButtonText={openedNamespace ? 'Save' : 'Create'}
      primaryButtonDisabled={!validName || duplicateName}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <Stack gap={7}>
        <TextInput
          id="namespace-name"
          labelText="Name"
          placeholder="e.g. finance, my-namespace"
          helperText="Use 1–63 lowercase letters, numbers, or hyphens. Start and end with a letter or number."
          value={name}
          invalid={nameTouched && (!validName || duplicateName)}
          invalidText={
            !validName ? 'Enter a valid namespace name.' : `Namespace "${name}" already exists.`
          }
          onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
            setName(event.target.value);
            setNameTouched(true);
          }}
          disabled={Boolean(openedNamespace)}
          required
        />
        <Toggle
          id="namespace-virtual"
          labelText="Virtual namespace"
          labelA="Off"
          labelB="On"
          toggled={virtual}
          onToggle={setVirtual}
          disabled={Boolean(openedNamespace)}
        />
        {virtual && (
          <MultiSelect<string>
            id="namespace-members"
            titleText="Member namespaces"
            label="Choose concrete namespaces"
            helperText="Tools, resources, prompts, and A2A agents are exposed from these concrete namespaces. An empty selection creates an empty virtual namespace."
            items={memberOptions}
            initialSelectedItems={members}
            itemToString={(item) => item ?? ''}
            onChange={({ selectedItems }) => setMembers(selectedItems ?? [])}
          />
        )}
      </Stack>
    </Modal>
  );
};
