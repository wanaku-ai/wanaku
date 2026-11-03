import React, {useState} from "react"
import {
  Checkbox, DataTable,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell, TableContainer, TableHead, TableHeader,
  TableRow,
  TextInput
} from "@carbon/react"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"
import {MatchKind, NamespaceEntry, TargetType} from "../../models"
import {DEFAULT_NAMESPACE} from "../../hooks/api/use-namespaces"


interface RuleSelectorsPanelProps {

}


export const RuleSelectorsPanel: React.FC<RuleSelectorsPanelProps> = ({}) => {
  
  const [selectionByLabel, setSelectionByLabel] = useState(false)
  const [labelValue, setLabelValue] = useState("")
  const [namespaceSelection, setNamespaceSelection] = useState(false)
  const [selectedNamespace, setSelectedNamespace] = useState<NamespaceEntry>(DEFAULT_NAMESPACE)
  const [operationSelection, setOperationSelection] = useState(false)
  const [operationValue, setOperationValue] = useState("")
  const [targetNameSelection, setTargetNameSelection] = useState(false)
  const [targetNameMatcher, setTargetNameMatcher] = useState<MatchKind>("exact")
  const [targetNameValue, setTargetNameValue] = useState("")
  const [targetTypeSelection, setTargetTypeSelection] = useState(false)
  const [targetTypeValue, setTargetTypeValue] = useState<TargetType>("tool")
  const [uriSelection, setUriSelection] = useState(false)
  const [uriMatcher, setUriMatcher] = useState<MatchKind>("exact")
  const [uriValue, setUriValue] = useState("")
  
  
  const headers = [
    {key: "selector", header: "Selector"},
    {key: "additional", header: ""},
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
                <TableRow key="label-selector">
                  <TableCell>
                    <Checkbox
                      id="label-selector-checkbox"
                      labelText="Label"
                      checked={selectionByLabel}
                      onChange={(_, { checked }) => {
                        setSelectionByLabel(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <TextInput
                      id="label-selector-input"
                      labelText=""
                      value={labelValue}
                      onChange={event => {
                        const labelValue = event.target.value
                        setLabelValue(labelValue)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow key="namespace-selector">
                  <TableCell>
                    <Checkbox
                      id="namespace-selector-checkbox"
                      labelText="Namespace"
                      checked={namespaceSelection}
                      onChange={(_, { checked }) => {
                        setNamespaceSelection(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <NamespaceSelect
                      value={selectedNamespace.name}
                      onChange={(namespace: NamespaceEntry) => {
                        setSelectedNamespace(namespace)
                      }}>
                    </NamespaceSelect>
                  </TableCell>
                </TableRow>
          
                <TableRow key="operation-selector">
                  <TableCell>
                    <Checkbox
                      id="operation-selector-checkbox"
                      labelText="Operation"
                      checked={operationSelection}
                      onChange={(_, { checked }) => {
                        setOperationSelection(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <TextInput
                      id="operation-selector-input"
                      labelText=""
                      value={operationValue}
                      onChange={event => {
                        const operationValue = event.target.value
                        setOperationValue(operationValue)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow key="target-name-selector">
                  <TableCell>
                    <Checkbox
                      id="target-name-selector-checkbox"
                      labelText="Target name"
                      checked={targetNameSelection}
                      onChange={(_, { checked }) => {
                        setTargetNameSelection(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <MatchSelect
                      value={targetNameMatcher}
                      onChange={setTargetNameMatcher}
                    />
                  </TableCell>
                  <TableCell>
                    <TextInput
                      id="target-name-selector-value"
                      labelText=""
                      value={targetNameValue}
                      onChange={event => {
                        const targetNameValue = event.target.value
                        setTargetNameValue(targetNameValue)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow>
                  <TableCell>
                    <Checkbox
                      id="target-type-selector"
                      labelText="Target type"
                      checked={targetTypeSelection}
                      onChange={(_, { checked }) => {
                        setTargetTypeSelection(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <Select
                      id="target-type-selector-select"
                      labelText=""
                      value={targetTypeValue}
                      onChange={event => {
                        const targetType = event.target.value as TargetType
                        setTargetTypeValue(targetType)
                      }}
                    >
                      <SelectItem text="Tool" value="tool" />
                      <SelectItem text="Resource" value="resource" />
                      <SelectItem text="Prompt" value="prompt" />
                    </Select>
                  </TableCell>
                </TableRow>
          
                <TableRow>
                  <TableCell>
                    <Checkbox
                      id="uri-selector"
                      labelText="URI"
                      checked={uriSelection}
                      onChange={(_, { checked }) => {
                        setUriSelection(checked)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <MatchSelect
                      value={uriMatcher}
                      onChange={setUriMatcher}
                    />
                  </TableCell>
                  <TableCell>
                    <TextInput
                      id="uri-selector-value"
                      labelText=""
                      value={uriValue}
                      onChange={event => {
                        const uri = event.target.value
                        setUriValue(uri)
                      }}
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


interface MatchSelectProps {
  value: MatchKind
  onChange: (value: MatchKind) => void
}


const MatchSelect: React.FC<MatchSelectProps> = ({ value, onChange }) => {
  return (
    <Select
      id="target-name-matcher"
      labelText=""
      value={value}
      onChange={event => {
        const value = event.target.value as MatchKind
        onChange(value)
      }}
    >
      <SelectItem text="Exact" value="exact" />
      <SelectItem text="Glob" value="glob" />
      <SelectItem text="Prefix" value="prefix" />
    </Select>
  )
}