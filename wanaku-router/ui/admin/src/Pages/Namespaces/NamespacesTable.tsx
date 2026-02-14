import {Column, DataTable, Grid, Table, TableBody, TableCell, TableHead, TableHeader, TableRow,} from "@carbon/react";
import React from "react";
import {Namespace} from "../../models";

interface NamespaceTableProps {
  namespaces: Namespace[];
}

export const NamespaceTable: React.FC<NamespaceTableProps> = ({
  namespaces,
}) => {
  return (
    <Grid>
      <Column lg={12} md={8} sm={4}>
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            alignItems: "center",
          }}
        >
        </div>
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
          )}
        </DataTable>
      </Column>
    </Grid>
  );
};