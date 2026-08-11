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
import { Add, TrashCan } from "@carbon/icons-react";
import React from "react";
import { TableEmptyState } from "../EmptyTableState";

export interface BindingEntry {
  namespace: string;
  conversationId: string;
}

interface BindingsTableProps {
  bindings: BindingEntry[];
  onAdd: () => void;
  onDelete: (binding: BindingEntry) => void;
  disabled?: boolean;
}

export const BindingsTable: React.FC<BindingsTableProps> = ({
  bindings,
  onAdd,
  onDelete,
  disabled,
}) => {
  const headers = [
    { key: "namespace", header: "Namespace" },
    { key: "conversationId", header: "Conversation ID" },
  ];

  const bindingsByNamespace = new Map(bindings.map((b) => [b.namespace, b]));

  function bindingsToRows() {
    return bindings.map((b) => ({
      id: b.namespace,
      namespace: b.namespace,
      conversationId: b.conversationId,
    }));
  }

  return (
    <DataTable rows={bindingsToRows()} headers={headers}>
      {({ rows, headers, getToolbarProps, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd} disabled={disabled}>
                Add Binding
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
                const binding = bindingsByNamespace.get(row.id);
                if (!binding) return null;
                return (
                  <TableRow {...getRowProps({ row })} key={row.id}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>{cell.value}</TableCell>
                    ))}
                    <TableCell>
                      <Button
                        kind="ghost"
                        renderIcon={TrashCan}
                        iconDescription="Delete"
                        hasIconOnly
                        onClick={() => onDelete(binding)}
                        disabled={disabled}
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
              {bindings.length === 0 && (
                <TableEmptyState
                  colSpan={headers.length + 1}
                  title="No namespace bindings"
                  body="Click Add Binding to bind a namespace to a conversation."
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
