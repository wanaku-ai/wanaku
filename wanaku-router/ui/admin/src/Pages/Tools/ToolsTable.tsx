import { Add, Upload, TrashCan, Edit } from "@carbon/icons-react";
import {
  Button,
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableExpandRow,
  TableExpandedRow,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent
} from "@carbon/react";
import React, { FunctionComponent } from "react";
import { ToolReference } from "../../models";
import { getNamespacePathById } from "../../hooks/api/use-namespaces";
import { formatInputSchema } from "./tools-utils.ts";
import { TableExpandHeader } from "@carbon/react";

interface ToolListProps {
  fetchedData: ToolReference[];
  onDelete: (toolName?: string) => void;
  onImport: () => void;
  onAdd: () => void;
  onEdit: (tool: ToolReference) => void
}

export const ToolsTable: FunctionComponent<ToolListProps> = ({
  fetchedData,
  onDelete,
  onImport,
  onAdd,
  onEdit
}) => {
  const headers = [
    {key: "name", header: "Name"},
    {key: "type", header: "Type"},
    {key: "description", header: "Description"},
    {key: "uri", header: "URI"},
    {key: "input-schema", header: "Input Schema"},
    {key: "namespace", header: "Namespace"},
    {key: "actions", header: "Actions"}
  ]

  function toolsToRows() {
    return fetchedData.map((tool: ToolReference, index: number) => ({
      id: tool.id || `tool-${index}`,
      ...tool
    }))
  }

  function toolHasDetails(tool: ToolReference) {
    return tool.configurationURI || tool.secretsURI
  }

  function tableCells(tool: ToolReference) {
    return (
      <React.Fragment>
        <TableCell>{tool.name}</TableCell>
        <TableCell>{tool.type}</TableCell>
        <TableCell>{tool.description}</TableCell>
        <TableCell style={{ wordWrap: "break-word" }}>
          {tool.uri}
        </TableCell>
        <TableCell style={{ fontSize: "14px" }}>
          {formatInputSchema(tool.inputSchema)}
        </TableCell>
        <TableCell>{getNamespacePathById(tool.namespace) || "default"}</TableCell>
        <TableCell>
          <Button
            kind="ghost"
            renderIcon={Edit}
            hasIconOnly
            iconDescription="Edit"
            onClick={() => onEdit(tool)}
          />
          <Button
            kind="ghost"
            renderIcon={TrashCan}
            iconDescription="Delete"
            hasIconOnly
            onClick={() => onDelete(tool.name)}
          />
        </TableCell>
      </React.Fragment>
    )
  }

  function toolDetails(tool: ToolReference, rowProps) {
    return (
        <TableExpandedRow colSpan={headers.length + 3} {...rowProps}>
          {tool.configurationURI && (
            <div>
              <strong>Configuration URI:</strong> {tool.configurationURI}
            </div>
          )}
          {tool.secretsURI && (
            <div>
              <strong>Secrets URI:</strong> {tool.secretsURI}
            </div>
          )}
        </TableExpandedRow>
    )
  }

  return (
    <DataTable headers={headers} rows={toolsToRows()}>
      {({
        headers,
        rows,
        getTableProps,
        getHeaderProps,
        getRowProps,
        getExpandedRowProps,
        getToolbarProps
        }) => (
          <TableContainer>
            <TableToolbar {...getToolbarProps()}>
              <TableToolbarContent>
                <Button
                  renderIcon={Upload}
                  kind="secondary"
                  onClick={onImport}>
                    Import Toolset
                </Button>
                <Button
                  renderIcon={Add}
                  onClick={onAdd}>
                    Add Tool
                </Button>
              </TableToolbarContent>
            </TableToolbar>
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  <TableExpandHeader/>
                  {headers.map((header) => (
                      <TableHeader {...getHeaderProps({header})}>
                        {header.header}
                      </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => {
                  const tool = fetchedData.find((item) => item.id === row.id)
                  if (tool && toolHasDetails(tool)) {
                    // tool with details, expansion available
                    return (
                        <React.Fragment key={tool.id}>
                          <TableExpandRow expandIconDescription="Show details" {...getRowProps({row})}>
                            {tableCells(tool)}
                          </TableExpandRow>
                          {row.isExpanded && toolDetails(tool, getExpandedRowProps({row}))}
                        </React.Fragment>
                    )
                  } else if (tool) {
                    // tool without details, no expansion available
                    return (
                      <TableRow {...getRowProps({row})}>
                        <TableCell />
                        {tableCells(tool)}
                      </TableRow>
                    )
                  }
                })}
              </TableBody>
            </Table>
          </TableContainer>
      )}
    </DataTable>
  )
}
