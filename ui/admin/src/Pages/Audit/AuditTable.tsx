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
  Tag,
} from "@carbon/react";
import { View } from "@carbon/icons-react";
import type { AuditEvent } from "../../models";
import { TableEmptyState } from "../EmptyTableState";
import { formatAuditDate, getDecisionTagType } from "./audit-utils";

interface AuditTableProps {
  events: AuditEvent[];
  limit: number;
  offset: number;
  total: number;
  onPageChange: (offset: number, limit: number) => void;
  onSelect: (event: AuditEvent) => void;
}

const headers = [
  { key: "timestamp", header: "Timestamp" },
  { key: "decision", header: "Decision" },
  { key: "category", header: "Category" },
  { key: "operation", header: "Operation" },
  { key: "target", header: "Target" },
  { key: "namespace", header: "Namespace" },
  { key: "reasonCode", header: "Reason code" },
  { key: "correlationId", header: "Correlation ID" },
];

export const AuditTable = ({ events, limit, offset, total, onPageChange, onSelect }: AuditTableProps) => {
  const eventsById = new Map(events.map((event) => [event.event_id, event]));
  const rows = events.map((event) => ({
    id: event.event_id,
    category: event.category,
    correlationId: event.correlation_id,
    decision: event.decision,
    namespace: event.namespace ?? "—",
    operation: event.operation,
    reasonCode: event.reason_code,
    target: event.target ?? "—",
    timestamp: formatAuditDate(event.timestamp),
  }));

  return (
    <div data-testid="audit-table">
      <DataTable rows={rows} headers={headers}>
        {({ rows: tableRows, headers: tableHeaders, getTableProps, getHeaderProps, getRowProps }) => (
          <TableContainer>
            <Table {...getTableProps()} aria-label="Audit events">
              <TableHead>
                <TableRow>
                  {tableHeaders.map((header) => (
                    <TableHeader {...getHeaderProps({ header })} key={header.key}>{header.header}</TableHeader>
                  ))}
                  <TableHeader>Details</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {tableRows.map((row) => {
                  const event = eventsById.get(row.id);
                  if (!event) return null;
                  return (
                    <TableRow {...getRowProps({ row })} key={row.id}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.info.header === "decision" ? (
                            <Tag type={getDecisionTagType(event.decision)} size="sm">{event.decision}</Tag>
                          ) : <span className="audit-table__value" title={String(cell.value)}>{cell.value}</span>}
                        </TableCell>
                      ))}
                      <TableCell>
                        <Button
                          hasIconOnly
                          kind="ghost"
                          renderIcon={View}
                          iconDescription={`View audit event ${event.event_id}`}
                          onClick={() => onSelect(event)}
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
                {events.length === 0 && (
                  <TableEmptyState
                    colSpan={headers.length + 1}
                    title="No audit events"
                    body="No retained events match the current filters."
                  />
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DataTable>
      {total > 0 && (
        <Pagination
          page={Math.floor(offset / limit) + 1}
          pageSize={limit}
          pageSizes={[25, 50, 100]}
          totalItems={total}
          onChange={({ page, pageSize }) => onPageChange((page - 1) * pageSize, pageSize)}
        />
      )}
    </div>
  );
};
