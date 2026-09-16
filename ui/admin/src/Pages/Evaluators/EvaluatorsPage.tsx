import {InlineNotification, Modal} from "@carbon/react";
import { PageSkeleton } from "../../components/PageSkeleton";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { EvaluatorDef, useEvaluators } from "../../hooks/api/use-evaluators";
import { EvaluatorsTable } from "./EvaluatorsTable";
import { EvaluatorModal } from "./EvaluatorModal";
import { BindingsTable, BindingEntry } from "./BindingsTable";
import { BindingModal } from "./BindingModal";
import {useErrorNotification} from "../../hooks/error-notifications"
import {ErrorNotification} from "../../components/ErrorNotification"
import {NamespaceEntry} from "../../models"
import {DeleteConfirmationModal} from "../../components/DeleteConfirmationModal"

const EvaluatorsPage: React.FC = () => {
  const [evaluators, setEvaluators] = useState<EvaluatorDef[]>([]);
  const [connections, setConnections] = useState<string[]>([]);
  const [bindings, setBindings] = useState<BindingEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const { errorMessage, setErrorMessage } = useErrorNotification()
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [isEvaluatorModalOpen, setIsEvaluatorModalOpen] = useState(false);
  const [openedEvaluator, setOpenedEvaluator] = useState<EvaluatorDef>();
  const [isBindingModalOpen, setIsBindingModalOpen] = useState(false);
  
  const [evaluatorDeletion, setEvaluatorDeletion] = useState<EvaluatorDef>()
  const [bindingUpdate, setBindingUpdate] = useState<BindingEntry>()
  const [bindingDeletion, setBindingDeletion] = useState<BindingEntry>()

  const evaluatorsRef = useRef(evaluators);
  evaluatorsRef.current = evaluators;

  const { listEvaluators, updateEvaluators, listLlmConnections, listBindings, bindNamespace, unbindNamespace } =
    useEvaluators();

  const fetchEvaluators = useCallback(async () => {
    try {
      const result = await listEvaluators();
      if (result.status === 200 && result.data) {
        setEvaluators(result.data);
      } else {
        setEvaluators([]);
      }
    } catch {
      setErrorMessage("Failed to load evaluators");
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

  const fetchBindings = useCallback(async () => {
    try {
      const result = await listBindings();
      if (result.status === 200 && result.data) {
        const entries: BindingEntry[] = Object.entries(result.data).map(
          ([namespace, conversationId]) => ({ namespace, conversationId })
        );
        setBindings(entries);
      } else {
        setBindings([]);
      }
    } catch {
      setBindings([]);
    }
  }, [listBindings]);

  useEffect(() => {
    Promise.all([fetchEvaluators(), fetchConnections(), fetchBindings()]).finally(() => setIsLoading(false));
  }, [fetchEvaluators, fetchConnections, fetchBindings]);

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
        setSuccessMessage(openedEvaluator ? "Evaluator updated" : "Evaluator added");
        await fetchEvaluators();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to save evaluators");
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
        setSuccessMessage("Evaluator deleted");
        await fetchEvaluators();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to delete evaluator");
    } finally {
      setIsMutating(false);
      setEvaluatorDeletion(undefined)
    }
  };

  async function handleBindingSubmit(namespace: NamespaceEntry, conversationId: string) {
    const binding = { namespace: namespace.name, conversationId }
    if (bindings.find(item => item.namespace === namespace.name)) {
      setIsBindingModalOpen(false)
      requestBindingUpdateConfirmation(binding)
    } else {
      await submitBinding(binding)
    }
  }
  
  async function submitBinding(binding: BindingEntry) {
    if (isMutating) {
      return
    }
    setIsMutating(true)
    
    try {
      const { namespace, conversationId } = binding
      const result = await bindNamespace(namespace, conversationId)
      if (result.status === 200) {
        setSuccessMessage(`Namespace "${binding.namespace}" bound`)
        await fetchBindings()
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to bind namespace")
    } finally {
      setIsBindingModalOpen(false)
      setIsMutating(false)
    }
  }
  
  function requestEvaluatorDeleteConfirmation(evaluator: EvaluatorDef) {
    setEvaluatorDeletion(evaluator)
  }
  
  function requestBindingUpdateConfirmation(binding: BindingEntry) {
    setBindingUpdate(binding)
  }
  
  function requestBindingDeleteConfirmation(binding: BindingEntry) {
    setBindingDeletion(binding)
  }

  const handleBindingDelete = async (binding: BindingEntry) => {
    if (isMutating) return;
    setIsMutating(true);

    try {
      const result = await unbindNamespace(binding.namespace);
      if (result.status === 200) {
        setSuccessMessage(`Namespace "${binding.namespace}" unbound`);
        await fetchBindings();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to unbind namespace");
    } finally {
      setIsMutating(false);
      setBindingDeletion(undefined)
    }
  };

  if (isLoading) return <PageSkeleton title="Evaluators" />;

  return (
    <div>
      {errorMessage && (
        <ErrorNotification
          errorMessage={errorMessage}
          onClose={() => setErrorMessage(null)}
        />
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

        <h2 className="title" style={{ marginTop: "2rem" }}>
          Namespace Bindings
        </h2>
        <p className="description">
          Bind namespaces to conversation IDs for evaluator context tracking.
        </p>

        <BindingsTable
          bindings={bindings}
          onAdd={() => setIsBindingModalOpen(true)}
          onDelete={requestBindingDeleteConfirmation}
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

      {isBindingModalOpen && (
        <BindingModal
          onRequestClose={() => setIsBindingModalOpen(false)}
          onSubmit={handleBindingSubmit}
        />
      )}
      
      {bindingUpdate && (
        <Modal
          open={true}
          modalHeading="Update existing binding"
          primaryButtonText={"OK"}
          secondaryButtonText="Cancel"
          onRequestSubmit={() => {
            setBindingUpdate(undefined)
            submitBinding(bindingUpdate)
          }}
          onRequestClose={() => setBindingUpdate(undefined)}
        >
          <div>{`Namespace ${bindingUpdate.namespace} is already bound to a conversation id. Do you want to update this binding with the new data?`}</div>
        </Modal>
      )}
      
      {bindingDeletion && (
        <DeleteConfirmationModal
          heading="Delete binding"
          text={`Are you sure you want to delete binding of namespace "${bindingDeletion.namespace}" to conversation ID "${bindingDeletion.conversationId}"?`}
          onConfirm={() => handleBindingDelete(bindingDeletion)}
          onCancel={() => setBindingDeletion(undefined)}
        />
      )}
    </div>
  );
};

export const Component = EvaluatorsPage;
