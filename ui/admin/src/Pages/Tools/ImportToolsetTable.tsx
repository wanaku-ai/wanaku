import React from "react"
import {ToolEntry} from "../../models"
import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableSelectAll,
  TableSelectRow
} from "@carbon/react"


interface ImportToolsetTableProps {
  tools: ToolEntry[]
  selectedTools: ToolEntry[]
  onSelectionChange: (tools: ToolEntry[]) => void
}

export const ImportToolsetTable: React.FC<ImportToolsetTableProps> = ({ tools, selectedTools, onSelectionChange }) => {

  function addTool(tool: ToolEntry): void {
    if (!selectedTools.includes(tool)) {
      onSelectionChange([...selectedTools, tool])
    }
  }

  function removeTool(tool: ToolEntry): void {
    if (selectedTools.includes(tool)) {
      onSelectionChange(selectedTools.filter(item => item !== tool))
    }
  }

  return (
    <DataTable
      headers={[
        {key: "name", header: "Name"},
        {key: "description", header: "Description"}
      ]}
      rows={tools.map((tool: ToolEntry) => ({
        ...tool,
        id: tool.name!,
        isSelected: selectedTools.includes(tool)
      }))}
    >
    {({
      headers,
      rows,
      selectedRows,
      selectAll,
      getTableProps,
      getHeaderProps,
      getRowProps,
      getSelectionProps
    }) => {

      return (
        <Table {...getTableProps()}>
          <TableHead>
            <TableRow>
              <TableSelectAll {...getSelectionProps()}
                onSelect={() => {
                  const isChecked = selectedRows.length === rows.length
                  const isIndeterminate = selectedRows.length > 0 && selectedRows.length < rows.length
                  if (isChecked || isIndeterminate) {
                    onSelectionChange([])
                  } else {
                    onSelectionChange(tools)
                  }
                  selectAll()
                }}
              />
              {headers.map((header) => (
                <TableHeader {...getHeaderProps({header})}>
                  {header.header}
                </TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const tool = tools.find(item => item.name === row.id)!
              return (
                <TableRow {...getRowProps({ row })} key={tool.name}>
                  <TableSelectRow {...getSelectionProps({ row })}
                    onChange={value => {
                      if (value) {
                        addTool(tool)
                      } else {
                        removeTool(tool)
                      }
                    }}
                  />
                  <TableCell>{tool.name}</TableCell>
                  <TableCell>{tool.description}</TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}}
    </DataTable>
  )
}