import { Accordion, AccordionItem, Button, InlineNotification, Modal, Tile } from '@carbon/react';
import { useState } from 'react';
import { RevisionDetails } from '../../components/RevisionDetails';
import {
  actionPolicyErrorKind,
  activateRevision,
  type ActionPolicyRevision,
} from '../../hooks/api/use-action-policies';
import { PolicyComparison } from './PolicyComparison';
import { PolicyDetails } from './PolicyDetails';

interface PolicyRevisionHistoryProps {
  active?: ActionPolicyRevision;
  revisions: ActionPolicyRevision[];
  editing: boolean;
  onRefresh: () => Promise<void>;
  onActivate: (id: number) => void;
}

export const PolicyRevisionHistory = ({
  active,
  revisions,
  editing,
  onRefresh,
  onActivate,
}: PolicyRevisionHistoryProps) => {
  const [openRevisions, setOpenRevisions] = useState<Set<number>>(() => new Set());
  const [rollback, setRollback] = useState<{ id: number; expected?: number }>();
  const [rollbackError, setRollbackError] = useState<{ message: string; conflict: boolean }>();
  const [busy, setBusy] = useState(false);

  const toggleRevision = (id: number, isOpen: boolean) => {
    setOpenRevisions((previous) => {
      const next = new Set(previous);
      if (isOpen) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const closeRollback = () => {
    setRollback(undefined);
    setRollbackError(undefined);
  };

  const submitRollback = async () => {
    if (!rollback || busy) return;
    setBusy(true);
    const refreshing = rollbackError?.conflict === true;
    try {
      if (refreshing) {
        await onRefresh();
        closeRollback();
      } else {
        const saved = await activateRevision(rollback.id, rollback.expected);
        closeRollback();
        onActivate(saved.revision.id);
      }
    } catch (error) {
      setRollbackError({
        message: error instanceof Error ? error.message : 'The request failed.',
        conflict: refreshing || actionPolicyErrorKind(error) === 'conflict',
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <section aria-labelledby="revision-history-heading" className="action-policies__history">
        <h2 id="revision-history-heading">Revision history</h2>
        {revisions.length === 0 ? (
          <Tile>No policy revisions are available.</Tile>
        ) : (
          <Accordion align="start">
            {revisions.map(({ revision, policy }) => (
              <AccordionItem
                key={revision.id}
                onHeadingClick={({ isOpen }) => toggleRevision(revision.id, isOpen)}
                title={`Revision ${revision.id} — ${revision.status}`}
              >
                <RevisionDetails revision={revision} />
                <h4>Policy rules</h4>
                <PolicyDetails policy={policy} />
                {openRevisions.has(revision.id) && active && active.revision.id !== revision.id && (
                  <PolicyComparison active={active} policy={policy} />
                )}
                {revision.status === 'superseded' && active?.revision.id !== revision.id && (
                  <Button
                    kind="tertiary"
                    size="md"
                    className="action-policies__edit"
                    disabled={editing || busy}
                    onClick={() => setRollback({ id: revision.id, expected: active?.revision.id })}
                  >
                    Roll back to revision {revision.id}
                  </Button>
                )}
              </AccordionItem>
            ))}
          </Accordion>
        )}
      </section>
      {rollback && (
        <Modal
          open
          danger={!rollbackError?.conflict}
          modalHeading={`Roll back to revision ${rollback.id}?`}
          primaryButtonText={rollbackError?.conflict ? 'Reload active revision' : 'Roll back'}
          secondaryButtonText="Cancel"
          primaryButtonDisabled={busy}
          onRequestClose={() => {
            if (!busy) closeRollback();
          }}
          onRequestSubmit={() => void submitRollback()}
        >
          <p>
            Rollback creates a new revision from revision {rollback.id}.
            {rollback.expected !== undefined &&
              ` It replaces active revision ${rollback.expected}.`}
          </p>
          {rollbackError && (
            <InlineNotification
              kind="error"
              title={rollbackError.conflict ? 'The active revision changed' : 'Rollback failed'}
              subtitle={
                rollbackError.conflict
                  ? `${rollbackError.message} Reload to review the updated comparison before retrying.`
                  : rollbackError.message
              }
              lowContrast
              hideCloseButton
            />
          )}
        </Modal>
      )}
    </>
  );
};
