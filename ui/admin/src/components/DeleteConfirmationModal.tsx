import { Modal } from "@carbon/react"
import React from "react"


interface DeleteConfirmationModalProps {
  heading: string
  text: string
  onConfirm: () => void
  onCancel: () => void
}


export const DeleteConfirmationModal: React.FC<DeleteConfirmationModalProps> = ({
  heading,
  text,
  onConfirm,
  onCancel
}) => {
  return (
    <Modal
      open={true}
      modalHeading={heading}
      primaryButtonText={"OK"}
      secondaryButtonText="Cancel"
      onRequestSubmit={onConfirm}
      onRequestClose={onCancel}
    >
      <div>{text}</div>
    </Modal>
  )
}