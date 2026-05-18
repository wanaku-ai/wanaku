import {FunctionComponent, useState} from "react";
import {
  Button,
  DataTable,
  DataTableRow,
  Stack,
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
import {Add, Close, Edit, Save, TrashCan} from "@carbon/icons-react";
import {Param} from "../../models";


interface ParameterDraft {
  parameterName: string
  parameterValue: string
}

interface ParameterEntry extends Param {
  updateDraft?: ParameterDraft
}

interface ParametersTableProps {
  parameters: Param[]
  onUpdate: (parameters: Param[]) => void
  onInlineEditorOpen?: () => void
  onInlineEditorClose?: () => void
}

export const ParametersTable: FunctionComponent<ParametersTableProps> = ({
  parameters, onUpdate, onInlineEditorOpen, onInlineEditorClose
}) => {

  const [entries, setEntries] = useState<ParameterEntry[]>(parameters)
  
  const headers = [
    {key: "name", header: "Name"},
    {key: "value", header: "Value"},
    {key: "actions", header: "Actions"}
  ]
  
  function addParameter() {
    setEntries([...entries, { updateDraft: { parameterName: "", parameterValue: "" } }])
    if (onInlineEditorOpen) {
      onInlineEditorOpen()
    }
  }
  
  function updateParameterDraft(i: number, key: "parameterName" | "parameterValue", value: string) {
    const newEntries = structuredClone(entries)
    const entry = newEntries[i]
    if (entry.updateDraft) {
      entry.updateDraft[key] = value
      setEntries(newEntries)
    }
  }
  
  function editParameter(i: number) {
    const newEntries = structuredClone(entries)
    if (!newEntries.some(entry => entry.updateDraft) && onInlineEditorOpen) {
      onInlineEditorOpen()
    }
    const entry = newEntries[i]
    entry.updateDraft = { parameterName: entry.name!, parameterValue: entry.value! }
    setEntries(newEntries)
  }
  
  function mergeParameterDraft(i: number) {
    const newEntries = structuredClone(entries)
    const entry = newEntries[i]
    if (entry.updateDraft) {
      entry.name = entry.updateDraft.parameterName
      entry.value = entry.updateDraft.parameterValue
      entry.updateDraft = undefined
    }
    setEntries(newEntries)
    onUpdate(newEntries)
    if (newEntries.every(entry => !entry.updateDraft) && onInlineEditorClose) {
      onInlineEditorClose()
    }
  }
  
  function clearParameterDraft(i: number) {
    const newEntries = structuredClone(entries)
    const entry = newEntries[i]
    if (entry.name) {
      // underlying parameter exists, only remove the draft
      newEntries[i].updateDraft = undefined
    } else {
      // no underlying parameter, remove the whole entry
      newEntries.splice(i, 1)
    }
    setEntries(newEntries)
    if (newEntries.every(entry => !entry.updateDraft) && onInlineEditorClose) {
      onInlineEditorClose()
    }
  }
  
  function removeParameter(i: number) {
    const newEntries = structuredClone(entries)
    newEntries.splice(i, 1)
    setEntries(newEntries)
  }

  return (
    <DataTable headers={headers}
      rows={entries.map((_, i: number) => ({ id: `parameter-${i}` }))}>
      {({
        headers,
        rows,
        getTableProps,
        getHeaderProps,
        getRowProps,
        getToolbarProps,
      }) => {
        
        function createEmptyTableState() {
          return (
            <TableRow>
              <TableCell colSpan={headers.length}>
                <Stack gap={6} style={{
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '4rem 0',
                  color: 'var(--cds-text-secondary)'
                }}>
                  <div style={{ textAlign: 'center' }}>
                    <h4 style={{ marginBottom: '0.5rem', color: 'var(--cds-text-primary)' }}>
                      No parameters
                    </h4>
                    <p>You can add resource parameters here.</p>
                  </div>
                </Stack>
              </TableCell>
            </TableRow>
          )
        }
        
        function createParameterRow(row: DataTableRow<any[]>, i: number) {
          const entry = entries[i]
          return (
            <TableRow {...getRowProps({row})}>
              <TableCell>{entry.name}</TableCell>
              <TableCell>{entry.value}</TableCell>
              <TableCell>
                <Button
                  kind="ghost"
                  renderIcon={Edit}
                  iconDescription="Edit"
                  hasIconOnly
                  onClick={() => editParameter(i)}
                />
                <Button
                  kind="ghost"
                  renderIcon={TrashCan}
                  iconDescription="Delete"
                  hasIconOnly
                  onClick={() => removeParameter(i)}
                />
              </TableCell>
            </TableRow>
          )
        }
        
        function createParameterDraftRow(row: DataTableRow<any[]>, i: number) {
          const entry = entries[i]
          const draft = entry.updateDraft!
          return (
            <TableRow {...getRowProps({row})}>
              <TableCell>
                <TextInput
                  id={"parameter-" + i + "-name"}
                  labelText=""
                  placeholder="Parameter name"
                  value={draft.parameterName}
                  onChange={(event) => {
                    const name = event.target.value
                    updateParameterDraft(i, "parameterName", name)
                  }}
                />
              </TableCell>
              <TableCell>
                <TextInput
                  id={"parameter-" + i + "-value"}
                  labelText=""
                  placeholder="Parameter value"
                  value={draft.parameterValue}
                  onChange={(event) => {
                    const value = event.target.value
                    updateParameterDraft(i, "parameterValue", value)
                  }}
                />
              </TableCell>
              <TableCell>
                <Button
                  kind="ghost"
                  renderIcon={Save}
                  hasIconOnly
                  iconDescription="Save changes"
                  onClick={() => mergeParameterDraft(i)}
                />
                <Button
                  kind="ghost"
                  renderIcon={Close}
                  hasIconOnly
                  iconDescription="Cancel changes"
                  onClick={() => clearParameterDraft(i)}
                />
              </TableCell>
            </TableRow>
          )
        }
        
        return (
          <TableContainer>
            <TableToolbar {...getToolbarProps()}>
              <TableToolbarContent>
                <Button renderIcon={Add} kind="ghost" onClick={addParameter}>Add parameter</Button>
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
                {rows.filter((_, i) => entries[i]).map((row, i) => {
                  const entry = entries[i]
                  return entry.updateDraft
                    ? createParameterDraftRow(row, i)
                    : createParameterRow(row, i)
                })}
                {entries.length == 0 && createEmptyTableState()}
              </TableBody>
            </Table>
          </TableContainer>
      )}}
    </DataTable>
  )
}