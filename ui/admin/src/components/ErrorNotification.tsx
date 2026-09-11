import React from "react"
import {InlineNotification} from "@carbon/react"


interface ErrorNotificationProps {
  errorMessage: string
  onClose: () => void
}


export const ErrorNotification: React.FC<ErrorNotificationProps> = ({ errorMessage, onClose }) => {
  return (
    <InlineNotification
      kind="error"
      title="Error"
      subtitle={errorMessage}
      onCloseButtonClick={onClose}
      lowContrast
      hideCloseButton={false}
    />
  )
}