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
import { Play, Stop } from "@carbon/icons-react"
import React from "react"

export interface ProcessStatus {
  running: boolean;
  port: number;
  startedAt: string | null;
  exitCode: number | null;
}

export interface CatalogSystem {
  catalogName: string;
  systemName: string;
}

interface LauncherTableProps {
  systems: CatalogSystem[];
  statusMap: Record<string, ProcessStatus>;
  onLaunch: (catalogName: string, systemName: string) => void;
  onStop: (catalogName: string, systemName: string) => void;
}

export const InstallationsTable: React.FC<LauncherTableProps> = ({
  systems,
  statusMap,
  onLaunch,
  onStop
}) => {

  const headers = [
    { key: "catalog", header: "Catalog" },
    { key: "system", header: "System" },
    { key: "status", header: "Status" },
    { key: "port", header: "Port" }
  ]

  function systemsToRows() {
    return systems.map((s, idx) => {
      const key = `${s.catalogName}:${s.systemName}`
      const status = statusMap[key]
      return {
        id: `${idx}-${key}`,
        catalog: s.catalogName,
        system: s.systemName,
        status: status?.running ? "Running" : "Stopped",
        port: status?.running ? String(status.port) : "-"
      }
    })
  }

  return (
    <DataTable rows={systemsToRows()} headers={headers}>
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
            <TableToolbarContent />
          </TableToolbar>
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                const sys = systems.find(
                  (_s, idx) => row.id === `${idx}-${_s.catalogName}:${_s.systemName}`
                )
                if (!sys) return null
                const key = `${sys.catalogName}:${sys.systemName}`
                const isRunning = statusMap[key]?.running
                return (
                  <TableRow {...getRowProps({ row })} key={row.id}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>
                        {cell.info.header === "status" ? (
                          <Tag type={cell.value === "Running" ? "green" : "gray"} size="sm">
                            {cell.value}
                          </Tag>
                        ) : (
                          cell.value
                        )}
                      </TableCell>
                    ))}
                    <TableCell>
                      {isRunning ? (
                        <Button
                          kind="danger--ghost"
                          renderIcon={Stop}
                          iconDescription="Stop"
                          hasIconOnly
                          onClick={() => onStop(sys.catalogName, sys.systemName)}
                        />
                      ) : (
                        <Button
                          kind="ghost"
                          renderIcon={Play}
                          iconDescription="Launch"
                          hasIconOnly
                          onClick={() => onLaunch(sys.catalogName, sys.systemName)}
                        />
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  )
}
