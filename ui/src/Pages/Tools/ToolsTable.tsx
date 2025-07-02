import { Add, Upload, TrashCan } from "@carbon/icons-react";
import {
  Button,
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import { FunctionComponent } from "react";
import { ToolReference } from "../../models";

interface ToolListProps {
  fetchedData: ToolReference[];
  onDelete: (toolName?: string) => void;
  onImport: () => void;
  onAdd: () => void;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const formatInputSchema = (inputSchema: any) => {
  return (
    Object.entries(inputSchema.properties)
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .map(([key, value]: [string, any]) => {
        return `${key}: ${value.type} - ${value.description}`;
      })
      .join("\n")
  );
};

export const ToolsTable: FunctionComponent<ToolListProps> = ({
  fetchedData,
  onDelete,
  onImport,
  onAdd,
}) => {
  const headers = [
    "Name",
    "Type",
    "Description",
    "URI",
    "Input Schema",
    "Namespace",
    "Actions",
  ];

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
          <Button kind="secondary" renderIcon={Upload} onClick={onImport}>
            Import Toolset
          </Button>
          <Button renderIcon={Add} onClick={onAdd}>
            Add Tool
          </Button>
        </div>
        <Table aria-label="Tools table">
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
            {fetchedData.map((row: ToolReference) => (
              <TableRow key={row.name}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.type}</TableCell>
                <TableCell>{row.description}</TableCell>
                <TableCell style={{ wordWrap: "break-word" }}>
                  {row.uri}
                </TableCell>
                <TableCell style={{ fontSize: "14px" }}>
                  {formatInputSchema(row.inputSchema)}
                </TableCell>
                <TableCell>{row.namespace || "default"}</TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    renderIcon={TrashCan}
                    iconDescription="Delete"
                    hasIconOnly
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
