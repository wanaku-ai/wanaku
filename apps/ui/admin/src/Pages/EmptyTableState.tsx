import {Stack, TableCell, TableRow} from "@carbon/react"
import React from "react"


interface TableEmptyStateProps {
  colSpan: number
  title: string
  body: string
}

export const TableEmptyState: React.FC<TableEmptyStateProps> = ({ colSpan, title, body }) => {
  return (
    <TableRow>
      <TableCell colSpan={colSpan}>
        <Stack gap={6} style={{
          alignItems: "left",
          justifyContent: "left",
          padding: "4rem 0",
          color: "var(--cds-text-secondary)"
        }}>
          <div style={{ textAlign: "left", paddingLeft: "4rem" }}>
            <h4 style={{ marginBottom: "0.5rem", color: "var(--cds-text-primary)" }}>
              {title}
            </h4>
            <p>{body}</p>
          </div>
        </Stack>
      </TableCell>
    </TableRow>
  )
}