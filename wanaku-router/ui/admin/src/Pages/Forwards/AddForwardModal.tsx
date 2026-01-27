import {
  Modal,
  TextInput,
  Select,
  SelectItem,
} from "@carbon/react";
import React, { useEffect, useState } from "react";
import { ForwardReference, Namespace } from "../../models";
import { listNamespaces } from "../../hooks/api/use-namespaces";

interface AddForwardModalProps {
  onRequestClose: () => void;
  onSubmit: (newForward: ForwardReference) => void;
}

export const AddForwardModal: React.FC<AddForwardModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [selectedNamespace, setSelectedNamespace] = useState("");

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
      name,
      address,
      namespace: selectedNamespace || undefined,
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
