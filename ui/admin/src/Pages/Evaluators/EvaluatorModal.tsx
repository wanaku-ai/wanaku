import {
  Modal,
  Select,
  SelectItem,
  Stack,
  TextArea,
  TextInput,
} from "@carbon/react";
import React, { useState } from "react";
import { EvaluatorDef } from "../../hooks/api/use-evaluators";
import type {
  EvaluationEngine,
  LlmDef,
  LlmOperation,
  SystemOneDef,
  SystemOneState,
} from "../../models";

type EngineType = "llm" | "passthrough" | "typesafe-system-one";

const getLlmConfiguration = (evaluator?: EvaluatorDef): LlmDef | undefined =>
  evaluator?.engine?.type === "llm" ? evaluator.engine : undefined;

const getSystemOneConfiguration = (evaluator?: EvaluatorDef): SystemOneDef | undefined =>
  evaluator?.engine?.type === "typesafe-system-one" ? evaluator.engine : undefined;

interface EvaluatorModalProps {
  evaluator?: EvaluatorDef;
  existingNames: string[];
  connections: string[];
  onRequestClose: () => void;
  onSubmit: (evaluator: EvaluatorDef) => void;
}

interface SystemOneFieldsProps {
  connection: string;
  state: SystemOneState;
  noulId: string;
  instructions: string;
  trueCriteria: string;
  falseCriteria: string;
  onConnectionChange: (value: string) => void;
  onStateChange: (value: SystemOneState) => void;
  onNoulIdChange: (value: string) => void;
  onInstructionsChange: (value: string) => void;
  onTrueCriteriaChange: (value: string) => void;
  onFalseCriteriaChange: (value: string) => void;
}

const SystemOneFields: React.FC<SystemOneFieldsProps> = ({
  connection,
  state,
  noulId,
  instructions,
  trueCriteria,
  falseCriteria,
  onConnectionChange,
  onStateChange,
  onNoulIdChange,
  onInstructionsChange,
  onTrueCriteriaChange,
  onFalseCriteriaChange,
}) => (
  <>
    <TextInput
      id="system-one-connection"
      labelText="TypeSafe Connection"
      placeholder="Name from typesafe_system_one_connections"
      value={connection}
      onChange={(e: React.ChangeEvent<HTMLInputElement>) => onConnectionChange(e.target.value)}
      helperText="Connections and credentials are configured in wanaku.yaml"
      required
    />
    <Select
      id="system-one-state"
      labelText="State Mapping"
      value={state}
      onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
        onStateChange(e.target.value as SystemOneState)
      }
    >
      <SelectItem value="context" text="Full MCP context" />
      <SelectItem value="arguments" text="Tool arguments only" />
    </Select>
    <TextInput
      id="system-one-noul-id"
      labelText="Noul Primitive ID"
      value={noulId}
      onChange={(e: React.ChangeEvent<HTMLInputElement>) => onNoulIdChange(e.target.value)}
      required
    />
    <TextArea
      id="system-one-noul-instructions"
      labelText="Noul Instructions"
      placeholder="Is this request safe to run?"
      value={instructions}
      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => onInstructionsChange(e.target.value)}
      rows={3}
      required
    />
    <TextInput
      id="system-one-noul-true"
      labelText="True Criteria (optional)"
      value={trueCriteria}
      onChange={(e: React.ChangeEvent<HTMLInputElement>) => onTrueCriteriaChange(e.target.value)}
      invalid={Boolean(trueCriteria.trim()) !== Boolean(falseCriteria.trim())}
      invalidText="Set both true and false criteria, or leave both empty"
    />
    <TextInput
      id="system-one-noul-false"
      labelText="False Criteria (optional)"
      value={falseCriteria}
      onChange={(e: React.ChangeEvent<HTMLInputElement>) => onFalseCriteriaChange(e.target.value)}
      invalid={Boolean(trueCriteria.trim()) !== Boolean(falseCriteria.trim())}
      invalidText="Set both true and false criteria, or leave both empty"
    />
  </>
);

export const EvaluatorModal: React.FC<EvaluatorModalProps> = ({
  evaluator,
  existingNames,
  connections,
  onRequestClose,
  onSubmit,
}) => {
  const llmConfiguration = getLlmConfiguration(evaluator);
  const systemOneConfiguration = getSystemOneConfiguration(evaluator);
  const [name, setName] = useState(evaluator?.name || "");
  const [triggerMethod, setTriggerMethod] = useState(evaluator?.trigger.method || "tools/call");
  const [triggerNamespace, setTriggerNamespace] = useState(evaluator?.trigger.namespace || "");
  const [engineType, setEngineType] = useState<EngineType>(
    evaluator?.engine?.type === "passthrough"
      ? "passthrough"
      : evaluator?.engine?.type === "typesafe-system-one"
        ? "typesafe-system-one"
        : "llm",
  );
  const [llmOperation, setLlmOperation] = useState<LlmOperation>(
    llmConfiguration?.operation || "classify",
  );
  const [llmPrompt, setLlmPrompt] = useState(llmConfiguration?.prompt || "");
  const [llmConnection, setLlmConnection] = useState(llmConfiguration?.connection || "");
  const [systemOneConnection, setSystemOneConnection] = useState(systemOneConfiguration?.connection || "");
  const [systemOneState, setSystemOneState] = useState(systemOneConfiguration?.state || "context");
  const [noulId, setNoulId] = useState(systemOneConfiguration?.noul.id || "is_safe");
  const [noulInstructions, setNoulInstructions] = useState(
    typeof systemOneConfiguration?.noul.instructions === "string"
      ? systemOneConfiguration.noul.instructions
      : "",
  );
  const [noulTrueCriteria, setNoulTrueCriteria] = useState(
    typeof systemOneConfiguration?.noul.criteria?.true === "string"
      ? systemOneConfiguration.noul.criteria.true
      : "",
  );
  const [noulFalseCriteria, setNoulFalseCriteria] = useState(
    typeof systemOneConfiguration?.noul.criteria?.false === "string"
      ? systemOneConfiguration.noul.criteria.false
      : "",
  );
  const [processorPath, setProcessorPath] = useState(evaluator?.processor.path || "");
  const [onError, setOnError] = useState(evaluator?.on_error || "continue");

  const trimmedName = name.trim();
  const isDuplicate = !evaluator && existingNames.includes(trimmedName);
  const hasNoulCriteria = Boolean(noulTrueCriteria.trim() && noulFalseCriteria.trim());
  const hasIncompleteNoulCriteria =
    Boolean(noulTrueCriteria.trim()) !== Boolean(noulFalseCriteria.trim());

  const handleSubmit = () => {
    const engine: EvaluationEngine =
      engineType === "llm"
        ? {
            type: "llm",
            operation: llmOperation,
            prompt: llmPrompt,
            connection: llmConnection,
            result_schema: llmConfiguration?.result_schema,
          }
        : engineType === "typesafe-system-one"
          ? {
              type: "typesafe-system-one",
              connection: systemOneConnection.trim(),
              state: systemOneState,
              noul: {
                id: noulId.trim(),
                instructions: noulInstructions.trim(),
                criteria:
                  hasNoulCriteria
                    ? { true: noulTrueCriteria.trim(), false: noulFalseCriteria.trim() }
                    : undefined,
              },
            }
          : { type: "passthrough" };

    onSubmit({
      name: trimmedName,
      trigger: {
        method: triggerMethod,
        namespace: triggerNamespace.trim() || undefined,
      },
      engine,
      processor: {
        path: processorPath.trim(),
      },
      on_error: onError as "continue" | "block",
    });
  };

  const connectionHelperText =
    connections.length === 0
      ? "Add at least one entry under llm_connections in the server's wanaku.yaml file before you can create an evaluator."
      : undefined;

  const isValid =
    trimmedName &&
    !isDuplicate &&
    triggerMethod &&
    (engineType === "passthrough" ||
      (engineType === "llm" && llmPrompt.trim() && llmConnection) ||
      (engineType === "typesafe-system-one" &&
        systemOneConnection.trim() &&
        noulId.trim() &&
        noulInstructions.trim() &&
        !hasIncompleteNoulCriteria)) &&
    processorPath.trim();

  return (
    <Modal
      open={true}
      modalHeading={evaluator ? "Edit Evaluator" : "Add Evaluator"}
      primaryButtonText={evaluator ? "Save" : "Add"}
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
      primaryButtonDisabled={!isValid}
      size="lg"
    >
      <Stack gap={5}>
        <TextInput
          id="evaluator-name"
          labelText="Name"
          placeholder="e.g. safety-gate"
          value={name}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
          disabled={!!evaluator}
          invalid={isDuplicate}
          invalidText="An evaluator with this name already exists"
          required
        />

        <Select
          id="trigger-method"
          labelText="Trigger Method"
          value={triggerMethod}
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setTriggerMethod(e.target.value)}
        >
          <SelectItem value="tools/call" text="tools/call" />
          <SelectItem value="tools/list" text="tools/list" />
          <SelectItem value="resources/read" text="resources/read" />
          <SelectItem value="prompts/get" text="prompts/get" />
        </Select>

        <TextInput
          id="trigger-namespace"
          labelText="Trigger Namespace (optional)"
          placeholder="Leave empty for all namespaces"
          value={triggerNamespace}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setTriggerNamespace(e.target.value)}
        />

        <Select
          id="evaluator-engine"
          labelText="Evaluation Engine"
          value={engineType}
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
            setEngineType(e.target.value as EngineType)
          }
          helperText="Select how the evaluator produces input for the processor"
        >
          <SelectItem value="llm" text="LLM" />
          <SelectItem value="typesafe-system-one" text="TypeSafe System One" />
          <SelectItem value="passthrough" text="Passthrough" />
        </Select>

        {engineType === "llm" && (
          <>
            <Select
              id="llm-operation"
              labelText="LLM Operation"
              value={llmOperation}
              onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                setLlmOperation(e.target.value as LlmOperation)
              }
            >
              <SelectItem value="classify" text="Classify" />
              <SelectItem value="filter" text="Filter" />
              <SelectItem value="augment" text="Augment" />
            </Select>

            <TextArea
              id="llm-prompt"
              labelText="LLM Prompt"
              placeholder="System prompt for the LLM"
              value={llmPrompt}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setLlmPrompt(e.target.value)}
              rows={4}
              required
            />

            <Select
              id="llm-connection"
              labelText="LLM Connection"
              value={llmConnection}
              onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setLlmConnection(e.target.value)}
              disabled={connections.length === 0}
              helperText={connectionHelperText}
            >
              {connections.length === 0 ? (
                <SelectItem disabled hidden text="No connections configured" value="" />
              ) : (
                <>
                  <SelectItem disabled hidden text="Select a connection..." value="" />
                  {connections.map((name) => (
                    <SelectItem key={name} value={name} text={name} />
                  ))}
                </>
              )}
            </Select>
          </>
        )}

        {engineType === "typesafe-system-one" && (
          <SystemOneFields
            connection={systemOneConnection}
            state={systemOneState}
            noulId={noulId}
            instructions={noulInstructions}
            trueCriteria={noulTrueCriteria}
            falseCriteria={noulFalseCriteria}
            onConnectionChange={setSystemOneConnection}
            onStateChange={setSystemOneState}
            onNoulIdChange={setNoulId}
            onInstructionsChange={setNoulInstructions}
            onTrueCriteriaChange={setNoulTrueCriteria}
            onFalseCriteriaChange={setNoulFalseCriteria}
          />
        )}

        <TextInput
          id="processor-path"
          labelText="Processor Path"
          placeholder="/path/to/action.wasm"
          value={processorPath}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setProcessorPath(e.target.value)}
          helperText="Path to the WASM action script"
          required
        />

        <Select
          id="on-error"
          labelText="Error Policy"
          value={onError}
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
            setOnError(e.target.value as "continue" | "block")
          }
          helperText="What to do if the evaluator fails"
        >
          <SelectItem value="continue" text="Continue" />
          <SelectItem value="block" text="Block" />
        </Select>
      </Stack>
    </Modal>
  );
};
