import React, {useState} from "react"
import {
  Checkbox,
  DataTable,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput
} from "@carbon/react"
import {NamespaceSelect} from "../Namespaces/NamespaceSelect"
import {MatchKind, NamespaceEntry, Selectors, SelectorsLabels, TargetType} from "../../models"
import {DEFAULT_NAMESPACE} from "../../hooks/api/use-namespaces"


interface RuleSelectors {
  selectionByLabel: boolean
  labelValue: string
  namespaceSelection: boolean
  selectedNamespace: string
  operationSelection: boolean
  operationValue: string
  targetNameSelection: boolean
  targetNameMatcher: MatchKind
  targetNameValue: string
  targetTypeSelection: boolean
  targetTypeValue: TargetType
  uriSelection: boolean
  uriMatcher: MatchKind
  uriValue: string
}


interface RuleSelectorsPanelProps {
  selectors?: Selectors
  onChange: (selectors: Selectors) => void
}


export const RuleSelectorsPanel: React.FC<RuleSelectorsPanelProps> = ({ selectors, onChange }) => {
  
  const [ruleSelectors, setRuleSelectors] = useState<RuleSelectors>({
    selectionByLabel: selectors?.labels !== undefined,
    labelValue: selectors?.labels ? formatSelectorsLabels(selectors.labels!) : "",
    namespaceSelection: selectors?.namespace != null,
    selectedNamespace: selectors?.namespace || DEFAULT_NAMESPACE.name,
    operationSelection: selectors?.operation != null,
    operationValue: selectors?.operation || "",
    targetNameSelection: selectors?.target_name != null,
    targetNameMatcher: selectors?.target_name?.matcher || "exact",
    targetNameValue: selectors?.target_name?.value || "",
    targetTypeSelection: selectors?.target_type != null,
    targetTypeValue: selectors?.target_type || "tool",
    uriSelection: selectors?.uri != null,
    uriMatcher: selectors?.uri?.matcher || "exact",
    uriValue: selectors?.uri?.value || ""
  })
  
  const headers = [
    {key: "selector", header: "Selector"},
    {key: "additional", header: ""},
    {key: "value", header: "Value"}
  ]
  
  
  function propagateChanges(ruleSelectors: RuleSelectors) {
    const selectors: Selectors = {
      labels: parseSelectorsLabels(ruleSelectors.labelValue),
      namespace: ruleSelectors.namespaceSelection ? ruleSelectors.selectedNamespace : undefined,
      operation: ruleSelectors.operationSelection ? ruleSelectors.operationValue : undefined,
      target_name: ruleSelectors.targetNameSelection ? { matcher: ruleSelectors.targetNameMatcher, value: ruleSelectors.targetNameValue } : undefined,
      target_type: ruleSelectors.targetTypeSelection ? ruleSelectors.targetTypeValue : undefined,
      uri: ruleSelectors.uriSelection ? { matcher: ruleSelectors.uriMatcher, value: ruleSelectors.uriValue } : undefined
    }
    onChange(selectors)
  }
  
  function parseSelectorsLabels(input: string): SelectorsLabels {
    const result: SelectorsLabels = {}
    if (!input || !input.trim()) {
      return result
    }
    // Split by comma into individual key-value pairs
    const pairs = input.split(',')
    
    for (const pair of pairs) {
      const colonIndex = pair.indexOf(':')
      // Only process entries containing a colon
      if (colonIndex !== -1) {
        const key = pair.slice(0, colonIndex).trim()
        const value = pair.slice(colonIndex + 1).trim()
        if (key) {
          result[key] = value
        }
      }
    }
    return result
  }
  
  function formatSelectorsLabels(obj: SelectorsLabels): string {
    return Object.entries(obj).map(([key, value]) => `${key}: ${value}`).join(", ")
  }
  
  
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
                      checked={ruleSelectors.selectionByLabel}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, selectionByLabel: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <TextInput
                      id="label-selector-input"
                      labelText=""
                      value={ruleSelectors.labelValue}
                      placeholder="Label: value, Label2: value"
                      onChange={event => {
                        const value = event.target.value
                        const selectors = { ...ruleSelectors, labelValue: value }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow key="namespace-selector">
                  <TableCell>
                    <Checkbox
                      id="namespace-selector-checkbox"
                      labelText="Namespace"
                      checked={ruleSelectors.namespaceSelection}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, namespaceSelection: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <NamespaceSelect
                      value={ruleSelectors.selectedNamespace}
                      onChange={(namespace: NamespaceEntry) => {
                        const selectors = { ...ruleSelectors, namespace: namespace.name }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}>
                    </NamespaceSelect>
                  </TableCell>
                </TableRow>
          
                <TableRow key="operation-selector">
                  <TableCell>
                    <Checkbox
                      id="operation-selector-checkbox"
                      labelText="Operation"
                      checked={ruleSelectors.operationSelection}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, operationSelection: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <TextInput
                      id="operation-selector-input"
                      labelText=""
                      value={ruleSelectors.operationValue}
                      onChange={event => {
                        const value = event.target.value
                        const selectors = { ...ruleSelectors, operationValue: value }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow key="target-name-selector">
                  <TableCell>
                    <Checkbox
                      id="target-name-selector-checkbox"
                      labelText="Target name"
                      checked={ruleSelectors.targetNameSelection}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, targetNameSelection: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <MatchSelect
                      value={ruleSelectors.targetNameMatcher}
                      onChange={matchKind => {
                        const selectors = { ...ruleSelectors, targetNameMatcher: matchKind }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <TextInput
                      id="target-name-selector-value"
                      labelText=""
                      value={ruleSelectors.targetNameValue}
                      onChange={event => {
                        const targetNameValue = event.target.value
                        const selectors = { ...ruleSelectors, targetNameValue }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                </TableRow>
          
                <TableRow>
                  <TableCell>
                    <Checkbox
                      id="target-type-selector"
                      labelText="Target type"
                      checked={ruleSelectors.targetTypeSelection}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, targetTypeSelection: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell />
                  <TableCell>
                    <Select
                      id="target-type-selector-select"
                      labelText=""
                      value={ruleSelectors.targetTypeValue}
                      onChange={event => {
                        const targetType = event.target.value as TargetType
                        const selectors = { ...ruleSelectors, targetTypeValue: targetType }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
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
                      checked={ruleSelectors.uriSelection}
                      onChange={(_, { checked }) => {
                        const selectors = { ...ruleSelectors, uriSelection: checked }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <MatchSelect
                      value={ruleSelectors.uriMatcher}
                      onChange={matchKind => {
                        const selectors = { ...ruleSelectors, uriMatcher: matchKind }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
                      }}
                    />
                  </TableCell>
                  <TableCell>
                    <TextInput
                      id="uri-selector-value"
                      labelText=""
                      value={ruleSelectors.uriValue}
                      onChange={event => {
                        const selectors = { ...ruleSelectors, uriValue: event.target.value }
                        setRuleSelectors(selectors)
                        propagateChanges(selectors)
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