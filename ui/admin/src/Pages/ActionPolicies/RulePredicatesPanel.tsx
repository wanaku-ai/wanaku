import {
  Checkbox,
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput
} from "@carbon/react"
import React, {useState} from "react"
import {BooleanSelect} from "../../components/BooleanSelect"
import {Predicate} from "../../models"


interface RulePredicates {
  existsSelected: boolean
  existsPointer: string
  existsValue: boolean
  equalsSelected: boolean
  equalsPointer: string
  equalsValue: string
  notEqualsSelected: boolean
  notEqualsPointer: string
  notEqualsValue: string
  oneOfSelected: boolean
  oneOfPointer: string
  oneOfValue: string
  notOneOfSelected: boolean
  notOneOfPointer: string
  notOneOfValue: string
}


interface RulePredicatesPanelProps {
  predicates: Predicate[]
  onChange: (predicates: Predicate[]) => void
}


export const RulePredicatesPanel: React.FC<RulePredicatesPanelProps> = ({ predicates, onChange }) => {
  
  const [rulePredicates, setRulePredicates] = useState<RulePredicates>({
    existsSelected: containsPredicate("exists"),
    existsPointer: getPointer("exists"),
    existsValue: getBooleanValue("exists"),
    equalsSelected: containsPredicate("equals"),
    equalsPointer: getPointer("equals"),
    equalsValue: getStringValue("equals"),
    notEqualsSelected: containsPredicate("not_equals"),
    notEqualsPointer: getPointer("not_equals"),
    notEqualsValue: getStringValue("not_equals"),
    oneOfSelected: containsPredicate("one_of"),
    oneOfPointer: getPointer("one_of"),
    oneOfValue: getJointStringValue("one_of"),
    notOneOfSelected: containsPredicate("not_one_of"),
    notOneOfPointer: getPointer("not_one_of"),
    notOneOfValue: getJointStringValue("not_one_of")
  })
  
  const headers = [
    {key: "predicate", header: "Predicate"},
    {key: "pointer", header: "Pointer"},
    {key: "value", header: "Value"}
  ]

  
  function propagateChanges(rulePredicates: RulePredicates) {
    const predicates: Predicate[] = []
    if (rulePredicates.existsSelected) {
      predicates.push({ operator: "exists", pointer: rulePredicates.existsPointer, value: rulePredicates.existsValue })
    }
    if (rulePredicates.equalsSelected) {
      predicates.push({ operator: "equals", pointer: rulePredicates.equalsPointer, value: rulePredicates.equalsValue })
    }
    if (rulePredicates.notEqualsSelected) {
      predicates.push({ operator: "not_equals", pointer: rulePredicates.notEqualsPointer, value: rulePredicates.notEqualsValue })
    }
    if (rulePredicates.oneOfSelected) {
      predicates.push({ operator: "one_of", pointer: rulePredicates.oneOfPointer, values: rulePredicates.oneOfValue.split(",") })
    }
    if (rulePredicates.notOneOfSelected) {
      predicates.push({ operator: "not_one_of", pointer: rulePredicates.notOneOfPointer, values: rulePredicates.notOneOfValue.split(",")})
    }
    onChange(predicates)
  }
  
  function containsPredicate(operator: "exists" | "equals" | "not_equals" | "one_of" | "not_one_of"): boolean {
    return predicates.some((predicate) => predicate.operator === operator)
  }
  
  function getPointer(operator: "exists" | "equals" | "not_equals" | "one_of" | "not_one_of"): string {
    const predicate = predicates.find(p => p.operator === operator)
    return predicate?.pointer || ""
  }
  
  function getBooleanValue(operator: "exists"): boolean {
    const predicate = predicates.find(p => p.operator === operator)
    return predicate?.value || false
  }
  
  function getStringValue(operator: "equals" | "not_equals"): string {
    const predicate = predicates.find(p => p.operator === operator) as any
    return predicate?.value || ""
  }
  
  function getJointStringValue(operator: "one_of" | "not_one_of"): string {
    const predicate = predicates.find(p => p.operator === operator) as any
    return predicate?.values?.join(",") || ""
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
              <TableRow key="exists">
                <TableCell>
                  <Checkbox
                    id="exists-predicate"
                    labelText="Exists"
                    checked={rulePredicates.existsSelected}
                    onChange={(_, { checked }) => {
                      const predicates = { ...rulePredicates, existsSelected: checked }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="exists-pointer"
                    labelText=""
                    value={rulePredicates.existsPointer}
                    onChange={event => {
                      const pointer = event.target.value
                      const predicates = { ...rulePredicates, existsPointer: pointer }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <BooleanSelect
                    id="exists-value"
                    value={rulePredicates.existsValue}
                    onChange={value => {
                      const predicates = { ...rulePredicates, existsValue: value }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="equals">
                <TableCell>
                  <Checkbox
                    id="equals-predicate"
                    labelText="Equals"
                    checked={rulePredicates.equalsSelected}
                    onChange={(_, { checked }) => {
                      const predicates = { ...rulePredicates, equalsSelected: checked }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="equals-pointer"
                    labelText=""
                    value={rulePredicates.equalsPointer}
                    onChange={event => {
                      const pointer = event.target.value
                      const predicates = { ...rulePredicates, equalsPointer: pointer }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="equals-value"
                    labelText=""
                    value={rulePredicates.equalsValue}
                    onChange={event => {
                      const value = event.target.value
                      const predicates = { ...rulePredicates, equalsValue: value }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="not-equals">
                <TableCell>
                  <Checkbox
                    id="not-equals-predicate"
                    labelText="Not equals"
                    checked={rulePredicates.notEqualsSelected}
                    onChange={(_, { checked }) => {
                      const predicates = { ...rulePredicates, notEqualsSelected: checked }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="not-equals-pointer"
                    labelText=""
                    value={rulePredicates.notEqualsPointer}
                    onChange={event => {
                      const pointer = event.target.value
                      const predicates = { ...rulePredicates, notEqualsPointer: pointer }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="not-equals-value"
                    labelText=""
                    value={rulePredicates.notEqualsValue}
                    onChange={event => {
                      const value = event.target.value
                      const predicates = { ...rulePredicates, notEqualsValue: value }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="one-of">
                <TableCell>
                  <Checkbox
                    id="one-of-predicate"
                    labelText="One of"
                    checked={rulePredicates.oneOfSelected}
                    onChange={(_, { checked }) => {
                      const predicates = { ...rulePredicates, oneOfSelected: checked }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="one-of-pointer"
                    labelText=""
                    value={rulePredicates.oneOfPointer}
                    onChange={event => {
                      const pointer = event.target.value
                      const predicates = { ...rulePredicates, oneOfPointer: pointer }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="one-of-value"
                    labelText=""
                    placeholder="a,b,c"
                    value={rulePredicates.oneOfValue}
                    onChange={event => {
                      const value = event.target.value
                      const predicates = { ...rulePredicates, oneOfValue: value }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
              </TableRow>
              
              <TableRow key="not-one-of">
                <TableCell>
                  <Checkbox
                    id="not-one-of-predicate"
                    labelText="Not one of"
                    placeholder="a,b,c"
                    checked={rulePredicates.notOneOfSelected}
                    onChange={(_, { checked }) => {
                      const predicates = { ...rulePredicates, notOneOfSelected: checked }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="not-one-of-pointer"
                    labelText=""
                    value={rulePredicates.notOneOfPointer}
                    onChange={event => {
                      const pointer = event.target.value
                      const predicates = { ...rulePredicates, notOneOfPointer: pointer }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
                    }}
                  />
                </TableCell>
                <TableCell>
                  <TextInput
                    id="not-one-of-value"
                    labelText=""
                    value={rulePredicates.notOneOfValue}
                    onChange={event => {
                      const value = event.target.value
                      const predicates = { ...rulePredicates, notOneOfValue: value }
                      setRulePredicates(predicates)
                      propagateChanges(predicates)
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