import {Add, TrashCan} from "@carbon/icons-react";
import {
    Button,
    DataTable,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableHeader,
    TableRow,
    TableToolbar,
    TableToolbarContent,
} from "@carbon/react";
import {FunctionComponent} from "react";
import {PromptReference} from "../../models";
import {getNamespacePathById} from "../../hooks/api/use-namespaces";

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
    {key: "name", header: "Name"},
    {key: "description", header: "Description"},
    {key: "messages", header: "Messages"},
    {key: "arguments", header: "Arguments"},
    {key: "toolReferences", header: "Tool References"},
    {key: "namespace", header: "Namespace"},
    {key: "actions", header: "Actions"},
  ];

  function promptsToRows() {
    return fetchedData.map((prompt: PromptReference, index: number) => ({
      id: prompt.name || `prompt-${index}`,
      name: prompt.name,
      description: prompt.description,
      messages: formatMessages(prompt.messages),
      arguments: formatArguments(prompt.arguments),
      toolReferences: prompt.toolReferences?.join(", ") || "None",
      namespace: getNamespacePathById(prompt.namespace) || "default",
    }));
  }

  return (
    <DataTable headers={headers} rows={promptsToRows()}>
      {({headers, rows, getTableProps, getHeaderProps, getRowProps}) => (
          <TableContainer>
            <TableToolbar>
              <TableToolbarContent>
                <Button renderIcon={Add} onClick={onAdd}>
                  Add Prompt
                </Button>
              </TableToolbarContent>
            </TableToolbar>
            <Table {...getTableProps()} aria-label="Prompts table">
              <TableHead>
                <TableRow>
                  {headers.map((header) => (
                    <TableHeader {...getHeaderProps({header})}>
                      {header.header}
                    </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => {
                  const prompt = fetchedData.find((item) => item.name === row.id);
                  return (
                    <TableRow {...getRowProps({row})}>
                      {row.cells.map((cell) => {
                        if (cell.info.header === "arguments") {
                          return (
                            <TableCell key={cell.id} style={{ fontSize: "14px" }}>
                              {cell.value}
                            </TableCell>
                          );
                        }
                        if (cell.info.header === "actions") {
                          return (
                            <TableCell key={cell.id}>
                              <Button
                                kind="ghost"
                                renderIcon={TrashCan}
                                iconDescription="Delete"
                                hasIconOnly
                                onClick={() => onDelete(prompt?.name)}
                              />
                            </TableCell>
                          );
                        }
                        return <TableCell key={cell.id}>{cell.value}</TableCell>;
                      })}
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
