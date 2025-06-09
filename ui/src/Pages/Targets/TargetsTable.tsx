import {
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import React from "react";
import { ResourceReference, ServiceTarget } from "../../models";
import { ServiceTargetState } from "./ServiceTargetState";

interface TargetsTableProps {
  targets: ResourceReference[];
}

export const TargetsTable: React.FC<TargetsTableProps> = ({
  targets,
}) => {
  const headers = ["Service", "Service Type", "Host", "Port", "Active", "Last Seen"];

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
        <Table aria-label="Targets table">
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader id={header} key={header}>
                  {header}
                </TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {targets.map((row: ServiceTargetState) => (
              <TableRow key={row.service}>
                <TableCell>{row.service}</TableCell>
                <TableCell>{row.serviceType}</TableCell>
                <TableCell>{row.host}</TableCell>
                <TableCell>{row.port}</TableCell>
                <TableCell>{row.active ? "Active" : "Inactive"}</TableCell>
                <TableCell>{row.lastSeen}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Column>
    </Grid>
  );
};
