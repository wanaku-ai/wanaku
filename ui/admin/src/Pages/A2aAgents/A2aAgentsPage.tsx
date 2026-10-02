import { useCallback, useEffect, useState } from 'react';
import { Button, DataTableSkeleton, InlineNotification, Modal, Stack } from '@carbon/react';
import ErrorBoundary from '../../components/ErrorBoundary';
import { fetchAgents, removeAgent, saveAgent } from '../../hooks/api/use-a2a-agents';
import type { AgentEntry, AgentView } from '../../models';
import { AgentModal } from './AgentModal';
import { AgentsTable } from './AgentsTable';

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; agents: AgentView[] };
type Editor = { mode: 'add' } | { mode: 'edit'; agent: AgentView };

function A2aAgentsPage() {
  const [state, setState] = useState<LoadState>({ status: 'loading' });
  const [editor, setEditor] = useState<Editor>();
  const [details, setDetails] = useState<AgentView>();
  const [deleting, setDeleting] = useState<AgentView>();
  const [busy, setBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string>();
  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'success', agents: await fetchAgents() });
    } catch (cause) {
      setState({
        status: 'error',
        message: cause instanceof Error ? cause.message : 'Could not load A2A agents.',
      });
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  async function save(entry: AgentEntry) {
    await saveAgent(entry, editor?.mode === 'edit' ? editor.agent : undefined);
    await load();
  }

  async function confirmDelete() {
    if (!deleting) return;
    setBusy(true);
    setDeleteError(undefined);
    try {
      await removeAgent(deleting);
      setDeleting(undefined);
      await load();
    } catch (cause) {
      setDeleteError(cause instanceof Error ? cause.message : 'Could not delete the agent.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <ErrorBoundary>
      <h1 className="title">A2A agents</h1>
      <p className="description">Register upstream agents and view their Wanaku proxy endpoints.</p>
      <div id="page-content">
        {state.status === 'loading' && <DataTableSkeleton columnCount={5} rowCount={3} />}
        {state.status === 'error' && (
          <Stack gap={5}>
            <InlineNotification
              kind="error"
              title="Could not load A2A agents"
              subtitle={state.message}
              hideCloseButton
            />
            <Button onClick={() => void load()}>Retry</Button>
          </Stack>
        )}
        {state.status === 'success' && (
          <AgentsTable
            agents={state.agents}
            onAdd={() => setEditor({ mode: 'add' })}
            onDetails={setDetails}
            onEdit={(agent) => setEditor({ mode: 'edit', agent })}
            onDelete={(agent) => {
              setDeleteError(undefined);
              setDeleting(agent);
            }}
          />
        )}
      </div>
      {editor && (
        <AgentModal
          agent={editor.mode === 'edit' ? editor.agent : undefined}
          onClose={() => setEditor(undefined)}
          onSave={save}
        />
      )}
      {details && (
        <Modal
          open
          passiveModal
          modalHeading={details.name}
          onRequestClose={() => setDetails(undefined)}
        >
          <Stack gap={6}>
            <div>
              <strong>Namespace</strong>
              <p>{details.namespace}</p>
            </div>
            <div>
              <strong>Description</strong>
              <p>{details.description || 'No description provided.'}</p>
            </div>
            <div>
              <strong>Upstream A2A endpoint</strong>
              <p>{details.address}</p>
            </div>
            <div>
              <strong>Wanaku proxy endpoint</strong>
              <p>{details.proxyUrl}</p>
            </div>
            <div>
              <strong>Agent card URL</strong>
              <p>{details.cardAddress || 'Standard upstream discovery path'}</p>
            </div>
          </Stack>
        </Modal>
      )}
      {deleting && (
        <Modal
          open
          danger
          modalHeading="Delete A2A agent"
          primaryButtonText={busy ? 'Deleting…' : 'Delete'}
          secondaryButtonText="Cancel"
          primaryButtonDisabled={busy}
          onRequestSubmit={() => void confirmDelete()}
          onRequestClose={() => {
            if (!busy) setDeleting(undefined);
          }}
        >
          <Stack gap={6}>
            <p>
              Delete {deleting.name} from namespace {deleting.namespace}? Wanaku will stop routing
              requests to this agent.
            </p>
            {deleteError && (
              <InlineNotification
                kind="error"
                title="Agent was not deleted"
                subtitle={deleteError}
                hideCloseButton
              />
            )}
          </Stack>
        </Modal>
      )}
    </ErrorBoundary>
  );
}

export const Component = A2aAgentsPage;
