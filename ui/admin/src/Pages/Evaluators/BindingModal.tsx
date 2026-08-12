import { Modal, Stack, TextInput } from "@carbon/react";
import React, { useState } from "react";

interface BindingModalProps {
  onRequestClose: () => void;
  onSubmit: (namespace: string, conversationId: string) => void;
}

export const BindingModal: React.FC<BindingModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [namespace, setNamespace] = useState("");
  const [conversationId, setConversationId] = useState("");

  const handleSubmit = () => {
    onSubmit(namespace, conversationId);
  };

  return (
    <Modal
      open={true}
      modalHeading="Add Namespace Binding"
      primaryButtonText="Add"
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
      primaryButtonDisabled={!namespace || !conversationId}
    >
      <Stack gap={5}>
        <TextInput
          id="binding-namespace"
          labelText="Namespace"
          placeholder="e.g. finance"
          value={namespace}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNamespace(e.target.value)}
          required
        />
        <TextInput
          id="binding-conversation-id"
          labelText="Conversation ID"
          placeholder="e.g. wk-A1b2C3d4"
          value={conversationId}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setConversationId(e.target.value)}
          required
        />
      </Stack>
    </Modal>
  );
};
