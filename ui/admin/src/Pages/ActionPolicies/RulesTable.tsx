import { Rule } from "../../models"
import React from "react"
import {
  Button,
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableExpandHeader,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent
} from "@carbon/react"
import { Add } from "@carbon/icons-react"
import {TableEmptyState} from "../EmptyTableState.tsx";


interface RulesTableProps {
  rules: Rule[]
  onAdd: () => void
}


export const RulesTable: React.FC<RulesTableProps> = ({ rules, onAdd }) => {
  
  const headers = [
    { key: "description", header: "Description" }
  ]
  
  const rows = rules.map((rule, i) => ({
    id: `rule-${i}`,
    description: rule.description
  }))
  
  
  return (
    <DataTable headers={headers} rows={rows}>
      {({
          headers,
          rows,
          getToolbarProps,
          getTableProps,
          getHeaderProps,
          getRowProps
        }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd}>
                Add Rule
              </Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                <TableExpandHeader />
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row, i) => {
                const rule = rules[i]
                return (
                  <TableRow {...getRowProps({ row })} key={i}>
                    <TableCell>{rule.description}</TableCell>
                  </TableRow>
                )
              })}
              {rules.length == 0 && (
                <TableEmptyState
                  colSpan={headers.length}
                  title="No rules"
                  body="Add a new policy rule"
                />
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  )
}