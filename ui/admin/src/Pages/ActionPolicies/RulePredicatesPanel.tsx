import {
  Checkbox,
  DataTable,
  Table,
  TableBody, TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow, TextInput
} from "@carbon/react"
import React from "react"
import {BooleanSelect} from "../../components/BooleanSelect"


interface RulePredicatesPanelProps {

}


export const RulePredicatesPanel: React.FC<RulePredicatesPanelProps> = ({}) => {
  
  const headers = [
    {key: "predicate", header: "Predicate"},
    {key: "pointer", header: "Pointer"},
    {key: "value", header: "Value"}
  ]
  
  
  return (
    <DataTable headers={headers} rows={[]}>
      {({
          headers,
          getTableProps,
          getHeaderProps
        }) => (
        <TableContainer>
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
              <TableRow key="exists">
                <TableCell>
                  <Checkbox
                    id="exists-predicate"
                    labelText="Exists"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="exists-pointer"
                  />
                </TableCell>
                <TableCell>
                  <BooleanSelect id="exists-value" />
                </TableCell>
              </TableRow>
              
              <TableRow key="equals">
                <TableCell>
                  <Checkbox
                    id="equals-predicate"
                    labelText="Equals"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="equals-pointer"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="equals-value"
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="not-equals">
                <TableCell>
                  <Checkbox
                    id="not-equals-predicate"
                    labelText="Not equals"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="not-equals-pointer"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="not-equals-value"
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="one-of">
                <TableCell>
                  <Checkbox
                    id="one-of-predicate"
                    labelText="One of"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="one-of-pointer"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="one-of-value"
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="not-one-of">
                <TableCell>
                  <Checkbox
                    id="not-one-of-predicate"
                    labelText="Not one of"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="not-one-of-pointer"
                  />
                </TableCell>
                <TableCell>
                  <TableTextInput
                    id="not-one-of-value"
                  />
                </TableCell>
              </TableRow>
              
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </DataTable>
  )
}


interface TableTextInputProps {
  id: string
}


const TableTextInput : React.FC<TableTextInputProps> = ({ id }) => {
  
  return (
    <TextInput
      id={id}
      labelText=""
    />
  )
  
}