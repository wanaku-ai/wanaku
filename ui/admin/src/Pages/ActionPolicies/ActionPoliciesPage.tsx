import { Accordion, AccordionItem, InlineNotification, Tag, Tile } from "@carbon/react";
import { useCallback, useEffect, useState } from "react";
import { PageSkeleton } from "../../components/PageSkeleton";
import { useActionPolicies, type ActionPolicyRevision } from "../../hooks/api/use-action-policies";
import { PolicyDetails } from "./PolicyDetails";
import "./ActionPoliciesPage.scss";

type PageState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; active?: ActionPolicyRevision; revisions: ActionPolicyRevision[] };

const formatDate = (value?: string | null): string => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "Not activated";

const statusTagType = (status: ActionPolicyRevision["revision"]["status"]): "green" | "gray" | "red" => {
  if (status === "active") return "green";
  if (status === "rejected") return "red";
  return "gray";
};

export const ActionPoliciesPage = () => {
  const { getActivePolicy, listRevisions, getRevision } = useActionPolicies();
  const [state, setState] = useState<PageState>({ status: "loading" });

  const load = useCallback(async () => {
    setState({ status: "loading" });
    try {
      const revisionsResponse = await listRevisions();
      let active: ActionPolicyRevision | undefined;
      try {
        active = (await getActivePolicy()).data;
      } catch (error) {
        const message = error instanceof Error ? error.message : "Failed to load the active action policy";
        if (!message.includes("no action policy revision found")) throw error;
      }
      const details = await Promise.all(revisionsResponse.data.map(({ id }) => getRevision(id)));
      setState({ status: "success", active, revisions: details.map(({ data }) => data) });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to load action policies";
      setState({ status: "error", message });
    }
  }, [getActivePolicy, getRevision, listRevisions]);

  useEffect(() => { void load(); }, [load]);

  if (state.status === "loading") return <PageSkeleton title="Action Policies" />;

  return (
    <div>
      <h1 className="title">Action Policies</h1>
      <p className="description">Review the active policy and its revision history. Policies are read-only in the admin UI.</p>

      {state.status === "error" ? (
        <div className="action-policies__notification">
          <InlineNotification kind="error" title="Action policies are unavailable" subtitle={state.message} lowContrast hideCloseButton />
        </div>
      ) : (
        <div id="page-content" className="action-policies">
          <section aria-labelledby="active-policy-heading">
            <h2 id="active-policy-heading">Active policy</h2>
            {state.active ? (
              <>
                <div className="action-policies__summary">
                  <Tile><span>Revision</span><strong>{state.active.revision.id}</strong></Tile>
                  <Tile><span>Status</span><Tag type={statusTagType(state.active.revision.status)}>{state.active.revision.status}</Tag></Tile>
                  <Tile><span>Rules</span><strong>{state.active.policy.rules?.length ?? 0}</strong></Tile>
                  <Tile><span>Activated</span><strong>{formatDate(state.active.revision.activated_at)}</strong></Tile>
                </div>
                <PolicyDetails policy={state.active.policy} />
              </>
            ) : (
              <Tile>
                <h3>No active policy</h3>
                <p>No policy revision is currently active.</p>
              </Tile>
            )}
          </section>

          <section aria-labelledby="revision-history-heading" className="action-policies__history">
            <h2 id="revision-history-heading">Revision history</h2>
            {state.revisions.length === 0 ? <Tile>No policy revisions are available.</Tile> : (
              <Accordion align="start">
                {state.revisions.map(({ revision, policy }) => (
                  <AccordionItem key={revision.id} title={`Revision ${revision.id} — ${revision.status}`}>
                    <div className="revision-details">
                      <div><span>Status</span><Tag type={statusTagType(revision.status)}>{revision.status}</Tag></div>
                      <div><span>Origin</span><strong>{revision.origin}</strong></div>
                      <div><span>Created</span><strong>{formatDate(revision.created_at)}</strong></div>
                      <div><span>Activated</span><strong>{formatDate(revision.activated_at)}</strong></div>
                      <div><span>Checksum</span><code>{revision.checksum}</code></div>
                      {revision.actor && <div><span>Actor</span><strong>{revision.actor}</strong></div>}
                      {revision.failure_reason && <div><span>Failure reason</span><strong>{revision.failure_reason}</strong></div>}
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
    </div>
  );
};
