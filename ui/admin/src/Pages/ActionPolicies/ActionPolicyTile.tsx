import {ActionPolicy, ActionPolicyRevision} from "../../hooks/api/use-action-policies"
import React from "react"
import {Button, Stack, Tag, Tile} from "@carbon/react"
import {PolicyDetails} from "./PolicyDetails"
import {statusTagType} from "./status"
import {Add} from "@carbon/icons-react"


interface ActionPolicyTileProps {
  policy?: ActionPolicyRevision
  onAdd: () => void
  onSave: (policy: ActionPolicy) => void
}


export const ActionPolicyTile: React.FC<ActionPolicyTileProps> = ({ policy, onAdd, onSave }) => {
  
  function formatDate(value?: string | null): string {
    return value
      ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
      : "Not activated"
  }
 
  return (
    policy ? (
      <>
        <div className="action-policies__summary">
          <Tile>
            <span>Revision</span><strong>{policy.revision.id}</strong>
          </Tile>
          <Tile>
            <span>Status</span>
            <Tag type={statusTagType(policy.revision.status)}>{policy.revision.status}</Tag>
          </Tile>
          <Tile>
            <span>Rules</span>
            <strong>{policy.policy.rules?.length ?? 0}</strong>
          </Tile>
          <Tile>
            <span>Activated</span>
            <strong>{formatDate(policy.revision.activated_at)}</strong>
          </Tile>
        </div>
        <PolicyDetails policy={policy.policy} />
        <Button
          onClick={() => onSave(policy.policy)}
        >
          Save
        </Button>
      </>
    ) : (
      <Stack>
        <h3>No active policy</h3>
        <p>No policy revision is currently active. Start by creating a new rule for your action policy</p>
        <Button renderIcon={Add} onClick={onAdd}>
          Create Policy
        </Button>
      </Stack>
    )
  )
}