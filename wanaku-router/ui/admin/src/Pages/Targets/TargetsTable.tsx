import {
  Column,
  DataTable,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import React from "react";
import { ServiceTargetState } from "./ServiceTargetState";

interface TargetsTableProps {
  targets: ServiceTargetState[];
}

export const TargetsTable: React.FC<TargetsTableProps> = ({
  targets,
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
        rows={targets.map((target, index) => ({
          id: `${target.serviceName}-${index}`,
          service: target.serviceName,
          serviceType: target.serviceType,
          host: target.host,
          port: target.port,
          active: target.active ? "Active" : "Inactive",
          lastSeen: target.lastSeen,
          reason: target.reason
        }))}
        headers={[
          { key: 'service', header: 'Service' },
          { key: 'serviceType', header: 'Service Type' },
          { key: 'host', header: 'Host' },
          { key: 'port', header: 'Port' },
          { key: 'active', header: 'Status' },
          { key: 'lastSeen', header: 'Last Seen' },
          { key: 'reason', header: 'Reason' },
        ]}
      >
{({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
          <Table {...getTableProps()} aria-label="Targets table">
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
