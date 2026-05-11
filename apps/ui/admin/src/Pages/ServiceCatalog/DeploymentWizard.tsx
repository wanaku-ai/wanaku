import React, { useCallback, useMemo, useState } from "react";
import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  Loading,
  InlineNotification,
  ProgressIndicator,
  ProgressStep,
  RadioButtonGroup,
  RadioButton,
  TextInput,
  CodeSnippet,
  FormLabel,
} from "@carbon/react";
import { useServiceCatalog } from "../../hooks/api/use-service-catalog";
import type { DeploymentInstructions } from "../../models/deploymentInstructions";
import type { PlaceholderDefinition } from "../../models/placeholderDefinition";

interface DeploymentWizardProps {
  catalogName: string;
  onClose: () => void;
}

export const DeploymentWizard: React.FC<DeploymentWizardProps> = ({
  catalogName,
  onClose,
}) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedModel, setSelectedModel] = useState("local");
  const [instructions, setInstructions] = useState<DeploymentInstructions | null>(null);
  const [placeholderValues, setPlaceholderValues] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { getDeploymentInstructions } = useServiceCatalog();

  const handleNext = useCallback(async () => {
    if (currentStep === 0) {
      setIsLoading(true);
      setError(null);
      try {
        const result = await getDeploymentInstructions(catalogName, selectedModel);
        const body = result.data as { data: DeploymentInstructions };
        const data = body.data;
        setInstructions(data);

        const initialValues: Record<string, string> = {};
        data.placeholders?.forEach((p: PlaceholderDefinition) => {
          initialValues[p.key || ""] = p.defaultValue || "";
        });
        setPlaceholderValues(initialValues);

        setCurrentStep(1);
      } catch (err) {
        console.error("Error fetching deployment instructions:", err);
        setError("Failed to generate deployment instructions");
      } finally {
        setIsLoading(false);
      }
    }
  }, [currentStep, catalogName, selectedModel, getDeploymentInstructions]);

  const handleBack = useCallback(() => {
    if (currentStep === 1) {
      setCurrentStep(0);
      setInstructions(null);
      setError(null);
    }
  }, [currentStep]);

  const renderedInstructions = useMemo(() => {
    if (!instructions?.systems) return [];
    return instructions.systems.map((sys) => {
      let text = sys.instruction || "";
      Object.entries(placeholderValues).forEach(([key, value]) => {
        if (value) {
          text = text.split(`<${key}>`).join(value);
        }
      });
      return { ...sys, renderedInstruction: text };
    });
  }, [instructions, placeholderValues]);

  const isInvalidUrl = useCallback((value: string): boolean => {
    if (!value || value === "http://" || value === "https://") return false;
    return !value.startsWith("http://") && !value.startsWith("https://");
  }, []);

  const modelLabels: Record<string, string> = {
    local: "Local (java -jar)",
    docker: "Docker / Podman",
    kubernetes: "Kubernetes / OpenShift",
  };

  return (
    <ComposedModal open={true} onClose={onClose} size="lg">
      <ModalHeader title={`Deploy: ${catalogName}`} />
      <ModalBody>
        <ProgressIndicator
          currentIndex={currentStep}
          className="deployment-wizard-progress"
        >
          <ProgressStep label="Deployment Model" />
          <ProgressStep label="Instructions" />
        </ProgressIndicator>

        {currentStep === 0 && (
          <div className="deployment-wizard-step">
            <p className="deployment-wizard-description">
              Choose how you want to deploy this service catalog.
            </p>
            <RadioButtonGroup
              legendText="Deployment model"
              name="deployment-model"
              defaultSelected={selectedModel}
              onChange={(value: string | number | undefined) => setSelectedModel(String(value || "local"))}
              className="deployment-wizard-radio-group"
              orientation="vertical"
            >
              <RadioButton
                labelText="Local (java -jar)"
                value="local"
                id="model-local"
              />
              <RadioButton
                labelText="Docker / Podman"
                value="docker"
                id="model-docker"
              />
              <RadioButton
                labelText="Kubernetes / OpenShift"
                value="kubernetes"
                id="model-kubernetes"
              />
            </RadioButtonGroup>
          </div>
        )}

        {currentStep === 1 && isLoading && (
          <Loading description="Generating deployment instructions..." />
        )}

        {currentStep === 1 && error && (
          <InlineNotification
            kind="error"
            title="Error"
            subtitle={error}
            hideCloseButton
          />
        )}

        {currentStep === 1 && instructions && !isLoading && !error && (
          <div className="deployment-wizard-step">
            <p className="deployment-wizard-type-note">
              Detected type: <strong>{instructions.catalogType}</strong>
              {" — "}Deployment model: <strong>{modelLabels[instructions.deploymentModel || ""] || instructions.deploymentModel}</strong>
            </p>

            {instructions.placeholders && instructions.placeholders.length > 0 && (
              <div className="deployment-wizard-placeholders">
                <FormLabel className="deployment-wizard-section-label">
                  Configuration
                </FormLabel>
                {instructions.placeholders.map((p) => {
                  const value = placeholderValues[p.key || ""] || "";
                  const urlInvalid = p.type === "url" && isInvalidUrl(value);
                  return (
                    <TextInput
                      key={p.key}
                      id={`placeholder-${p.key}`}
                      labelText={p.label || p.key || ""}
                      helperText={!urlInvalid ? p.description : undefined}
                      placeholder={p.defaultValue || `Enter ${p.label || p.key}`}
                      value={value}
                      invalid={urlInvalid}
                      invalidText="URL must start with http:// or https://"
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        setPlaceholderValues((prev) => ({
                          ...prev,
                          [p.key || ""]: e.target.value,
                        }))
                      }
                    />
                  );
                })}
              </div>
            )}

            <div className="deployment-wizard-instructions">
              <FormLabel className="deployment-wizard-section-label">
                {renderedInstructions.length === 1 && renderedInstructions[0].systemName === "all"
                  ? "Deployment Manifest"
                  : "Commands"}
              </FormLabel>
              {renderedInstructions.map((sys, index) => (
                <div key={sys.systemName || index} className="deployment-wizard-system-block">
                  {sys.systemName !== "all" && (
                    <p className="deployment-wizard-system-label">
                      System: <strong>{sys.systemName}</strong>
                    </p>
                  )}
                  <CodeSnippet
                    type="multi"
                    feedback="Copied!"
                    className="deployment-wizard-snippet"
                  >
                    {sys.renderedInstruction}
                  </CodeSnippet>
                </div>
              ))}
            </div>
          </div>
        )}
      </ModalBody>
      <ModalFooter>
        {currentStep === 0 ? (
          <>
            <Button kind="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button kind="primary" onClick={handleNext} disabled={isLoading}>
              Next
            </Button>
          </>
        ) : (
          <>
            <Button kind="secondary" onClick={handleBack}>
              Back
            </Button>
            <Button kind="primary" onClick={onClose}>
              Done
            </Button>
          </>
        )}
      </ModalFooter>
    </ComposedModal>
  );
};
