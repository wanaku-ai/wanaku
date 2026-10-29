import { View } from '@carbon/icons-react';
import {
  Button,
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
import React, { FunctionComponent, memo, useState } from 'react';
import { ToolEntry } from '../../models';
import { getNamespacePathById } from '../../hooks/api/use-namespaces';
import { InputSchemaModal } from './InputSchemaModal';
import { TableEmptyState } from '../EmptyTableState';
import { EnabledToggle } from '../../components/EnabledToggle';
import { PAGE_SIZES, useSearchPagination } from '../../hooks/search-pagination';

interface ToolListProps {
  fetchedData: ToolEntry[];
  onSetEnabled: (toolName: string, enabled: boolean) => void;
}

const toolSearchableText = (tool: ToolEntry) => [
  tool.name,
  tool.type,
  tool.description,
  tool.uri,
  getNamespacePathById(tool.namespace ?? undefined),
];

export const ToolsTable: FunctionComponent<ToolListProps> = memo(
  ({ fetchedData, onSetEnabled }) => {
    const [schemaModalTool, setSchemaModalTool] = useState<ToolEntry | null>(null);
    const { searchTerm, setSearchTerm, page, pageSize, onPageChange, pageItems, filteredCount } =
      useSearchPagination(fetchedData, toolSearchableText);
    const headers = [
      { key: 'name', header: 'Name' },
      { key: 'type', header: 'Type' },
      { key: 'description', header: 'Description' },
      { key: 'uri', header: 'URI' },
      { key: 'input-schema', header: 'Input Schema' },
      { key: 'namespace', header: 'Namespace' },
      { key: 'enabled', header: 'Enabled' },
    ];

    function toolsToRows() {
      return pageItems.map((tool: ToolEntry, index: number) => ({
        ...tool,
        id: tool.name || tool.id || `tool-${index}`,
      }));
    }

    function tableCells(tool: ToolEntry) {
      return (
        <React.Fragment>
          <TableCell>{tool.name}</TableCell>
          <TableCell>{tool.type}</TableCell>
          <TableCell>{tool.description}</TableCell>
          <TableCell style={{ wordWrap: 'break-word' }}>{tool.uri}</TableCell>
          <TableCell>
            {tool.inputSchema &&
            typeof tool.inputSchema === 'object' &&
            'properties' in tool.inputSchema &&
            (tool.inputSchema as any).properties &&
            Object.keys((tool.inputSchema as any).properties).length > 0 ? (
              <Button
                kind="ghost"
                size="sm"
                renderIcon={View}
                hasIconOnly
                iconDescription="View input schema"
                onClick={() => setSchemaModalTool(tool)}
              />
            ) : (
              <span style={{ color: 'var(--cds-text-secondary)' }}>&mdash;</span>
            )}
          </TableCell>
          <TableCell>{getNamespacePathById(tool.namespace ?? undefined)}</TableCell>
          <TableCell>
            <EnabledToggle
              name={tool.name}
              enabled={tool.enabled !== false}
              onSetEnabled={onSetEnabled}
            />
          </TableCell>
        </React.Fragment>
      );
    }

    return (
      <>
        <DataTable headers={headers} rows={toolsToRows()}>
          {({ headers, rows, getTableProps, getHeaderProps, getRowProps, getToolbarProps }) => (
            <TableContainer>
              <TableToolbar {...getToolbarProps()}>
                <TableToolbarContent>
                  <TableToolbarSearch
                    persistent
                    placeholder="Search tools"
                    value={searchTerm}
                    onChange={(event) =>
                      setSearchTerm(typeof event === 'string' ? event : event.target.value)
                    }
                  />
                </TableToolbarContent>
              </TableToolbar>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {headers.map((header) => (
                      <TableHeader {...getHeaderProps({ header })}>{header.header}</TableHeader>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => {
                    const tool = fetchedData.find((item) => (item.name || item.id) === row.id);
                    if (tool) {
                      return <TableRow {...getRowProps({ row })}>{tableCells(tool)}</TableRow>;
                    }
                  })}
                  {fetchedData.length == 0 && (
                    <TableEmptyState
                      colSpan={headers.length}
                      title="No tools discovered yet"
                      body="Register a forwarded MCP server to auto-discover tools"
                    />
                  )}
                  {fetchedData.length > 0 && filteredCount == 0 && (
                    <TableEmptyState
                      colSpan={headers.length}
                      title="No matching tools"
                      body="No tools match the current search."
                    />
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
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
        {schemaModalTool && (
          <InputSchemaModal
            inputSchema={schemaModalTool.inputSchema as any}
            toolName={schemaModalTool.name}
            open={true}
            onClose={() => setSchemaModalTool(null)}
          />
        )}
      </>
    );
  },
);
