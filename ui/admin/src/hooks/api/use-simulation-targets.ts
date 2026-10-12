import { useEffect, useState } from 'react';
import { useTools } from './use-tools';
import { useResources } from './use-resources';
import { usePrompts } from './use-prompts';
import type { ToolEntry, ResourceEntry, PromptEntry } from '../../models';

type TargetEntry = ToolEntry | ResourceEntry | PromptEntry;
interface TargetState {
  namespace: string;
  method: string;
  entries?: TargetEntry[];
  error: string;
}

export const useSimulationTargets = (namespace: string, method: string) => {
  const { listTools } = useTools();
  const { listResources } = useResources();
  const { listPrompts } = usePrompts();
  const [state, setState] = useState<TargetState>({ namespace: '', method: '', error: '' });

  useEffect(() => {
    if (!namespace) return;
    const controller = new AbortController();
    void (async () => {
      try {
        const response = await (
          method === 'tools/call'
            ? listTools
            : method === 'resources/read'
              ? listResources
              : listPrompts
        )({ signal: controller.signal });
        if (response.status !== 200) throw new Error('Registered actions are unavailable.');
        setState({
          namespace,
          method,
          entries: response.data.filter((entry) => (entry.namespace ?? 'default') === namespace),
          error: '',
        });
      } catch (cause) {
        if (!controller.signal.aborted)
          setState({
            namespace,
            method,
            entries: [],
            error: cause instanceof Error ? cause.message : 'Registered actions are unavailable.',
          });
      }
    })();
    return () => controller.abort();
  }, [namespace, method, listTools, listResources, listPrompts]);

  const current = state.namespace === namespace && state.method === method;
  const targets = current
    ? (state.entries ?? [])
        .map((entry) => ({
          value: 'location' in entry ? entry.location : entry.name,
          label: `${entry.name}${'location' in entry ? ` — ${entry.location}` : ''}${entry.enabled === false ? ' (disabled)' : ''}`,
        }))
        .sort((a, b) => a.label.localeCompare(b.label))
    : [];
  return {
    targets,
    loading: Boolean(namespace) && (!current || state.entries === undefined),
    error: current ? state.error : '',
  };
};
