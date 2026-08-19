import {Select, SelectItem} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {NamespaceEntry} from "../../models"
import {useNamespaces} from "../../hooks/api/use-namespaces"
import {sortedNamespaces} from "./namespaces.ts";


interface NamespaceSelectProps {
  id?: string
  labelText?: string
  helperText?: string
  value?: string
  onChange: (namespace: NamespaceEntry) => void
}

export const NamespaceSelect : React.FC<NamespaceSelectProps> = ({ id, labelText, helperText, value, onChange }) => {

  const [namespaces, setNamespaces] = useState<NamespaceEntry[]>([])
  const [selectedNamespace, setSelectedNamespace] = useState<NamespaceEntry>()
  const { listNamespaces } = useNamespaces()

  useEffect(() => {
    (async () => {
      const response = await listNamespaces()
      if (response.status == 200 && Array.isArray(response.data)) {
        const namespaces = sortedNamespaces(response.data)
        let selected = findDefaultNamespaceAmong(namespaces)
        if (value) {
          selected = findNamespaceAmong(value, namespaces)
        }
        setNamespaces(namespaces)
        setSelectedNamespace(selected)
      }
    })()
  }, [listNamespaces])

  function findNamespace(id: string): NamespaceEntry | undefined {
    return findNamespaceAmong(id, namespaces)
  }

  function findNamespaceAmong(id: string, namespaces: readonly NamespaceEntry[]): NamespaceEntry | undefined {
    return namespaces.find(namespace => namespace.id == id)
  }

  function defaultNamespace(): NamespaceEntry | undefined {
    return findDefaultNamespaceAmong(namespaces)
  }

  function findDefaultNamespaceAmong(namespaces: readonly NamespaceEntry[]): NamespaceEntry | undefined {
    return namespaces.find(namespace => namespace.path == "default")
  }

  return (
    <Select
      id={id || "namespace"}
      labelText={labelText || ""}
      helperText={helperText || ""}
      value={selectedNamespace?.id ?? defaultNamespace()?.id ?? undefined}
      onChange={(event) => {
        const namespace = findNamespace(event.target.value)
        if (namespace) {
          setSelectedNamespace(namespace)
          onChange(namespace)
        }
      }}
    >
      <SelectItem disabled hidden text="Choose a namespace" value="" />
      {namespaces.map((namespace: NamespaceEntry) => (
        <SelectItem
          key={namespace.id ?? namespace.name}
          id={namespace.id ?? ""}
          text={namespace.path ?? namespace.name}
          value={namespace.id ?? ""}
        />
      ))}
    </Select>
  )
}