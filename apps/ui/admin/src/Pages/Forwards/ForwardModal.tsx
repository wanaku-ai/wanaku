import {Modal, TextInput,} from "@carbon/react";
import React, {useState} from "react";
import {ForwardReference} from "../../models";
import {NamespaceSelect} from "../Namespaces/NamespaceSelect.tsx";

interface ForwardModalProps {
  forward?: ForwardReference
  onRequestClose: () => void;
  onSubmit: (newForward: ForwardReference) => void;
}

export const ForwardModal: React.FC<ForwardModalProps> = ({
  forward,
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState(forward?.name || "")
  const [address, setAddress] = useState(forward?.address || "")
  const [selectedNamespace, setSelectedNamespace] = useState(forward?.namespace)

  const handleSubmit = () => {
    onSubmit({
      id: forward?.id,
      name,
      address,
      namespace: selectedNamespace,
    });
  };

  return (
    <Modal
      open={true}
      modalHeading={forward ? "Edit forward" : "Add a Forward"}
      primaryButtonText={forward ? "Save" : "Add"}
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
        value={selectedNamespace}
        onChange={namespace => setSelectedNamespace(namespace.id!)}
      />
    </Modal>
  );
};
