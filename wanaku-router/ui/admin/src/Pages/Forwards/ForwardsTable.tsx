import {
  Button,
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent
} from "@carbon/react"
import {Add, Edit, Renew, TrashCan} from "@carbon/icons-react"
import {ForwardReference} from "../../models"
import {getNamespacePathById} from "../../hooks/api/use-namespaces.ts"
import React from "react"

interface ForwardsTableProps {
  forwards: ForwardReference[]
  onAdd: () => void
  onEdit: (forward: ForwardReference) => void
  onDelete: (forward: ForwardReference) => void
  onRefresh: (forward: ForwardReference) => void
}

export const ForwardsTable: React.FC<ForwardsTableProps> = ({
  forwards,
  onAdd,
  onEdit,
  onDelete,
  onRefresh
}) => {

  const headers = [
    {key: "name", header: "Name"},
    {key: "address", header: "Address"},
    {key: "namespace", header: "Namespace"}
  ]

  function forwardsToRows() {
    return forwards
      //.filter((forward: ForwardReference) => forward.id)
      .map((forward: ForwardReference) => ({
        id: forward.id!,
        name: forward.name,
        address: forward.address,
        namespace: getNamespacePathById(forward.namespace)
      }))
  }

  return (
    <DataTable rows={forwardsToRows()} headers={headers}>
    {({
      rows,
      headers,
      getToolbarProps,
      getTableProps,
      getHeaderProps,
      getRowProps
    }) => (
      <TableContainer>
        <TableToolbar {...getToolbarProps()}>
          <TableToolbarContent>
            <Button renderIcon={Add} onClick={onAdd}>
              Add Forward
            </Button>
          </TableToolbarContent>
        </TableToolbar>
        <Table {...getTableProps()}>
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader {...getHeaderProps({header})} key={header.key} >
                  {header.header}
                </TableHeader>
              ))}
              <TableHeader>Actions</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const forward = forwards.find(forward => forward.id === row.id)
              if (forward) {
                return (
                  <TableRow {...getRowProps({row})}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>{cell.value}</TableCell>
                    ))}
                    <TableCell>
                      <Button
                        kind="ghost"
                        renderIcon={Renew}
                        iconDescription="Refresh"
                        hasIconOnly
                        onClick={() => {onRefresh(forward)}}
                      />
                      <Button
                        kind="ghost"
                        renderIcon={Edit}
                        iconDescription="Edit"
                        hasIconOnly
                        onClick={() => onEdit(forward)}
                      />
                      <Button
                        kind="ghost"
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        hasIconOnly
                        onClick={() => {onDelete(forward)}}
                      />
                    </TableCell>
                  </TableRow>
                )
              }
            })}
          </TableBody>
        </Table>
      </TableContainer>
    )}
    </DataTable>
  )
}