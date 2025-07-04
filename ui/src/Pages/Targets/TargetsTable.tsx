import {
  Column,
  DataTable,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableExpandedRow,
  TableExpandHeader,
  TableExpandRow,
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
          id: `${target.service}-${index}`,
          service: target.service,
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
{({ rows, headers, getTableProps, getHeaderProps, getRowProps, getExpandHeaderProps }) => (
          <Table {...getTableProps()} aria-label="Targets table">
            <TableHead>
              <TableRow>
                <TableExpandHeader {...getExpandHeaderProps()} />
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
                  <React.Fragment key={row.id}>
                    <TableExpandRow key={key} {...rowProps}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
                      ))}
                    </TableExpandRow>
                    {row.isExpanded && (
                      <TableExpandedRow colSpan={headers.length + 1}>
                        <div style={{ padding: '1rem' }}>
                          <h6 style={{ marginBottom: '1rem' }}>Configurations</h6>
                        </div>
                      </TableExpandedRow>
                    )}
                  </React.Fragment>
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
