import React, {useEffect} from "react"
import {ToolReference} from "../../models"
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
  tools: ToolReference[]
  selectedTools: ToolReference[]
  onSelectionChange: (tools: ToolReference[]) => void
}

export const ImportToolsetTable: React.FC<ImportToolsetTableProps> = ({ tools, selectedTools, onSelectionChange }) => {
  
  function findTool(rowId: string) {
    return tools.find(item => item.name === rowId)
  }
  
  return (
    <DataTable
      headers={[
        {key: "name", header: "Name"},
        {key: "description", header: "Description"}
      ]}
      rows={tools.map((tool: ToolReference) => ({
        ...tool,
        id: tool.name!,
        isSelected: selectedTools.includes(tool)
      }))}
    >
    {({
      headers,
      rows,
      selectedRows,
      getTableProps,
      getHeaderProps,
      getRowProps,
      getSelectionProps
    }) => {
      
      // synchronize row selection with parent component
      useEffect(() => {
        onSelectionChange(selectedRows.map(row => findTool(row.id)!))
      }, [selectedRows])
      
      return (
        <Table {...getTableProps()}>
          <TableHead>
            <TableRow>
              <TableSelectAll {...getSelectionProps()} />
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
                  <TableSelectRow {...getSelectionProps({ row })} />
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