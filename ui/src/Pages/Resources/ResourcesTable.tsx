import {
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Button,
} from "@carbon/react";
import { TrashCan } from "@carbon/icons-react";
import React from "react";
import { ResourceReference } from "../../models";

interface ResourcesTableProps {
  resources: ResourceReference[];
  onDelete: (resourceName?: string) => void;
  onAdd: () => void;
}

export const ResourcesTable: React.FC<ResourcesTableProps> = ({
  resources,
  onDelete,
  onAdd,
}) => {
  const headers = ["Name", "Location", "Type", "Description", "Actions"];

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
          <Button onClick={onAdd}>Add Resource</Button>
        </div>
        <Table aria-label="Resources table">
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
            {resources.map((row: ResourceReference) => (
              <TableRow key={row.name}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.location}</TableCell>
                <TableCell>{row.type}</TableCell>
                <TableCell>{row.description}</TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    renderIcon={TrashCan}
                    hasIconOnly
                    iconDescription="Delete"
                    onClick={() => onDelete(row.name)}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Column>
    </Grid>
  );
};
