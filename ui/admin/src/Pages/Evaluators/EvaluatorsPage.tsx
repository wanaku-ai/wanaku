import { ToastNotification } from "@carbon/react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { EvaluatorDef, useEvaluators } from "../../hooks/api/use-evaluators";
import { EvaluatorsTable } from "./EvaluatorsTable";
import { EvaluatorModal } from "./EvaluatorModal";
import { BindingsTable, BindingEntry } from "./BindingsTable";
import { BindingModal } from "./BindingModal";

const EvaluatorsPage: React.FC = () => {
  const [evaluators, setEvaluators] = useState<EvaluatorDef[]>([]);
  const [bindings, setBindings] = useState<BindingEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [isEvaluatorModalOpen, setIsEvaluatorModalOpen] = useState(false);
  const [openedEvaluator, setOpenedEvaluator] = useState<EvaluatorDef>();
  const [isBindingModalOpen, setIsBindingModalOpen] = useState(false);

  const evaluatorsRef = useRef(evaluators);
  evaluatorsRef.current = evaluators;

  const { listEvaluators, updateEvaluators, listBindings, bindNamespace, unbindNamespace } =
    useEvaluators();

  const fetchEvaluators = useCallback(async () => {
    try {
      const result = await listEvaluators();
      if (result.status === 200 && result.data.data) {
        setEvaluators(result.data.data);
      } else {
        setEvaluators([]);
      }
    } catch {
      setErrorMessage("Failed to load evaluators");
      setEvaluators([]);
    }
  }, [listEvaluators]);

  const fetchBindings = useCallback(async () => {
    try {
      const result = await listBindings();
      if (result.status === 200 && result.data.data) {
        const entries: BindingEntry[] = Object.entries(result.data.data).map(
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
    Promise.all([fetchEvaluators(), fetchBindings()]).finally(() => setIsLoading(false));
  }, [fetchEvaluators, fetchBindings]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 10_000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

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
      } else {
        setErrorMessage(result.data.error || "Failed to save evaluators");
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
    if (!window.confirm(`Delete evaluator "${evaluator.name}"?`)) return;
    if (isMutating) return;
    setIsMutating(true);

    const current = evaluatorsRef.current;
    const updated = current.filter((ev) => ev.name !== evaluator.name);
    try {
      const result = await updateEvaluators(updated);
      if (result.status === 200) {
        setSuccessMessage("Evaluator deleted");
        await fetchEvaluators();
      } else {
        setErrorMessage(result.data.error || "Failed to delete evaluator");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to delete evaluator");
    } finally {
      setIsMutating(false);
    }
  };

  const handleBindingSubmit = async (namespace: string, conversationId: string) => {
    if (isMutating) return;
    setIsMutating(true);

    try {
      const result = await bindNamespace(namespace, conversationId);
      if (result.status === 200) {
        setSuccessMessage(`Namespace "${namespace}" bound`);
        await fetchBindings();
      } else {
        setErrorMessage(result.data.error || "Failed to bind namespace");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to bind namespace");
    } finally {
      setIsBindingModalOpen(false);
      setIsMutating(false);
    }
  };

  const handleBindingDelete = async (binding: BindingEntry) => {
    if (!window.confirm(`Unbind namespace "${binding.namespace}"?`)) return;
    if (isMutating) return;
    setIsMutating(true);

    try {
      const result = await unbindNamespace(binding.namespace);
      if (result.status === 200) {
        setSuccessMessage(`Namespace "${binding.namespace}" unbound`);
        await fetchBindings();
      } else {
        setErrorMessage(result.data.error || "Failed to unbind namespace");
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Failed to unbind namespace");
    } finally {
      setIsMutating(false);
    }
  };

  if (isLoading) return <div>Loading...</div>;

  return (
    <div>
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ float: "right" }}
        />
      )}
      {successMessage && (
        <ToastNotification
          kind="success"
          title="Success"
          subtitle={successMessage}
          onCloseButtonClick={() => setSuccessMessage(null)}
          timeout={5000}
          style={{ float: "right" }}
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
          onDelete={handleEvaluatorDelete}
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
          onDelete={handleBindingDelete}
          disabled={isMutating}
        />
      </div>

      {isEvaluatorModalOpen && (
        <EvaluatorModal
          evaluator={openedEvaluator}
          existingNames={evaluators.map((ev) => ev.name)}
          onRequestClose={() => {
            setOpenedEvaluator(undefined);
            setIsEvaluatorModalOpen(false);
          }}
          onSubmit={handleEvaluatorSubmit}
        />
      )}

      {isBindingModalOpen && (
        <BindingModal
          onRequestClose={() => setIsBindingModalOpen(false)}
          onSubmit={handleBindingSubmit}
        />
      )}
    </div>
  );
};

export const Component = EvaluatorsPage;
