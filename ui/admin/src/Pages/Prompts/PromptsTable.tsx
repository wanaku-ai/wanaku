import {
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
} from '@carbon/react';
import { FunctionComponent, memo } from 'react';
import { PromptEntry } from '../../models';
import { getNamespacePathById } from '../../hooks/api/use-namespaces';
import { TableEmptyState } from '../EmptyTableState';
import { EnabledToggle } from '../../components/EnabledToggle';

interface PromptsListProps {
  fetchedData: PromptEntry[];
  onSetEnabled: (promptName: string, enabled: boolean) => void;
}

const formatMessages = (messages?: any[]) => {
  if (!messages || messages.length === 0) return 'No messages';
  return `${messages.length} message(s)`;
};

const formatArguments = (args?: any[]) => {
  if (!args || args.length === 0) return 'No arguments';
  return args.map((arg) => `${arg.name}${arg.required ? '*' : ''}`).join(', ');
};

export const PromptsTable: FunctionComponent<PromptsListProps> = memo(
  ({ fetchedData, onSetEnabled }) => {
    const headers = [
      { key: 'name', header: 'Name' },
      { key: 'description', header: 'Description' },
      { key: 'messages', header: 'Messages' },
      { key: 'arguments', header: 'Arguments' },
      { key: 'toolReferences', header: 'Tool References' },
      { key: 'namespace', header: 'Namespace' },
      { key: 'enabled', header: 'Enabled' },
    ];

    function promptsToRows() {
      return fetchedData.map((prompt: PromptEntry, index: number) => ({
        id: prompt.name || `prompt-${index}`,
        name: prompt.name,
        description: prompt.description,
        messages: formatMessages(prompt.messages),
        arguments: formatArguments(prompt.arguments),
        toolReferences: 'N/A', // toolReferences not in API schema
        namespace: getNamespacePathById(prompt.namespace ?? undefined),
      }));
    }

    return (
      <DataTable headers={headers} rows={promptsToRows()}>
        {({ headers, rows, getTableProps, getHeaderProps, getRowProps }) => {
          return (
            <TableContainer>
              <TableToolbar>
                <TableToolbarContent />
              </TableToolbar>
              <Table {...getTableProps()} aria-label="Prompts table">
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({ header })}>{header.header}</TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => {
                    const prompt = fetchedData.find((item) => item.name === row.id);
                    return (
                      <TableRow {...getRowProps({ row })}>
                        {row.cells.map((cell) => {
                          if (cell.info.header === 'arguments') {
                            return (
                              <TableCell key={cell.id} style={{ fontSize: '14px' }}>
                                {cell.value}
                              </TableCell>
                            );
                          }
                          if (cell.info.header === 'enabled' && prompt) {
                            return (
                              <TableCell key={cell.id}>
                                <EnabledToggle
                                  name={prompt.name}
                                  enabled={prompt.enabled !== false}
                                  onSetEnabled={onSetEnabled}
                                />
                              </TableCell>
                            );
                          }
                          return <TableCell key={cell.id}>{cell.value}</TableCell>;
                        })}
                      </TableRow>
                    );
                  })}
                  {fetchedData.length == 0 && (
                    <TableEmptyState
                      colSpan={headers.length}
                      title="No prompts discovered yet"
                      body="Register a forwarded MCP server to auto-discover prompts"
                    />
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          );
        }}
      </DataTable>
    );
  },
);
