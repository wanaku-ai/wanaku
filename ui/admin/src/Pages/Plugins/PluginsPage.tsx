import { InlineNotification } from "@carbon/react";
import { PageSkeleton } from "../../components/PageSkeleton";
import React, { useCallback, useEffect, useState } from "react";
import type { PluginManifest } from "../../plugins/types";
import type { InstallPluginRequest } from "../../models";
import { usePlugins } from "../../hooks/api/use-plugins";
import { PluginsTable } from "./PluginsTable";
import { PluginDetailModal } from "./PluginDetailModal";
import { PluginInstallModal } from "./PluginInstallModal";
import { PluginConfigModal } from "./PluginConfigModal";
import { ErrorNotification } from "../../components/ErrorNotification";
import { useErrorNotification } from "../../hooks/error-notifications";
import { unwrapData } from "../../utils/api-response";

const PluginsPage: React.FC = () => {
  const [plugins, setPlugins] = useState<PluginManifest[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { errorMessage, setErrorMessage } = useErrorNotification();
  const [selectedPlugin, setSelectedPlugin] = useState<PluginManifest | null>(null);
  const [configPlugin, setConfigPlugin] = useState<PluginManifest | null>(null);
  const [isInstallOpen, setIsInstallOpen] = useState(false);
  const [isInstalling, setIsInstalling] = useState(false);
  const [isSavingConfig, setIsSavingConfig] = useState(false);
  const [restartNotice, setRestartNotice] = useState(false);

  const { listPlugins, installPlugin, configurePlugin } = usePlugins();

  const fetchPlugins = useCallback(async () => {
    try {
      const result = await listPlugins();
      if (result.status === 200 && result.data) {
        const data = unwrapData<PluginManifest[]>(result as unknown as { data: PluginManifest[] });
        setPlugins(Array.isArray(data) ? data : (result.data as unknown as PluginManifest[]));
      } else {
        setPlugins([]);
      }
    } catch {
      setErrorMessage("Failed to load plugins");
      setPlugins([]);
    }
  }, [listPlugins, setErrorMessage]);

  useEffect(() => {
    fetchPlugins().finally(() => setIsLoading(false));
  }, [fetchPlugins]);

  const handleView = (plugin: PluginManifest) => {
    setSelectedPlugin(plugin);
  };

  const handleConfigure = (plugin: PluginManifest) => {
    setConfigPlugin(plugin);
  };

  const handleCloseModal = () => {
    setSelectedPlugin(null);
  };

  const handleInstall = async (req: InstallPluginRequest) => {
    setIsInstalling(true);
    try {
      const response = await installPlugin(req);
      if (response.status === 200 && response.data) {
        setIsInstallOpen(false);
        await fetchPlugins();
        const payload = response.data as unknown as { manifest?: PluginManifest; data?: { manifest?: PluginManifest } };
        const installedManifest = payload?.manifest || payload?.data?.manifest;
        if (installedManifest && (installedManifest.requires?.services?.length ?? 0) > 0) {
          setConfigPlugin(installedManifest);
        } else {
          setRestartNotice(true);
        }
      } else {
        const errPayload = response.data as unknown as { error?: string };
        const err = errPayload?.error || "Failed to install plugin";
        setErrorMessage(typeof err === "string" ? err : JSON.stringify(err));
      }
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "Failed to install plugin");
    } finally {
      setIsInstalling(false);
    }
  };

  const handleSaveConfig = async (
    pluginId: string,
    services: Record<string, { target: string }>
  ) => {
    setIsSavingConfig(true);
    try {
      const response = await configurePlugin(pluginId, { services });
      if (response.status === 200) {
        setConfigPlugin(null);
        setRestartNotice(true);
      } else {
        const errPayload = response.data as unknown as { error?: string };
        const err = errPayload?.error || "Failed to save configuration";
        setErrorMessage(typeof err === "string" ? err : JSON.stringify(err));
      }
    } catch (e) {
      setErrorMessage(e instanceof Error ? e.message : "Failed to save configuration");
    } finally {
      setIsSavingConfig(false);
    }
  };

  if (isLoading) return <PageSkeleton title="Plugins" />;

  return (
    <div>
      {errorMessage && (
        <ErrorNotification
          errorMessage={errorMessage}
          onClose={() => setErrorMessage(null)}
        />
      )}

      {restartNotice && (
        <InlineNotification
          kind="info"
          title="Server restart required"
          subtitle="Plugin configuration has been updated. A server restart is required for changes to become active."
          onCloseButtonClick={() => setRestartNotice(false)}
          style={{ marginBottom: "1rem" }}
        />
      )}

      <h1 className="title">Plugins</h1>
      <p className="description">Installed UI plugins and their configuration.</p>

      <div id="page-content">
        <PluginsTable
          plugins={plugins}
          onView={handleView}
          onConfigure={handleConfigure}
          onInstallClick={() => setIsInstallOpen(true)}
        />
      </div>

      {selectedPlugin && (
        <PluginDetailModal plugin={selectedPlugin} onRequestClose={handleCloseModal} />
      )}

      {isInstallOpen && (
        <PluginInstallModal
          onInstall={handleInstall}
          onRequestClose={() => setIsInstallOpen(false)}
          isInstalling={isInstalling}
        />
      )}

      {configPlugin && (
        <PluginConfigModal
          plugin={configPlugin}
          onSave={handleSaveConfig}
          onRequestClose={() => setConfigPlugin(null)}
          isSaving={isSavingConfig}
        />
      )}
    </div>
  );
};

export const Component = PluginsPage;
