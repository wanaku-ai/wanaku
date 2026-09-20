import React, {ReactNode} from "react"
import { FormLabel, Toggletip, ToggletipButton, ToggletipContent } from "@carbon/react"
import { Information } from "@carbon/icons-react"


export function createInfoLabel(labelText: string, tooltipText: string): ReactNode {
  
  function format(tooltip: string) {
    const lines = tooltip.split("\n")
    return (
      <span>
        {lines.map((line, index) => (
          <React.Fragment>
            {line}
            {index < lines.length - 1 && <br />}
          </React.Fragment>
        ))}
      </span>
    )
  }
  
  return (
    <div style={{ display: 'flex', flexDirection: 'row', gap: '1rem', alignItems: 'flex-end' }}>
      <FormLabel>
        {labelText}
      </FormLabel>
      <Toggletip align="right-bottom">
        <ToggletipButton label="">
          <Information size={14} />
        </ToggletipButton>
        <ToggletipContent>
          {format(tooltipText)}
        </ToggletipContent>
      </Toggletip>
    </div>
  )
}