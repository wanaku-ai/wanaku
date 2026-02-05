import { Add, Upload, TrashCan, Edit } from "@carbon/icons-react";
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
import { getNamespacePathById } from "../../hooks/api/use-namespaces";
import { formatInputSchema } from "./tools-utils.ts";

interface ToolListProps {
  fetchedData: ToolReference[];
  onDelete: (toolName?: string) => void;
  onImport: () => void;
  onAdd: () => void;
  onEdit: (tool: ToolReference) => void
}

export const ToolsTable: FunctionComponent<ToolListProps> = ({
  fetchedData,
  onDelete,
  onImport,
  onAdd,
  onEdit
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
            {fetchedData.map((tool: ToolReference) => (
              <TableRow key={tool.name}>
                <TableCell>{tool.name}</TableCell>
                <TableCell>{tool.type}</TableCell>
                <TableCell>{tool.description}</TableCell>
                <TableCell style={{ wordWrap: "break-word" }}>
                  {tool.uri}
                </TableCell>
                <TableCell style={{ fontSize: "14px" }}>
                  {formatInputSchema(tool.inputSchema)}
                </TableCell>
                <TableCell>{getNamespacePathById(tool.namespace) || "default"}</TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    renderIcon={Edit}
                    hasIconOnly
                    iconDescription="Edit"
                    onClick={() => onEdit(tool)}
                  />
                  <Button
                    kind="ghost"
                    renderIcon={TrashCan}
                    iconDescription="Delete"
                    hasIconOnly
                    onClick={() => onDelete(tool.name)}
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
