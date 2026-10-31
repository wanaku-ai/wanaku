import {
  DataTable,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
} from '@carbon/react';
import { FunctionComponent, memo } from 'react';
import { PromptEntry } from '../../models';
import { getNamespacePathById } from '../../hooks/api/use-namespaces';
import { TableEmptyState } from '../EmptyTableState';
import { EnabledToggle } from '../../components/EnabledToggle';
import { PAGE_SIZES, useSearchPagination } from '../../hooks/search-pagination';

interface PromptsListProps {
  fetchedData: PromptEntry[];
  onSetEnabled: (promptName: string, namespace: string, enabled: boolean) => void;
}

const formatMessages = (messages?: any[]) => {
  if (!messages || messages.length === 0) return 'No messages';
  return `${messages.length} message(s)`;
};

const formatArguments = (args?: any[]) => {
  if (!args || args.length === 0) return 'No arguments';
  return args.map((arg) => `${arg.name}${arg.required ? '*' : ''}`).join(', ');
};

const promptSearchableText = (prompt: PromptEntry) => [
  prompt.name,
  prompt.description,
  getNamespacePathById(prompt.namespace ?? undefined),
];

export const PromptsTable: FunctionComponent<PromptsListProps> = memo(
  ({ fetchedData, onSetEnabled }) => {
    const { searchTerm, setSearchTerm, page, pageSize, onPageChange, pageItems, filteredCount } =
      useSearchPagination(fetchedData, promptSearchableText);
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
      return pageItems.map((prompt: PromptEntry, index: number) => ({
        id: `${prompt.namespace ?? 'default'}/${prompt.name || `prompt-${index}`}`,
        name: prompt.name,
        description: prompt.description,
        messages: formatMessages(prompt.messages),
        arguments: formatArguments(prompt.arguments),
        toolReferences: 'N/A', // toolReferences not in API schema
        namespace: getNamespacePathById(prompt.namespace ?? undefined),
      }));
    }

    return (
      <>
        <DataTable headers={headers} rows={promptsToRows()}>
          {({ headers, rows, getTableProps, getHeaderProps, getRowProps }) => {
            return (
              <TableContainer>
                <TableToolbar>
                  <TableToolbarContent>
                    <TableToolbarSearch
                      persistent
                      placeholder="Search prompts"
                      value={searchTerm}
                      onChange={(event) =>
                        setSearchTerm(typeof event === 'string' ? event : event.target.value)
                      }
                    />
                  </TableToolbarContent>
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
                      const prompt = fetchedData.find(
                        (item) => `${item.namespace ?? 'default'}/${item.name}` === row.id,
                      );
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
                                    namespace={prompt.namespace}
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
                    {fetchedData.length > 0 && filteredCount == 0 && (
                      <TableEmptyState
                        colSpan={headers.length}
                        title="No matching prompts"
                        body="No prompts match the current search."
                      />
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            );
          }}
        </DataTable>
        {filteredCount > 0 && (
          <Pagination
            page={page}
            pageSize={pageSize}
            pageSizes={PAGE_SIZES}
            totalItems={filteredCount}
            onChange={onPageChange}
          />
        )}
      </>
    );
  },
);
