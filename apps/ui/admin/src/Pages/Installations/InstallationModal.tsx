import { Modal, TextInput, RadioButtonGroup, RadioButton } from "@carbon/react";
import React, { useState } from "react";
import { InstallationConfig } from "./installation-types";

interface InstallationModalProps {
  installation?: { id?: string; name?: string; data?: string; labels?: Record<string, string> };
  onRequestClose: () => void;
  onSubmit: (installation: { id?: string; name: string; data: string; labels: Record<string, string> }) => void;
}

export const InstallationModal: React.FC<InstallationModalProps> = ({
  installation,
  onRequestClose,
  onSubmit,
}) => {
  const existingConfig: InstallationConfig | null = installation?.data
    ? JSON.parse(installation.data)
    : null;

  const [name, setName] = useState(installation?.name || "");
  const [type, setType] = useState<'native' | 'cic' | 'code-execution'>(
    existingConfig?.type || 'native'
  );
  const [executablePath, setExecutablePath] = useState(existingConfig?.executablePath || "");
  const [serviceCatalog, setServiceCatalog] = useState(existingConfig?.serviceCatalog || "");
  const [serviceCatalogSystem, setServiceCatalogSystem] = useState(
    existingConfig?.serviceCatalogSystem || ""
  );
  const [engineType, setEngineType] = useState(existingConfig?.engineType || "");
  const [language, setLanguage] = useState(existingConfig?.language || "");

  const handleSubmit = () => {
    const labels: Record<string, string> = {
      "wanaku.type": "installation",
      "wanaku.installation.type": type
    };

    const config: InstallationConfig = {
      type,
      executablePath
    };

    if (type === 'cic') {
      config.serviceCatalog = serviceCatalog;
      config.serviceCatalogSystem = serviceCatalogSystem;
    } else if (type === 'code-execution') {
      config.engineType = engineType;
      config.language = language;
    }

    onSubmit({
      id: installation?.id,
      name,
      data: JSON.stringify(config),
      labels
    });
  };

  const isPrimaryDisabled =
    !name ||
    !executablePath ||
    (type === 'cic' && (!serviceCatalog || !serviceCatalogSystem)) ||
    (type === 'code-execution' && (!engineType || !language));

  return (
    <Modal
      open={true}
      modalHeading={installation ? "Edit Installation" : "Add Installation"}
      primaryButtonText={installation ? "Save" : "Add"}
      secondaryButtonText="Cancel"
      onRequestClose={onRequestClose}
      onRequestSubmit={handleSubmit}
      primaryButtonDisabled={isPrimaryDisabled}
    >
      <RadioButtonGroup
        legendText="Installation Type"
        name="installation-type"
        valueSelected={type}
        onChange={(value) => setType(value as 'native' | 'cic' | 'code-execution')}
      >
        <RadioButton labelText="Native Capability" value="native" id="type-native" />
        <RadioButton labelText="Camel Integration Capability" value="cic" id="type-cic" />
        <RadioButton labelText="Code Execution Engine" value="code-execution" id="type-code-execution" />
      </RadioButtonGroup>

      <TextInput
        id="installation-name"
        labelText="Name"
        placeholder="e.g. my-capability"
        value={name}
        onChange={(e) => setName(e.target.value)}
        required
      />

      <TextInput
        id="executable-path"
        labelText="Executable Path"
        placeholder="e.g. /path/to/quarkus-run.jar"
        value={executablePath}
        onChange={(e) => setExecutablePath(e.target.value)}
        required
      />

      {type === 'cic' && (
        <>
          <TextInput
            id="service-catalog"
            labelText="Service Catalog"
            placeholder="e.g. my-catalog"
            value={serviceCatalog}
            onChange={(e) => setServiceCatalog(e.target.value)}
            required
          />
          <TextInput
            id="service-catalog-system"
            labelText="Service Catalog System"
            placeholder="e.g. default"
            value={serviceCatalogSystem}
            onChange={(e) => setServiceCatalogSystem(e.target.value)}
            required
          />
        </>
      )}

      {type === 'code-execution' && (
        <>
          <TextInput
            id="engine-type"
            labelText="Engine Type"
            placeholder="e.g. jvm, interpreted, camel"
            value={engineType}
            onChange={(e) => setEngineType(e.target.value)}
            required
          />
          <TextInput
            id="language"
            labelText="Language"
            placeholder="e.g. java, python, yaml"
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            required
          />
        </>
      )}
    </Modal>
  );
};
