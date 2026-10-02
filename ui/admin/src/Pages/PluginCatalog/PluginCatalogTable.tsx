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
} from '@carbon/react';
import type { PluginCatalogEntry } from '../../models';
import { TableEmptyState } from '../EmptyTableState';

interface PluginCatalogTableProps {
  plugins: PluginCatalogEntry[];
  onInfo: (plugin: PluginCatalogEntry, button: HTMLButtonElement) => void;
  onInstall: (plugin: PluginCatalogEntry) => void;
  installingId: string | null;
}

const headers = [
  { key: 'name', header: 'Name' },
  { key: 'version', header: 'Version' },
  { key: 'description', header: 'Description' },
];

export const PluginCatalogTable = ({
  plugins,
  onInfo,
  onInstall,
  installingId,
}: PluginCatalogTableProps) => (
  <DataTable
    rows={plugins.map((plugin, index) => ({
      id: String(index),
      name: plugin.name,
      version: plugin.version,
      description: plugin.description,
    }))}
    headers={headers}
  >
    {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
      <TableContainer>
        <Table {...getTableProps()} aria-label="Plugin catalog">
          <TableHead>
            <TableRow>
              {headers.map((header) => (
                <TableHeader {...getHeaderProps({ header })} key={header.key}>
                  {header.header}
                </TableHeader>
              ))}
              <TableHeader>Actions</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const plugin = plugins[Number(row.id)];
              return (
                <TableRow {...getRowProps({ row })} key={row.id}>
                  {row.cells.map((cell) => (
                    <TableCell key={cell.id}>
                      {cell.info.header === 'description' ? (
                        <span className="plugin-catalog__description">{cell.value}</span>
                      ) : (
                        cell.value
                      )}
                    </TableCell>
                  ))}
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      onClick={(event) => onInfo(plugin, event.currentTarget)}
                      aria-label={`Info for ${plugin.name}`}
                    >
                      Info
                    </Button>
                    <Button
                      kind="tertiary"
                      size="sm"
                      disabled={installingId !== null || !plugin.url}
                      onClick={() => onInstall(plugin)}
                      aria-label={`Install ${plugin.name}`}
                    >
                      {installingId === plugin.id ? 'Installing...' : 'Install'}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
            {plugins.length === 0 && (
              <TableEmptyState
                colSpan={4}
                title="No plugins available"
                body="The plugin catalog is empty."
              />
            )}
          </TableBody>
        </Table>
      </TableContainer>
    )}
  </DataTable>
);
