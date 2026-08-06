import {
  Button,
  Column,
  Grid,
  Select,
  SelectItem,
  Stack,
  TextInput,
  Tile,
  ToastNotification,
} from "@carbon/react";
import {Renew, Security, TrashCan} from "@carbon/icons-react";
import React, {useCallback, useEffect, useState} from "react";
import {SafetyConfig, useSafety} from "../../hooks/api/use-safety";
import "./SafetyPage.scss";

export const SafetyPage: React.FC = () => {
  const {getSafety, putSafety, deleteSafety} = useSafety();

  const [isEnabled, setIsEnabled] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [formData, setFormData] = useState<SafetyConfig>({
    llm_url: "http://localhost:11434/v1",
    llm_model: "llama3.2",
    llm_api_key: "",
    red_action: "block",
    yellow_action: "log",
  });

  const loadSafetyConfig = useCallback(async () => {
    try {
      const result = await getSafety();
      if (result.status === 200 && result.data.data) {
        setFormData(result.data.data);
        setIsEnabled(true);
      } else {
        setIsEnabled(false);
      }
    } catch {
      setErrorMessage("Failed to load safety configuration");
      setIsEnabled(false);
    } finally {
      setIsLoading(false);
    }
  }, [getSafety]);

  useEffect(() => {
    loadSafetyConfig();
  }, [loadSafetyConfig]);

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

  const handleSave = async () => {
    try {
      const result = await putSafety(formData);
      if (result.status === 200 && result.data.data) {
        setSuccessMessage("Safety classifier configuration saved");
        setIsEnabled(true);
        setFormData(result.data.data);
      } else {
        setErrorMessage(result.data.error || "Failed to save configuration");
      }
    } catch {
      setErrorMessage("Failed to save configuration");
    }
  };

  const handleDisable = async () => {
    if (!window.confirm("Are you sure you want to disable the safety classifier?")) {
      return;
    }

    try {
      const result = await deleteSafety();
      if (result.status === 200) {
        setSuccessMessage("Safety classifier disabled");
        setIsEnabled(false);
      } else {
        setErrorMessage(result.data.error || "Failed to disable classifier");
      }
    } catch {
      setErrorMessage("Failed to disable classifier");
    }
  };

  if (isLoading) return <div>Loading...</div>;

  return (
    <div className="safety-page">
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{float: "right"}}
        />
      )}
      {successMessage && (
        <ToastNotification
          kind="success"
          title="Success"
          subtitle={successMessage}
          onCloseButtonClick={() => setSuccessMessage(null)}
          timeout={5000}
          style={{float: "right"}}
        />
      )}

      <h1 className="title">Safety Classification</h1>
      <p className="description">
        Configure the LLM-based safety classifier that evaluates tool calls before execution.
        Status:{" "}
        <span className={`status-indicator status-indicator--${isEnabled ? "enabled" : "disabled"}`}>
          <Security size={16} />
          {isEnabled ? "Enabled" : "Disabled"}
        </span>
      </p>

      <div id="page-content">
        <Grid>
          <Column lg={8} md={8} sm={4}>
            <Tile className="safety-form-tile">
              <h3 className="section-heading">LLM Connection</h3>
              <Stack gap={5}>
                <TextInput
                  id="llm-url"
                  labelText="LLM URL"
                  value={formData.llm_url}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                    setFormData({...formData, llm_url: e.target.value})
                  }
                  placeholder="http://localhost:11434/v1"
                  helperText="OpenAI-compatible chat completions endpoint"
                />
                <TextInput
                  id="llm-model"
                  labelText="Model"
                  value={formData.llm_model}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                    setFormData({...formData, llm_model: e.target.value})
                  }
                  placeholder="llama3.2"
                />
                <TextInput
                  id="llm-api-key"
                  labelText="API Key"
                  type="password"
                  value={formData.llm_api_key}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                    setFormData({...formData, llm_api_key: e.target.value})
                  }
                  placeholder="Optional — only needed for cloud providers"
                  helperText="Bearer token for authentication"
                />
              </Stack>
            </Tile>
          </Column>

          <Column lg={8} md={8} sm={4}>
            <Tile className="safety-form-tile">
              <h3 className="section-heading">Classification Actions</h3>
              <Stack gap={5}>
                <Select
                  id="red-action"
                  labelText="Red (Dangerous)"
                  value={formData.red_action}
                  helperText="Action when a tool call is classified as dangerous"
                  onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                    setFormData({...formData, red_action: e.target.value as "log" | "warn" | "block"})
                  }
                >
                  <SelectItem value="log" text="Log only" />
                  <SelectItem value="warn" text="Warn" />
                  <SelectItem value="block" text="Block execution" />
                </Select>
                <Select
                  id="yellow-action"
                  labelText="Yellow (Ambiguous)"
                  value={formData.yellow_action}
                  helperText="Action when a tool call is classified as ambiguous or risky"
                  onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                    setFormData({...formData, yellow_action: e.target.value as "log" | "warn" | "block"})
                  }
                >
                  <SelectItem value="log" text="Log only" />
                  <SelectItem value="warn" text="Warn" />
                  <SelectItem value="block" text="Block execution" />
                </Select>
              </Stack>
            </Tile>
          </Column>
        </Grid>

        <div className="safety-actions">
          <Button kind="primary" renderIcon={Renew} onClick={handleSave}>
            Save Configuration
          </Button>
          {isEnabled && (
            <Button kind="danger--tertiary" renderIcon={TrashCan} onClick={handleDisable}>
              Disable Classifier
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};
