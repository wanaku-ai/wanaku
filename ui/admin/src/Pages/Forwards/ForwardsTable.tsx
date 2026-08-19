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
  TableToolbarContent,
  Tag
} from "@carbon/react"
import {Add, Edit, Information, Renew, TrashCan} from "@carbon/icons-react"
import {ForwardEntry} from "../../models"
import {getNamespacePathById} from "../../hooks/api/use-namespaces"
import React from "react"
import {TableEmptyState} from "../EmptyTableState"

interface ForwardsTableProps {
  forwards: ForwardEntry[]
  onAdd: () => void
  onDetail: (forward: ForwardEntry) => void
  onEdit: (forward: ForwardEntry) => void
  onDelete: (forward: ForwardEntry) => void
  onRefresh: (forward: ForwardEntry) => void
}

export const ForwardsTable: React.FC<ForwardsTableProps> = ({
  forwards,
  onAdd,
  onDetail,
  onEdit,
  onDelete,
  onRefresh
}) => {

  const headers = [
    {key: "name", header: "Name"},
    {key: "address", header: "Address"},
    {key: "namespace", header: "Namespace"},
    {key: "server", header: "Server"},
    {key: "status", header: "Status"}
  ]

  function forwardsToRows() {
    return forwards
      .map((forward: ForwardEntry) => {
        const si = forward.serverInfo
        const server = si?.serverName
          ? `${si.serverName} ${si.version ?? ""}`.trim()
          : ""
        return {
          id: forward.name,
          name: forward.name,
          address: forward.address,
          namespace: getNamespacePathById(forward.namespace ?? undefined),
          server,
          status: forward.available === true ? "available" : "unavailable"
        }
      })
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
              const forward = forwards.find(f => f.name === row.id)
              if (forward) {
                return (
                  <TableRow {...getRowProps({row})}>
                    {row.cells.map((cell) => {
                      if (cell.info.header === "status") {
                        return (
                          <TableCell key={cell.id}>
                            <Tag
                              type={cell.value === "available" ? "green" : "red"}
                              size="sm"
                            >
                              {cell.value === "available" ? "Available" : "Unavailable"}
                            </Tag>
                          </TableCell>
                        )
                      }
                      return <TableCell key={cell.id}>{cell.value}</TableCell>
                    })}
                    <TableCell>
                      <Button
                        kind="ghost"
                        renderIcon={Information}
                        iconDescription="Details"
                        hasIconOnly
                        onClick={() => {onDetail(forward)}}
                      />
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
            {forwards.length == 0 && (
              <TableEmptyState
                colSpan={headers.length + 1}
                title="Start by adding forwards"
                body="Click Add Forward to add your data"
              />
            )}
          </TableBody>
        </Table>
      </TableContainer>
    )}
    </DataTable>
  )
}
