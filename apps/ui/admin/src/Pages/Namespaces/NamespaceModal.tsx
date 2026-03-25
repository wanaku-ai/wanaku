import {Modal, Stack, TextInput} from "@carbon/react";
import React, {useState} from "react";
import {Namespace} from "../../models";

interface NamespaceModalProps {
  namespace?: Namespace;
  onSubmit: (namespace: Namespace) => void;
  onRequestClose: () => void;
}

export const NamespaceModal: React.FC<NamespaceModalProps> = ({
  namespace,
  onSubmit,
  onRequestClose,
}) => {
  const [name, setName] = useState(namespace?.name || "");
  const [path, setPath] = useState(namespace?.path || "");

  const handleSubmit = () => {
    onSubmit({
      id: namespace?.id,
      name: name || undefined,
      path: path || undefined,
      labels: namespace?.labels,
    });
  };

  return (
    <Modal
      open={true}
      modalHeading={namespace ? "Edit Namespace" : "Create Namespace"}
      primaryButtonText={namespace ? "Save" : "Create"}
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
          onChange={(e) => setName(e.target.value)}
        />
        <TextInput
          id="namespace-path"
          labelText="Path"
          placeholder="e.g. ns-0"
          helperText="The physical path identifier for this namespace."
          value={path}
          onChange={(e) => setPath(e.target.value)}
          disabled={!!namespace}
        />
      </Stack>
    </Modal>
  );
};
