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
  Tag,
} from "@carbon/react";
import { View } from "@carbon/icons-react";
import React from "react";
import type { CredentialBinding } from "../../models";
import { TableEmptyState } from "../EmptyTableState";
import { formatMechanism } from "./bindings";

interface BindingsTableProps {
  bindings: CredentialBinding[];
  onViewDetails: (binding: CredentialBinding) => void;
}

const headers = [
  { key: "id", header: "ID" },
  { key: "forwardId", header: "Forward" },
  { key: "origin", header: "Origin" },
  { key: "mechanism", header: "Mechanism" },
  { key: "allowedPurposes", header: "Allowed Purposes" },
  { key: "secretRefs", header: "Secret Refs" },
  { key: "revision", header: "Revision" },
];

export const BindingsTable: React.FC<BindingsTableProps> = ({ bindings, onViewDetails }) => {
  const bindingsById = new Map(bindings.map((binding) => [binding.id, binding]));

  const rows = bindings.map((binding) => ({
    id: binding.id,
    forwardId: binding.forwardId,
    origin: binding.origin,
    mechanism: formatMechanism(binding.mechanism),
    allowedPurposes: (binding.allowedPurposes ?? []).join(", "),
    secretRefs: (binding.secretRefs ?? []).join(", "),
    revision: binding.revision ?? "—",
  }));

  return (
    <div data-testid="bindings-table">
      <DataTable rows={rows} headers={headers}>
        {({ rows: tableRows, headers: tableHeaders, getTableProps, getHeaderProps, getRowProps }) => (
          <TableContainer>
            <Table {...getTableProps()} aria-label="Credential bindings">
              <TableHead>
                <TableRow>
                  {tableHeaders.map((header) => (
                    <TableHeader {...getHeaderProps({ header })} key={header.key}>
                      {header.header}
                    </TableHeader>
                  ))}
                  <TableHeader>Details</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {tableRows.map((row) => {
                  const binding = bindingsById.get(row.id);
                  if (!binding) return null;
                  return (
                    <TableRow {...getRowProps({ row })} key={row.id}>
                      {row.cells.map((cell) => {
                        if (cell.info.header === "allowedPurposes") {
                          return (
                            <TableCell key={cell.id}>
                              {(binding.allowedPurposes ?? []).map((purpose, index) => (
                                <Tag key={`${purpose}-${index}`} type="blue" size="sm">{purpose}</Tag>
                              ))}
                            </TableCell>
                          );
                        }
                        if (cell.info.header === "secretRefs") {
                          return (
                            <TableCell key={cell.id}>
                              {(binding.secretRefs ?? []).map((ref, index) => (
                                <Tag key={`${ref}-${index}`} type="gray" size="sm">{ref}</Tag>
                              ))}
                            </TableCell>
                          );
                        }
                        return <TableCell key={cell.id}>{cell.value}</TableCell>;
                      })}
                      <TableCell>
                        <Button
                          hasIconOnly
                          kind="ghost"
                          renderIcon={View}
                          iconDescription={`View binding ${binding.id}`}
                          onClick={() => onViewDetails(binding)}
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
                {bindings.length === 0 && (
                  <TableEmptyState
                    colSpan={headers.length + 1}
                    title="No credential bindings"
                    body="Credential bindings are created and managed by operators and are not configurable from this UI."
                  />
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DataTable>
    </div>
  );
};
