import { Add } from '@carbon/icons-react';
import {
  Button,
  DataTable,
  OverflowMenu,
  OverflowMenuItem,
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
import type { AgentView } from '../../models';
import { TableEmptyState } from '../EmptyTableState';

interface AgentsTableProps {
  agents: AgentView[];
  onAdd: () => void;
  onDetails: (agent: AgentView) => void;
  onEdit: (agent: AgentView) => void;
  onDelete: (agent: AgentView) => void;
}

const headers = [
  { key: 'name', header: 'Name' },
  { key: 'namespace', header: 'Namespace' },
  { key: 'description', header: 'Description' },
  { key: 'address', header: 'Upstream endpoint' },
];

export function AgentsTable({ agents, onAdd, onDetails, onEdit, onDelete }: AgentsTableProps) {
  const rows = agents.map((agent) => ({
    ...agent,
    id: JSON.stringify([agent.namespace, agent.name]),
  }));
  return (
    <DataTable rows={rows} headers={headers}>
      {({
        rows: tableRows,
        headers: tableHeaders,
        getTableProps,
        getHeaderProps,
        getRowProps,
        getToolbarProps,
      }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd}>
                Add A2A agent
              </Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table {...getTableProps()} aria-label="A2A agents">
            <TableHead>
              <TableRow>
                {tableHeaders.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {tableRows.map((row) => {
                const agent = agents.find(
                  (entry) => JSON.stringify([entry.namespace, entry.name]) === row.id,
                );
                if (!agent) return null;
                return (
                  <TableRow {...getRowProps({ row })} key={row.id}>
                    {row.cells.map((cell) => (
                      <TableCell key={cell.id}>{cell.value || '—'}</TableCell>
                    ))}
                    <TableCell>
                      <OverflowMenu
                        ariaLabel={`Actions for ${agent.name} in ${agent.namespace}`}
                        flipped
                      >
                        <OverflowMenuItem itemText="Details" onClick={() => onDetails(agent)} />
                        <OverflowMenuItem itemText="Edit" onClick={() => onEdit(agent)} />
                        <OverflowMenuItem
                          itemText="Delete"
                          isDelete
                          onClick={() => onDelete(agent)}
                        />
                      </OverflowMenu>
                    </TableCell>
                  </TableRow>
                );
              })}
              {agents.length === 0 && (
                <TableEmptyState
                  colSpan={headers.length + 1}
                  title="No A2A agents registered"
                  body="Add an upstream agent to route A2A requests through Wanaku."
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
}
