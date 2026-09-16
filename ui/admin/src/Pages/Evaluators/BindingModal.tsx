import { Modal, Stack, TextInput } from "@carbon/react";
import React, { useState } from "react";
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"
import {NamespaceEntry} from "../../models"
import {isValidConversationId, isValidConversationIdFragment} from "../../utils/conversation"
import {DEFAULT_NAMESPACE} from "../../hooks/api/use-namespaces"

interface BindingModalProps {
  onRequestClose: () => void;
  onSubmit: (namespace: NamespaceEntry, conversationId: string) => void;
}

export const BindingModal: React.FC<BindingModalProps> = ({
  onRequestClose,
  onSubmit,
}) => {
  const [namespace, setNamespace] = useState<NamespaceEntry>(DEFAULT_NAMESPACE);
  const [conversationId, setConversationId] = useState("");
  const [conversationIdInvalid, setConversationIdInvalid] = useState(false)

  const handleSubmit = () => {
    onSubmit(namespace!, conversationId);
  };
  
  function validateConversationIdOnChange(id: string) {
    const valid = isValidConversationIdFragment(id)
    setConversationIdInvalid(!valid)
  }
  
  function validateConversationIdOnPaste(id: string) {
    id = id.trim()
    const valid = id.length >= 0 && isValidConversationId(id)
    setConversationIdInvalid(!valid)
  }

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
        <NamespaceSelect
          id="binding-namespace"
          labelText="Namespace"
          value={namespace?.name}
          onChange={(namespace: NamespaceEntry) => setNamespace(namespace)}
          required
        />
        <TextInput
          id="binding-conversation-id"
          labelText="Conversation ID"
          placeholder="e.g. wk-A1b2C3d4"
          invalid={conversationIdInvalid}
          invalidText="This is not valid conversation ID"
          value={conversationId}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
            const conversationId = e.target.value
            setConversationId(conversationId)
            validateConversationIdOnChange(conversationId)
          }}
          onPaste={event => {
            const conversationId = event.clipboardData.getData('text')
            validateConversationIdOnPaste(conversationId)
          }}
          required
        />
      </Stack>
    </Modal>
  );
};
