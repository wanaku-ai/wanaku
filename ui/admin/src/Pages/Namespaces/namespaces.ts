import {NamespaceEntry} from "../../models"

export function sortedNamespaces(namespaces: readonly NamespaceEntry[]): NamespaceEntry[] {
  const result = [...namespaces]
  result.sort((a, b) => {
    if (a.name === "default") return -1
    if (b.name === "default") return 1
    if (a.name === "public") return -1
    if (b.name === "public") return 1
    return (a.name ?? "").localeCompare(b.name ?? "")
  })
  return result
}
