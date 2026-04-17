import {Namespace} from "../../models"

export function sortedNamespaces(namespaces: readonly Namespace[]): Namespace[] {
  const result = [...namespaces]
  result.sort((a, b) => {
    // First is default namespace
    if (a.path === "default") {
      return -1
    }
    if (b.path === "default") {
      return 1
    }
    // Second is public namespace
    if (a.path === "public") {
      return -1
    }
    if (b.path === "public") {
      return 1
    }
    // Rest are sorte
    return a.path!.localeCompare(b.path!)
  })
  return result
}