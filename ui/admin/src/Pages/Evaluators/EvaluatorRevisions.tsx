import {
  Accordion,
  AccordionItem,
  Button,
  Modal,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  Tag,
  Tile,
} from '@carbon/react';
import { useState } from 'react';
import { RevisionDetails } from '../../components/RevisionDetails';
import type { EvaluatorRevision } from '../../hooks/api/use-evaluators';
import { diffEvaluators } from '../../utils/evaluator-diff';
import './EvaluatorRevisions.scss';

interface EvaluatorRevisionsProps {
  /** The active revision, or `undefined` when no evaluator revision is active. */
  active?: EvaluatorRevision;
  /** Every kept revision, including superseded and rejected revisions. */
  revisions: EvaluatorRevision[];
  disabled?: boolean;
  /** Activates the configuration of a historical revision as a new revision. */
  onActivate: (revision: EvaluatorRevision) => void;
}

const display = (value: unknown): string =>
  value === undefined ? '—' : typeof value === 'string' ? value : JSON.stringify(value);

const kindTagType = (kind: 'added' | 'removed' | 'changed'): 'green' | 'red' | 'blue' => {
  if (kind === 'added') return 'green';
  if (kind === 'removed') return 'red';
  return 'blue';
};

/** Shows how a revision differs from the active revision. */
const RevisionComparison = ({
  active,
  revision,
}: {
  active: EvaluatorRevision;
  revision: EvaluatorRevision;
}) => {
  const changes = diffEvaluators(active.evaluators, revision.evaluators);
  return (
    <div className="evaluator-comparison" data-testid={`comparison-${revision.revision.id}`}>
      <h4>
        Comparison with active revision {active.revision.id} (
        {active.revision.checksum.slice(0, 12)})
      </h4>
      {changes.length === 0 ? (
        <p className="evaluator-comparison__empty">
          This revision has the same evaluators as the active revision.
        </p>
      ) : (
        changes.map((change) => (
          <div key={change.name} className="evaluator-comparison__change">
            <p>
              <Tag type={kindTagType(change.kind)}>{change.kind}</Tag>{' '}
              <strong>{change.name}</strong>
              {change.kind === 'added' && ' is only in this revision.'}
              {change.kind === 'removed' && ' is only in the active revision.'}
            </p>
            {change.fields.length > 0 && (
              <StructuredListWrapper aria-label={`Changes to ${change.name}`}>
                <StructuredListHead>
                  <StructuredListRow head>
                    <StructuredListCell head>Field</StructuredListCell>
                    <StructuredListCell head>Active</StructuredListCell>
                    <StructuredListCell head>This revision</StructuredListCell>
                  </StructuredListRow>
                </StructuredListHead>
                <StructuredListBody>
                  {change.fields.map((field) => (
                    <StructuredListRow key={field.path}>
                      <StructuredListCell>{field.path}</StructuredListCell>
                      <StructuredListCell>{display(field.before)}</StructuredListCell>
                      <StructuredListCell>{display(field.after)}</StructuredListCell>
                    </StructuredListRow>
                  ))}
                </StructuredListBody>
              </StructuredListWrapper>
            )}
          </div>
        ))
      )}
    </div>
  );
};

export const EvaluatorRevisions = ({
  active,
  revisions,
  disabled,
  onActivate,
}: EvaluatorRevisionsProps) => {
  const [pending, setPending] = useState<EvaluatorRevision>();
  const sorted = [...revisions].sort((a, b) => b.revision.id - a.revision.id);

  return (
    <div className="evaluator-revisions">
      <section aria-labelledby="active-revision-heading">
        <h2 id="active-revision-heading">Active revision</h2>
        {active ? (
          <>
            <p>
              Revision <strong>{active.revision.id}</strong> holds {active.evaluators.length}{' '}
              evaluator(s).
            </p>
            <RevisionDetails revision={active.revision} />
          </>
        ) : (
          <Tile>No evaluator revision is currently active.</Tile>
        )}
      </section>

      <section aria-labelledby="revision-history-heading">
        <h2 id="revision-history-heading">Revision history</h2>
        {sorted.length === 0 ? (
          <Tile>No evaluator revisions are available.</Tile>
        ) : (
          <Accordion align="start">
            {sorted.map((entry) => {
              const { revision } = entry;
              const isActive = revision.id === active?.revision.id;
              return (
                <AccordionItem
                  key={revision.id}
                  title={`Revision ${revision.id} — ${revision.status}`}
                >
                  <RevisionDetails revision={revision} />
                  <p>{entry.evaluators.length} evaluator(s)</p>
                  {active && !isActive && <RevisionComparison active={active} revision={entry} />}
                  {revision.status === 'superseded' && (
                    <Button
                      kind="tertiary"
                      size="md"
                      disabled={disabled}
                      className="evaluator-revisions__activate"
                      onClick={() => setPending(entry)}
                    >
                      Activate as new revision
                    </Button>
                  )}
                </AccordionItem>
              );
            })}
          </Accordion>
        )}
      </section>

      {pending && (
        <Modal
          open
          danger
          modalHeading="Activate this revision?"
          primaryButtonText="Activate"
          secondaryButtonText="Cancel"
          onRequestSubmit={() => {
            onActivate(pending);
            setPending(undefined);
          }}
          onRequestClose={() => setPending(undefined)}
        >
          <p>
            The configuration of revision {pending.revision.id} becomes a new revision.{' '}
            {active
              ? `It replaces revision ${active.revision.id}. `
              : 'No revision is active now. '}
            Wanaku applies it to new requests immediately. The revision history stays unchanged.
          </p>
        </Modal>
      )}
    </div>
  );
};
