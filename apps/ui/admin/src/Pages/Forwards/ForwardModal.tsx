import {Modal, Select, SelectItem, TextInput,} from "@carbon/react";
import React, {useEffect, useState} from "react";
import {ForwardReference, Namespace} from "../../models";
import {listNamespaces} from "../../hooks/api/use-namespaces";

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
  const [namespaces, setNamespaces] = useState<Namespace[]>([])
  const [selectedNamespace, setSelectedNamespace] = useState(forward?.namespace|| "")

  useEffect(() => {
    listNamespaces().then((result) => {
      setNamespaces(result.data.data as Namespace[]);
    });
  }, []);

  const handleSelectionChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedNamespace(event.target.value);
  };

  const handleSubmit = () => {
    onSubmit({
      id: forward?.id,
      name,
      address,
      namespace: selectedNamespace || undefined,
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
      <Select
        id="namespace"
        labelText="Select a Namespace"
        helperText="Choose a Namespace from the list (optional)"
        value={selectedNamespace}
        onChange={handleSelectionChange}
      >
        <SelectItem text="Choose an option" value="" />
        {namespaces.map((namespace) => (
          <SelectItem
            key={namespace.id}
            text={namespace.path || "default"}
            value={namespace.id}
          />
        ))}
      </Select>
    </Modal>
  );
};
