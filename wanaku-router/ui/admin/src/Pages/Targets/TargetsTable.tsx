import {DataTable, Table, TableBody, TableCell, TableContainer, TableHead, TableHeader, TableRow,} from "@carbon/react";
import React from "react";
import {ServiceTargetState} from "./ServiceTargetState";

interface TargetsTableProps {
  targets: ServiceTargetState[];
}

const formatStatus = (status: string): string => {
  return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
};

export const TargetsTable: React.FC<TargetsTableProps> = ({
  targets,
}) => {
  return (
    <DataTable
      rows={targets.map((target, index) => ({
        id: `${target.serviceName}-${index}`,
        service: target.serviceName,
        serviceType: target.serviceType,
        host: target.host,
        port: target.port,
        status: formatStatus(target.healthStatus ?? "pending"),
        lastSeen: target.lastSeen,
        reason: target.reason
      }))}
      headers={[
        { key: 'service', header: 'Service' },
        { key: 'serviceType', header: 'Service Type' },
        { key: 'host', header: 'Host' },
        { key: 'port', header: 'Port' },
        { key: 'status', header: 'Status' },
        { key: 'lastSeen', header: 'Last Seen' },
        { key: 'reason', header: 'Reason' },
      ]}
    >
      {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer>
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
        </TableContainer>
      )}
    </DataTable>
  );
};
