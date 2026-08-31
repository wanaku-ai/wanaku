import {Modal, TextInput,} from "@carbon/react";
import React, {useState} from "react";
import {ForwardEntry} from "../../models";
import {NamespaceSelect} from "../Namespaces/NamespaceSelect.tsx";

interface ForwardModalProps {
  forward?: ForwardEntry
  onRequestClose: () => void;
  onSubmit: (newForward: ForwardEntry) => void;
}

export const ForwardModal: React.FC<ForwardModalProps> = ({
  forward,
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState(forward?.name || "")
  const [address, setAddress] = useState(forward?.address || "")
  const [selectedNamespace, setSelectedNamespace] = useState<string | null | undefined>(forward?.namespace)

  const handleSubmit = () => {
    onSubmit({
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
        value={selectedNamespace ?? undefined}
        onChange={namespace => setSelectedNamespace(namespace.name)}
      />
    </Modal>
  );
};
