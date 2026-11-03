import { Accordion, AccordionItem, InlineNotification, Tag, Tile } from '@carbon/react';
import { useCallback, useEffect, useState } from 'react';
import { PageSkeleton } from '../../components/PageSkeleton';
import {
  useActionPolicies,
  type ActionPolicyRevision,
  ActionPolicy,
  RevisionMetadata
} from "../../hooks/api/use-action-policies"
import { PolicyDetails } from './PolicyDetails';
import './ActionPoliciesPage.scss';
import {ActionPolicyTile} from "./ActionPolicyTile"
import {AddRuleModal} from "./AddRuleModal"
import {Rule} from "../../models"

type PageState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; active?: ActionPolicyRevision; revisions: ActionPolicyRevision[] };

const formatDate = (value?: string | null): string =>
  value
    ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(value),
      )
    : 'Not activated';

const statusTagType = (
  status: ActionPolicyRevision['revision']['status'],
): 'green' | 'gray' | 'red' => {
  if (status === 'active') return 'green';
  if (status === 'rejected') return 'red';
  return 'gray';
};


export const ActionPoliciesPage = () => {
  
  const { getActivePolicy, listRevisions, getRevision } = useActionPolicies();
  const [state, setState] = useState<PageState>({ status: "loading" });
  const [isModalOpen, setModalOpen] = useState(false)
  const [activePolicy, setActivePolicy] = useState<ActionPolicyRevision>()
  const [actionPolicyRules, setActionPolicyRules] = useState<Rule[]>([])
  
  
  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const [revisionsResponse, activePolicy] = await Promise.all([
        listRevisions(),
        getActivePolicy(),
      ]);
      const revisions = await Promise.all(revisionsResponse.data.map(({ id }) => getRevision(id)));
      const active = activePolicy.state === 'active' ? activePolicy.revision : undefined;
      setState({ status: 'success', active, revisions });
      setActivePolicy(active)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to load action policies';
      setState({ status: 'error', message });
    }
  }, [getActivePolicy, getRevision, listRevisions]);
  
  useEffect(() => {
    void load();
  }, [load]);

  
  function addActionPolicyRule(rule: Rule) {
    setActionPolicyRules([ ...actionPolicyRules, rule ])
    const revision = activePolicy?.revision || newRevision()
    const rules = activePolicy?.policy?.rules || []
    setActivePolicy({ policy: { ...activePolicy?.policy, rules: [ ...rules, rule ] }, revision })
  }
  
  function newRevision(): RevisionMetadata {
    return {
      id: 0,
      origin: "api",
      status: "active",
      created_at: "Not yet saved",
      checksum: "abcdefgh"
    }
  }
  
  /*function createdAtString(): string {
    const now = new Date();
    return now.toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true
    })
  }*/
  
  function confirmPolicySave(policy: ActionPolicy) {
    // TODO: display confirmation modal
    console.log(policy)
  }

  if (state.status === 'loading') return <PageSkeleton title="Action Policies" />;

  return (
    <div>
      <h1 className="title">Action Policies</h1>
      <p className="description">
        Review the active policy and its revision history. Policies are read-only in the admin UI.
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
            <ActionPolicyTile
              policy={state.active}
              onAdd={() => setModalOpen(true)}
              onSave={(policy: ActionPolicy) => confirmPolicySave(policy)}
            />
          </section>

          <section aria-labelledby="revision-history-heading" className="action-policies__history">
            <h2 id="revision-history-heading">Revision history</h2>
            {state.revisions.length === 0 ? (
              <Tile>No policy revisions are available.</Tile>
            ) : (
              <Accordion align="start">
                {state.revisions.map(({ revision, policy }) => (
                  <AccordionItem
                    key={revision.id}
                    title={`Revision ${revision.id} — ${revision.status}`}
                  >
                    <div className="revision-details">
                      <div>
                        <span>Status</span>
                        <Tag type={statusTagType(revision.status)}>{revision.status}</Tag>
                      </div>
                      <div>
                        <span>Origin</span>
                        <strong>{revision.origin}</strong>
                      </div>
                      <div>
                        <span>Created</span>
                        <strong>{formatDate(revision.created_at)}</strong>
                      </div>
                      <div>
                        <span>Activated</span>
                        <strong>{formatDate(revision.activated_at)}</strong>
                      </div>
                      <div>
                        <span>Checksum</span>
                        <code>{revision.checksum}</code>
                      </div>
                      {revision.actor && (
                        <div>
                          <span>Actor</span>
                          <strong>{revision.actor}</strong>
                        </div>
                      )}
                      {revision.failure_reason && (
                        <div>
                          <span>Failure reason</span>
                          <strong>{revision.failure_reason}</strong>
                        </div>
                      )}
                    </div>
                    <h4>Policy rules</h4>
                    <PolicyDetails policy={policy} />
                  </AccordionItem>
                ))}
              </Accordion>
            )}
          </section>
        </div>
      )}
      
      {isModalOpen && (
        <AddRuleModal
          onSubmit={(rule: Rule) => {
            addActionPolicyRule(rule)
            setModalOpen(false)}
          }
          onClose={() => {setModalOpen(false)}}
        />
      )}
    </div>
  );
};
