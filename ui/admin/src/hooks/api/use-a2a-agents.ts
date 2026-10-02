import { createAgent, deleteAgent, listAgents, updateAgent } from '../../api/wanaku-router-api';
import type { AgentEntry, AgentView } from '../../models';

export async function fetchAgents(): Promise<AgentView[]> {
  const response = await listAgents();
  // customFetch unwraps the management API envelope before returning the payload.
  return response.data as unknown as AgentView[];
}

export async function saveAgent(entry: AgentEntry, existing?: AgentView): Promise<void> {
  if (existing) {
    await updateAgent(existing.namespace, existing.name, entry);
  } else {
    await createAgent(entry);
  }
}

export async function removeAgent(agent: AgentView): Promise<void> {
  await deleteAgent(agent.namespace, agent.name);
}
