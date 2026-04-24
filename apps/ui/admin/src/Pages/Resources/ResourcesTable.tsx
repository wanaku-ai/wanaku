import React, {RefObject, useEffect, useImperativeHandle, useState} from "react"
import {
  Button,
  DataTable, DataTableSkeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableExpandedRow,
  TableExpandHeader,
  TableExpandRow,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent
} from "@carbon/react"
import {Add, Edit, TrashCan} from "@carbon/icons-react"
import {Param, ResourceReference} from "../../models"
import {getNamespacePathById} from "../../hooks/api/use-namespaces"
import {useResources} from "../../hooks/api/use-resources"


export interface RefreshHandle {
  refresh: () => void
}

interface ResourcesTableProps {
  onAdd: () => void
  onEdit: (resource: ResourceReference) => void
  onDelete: (resourceName: string) => void
  ref?: RefObject<RefreshHandle>
}

export const ResourcesTable: React.FC<ResourcesTableProps> = ({ onAdd, onEdit, onDelete, ref }) => {
  
  const [resources, setResources] = useState<ResourceReference[]>([])
  const [isLoading, setLoading] = useState(true)
  const { listResources } = useResources()
  
  useEffect(() => {
    (async () => {
      await fetchResources()
    })()
  }, [listResources])
  
  useImperativeHandle(ref, (): RefreshHandle => ({
    async refresh() {
      await fetchResources()
    }
  }), [])
  
  async function fetchResources() {
    try {
      const result = await listResources()
      const resources = result.data.data as ResourceReference[]
      setResources(resources)
    } catch (error) {
      console.error(error)
    } finally {
      setLoading(false)
    }
  }
  
  const headers = [
    {key: "name", header: "Name"},
    {key: "location", header: "Location"},
    {key: "type", header: "Type"},
    {key: "mimeType", header: "MIME Type"},
    {key: "description", header: "Description"},
    {key: "namespace", header: "Namespace"},
    {key: "actions", header: "Actions"}
  ]

  function resourcesToRows() {
    return resources.map((resource: ResourceReference, index: number) => ({
      id: resource.name || resource.id || `resource-${index}`,
      name: resource.name,
      location: resource.location,
      type: resource.type,
      mimeType: resource.mimeType,
      description: resource.description,
      namespace: resource.namespace
    }))
  }

  function resourceHasParameters(resource: ResourceReference): boolean {
    return !!resource.params?.length
  }

  function resourceHasDetails(resource: ResourceReference) {
    return resourceHasParameters(resource)
            || resource.configurationURI
            || resource.secretsURI
  }

  function tableCells(resource) {
    return (
      <React.Fragment>
        <TableCell>{resource.name}</TableCell>
        <TableCell>{resource.location}</TableCell>
        <TableCell>{resource.type}</TableCell>
        <TableCell>{resource.mimeType}</TableCell>
        <TableCell>{resource.description}</TableCell>
        <TableCell>{getNamespacePathById(resource.namespace)}</TableCell>
        <TableCell>
          <Button
            kind="ghost"
            renderIcon={Edit}
            hasIconOnly
            iconDescription="Edit"
            onClick={() => onEdit(resource)}
          />
          <Button
            kind="ghost"
            renderIcon={TrashCan}
            hasIconOnly
            iconDescription="Delete"
            onClick={() => onDelete(resource.name)}
          />
        </TableCell>
      </React.Fragment>
    )
  }

  function resourceDetails(resource: ResourceReference, rowProps) {
    return (
      <TableExpandedRow colSpan={headers.length + 3} {...rowProps}>
        {resourceHasParameters(resource) && (
          <div>
            <strong>Parameters:</strong>
            {resource.params?.map((parameter: Param) => {
              return (<div>{parameter.name + ": " + parameter.value}</div>)
            })}
          </div>
        )}
        {resource.configurationURI && (
          <div>
            <strong>Configuration URI:</strong> {resource.configurationURI}
          </div>
        )}
        {resource.secretsURI && (
          <div>
            <strong>Secrets URI:</strong> {resource.secretsURI}
          </div>
        )}
      </TableExpandedRow>
    )
  }
  
  return isLoading
    ? (<DataTableSkeleton />)
    : (<DataTable headers={headers} rows={resourcesToRows()}>
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
                <Button renderIcon={Add} onClick={onAdd}>Add Resource</Button>
              </TableToolbarContent>
            </TableToolbar>
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  <TableExpandHeader />
                  {headers.map((header) => (
                    <TableHeader {...getHeaderProps({header})}>
                      {header.header}
                    </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => {
                  const resource = resources.find((item) => (item.name || item.id) === row.id)
                  if (resource && resourceHasDetails(resource)) {
                    // resource with details, expansion available
                    return (
                      <React.Fragment key={resource.name}>
                        <TableExpandRow expandIconDescription="Show details" {...getRowProps({row})}>
                          {tableCells(resource)}
                        </TableExpandRow>
                        {row.isExpanded && resourceDetails(resource, getExpandedRowProps({row}))}
                      </React.Fragment>
                    )
                  } else if (resource) {
                    // resource without details, no expansion available
                    return (
                      <TableRow {...getRowProps({row})}>
                        <TableCell />
                        {tableCells(resource)}
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