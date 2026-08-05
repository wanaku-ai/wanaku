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
  const [path, setPath] = useState(openedNamespace?.path);
  const [invalidName, setInvalidName] = useState(false)
  const [invalidPath, setInvalidPath] = useState(false)

  function otherNamespaces(): Namespace[] {
    return namespaces.filter(namespace => namespace.id !== openedNamespace?.id)
  }
  
  function isInvalidName(name: string): boolean {
    return otherNamespaces().some(namespace => namespace.name === name)
  }
  
  function isInvalidPath(path: string): boolean {
    return otherNamespaces().some(namespace => namespace.path === path)
  }
  
  const handleSubmit = () => {
    onSubmit({
      id: openedNamespace?.id,
      name: name,
      path: path,
      labels: openedNamespace?.labels,
    });
  };

  return (
    <Modal
      open={true}
      modalHeading={openedNamespace ? "Edit Namespace" : "Create Namespace"}
      primaryButtonText={openedNamespace ? "Save" : "Create"}
      primaryButtonDisabled={invalidName || invalidPath}
      secondaryButtonText="Cancel"
      onRequestSubmit={handleSubmit}
      onRequestClose={onRequestClose}
    >
      <Stack gap={7}>
        <TextInput
          id="namespace-name"
          labelText="Name"
          placeholder="e.g. my-namespace"
          helperText="A human-readable name for this namespace. Leave empty for a preallocated slot."
          value={name}
          invalid={invalidName}
          invalidText={`Invalid namespace name: ${name} is already in use.`}
          onChange={(e) => {
            const name = e.target.value
            setName(name)
            setInvalidName(isInvalidName(name))
          }}
        />
        <TextInput
          id="namespace-path"
          labelText="Path"
          placeholder="e.g. ns-0"
          helperText="The physical path identifier for this namespace."
          value={path}
          invalid={invalidPath}
          invalidText={`Invalid namespace path: ${path} is already in use.`}
          onChange={(e) => {
            const path = e.target.value
            setPath(path)
            setInvalidPath(isInvalidPath(path))
          }}
          disabled={!!openedNamespace}
        />
      </Stack>
    </Modal>
  );
};
