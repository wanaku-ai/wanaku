import { TrashCan, View } from '@carbon/icons-react';
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
} from '@carbon/react';
import React, { FunctionComponent, useState } from 'react';
import { ToolEntry } from '../../models';
import { getNamespacePathById } from '../../hooks/api/use-namespaces';
import { InputSchemaModal } from './InputSchemaModal';
import { TableEmptyState } from '../EmptyTableState';

interface ToolListProps {
  fetchedData: ToolEntry[];
  onDelete: (toolName?: string) => void;
}

export const ToolsTable: FunctionComponent<ToolListProps> = ({ fetchedData, onDelete }) => {
  const [schemaModalTool, setSchemaModalTool] = useState<ToolEntry | null>(null);
  const headers = [
    { key: 'name', header: 'Name' },
    { key: 'type', header: 'Type' },
    { key: 'description', header: 'Description' },
    { key: 'uri', header: 'URI' },
    { key: 'input-schema', header: 'Input Schema' },
    { key: 'namespace', header: 'Namespace' },
    { key: 'actions', header: 'Actions' },
  ];

  function toolsToRows() {
    return fetchedData.map((tool: ToolEntry, index: number) => ({
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
          <Button
            kind="ghost"
            renderIcon={TrashCan}
            iconDescription="Delete"
            hasIconOnly
            onClick={() => onDelete(tool.name)}
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
              <TableToolbarContent />
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
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DataTable>
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
};
