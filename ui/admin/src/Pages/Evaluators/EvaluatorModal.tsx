import {
  Modal,
  Select,
  SelectItem,
  Stack, Tab, TabList, TabPanel, TabPanels, Tabs,
  TextArea,
  TextInput
} from "@carbon/react";
import React, {useState} from "react";
import { EvaluatorDef } from "../../hooks/api/use-evaluators";
import {validateJsonSchema} from "../../utils/json-schema-validation"
import {createInfoLabel} from "../../components/InfoLabel"

interface EvaluatorModalProps {
  evaluator?: EvaluatorDef;
  existingNames: string[];
  connections: string[];
  onRequestClose: () => void;
  onSubmit: (evaluator: EvaluatorDef) => void;
}

export const EvaluatorModal: React.FC<EvaluatorModalProps> = ({
  evaluator,
  existingNames,
  connections,
  onRequestClose,
  onSubmit,
}) => {
  const [name, setName] = useState(evaluator?.name || "");
  const [triggerMethod, setTriggerMethod] = useState(evaluator?.trigger.method || "tools/call");
  const [triggerNamespace, setTriggerNamespace] = useState(evaluator?.trigger.namespace || "");
  const [llmOperation, setLlmOperation] = useState(evaluator?.llm.operation || "classify");
  const [llmPrompt, setLlmPrompt] = useState(evaluator?.llm.prompt || "");
  const [llmConnection, setLlmConnection] = useState(evaluator?.llm.connection || "");
  const [llmResultSchema, setLlmResultSchema] = useState(getLlmResultSchema(evaluator))
  const [processorPath, setProcessorPath] = useState(evaluator?.processor.path || "");
  const [onError, setOnError] = useState(evaluator?.on_error || "continue");

  const [llmResultSchemaInvalid, setLlmResultSchemaInvalid] = useState(false)
  const [llmResultSchemaInvalidText, setLlmResultSchemaInvalidText] = useState("")
  
  const trimmedName = name.trim();
  const isDuplicate = !evaluator && existingNames.includes(trimmedName);

  
  function getLlmResultSchema(evaluator?: EvaluatorDef): string {
    if (evaluator?.llm.result_schema) {
      if (evaluator.llm.result_schema instanceof String) {
        return evaluator.llm.result_schema as string
      } else {
        return JSON.stringify(evaluator.llm.result_schema)
      }
    }
    return ""
  }
  
  function validateLlmResultSchema(resultSchema: string) {
    const result = validateJsonSchema(resultSchema)
    setLlmResultSchemaInvalid(!result.valid)
    if (result.valid) {
      setLlmResultSchemaInvalidText("")
    } else {
      setLlmResultSchemaInvalidText(result.errors[0])
    }
  }
  
  
  const handleSubmit = () => {
    onSubmit({
      name: trimmedName,
      trigger: {
        method: triggerMethod,
        namespace: triggerNamespace.trim() || undefined,
      },
      llm: {
        operation: llmOperation as "classify" | "filter" | "augment",
        prompt: llmPrompt,
        connection: llmConnection,
        result_schema: llmResultSchema
      },
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
    llmPrompt.trim() &&
    llmConnection &&
    processorPath.trim();
  
  const llmOperationLabel= createInfoLabel("Operation", "Classify - classify\nFilter - filter\nAugment - augment")

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
      <Tabs>
        <TabList>
          <Tab>Overview</Tab>
          <Tab>LLM</Tab>
          <Tab>Processor</Tab>
        </TabList>
        
        <TabPanels>
          <TabPanel>
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
            </Stack>
          </TabPanel>
          
          <TabPanel>
            <Stack gap={5}>
              <Select
                id="llm-operation"
                labelText={llmOperationLabel}
                value={llmOperation}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                  setLlmOperation(e.target.value as "classify" | "filter" | "augment")
                }
                
              >
                <SelectItem value="classify" text="Classify" />
                <SelectItem value="filter" text="Filter" />
                <SelectItem value="augment" text="Augment" />
              </Select>

              <TextArea
                id="llm-prompt"
                labelText="Prompt"
                placeholder="System prompt for the LLM"
                value={llmPrompt}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setLlmPrompt(e.target.value)}
                rows={4}
                required
              />

              <Select
                id="llm-connection"
                labelText="Connection"
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
              
              <TextArea
                id="llm-result-schema"
                labelText="Result Schema"
                value={llmResultSchema}
                placeholder={'e.g. { "type": "object", "properties": { "message": "string" } }'}
                invalid={llmResultSchemaInvalid}
                invalidText={llmResultSchemaInvalidText}
                onChange={event => {
                  const llmResultSchema = event.target.value
                  setLlmResultSchema(llmResultSchema)
                }}
                onBlur={() => validateLlmResultSchema(llmResultSchema)}
              />
            </Stack>
          </TabPanel>
          
          <TabPanel>
            <Stack gap={5}>
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
                labelText={createInfoLabel("Error Policy", "Continue - ...\nBlock - ...")}
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
          </TabPanel>
        </TabPanels>
      </Tabs>
    </Modal>
  );
};
