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
} from "@carbon/react";
import { Add, Edit, TrashCan, Play, Stop } from "@carbon/icons-react";
import React from "react";
import { ProcessStatus } from "./installation-types";

interface InstallationsTableProps {
  installations: Array<{ id?: string; name?: string; data?: string; labels?: Record<string, string> }>;
  statusMap: Record<string, ProcessStatus>;
  onAdd: () => void;
  onEdit: (installation: any) => void;
  onDelete: (id: string) => void;
  onLaunch: (id: string) => void;
  onStop: (id: string) => void;
}

export const InstallationsTable: React.FC<InstallationsTableProps> = ({
  installations,
  statusMap,
  onAdd,
  onEdit,
  onDelete,
  onLaunch,
  onStop
}) => {
  const headers = [
    { key: "name", header: "Name" },
    { key: "type", header: "Type" },
    { key: "status", header: "Status" },
    { key: "port", header: "Port" }
  ];

  function installationsToRows() {
    return installations
      .filter((installation) => installation.id)
      .map((installation) => {
        const config = JSON.parse(installation.data || '{}');
        const status = statusMap[installation.id!];
        return {
          id: installation.id!,
          name: installation.name || '',
          type: config.type || 'unknown',
          status: status?.running ? 'Running' : 'Stopped',
          port: status?.port ? String(status.port) : '-'
        };
      });
  }

  return (
    <DataTable rows={installationsToRows()} headers={headers}>
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
                Add Installation
              </Button>
            </TableToolbarContent>
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
                const installation = installations.find(inst => inst.id === row.id);
                const status = statusMap[row.id];
                const isRunning = status?.running || false;

                if (installation) {
                  return (
                    <TableRow {...getRowProps({ row })} key={row.id}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
                      ))}
                      <TableCell>
                        {isRunning ? (
                          <Button
                            kind="ghost"
                            renderIcon={Stop}
                            iconDescription="Stop"
                            hasIconOnly
                            onClick={() => onStop(row.id)}
                          />
                        ) : (
                          <Button
                            kind="ghost"
                            renderIcon={Play}
                            iconDescription="Launch"
                            hasIconOnly
                            onClick={() => onLaunch(row.id)}
                          />
                        )}
                        <Button
                          kind="ghost"
                          renderIcon={Edit}
                          iconDescription="Edit"
                          hasIconOnly
                          onClick={() => onEdit(installation)}
                        />
                        <Button
                          kind="ghost"
                          renderIcon={TrashCan}
                          iconDescription="Delete"
                          hasIconOnly
                          disabled={isRunning}
                          onClick={() => onDelete(row.id)}
                        />
                      </TableCell>
                    </TableRow>
                  );
                }
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
