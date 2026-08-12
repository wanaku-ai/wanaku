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
import { Add, Edit, TrashCan } from "@carbon/icons-react";
import React from "react";
import { EvaluatorDef } from "../../hooks/api/use-evaluators";
import { TableEmptyState } from "../EmptyTableState";

interface EvaluatorsTableProps {
  evaluators: EvaluatorDef[];
  onAdd: () => void;
  onEdit: (evaluator: EvaluatorDef) => void;
  onDelete: (evaluator: EvaluatorDef) => void;
  disabled?: boolean;
}

export const EvaluatorsTable: React.FC<EvaluatorsTableProps> = ({
  evaluators,
  onAdd,
  onEdit,
  onDelete,
  disabled,
}) => {
  const headers = [
    { key: "name", header: "Name" },
    { key: "method", header: "Trigger Method" },
    { key: "namespace", header: "Namespace" },
    { key: "operation", header: "LLM Operation" },
    { key: "model", header: "LLM Model" },
    { key: "on_error", header: "Error Policy" },
  ];

  function evaluatorsToRows() {
    return evaluators.map((ev) => ({
      id: ev.name,
      name: ev.name,
      method: ev.trigger.method,
      namespace: ev.trigger.namespace || "—",
      operation: ev.llm.operation,
      model: ev.llm.model,
      on_error: ev.on_error,
    }));
  }

  return (
    <DataTable rows={evaluatorsToRows()} headers={headers}>
      {({ rows, headers, getToolbarProps, getTableProps, getHeaderProps, getRowProps }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd} disabled={disabled}>
                Add Evaluator
              </Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table {...getTableProps()}>
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
                const evaluator = evaluators.find((ev) => ev.name === row.id);
                if (evaluator) {
                  return (
                    <TableRow {...getRowProps({ row })}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
                      ))}
                      <TableCell>
                        <Button
                          kind="ghost"
                          renderIcon={Edit}
                          iconDescription="Edit"
                          hasIconOnly
                          onClick={() => onEdit(evaluator)}
                          disabled={disabled}
                        />
                        <Button
                          kind="ghost"
                          renderIcon={TrashCan}
                          iconDescription="Delete"
                          hasIconOnly
                          onClick={() => onDelete(evaluator)}
                          disabled={disabled}
                        />
                      </TableCell>
                    </TableRow>
                  );
                }
              })}
              {evaluators.length === 0 && (
                <TableEmptyState
                  colSpan={headers.length + 1}
                  title="No evaluators configured"
                  body="Click Add Evaluator to create your first evaluator definition."
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  );
};
