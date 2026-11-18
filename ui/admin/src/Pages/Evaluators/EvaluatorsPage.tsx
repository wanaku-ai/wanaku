import { InlineNotification } from '@carbon/react';
import { PageSkeleton } from '../../components/PageSkeleton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  evaluatorErrorKind,
  useEvaluators,
  type EvaluatorDef,
  type EvaluatorRevision,
} from '../../hooks/api/use-evaluators';
import { EvaluatorsTable } from './EvaluatorsTable';
import { EvaluatorModal, type SaveFailure } from './EvaluatorModal';
import { EvaluatorRevisions } from './EvaluatorRevisions';
import { useErrorNotification } from '../../hooks/error-notifications';
import { ErrorNotification } from '../../components/ErrorNotification';
import { DeleteConfirmationModal } from '../../components/DeleteConfirmationModal';

const messageOf = (error: unknown, fallback: string): string =>
  error instanceof Error ? error.message : fallback;

const EvaluatorsPage: React.FC = () => {
  const [active, setActive] = useState<EvaluatorRevision>();
  const [revisions, setRevisions] = useState<EvaluatorRevision[]>([]);
  const [connections, setConnections] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [isEvaluatorModalOpen, setIsEvaluatorModalOpen] = useState(false);
  const [openedEvaluator, setOpenedEvaluator] = useState<EvaluatorDef>();
  const [saveFailure, setSaveFailure] = useState<SaveFailure>();

  const [evaluatorDeletion, setEvaluatorDeletion] = useState<EvaluatorDef>();

  const evaluators = active?.evaluators ?? [];
  const activeRef = useRef(active);
  activeRef.current = active;

  const {
    getActiveRevision,
    listRevisions,
    getRevision,
    saveEvaluators,
    activateRevision,
    listLlmConnections,
  } = useEvaluators();

  const fetchRevisions = useCallback(async () => {
    try {
      const [activeState, metadata] = await Promise.all([getActiveRevision(), listRevisions()]);
      const details = await Promise.all(metadata.map(({ id }) => getRevision(id)));
      setActive(activeState.state === 'active' ? activeState.revision : undefined);
      setRevisions(details);
    } catch (error) {
      setErrorMessage(messageOf(error, 'Failed to load evaluators'));
      setActive(undefined);
      setRevisions([]);
    }
  }, [getActiveRevision, listRevisions, getRevision, setErrorMessage]);

  const fetchConnections = useCallback(async () => {
    try {
      const result = await listLlmConnections();
      setConnections(result.status === 200 && result.data ? result.data : []);
    } catch {
      setConnections([]);
    }
  }, [listLlmConnections]);

  useEffect(() => {
    Promise.all([fetchRevisions(), fetchConnections()]).finally(() => setIsLoading(false));
  }, [fetchRevisions, fetchConnections]);

  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5_000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  const closeEvaluatorModal = () => {
    setOpenedEvaluator(undefined);
    setSaveFailure(undefined);
    setIsEvaluatorModalOpen(false);
  };

  const handleEvaluatorSubmit = async (evaluator: EvaluatorDef) => {
    if (isMutating) return;
    setIsMutating(true);
    setSaveFailure(undefined);

    // Apply the edit to the latest evaluators, so that a reload after a conflict keeps the edit.
    const current = activeRef.current?.evaluators ?? [];
    const updated = current.some((ev) => ev.name === evaluator.name)
      ? current.map((ev) => (ev.name === evaluator.name ? evaluator : ev))
      : [...current, evaluator];

    try {
      await saveEvaluators(updated, activeRef.current?.revision.id);
      setSuccessMessage(openedEvaluator ? 'Evaluator updated' : 'Evaluator added');
      closeEvaluatorModal();
      await fetchRevisions();
    } catch (error) {
      const kind = evaluatorErrorKind(error);
      // The modal stays open so that the edits are not lost.
      setSaveFailure({
        kind: kind === 'conflict' || kind === 'validation' ? kind : 'other',
        message: messageOf(error, 'Failed to save evaluators'),
      });
      // A rejected configuration is recorded as a rejected revision.
      if (kind === 'validation') await fetchRevisions();
    } finally {
      setIsMutating(false);
    }
  };

  const handleEvaluatorDelete = async (evaluator: EvaluatorDef) => {
    if (isMutating) return;
    setIsMutating(true);

    const current = activeRef.current?.evaluators ?? [];
    const updated = current.filter((ev) => ev.name !== evaluator.name);
    try {
      await saveEvaluators(updated, activeRef.current?.revision.id);
      setSuccessMessage('Evaluator deleted');
    } catch (error) {
      setErrorMessage(messageOf(error, 'Failed to delete evaluator'));
    } finally {
      await fetchRevisions();
      setIsMutating(false);
      setEvaluatorDeletion(undefined);
    }
  };

  const handleRevisionActivate = async (revision: EvaluatorRevision) => {
    if (isMutating) return;
    setIsMutating(true);
    try {
      const saved = await activateRevision(revision.revision.id, activeRef.current?.revision.id);
      setSuccessMessage(
        `Revision ${revision.revision.id} is active again as revision ${saved.revision.id}`,
      );
    } catch (error) {
      setErrorMessage(messageOf(error, 'Failed to activate revision'));
    } finally {
      await fetchRevisions();
      setIsMutating(false);
    }
  };

  const reloadLatest = async () => {
    setSaveFailure(undefined);
    await fetchRevisions();
  };

  if (isLoading) return <PageSkeleton title="Evaluators" />;

  return (
    <div>
      {errorMessage && (
        <ErrorNotification errorMessage={errorMessage} onClose={() => setErrorMessage(null)} />
      )}
      {successMessage && (
        <InlineNotification
          kind="success"
          title="Success"
          subtitle={successMessage}
          onCloseButtonClick={() => setSuccessMessage(null)}
          lowContrast
          hideCloseButton={false}
        />
      )}

      <h1 className="title">Evaluators</h1>
      <p className="description">
        Configure evaluator pipelines that trigger on MCP methods and run WASM action scripts.
      </p>

      <div id="page-content">
        <EvaluatorsTable
          evaluators={evaluators}
          onAdd={() => setIsEvaluatorModalOpen(true)}
          onEdit={(evaluator) => {
            setOpenedEvaluator(evaluator);
            setIsEvaluatorModalOpen(true);
          }}
          onDelete={setEvaluatorDeletion}
          disabled={isMutating}
        />
      </div>

      <EvaluatorRevisions
        active={active}
        revisions={revisions}
        disabled={isMutating}
        onActivate={handleRevisionActivate}
      />

      {isEvaluatorModalOpen && (
        <EvaluatorModal
          evaluator={openedEvaluator}
          existingNames={evaluators.map((ev) => ev.name)}
          connections={connections}
          failure={saveFailure}
          onReloadLatest={reloadLatest}
          onRequestClose={closeEvaluatorModal}
          onSubmit={handleEvaluatorSubmit}
        />
      )}

      {evaluatorDeletion && (
        <DeleteConfirmationModal
          heading="Delete evaluator"
          text={`Are you sure you want to delete evaluator "${evaluatorDeletion.name}"?`}
          onConfirm={() => handleEvaluatorDelete(evaluatorDeletion)}
          onCancel={() => setEvaluatorDeletion(undefined)}
        />
      )}
    </div>
  );
};

export const Component = EvaluatorsPage;
