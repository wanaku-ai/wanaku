import React, { RefObject, useEffect, useImperativeHandle, useState } from 'react';
import {
  Button,
  DataTable,
  DataTableSkeleton,
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
import { TrashCan } from '@carbon/icons-react';
import { ResourceEntry } from '../../models';
import { getNamespacePathById } from '../../hooks/api/use-namespaces';
import { useResources } from '../../hooks/api/use-resources';
import { TableEmptyState } from '../EmptyTableState';
import { getErrorMessage } from '../../utils/error';

export interface RefreshHandle {
  refresh: () => void;
}

interface ResourcesTableProps {
  onDelete: (resourceName: string) => void;
  onError?: (message: string) => void;
  ref?: RefObject<RefreshHandle>;
}

export const ResourcesTable: React.FC<ResourcesTableProps> = ({ onDelete, onError, ref }) => {
  const [resources, setResources] = useState<ResourceEntry[]>([]);
  const [isLoading, setLoading] = useState(true);
  const { listResources } = useResources();

  useEffect(() => {
    (async () => {
      await fetchResources();
    })();
  }, [listResources]);

  useImperativeHandle(
    ref,
    (): RefreshHandle => ({
      async refresh() {
        await fetchResources();
      },
    }),
    [],
  );

  async function fetchResources() {
    try {
      const result = await listResources();
      const resources = result.data as ResourceEntry[];
      setResources(resources);
    } catch (error) {
      onError?.(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  const headers = [
    { key: 'name', header: 'Name' },
    { key: 'location', header: 'Location' },
    { key: 'type', header: 'Type' },
    { key: 'mimeType', header: 'MIME Type' },
    { key: 'description', header: 'Description' },
    { key: 'namespace', header: 'Namespace' },
    { key: 'actions', header: 'Actions' },
  ];

  function resourcesToRows() {
    return resources.map((resource: ResourceEntry, index: number) => ({
      id: resource.name || resource.id || `resource-${index}`,
      name: resource.name,
      location: resource.location,
      type: resource.type,
      mimeType: resource.mimeType,
      description: resource.description,
      namespace: resource.namespace,
    }));
  }

  function tableCells(resource: ResourceEntry) {
    return (
      <React.Fragment>
        <TableCell>{resource.name}</TableCell>
        <TableCell>{resource.location}</TableCell>
        <TableCell>{resource.type}</TableCell>
        <TableCell>{resource.mimeType}</TableCell>
        <TableCell>{resource.description}</TableCell>
        <TableCell>{getNamespacePathById(resource.namespace ?? undefined)}</TableCell>
        <TableCell>
          <Button
            kind="ghost"
            renderIcon={TrashCan}
            hasIconOnly
            iconDescription="Delete"
            onClick={() => onDelete(resource.name)}
          />
        </TableCell>
      </React.Fragment>
    );
  }

  return isLoading ? (
    <DataTableSkeleton />
  ) : (
    <DataTable headers={headers} rows={resourcesToRows()}>
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
                const resource = resources.find((item) => (item.name || item.id) === row.id);
                if (resource) {
                  return <TableRow {...getRowProps({ row })}>{tableCells(resource)}</TableRow>;
                }
              })}
              {resources.length == 0 && (
                <TableEmptyState
                  colSpan={headers.length}
                  title="No resources discovered yet"
                  body="Register a forwarded MCP server to auto-discover resources"
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
