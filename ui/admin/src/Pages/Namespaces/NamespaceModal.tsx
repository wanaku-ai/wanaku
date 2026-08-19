import {Modal, Stack, TextInput} from "@carbon/react";
import React, {useState} from "react";
import {Namespace} from "../../models";

interface NamespaceModalProps {
  openedNamespace?: Namespace;
  namespaces: Namespace[]
  onSubmit: (namespace: Namespace) => void;
  onRequestClose: () => void;
}

export const NamespaceModal: React.FC<NamespaceModalProps> = ({
  openedNamespace,
  namespaces,
  onSubmit,
  onRequestClose,
}) => {
  const [name, setName] = useState(openedNamespace?.name);
  const [invalidName, setInvalidName] = useState(false)

  function otherNamespaces(): Namespace[] {
    return namespaces.filter(namespace => namespace.name !== openedNamespace?.name)
  }

  function isDuplicate(name: string): boolean {
    return otherNamespaces().some(namespace => namespace.name === name)
  }

  function isDnsLabelValid(name: string): boolean {
    return /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/.test(name);
  }

  const handleSubmit = () => {
    onSubmit({
      name: name,
      labels: openedNamespace?.labels,
    });
  };

  return (
    <Modal
      open={true}
      modalHeading={openedNamespace ? "Edit Namespace" : "Create Namespace"}
      primaryButtonText={openedNamespace ? "Save" : "Create"}
      primaryButtonDisabled={invalidName}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <Stack gap={7}>
        <TextInput
          id="namespace-name"
          labelText="Name"
          placeholder="e.g. finance, my-namespace"
          helperText="Lowercase letters, numbers, and hyphens only. Must start and end with a letter or number (1-63 characters). The name is also the URL path segment."
          value={name}
          invalid={invalidName}
          invalidText={
            name && !isDnsLabelValid(name)
              ? "Must contain only lowercase letters, numbers, and hyphens, and must start and end with a letter or number"
              : `Namespace "${name}" already exists.`
          }
          onChange={(e) => {
            const val = e.target.value
            setName(val)
            setInvalidName(!isDnsLabelValid(val) || isDuplicate(val))
          }}
          disabled={!!openedNamespace}
        />
      </Stack>
    </Modal>
  );
};
