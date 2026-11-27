import { Button, InlineNotification, Tag, Tile } from '@carbon/react';
import { Edit } from '@carbon/icons-react';
import { useCallback, useEffect, useState } from 'react';
import { PageSkeleton } from '../../components/PageSkeleton';
import { formatDate, statusTagType } from '../../utils/revision-format';
import {
  useActionPolicies,
  type ActionPolicy,
  type ActionPolicyRevision,
} from '../../hooks/api/use-action-policies';
import { PolicyRevisionHistory } from './PolicyRevisionHistory';
import { PolicyDetails } from './PolicyDetails';
import { PolicyEditor } from './PolicyEditor';
import './ActionPoliciesPage.scss';

type PageState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; active?: ActionPolicyRevision; revisions: ActionPolicyRevision[] };

export const ActionPoliciesPage = () => {
  const { getActivePolicy, listRevisions, getRevision, updatePolicy } = useActionPolicies();
  const [state, setState] = useState<PageState>({ status: 'loading' });
  const [editing, setEditing] = useState(false);
  const [activated, setActivated] = useState<number>();

  const fetchPolicies = useCallback(async () => {
    const [revisionsResponse, activePolicy] = await Promise.all([
      listRevisions(),
      getActivePolicy(),
    ]);
    const revisions = await Promise.all(revisionsResponse.data.map(({ id }) => getRevision(id)));
    const active = activePolicy.state === 'active' ? activePolicy.revision : undefined;
    setState({ status: 'success', active, revisions });
  }, [getActivePolicy, getRevision, listRevisions]);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      await fetchPolicies();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load action policies';
      setState({ status: 'error', message });
    }
  }, [fetchPolicies]);

  useEffect(() => {
    void load();
  }, [load]);

  const activate = async (policy: ActionPolicy) => {
    const saved = await updatePolicy(
      policy,
      state.status === 'success' ? state.active?.revision.id : undefined,
    );
    setEditing(false);
    setActivated(saved.revision.id);
    await load();
  };

  const reload = () => {
    setActivated(undefined);
    void load();
  };

  if (state.status === 'loading') return <PageSkeleton title="Action Policies" />;

  return (
    <div>
      <h1 className="title">Action Policies</h1>
      <p className="description">
        Review the active policy and its revision history. Edit the policy to activate a new
        revision, or open a revision in the history to compare or roll back.
      </p>

      {state.status === 'error' ? (
        <div className="action-policies__notification">
          <InlineNotification
            kind="error"
            title="Action policies are unavailable"
            subtitle={state.message}
            lowContrast
            hideCloseButton
          />
        </div>
      ) : (
        <div id="page-content" className="action-policies">
          <section aria-labelledby="active-policy-heading">
            <h2 id="active-policy-heading">Active policy</h2>
            {activated !== undefined && (
              <InlineNotification
                kind="success"
                title="Policy activated"
                subtitle={`Revision ${activated} is now active.`}
                lowContrast
                onCloseButtonClick={() => setActivated(undefined)}
              />
            )}
            {editing ? (
              <PolicyEditor
                key={state.active?.revision.id ?? 'none'}
                base={state.active}
                onActivate={activate}
                onReload={reload}
                onCancel={() => setEditing(false)}
              />
            ) : state.active ? (
              <>
                <div className="action-policies__summary">
                  <Tile>
                    <span>Revision</span>
                    <strong>{state.active.revision.id}</strong>
                  </Tile>
                  <Tile>
                    <span>Status</span>
                    <Tag type={statusTagType(state.active.revision.status)}>
                      {state.active.revision.status}
                    </Tag>
                  </Tile>
                  <Tile>
                    <span>Rules</span>
                    <strong>{state.active.policy.rules?.length ?? 0}</strong>
                  </Tile>
                  <Tile>
                    <span>Activated</span>
                    <strong>{formatDate(state.active.revision.activated_at)}</strong>
                  </Tile>
                </div>
                <PolicyDetails policy={state.active.policy} />
                <Button
                  renderIcon={Edit}
                  className="action-policies__edit"
                  onClick={() => setEditing(true)}
                >
                  Edit policy
                </Button>
              </>
            ) : (
              <Tile>
                <h3>No active policy</h3>
                <p>No policy revision is currently active.</p>
                <Button className="action-policies__edit" onClick={() => setEditing(true)}>
                  Create policy
                </Button>
              </Tile>
            )}
          </section>

          <PolicyRevisionHistory
            active={state.active}
            revisions={state.revisions}
            editing={editing}
            onRefresh={async () => {
              await fetchPolicies();
              setActivated(undefined);
            }}
            onActivate={(id) => {
              setActivated(id);
              void load();
            }}
          />
        </div>
      )}
    </div>
  );
};
