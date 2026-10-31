import { InlineNotification } from '@carbon/react';
import { PageSkeleton } from '../../components/PageSkeleton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { EvaluatorDef, useEvaluators } from '../../hooks/api/use-evaluators';
import { EvaluatorsTable } from './EvaluatorsTable';
import { EvaluatorModal } from './EvaluatorModal';
import { useErrorNotification } from '../../hooks/error-notifications';
import { ErrorNotification } from '../../components/ErrorNotification';
import { DeleteConfirmationModal } from '../../components/DeleteConfirmationModal';

const EvaluatorsPage: React.FC = () => {
  const [evaluators, setEvaluators] = useState<EvaluatorDef[]>([]);
  const [connections, setConnections] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [isEvaluatorModalOpen, setIsEvaluatorModalOpen] = useState(false);
  const [openedEvaluator, setOpenedEvaluator] = useState<EvaluatorDef>();

  const [evaluatorDeletion, setEvaluatorDeletion] = useState<EvaluatorDef>();

  const evaluatorsRef = useRef(evaluators);
  evaluatorsRef.current = evaluators;

  const { listEvaluators, updateEvaluators, listLlmConnections } = useEvaluators();

  const fetchEvaluators = useCallback(async () => {
    try {
      const result = await listEvaluators();
      if (result.status === 200 && result.data) {
        setEvaluators(result.data);
      } else {
        setEvaluators([]);
      }
    } catch {
      setErrorMessage('Failed to load evaluators');
      setEvaluators([]);
    }
  }, [listEvaluators]);

  const fetchConnections = useCallback(async () => {
    try {
      const result = await listLlmConnections();
      if (result.status === 200 && result.data) {
        setConnections(result.data);
      } else {
        setConnections([]);
      }
    } catch {
      setConnections([]);
    }
  }, [listLlmConnections]);

  useEffect(() => {
    Promise.all([fetchEvaluators(), fetchConnections()]).finally(() => setIsLoading(false));
  }, [fetchEvaluators, fetchConnections]);

  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5_000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  const handleEvaluatorSubmit = async (evaluator: EvaluatorDef) => {
    if (isMutating) return;
    setIsMutating(true);

    const current = evaluatorsRef.current;
    let updated: EvaluatorDef[];
    if (openedEvaluator) {
      updated = current.map((ev) => (ev.name === openedEvaluator.name ? evaluator : ev));
    } else {
      updated = [...current, evaluator];
    }

    try {
      const result = await updateEvaluators(updated);
      if (result.status === 200) {
        setSuccessMessage(openedEvaluator ? 'Evaluator updated' : 'Evaluator added');
        await fetchEvaluators();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to save evaluators');
    } finally {
      setOpenedEvaluator(undefined);
      setIsEvaluatorModalOpen(false);
      setIsMutating(false);
    }
  };

  const handleEvaluatorDelete = async (evaluator: EvaluatorDef) => {
    if (isMutating) return;
    setIsMutating(true);

    const current = evaluatorsRef.current;
    const updated = current.filter((ev) => ev.name !== evaluator.name);
    try {
      const result = await updateEvaluators(updated);
      if (result.status === 200) {
        setSuccessMessage('Evaluator deleted');
        await fetchEvaluators();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to delete evaluator');
    } finally {
      setIsMutating(false);
      setEvaluatorDeletion(undefined);
    }
  };

  function requestEvaluatorDeleteConfirmation(evaluator: EvaluatorDef) {
    setEvaluatorDeletion(evaluator);
  }

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
          onDelete={requestEvaluatorDeleteConfirmation}
          disabled={isMutating}
        />
      </div>

      {isEvaluatorModalOpen && (
        <EvaluatorModal
          evaluator={openedEvaluator}
          existingNames={evaluators.map((ev) => ev.name)}
          connections={connections}
          onRequestClose={() => {
            setOpenedEvaluator(undefined);
            setIsEvaluatorModalOpen(false);
          }}
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
