import type {ActionPolicyRevision} from "../../hooks/api/use-action-policies"


export function statusTagType(status: ActionPolicyRevision["revision"]["status"]): "green" | "gray" | "red" {
  if (status === "active") {
    return "green"
  }
  if (status === "rejected") {
    return "red"
  }
  return "gray"
}