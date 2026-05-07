import React, {useEffect, useState} from "react";
import {
  ComposedModal,
  ModalHeader,
  ModalBody,
  ModalFooter,
  Button,
  TextInput,
  Loading,
  InlineNotification,
  FormLabel,
} from "@carbon/react";
import {useServiceTemplate} from "../../hooks/api/use-service-template";

interface ServiceTemplateWizardProps {
  templateName: string;
  onClose: () => void;
  onSuccess: () => void;
}

interface TemplateProperties {
  [system: string]: {
    [key: string]: string;
  };
}

export const ServiceTemplateWizard: React.FC<ServiceTemplateWizardProps> = ({
  templateName,
  onClose,
  onSuccess,
}) => {
  const [properties, setProperties] = useState<TemplateProperties>({});
  const [formValues, setFormValues] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { getTemplateProperties, instantiateTemplate } = useServiceTemplate();

  useEffect(() => {
    const fetchProperties = async () => {
      try {
        const result = await getTemplateProperties(templateName);
        const body = result.data as { data: TemplateProperties };
        const props = body.data || {};
        setProperties(props);

        // Initialize form values with current property values
        const initialValues: Record<string, string> = {};
        Object.values(props).forEach((systemProps) => {
          Object.entries(systemProps).forEach(([key, value]) => {
            initialValues[key] = value || "";
          });
        });
        setFormValues(initialValues);
        setIsLoading(false);
      } catch (err) {
        console.error("Error fetching template properties:", err);
        setError("Failed to load template properties");
        setIsLoading(false);
      }
    };

    fetchProperties();
  }, [templateName, getTemplateProperties]);

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setError(null);

    try {
      await instantiateTemplate(templateName, formValues);
      onSuccess();
    } catch (err) {
      console.error("Error instantiating template:", err);
      setError("Failed to create service catalog from template");
      setIsSubmitting(false);
    }
  };

  const hasProperties = Object.keys(properties).length > 0 &&
    Object.values(properties).some(systemProps => Object.keys(systemProps).length > 0);

  return (
    <ComposedModal open={true} onClose={onClose} size="md">
      <ModalHeader title={`Create Service Catalog from Template: ${templateName}`} />
      <ModalBody>
        {isLoading ? (
          <Loading description="Loading template properties..." />
        ) : error ? (
          <InlineNotification
            kind="error"
            title="Error"
            subtitle={error}
            hideCloseButton
          />
        ) : !hasProperties ? (
          <p>This template has no configurable properties. Click "Create" to instantiate it.</p>
        ) : (
          <div className="template-wizard-form">
            <p className="template-wizard-description">
              Fill in the configuration parameters below to create a new service catalog.
            </p>
            {Object.entries(properties).map(([system, systemProps]) => (
              <div key={system} className="template-wizard-system">
                <FormLabel className="template-wizard-system-label">
                  System: <strong>{system}</strong>
                </FormLabel>
                {Object.entries(systemProps).map(([key, currentValue]) => (
                  <TextInput
                    key={key}
                    id={`prop-${system}-${key}`}
                    labelText={key}
                    placeholder={`Enter ${key}`}
                    value={formValues[key] || ""}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                      setFormValues((prev) => ({ ...prev, [key]: e.target.value }))
                    }
                    helperText={currentValue ? `Current: ${currentValue}` : undefined}
                  />
                ))}
              </div>
            ))}
          </div>
        )}
      </ModalBody>
      <ModalFooter>
        <Button kind="secondary" onClick={onClose} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button
          kind="primary"
          onClick={handleSubmit}
          disabled={isLoading || isSubmitting}
        >
          {isSubmitting ? "Creating..." : "Create"}
        </Button>
      </ModalFooter>
    </ComposedModal>
  );
};
