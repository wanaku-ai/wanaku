import {Select, SelectItem} from "@carbon/react"
import React, {useEffect, useState} from "react"
import {Namespace} from "../../models"
import {useNamespaces} from "../../hooks/api/use-namespaces"


interface NamespaceSelectProps {
  id?: string
  labelText?: string
  helperText?: string
  value?: string
  onChange: (namespace: Namespace) => void
}

export const NamespaceSelect : React.FC<NamespaceSelectProps> = ({ id, labelText, helperText, value, onChange }) => {
  
  const [namespaces, setNamespaces] = useState<Namespace[]>([])
  const [selectedNamespace, setSelectedNamespace] = useState<Namespace>()
  const { listNamespaces } = useNamespaces()
  
  useEffect(() => {
    (async () => {
      const response = await listNamespaces()
      if (response.status == 200 && Array.isArray(response.data.data)) {
        let selected = findDefaultNamespaceAmong(response.data.data)
        if (value) {
          selected = findNamespaceAmong(value, response.data.data)
        }
        setNamespaces(response.data.data)
        setSelectedNamespace(selected)
      }
    })()
  }, [])
  
  function findNamespace(id: string): Namespace | undefined {
    return findNamespaceAmong(id, namespaces)
  }
  
  function findNamespaceAmong(id: string, namespaces: readonly Namespace[]): Namespace | undefined {
    return namespaces.find(namespace => namespace.id == id)
  }
  
  function defaultNamespace(): Namespace | undefined {
    return findDefaultNamespaceAmong(namespaces)
  }
  
  function findDefaultNamespaceAmong(namespaces: readonly Namespace[]): Namespace | undefined {
    return namespaces.find(namespace => namespace.path == "default")
  }
  
  return (
    <Select
      id={id || "namespace"}
      labelText={labelText || ""}
      helperText={helperText || ""}
      value={selectedNamespace?.id || defaultNamespace()?.id}
      onChange={(event) => {
        const namespace = findNamespace(event.target.value)
        if (namespace) {
          setSelectedNamespace(namespace)
          onChange(namespace)
        }
      }}
    >
      <SelectItem disabled hidden text="Choose a namespace" value="" />
      {namespaces.map((namespace: Namespace) => (
        <SelectItem
          key={namespace.id}
          id={namespace.id}
          text={namespace.path!}
          value={namespace.id}
        />
      ))}
    </Select>
  )
}