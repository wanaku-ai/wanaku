import { ToastNotification } from "@carbon/react";
import React, { useCallback, useEffect, useState } from "react";
import type { PluginManifest } from "../../plugins/types";
import { usePlugins } from "../../hooks/api/use-plugins";
import { PluginsTable } from "./PluginsTable";
import { PluginDetailModal } from "./PluginDetailModal";

const PluginsPage: React.FC = () => {
  const [plugins, setPlugins] = useState<PluginManifest[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedPlugin, setSelectedPlugin] = useState<PluginManifest | null>(null);

  const { listPlugins } = usePlugins();

  const fetchPlugins = useCallback(async () => {
    try {
      const result = await listPlugins();
      if (result.status === 200 && result.data.data) {
        setPlugins(result.data.data);
      } else {
        setPlugins([]);
      }
    } catch {
      setErrorMessage("Failed to load plugins");
      setPlugins([]);
    }
  }, [listPlugins]);

  useEffect(() => {
    fetchPlugins().finally(() => setIsLoading(false));
  }, [fetchPlugins]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => setErrorMessage(null), 10_000);
      return () => clearTimeout(timer);
    }
  }, [errorMessage]);

  const handleView = (plugin: PluginManifest) => {
    setSelectedPlugin(plugin);
  };

  const handleCloseModal = () => {
    setSelectedPlugin(null);
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

      <h1 className="title">Plugins</h1>
      <p className="description">Installed UI plugins and their configuration.</p>

      <div id="page-content">
        <PluginsTable plugins={plugins} onView={handleView} />
      </div>

      {selectedPlugin && (
        <PluginDetailModal plugin={selectedPlugin} onRequestClose={handleCloseModal} />
      )}
    </div>
  );
};

export const Component = PluginsPage;
