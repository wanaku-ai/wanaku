import {DataTable, Table, TableBody, TableCell, TableContainer, TableHead, TableHeader, TableRow,} from "@carbon/react";
import React from "react";
import {Namespace} from "../../models";

interface NamespaceTableProps {
  namespaces: Namespace[];
}

export const NamespaceTable: React.FC<NamespaceTableProps> = ({
  namespaces,
}) => {
  return (
    <DataTable
      rows={namespaces.map((namespace, index) => ({
        id: namespace.id || `namespace-${index}`,
        name: namespace.name || 'N/A',
        path: namespace.path || 'N/A',
        namespaceid: namespace.id || 'N/A',
      }))}
      headers={[
        { key: 'namespaceid', header: 'ID' },
        { key: 'name', header: 'Name' },
        { key: 'path', header: 'Path' },
      ]}
    >
      {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer>
          <Table {...getTableProps()} aria-label="Namespaces table">
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                const { key, ...rowProps } = getRowProps({ row });
                return (
                  <TableRow key={key} {...rowProps}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>{cell.value}</TableCell>
                    ))}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
