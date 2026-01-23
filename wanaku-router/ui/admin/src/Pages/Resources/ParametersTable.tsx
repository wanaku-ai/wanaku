import { FunctionComponent } from "react";
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
  TextInput
} from "@carbon/react";
import { Add, TrashCan } from "@carbon/icons-react";
import { Param } from "../../models";

interface ParametersTableProps {
  parameters: Param[]
  onAdd: () => void
  onSetName: (index: number, name: string) => void
  onSetValue: (index: number, value: string) => void
  onDelete: (index: number) => void
}

export const ParametersTable: FunctionComponent<ParametersTableProps> = ({
  parameters,
  onDelete,
  onAdd,
  onSetName,
  onSetValue,
}) => {

  const headers = [
    {key: "name", header: "Name"},
    {key: "value", header: "Value"},
    {key: "actions", header: "Actions"}
  ]

  function parametersAsRows() {
    return parameters.map((parameter: Param, index: number) => ({
        id: parameter.name || `parameter-${index}`,
        name: parameter.name,
        value: parameter.value ,
    }))
  }

  return (
    <DataTable headers={headers} rows={parametersAsRows()}>
      {({
        headers,
        rows,
        getTableProps,
        getHeaderProps,
        getRowProps,
        getToolbarProps,
      }) => (
        <TableContainer>
          <TableToolbar {...getToolbarProps()}>
            <TableToolbarContent>
              <Button renderIcon={Add} onClick={onAdd}>New Parameter</Button>
            </TableToolbarContent>
          </TableToolbar>
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({header})}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row, i) => {
                const parameter = parameters[i]
                const alreadyFilled = parameter && parameter.name
                return (
                  <TableRow {...getRowProps({row})}>
                    <TableCell>
                      {alreadyFilled ? (
                        // if the name is already filled, just display it
                        parameter.name
                      ) : (
                        <TextInput
                            id={"parameter-" + i + "-name"}
                            labelText="Parameter name"
                            placeholder="Parameter name"
                            onChange={(event) => onSetName(i, event.target.value)}
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {alreadyFilled ? (
                        // if the value is already filled, just display it
                        parameter.value
                      ) : (
                        <TextInput
                            id={"parameter-" + i + "-value"}
                            labelText="Parameter value"
                            placeholder="Parameter value"
                            onChange={(event) => onSetValue(i, event.target.value)}
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      <Button kind="ghost"
                              renderIcon={TrashCan}
                              iconDescription="Delete"
                              hasIconOnly
                              onClick={() => onDelete(i)}
                      />
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  )
}