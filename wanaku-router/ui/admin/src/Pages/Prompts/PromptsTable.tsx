import { Add, TrashCan } from "@carbon/icons-react";
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
import { PromptReference } from "../../models";
import { getNamespacePathById } from "../../hooks/api/use-namespaces";

interface PromptsListProps {
  fetchedData: PromptReference[];
  onDelete: (promptName?: string) => void;
  onAdd: () => void;
}

const formatMessages = (messages?: any[]) => {
  if (!messages || messages.length === 0) return "No messages";
  return `${messages.length} message(s)`;
};

const formatArguments = (args?: any[]) => {
  if (!args || args.length === 0) return "No arguments";
  return args.map(arg => `${arg.name}${arg.required ? '*' : ''}`).join(", ");
};

export const PromptsTable: FunctionComponent<PromptsListProps> = ({
  fetchedData,
  onDelete,
  onAdd,
}) => {
  const headers = [
    "Name",
    "Description",
    "Messages",
    "Arguments",
    "Tool References",
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
          <Button renderIcon={Add} onClick={onAdd}>
            Add Prompt
          </Button>
        </div>
        <Table aria-label="Prompts table">
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
            {fetchedData.map((row: PromptReference) => (
              <TableRow key={row.name}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.description}</TableCell>
                <TableCell>{formatMessages(row.messages)}</TableCell>
                <TableCell style={{ fontSize: "14px" }}>
                  {formatArguments(row.arguments)}
                </TableCell>
                <TableCell>
                  {row.toolReferences?.join(", ") || "None"}
                </TableCell>
                <TableCell>{getNamespacePathById(row.namespace) || "default"}</TableCell>
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
