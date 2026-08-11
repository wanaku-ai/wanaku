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
} from "@carbon/react";
import { View } from "@carbon/icons-react";
import React from "react";
import type { PluginManifest } from "../../plugins/types";
import { TableEmptyState } from "../EmptyTableState";

interface PluginsTableProps {
  plugins: PluginManifest[];
  onView: (plugin: PluginManifest) => void;
}

export const PluginsTable: React.FC<PluginsTableProps> = ({ plugins, onView }) => {
  const headers = [
    { key: "name", header: "Name" },
    { key: "id", header: "ID" },
    { key: "version", header: "Version" },
    { key: "permissions", header: "Permissions" },
    { key: "hostApi", header: "Host API" },
  ];

  function pluginsToRows() {
    return plugins.map((plugin) => ({
      id: plugin.id,
      name: plugin.name,
      version: plugin.version,
      permissions:
        plugin.permissions && plugin.permissions.length > 0
          ? `${plugin.permissions.length} permissions`
          : "—",
      hostApi: plugin.requires?.hostApi || "—",
    }));
  }

  return (
    <DataTable rows={pluginsToRows()} headers={headers}>
      {({ rows, headers, getToolbarProps, getTableProps, getHeaderProps, getRowProps }) => (
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
                const plugin = plugins.find((p) => p.id === row.id);
                if (!plugin) return null;
                return (
                  <TableRow {...getRowProps({ row })} key={row.id}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>{cell.value}</TableCell>
                    ))}
                    <TableCell>
                      <Button
                        kind="ghost"
                        renderIcon={View}
                        iconDescription="View"
                        hasIconOnly
                        onClick={() => onView(plugin)}
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
              {plugins.length === 0 && (
                <TableEmptyState
                  colSpan={headers.length + 1}
                  title="No plugins installed"
                  body="Place plugin directories in the configured plugins path and restart the server."
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
