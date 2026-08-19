import {Modal, TextInput,} from "@carbon/react";
import React, {useState} from "react";
import {ForwardEntry} from "../../models";
import {NamespaceSelect} from "../Namespaces/NamespaceSelect.tsx";

interface ForwardModalProps {
  onRequestClose: () => void;
  onSubmit: (newForward: ForwardEntry) => void;
}

export const ForwardModal: React.FC<ForwardModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState("")
  const [address, setAddress] = useState("")
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>()

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
      modalHeading="Add a Forward"
      primaryButtonText="Add"
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
        onChange={namespace => setSelectedNamespace(namespace.id ?? undefined)}
      />
    </Modal>
  );
};
