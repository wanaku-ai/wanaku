import { Button, DataTableSkeleton, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { PluginCatalogEntry, PluginManifest } from '../../models';
import { usePluginCatalog } from '../../hooks/api/use-plugin-catalog';
import { usePlugins } from '../../hooks/api/use-plugins';
import { PluginConfigModal } from '../Plugins/PluginConfigModal';
import { PluginCatalogTable } from './PluginCatalogTable';
import { PluginCatalogInfoModal } from './PluginCatalogInfoModal';
import './PluginCatalog.scss';

type CatalogState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; plugins: PluginCatalogEntry[] };

export const PluginCatalogPage = () => {
  const { listCatalog } = usePluginCatalog();
  const { installPlugin, configurePlugin } = usePlugins();
  const [catalog, setCatalog] = useState<CatalogState>({ status: 'loading' });
  const [selectedPlugin, setSelectedPlugin] = useState<PluginCatalogEntry | null>(null);
  const infoButtonRef = useRef<HTMLButtonElement | null>(null);
  const [installingId, setInstallingId] = useState<string | null>(null);
  const [configPlugin, setConfigPlugin] = useState<PluginManifest | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [restartNotice, setRestartNotice] = useState(false);

  const fetchCatalog = useCallback(async () => {
    setCatalog({ status: 'loading' });
    try {
      const response = await listCatalog();
      if (response.status !== 200) throw new Error('Failed to load plugin catalog');
      setCatalog({ status: 'success', plugins: response.data });
    } catch (error) {
      setCatalog({
        status: 'error',
        message: error instanceof Error ? error.message : 'Failed to load plugin catalog',
      });
    }
  }, [listCatalog]);

  useEffect(() => {
    void fetchCatalog();
  }, [fetchCatalog]);

  const handleInstall = async (plugin: PluginCatalogEntry) => {
    if (!plugin.url) return;
    setInstallingId(plugin.id);
    setActionError(null);
    try {
      const response = await installPlugin({
        id: plugin.id,
        version: plugin.version,
        url: plugin.url,
      });
      if (response.status !== 200) throw new Error('Failed to install plugin');
      if (response.data.manifest.requires?.services?.length)
        setConfigPlugin(response.data.manifest);
      else setRestartNotice(true);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to install plugin');
    } finally {
      setInstallingId(null);
    }
  };

  const handleSaveConfig = async (id: string, services: Record<string, { target: string }>) => {
    setIsSaving(true);
    setActionError(null);
    try {
      const response = await configurePlugin(id, { services });
      if (response.status !== 200) throw new Error('Failed to save configuration');
      setConfigPlugin(null);
      setRestartNotice(true);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Failed to save configuration');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="plugin-catalog">
      <h1 className="title">Plugin Catalog</h1>
      <p className="description">
        Browse plugins from the configured catalog and install them on this server.
      </p>
      {actionError && (
        <InlineNotification
          kind="error"
          title="Plugin operation failed"
          subtitle={actionError}
          onCloseButtonClick={() => setActionError(null)}
        />
      )}
      {restartNotice && (
        <InlineNotification
          kind="info"
          title="Server restart required"
          subtitle="Restart the server to activate the installed plugin."
          onCloseButtonClick={() => setRestartNotice(false)}
        />
      )}
      <div id="page-content">
        {catalog.status === 'loading' && (
          <DataTableSkeleton columnCount={4} rowCount={5} showHeader={false} showToolbar={false} />
        )}
        {catalog.status === 'error' && (
          <>
            <InlineNotification
              kind="error"
              title="Failed to load plugin catalog"
              subtitle={catalog.message}
              hideCloseButton
            />
            <Button kind="tertiary" onClick={() => void fetchCatalog()}>
              Retry
            </Button>
          </>
        )}
        {catalog.status === 'success' && (
          <PluginCatalogTable
            plugins={catalog.plugins}
            onInfo={(plugin, button) => {
              infoButtonRef.current = button;
              setSelectedPlugin(plugin);
            }}
            onInstall={handleInstall}
            installingId={installingId}
          />
        )}
      </div>
      {selectedPlugin && (
        <PluginCatalogInfoModal
          plugin={selectedPlugin}
          onRequestClose={() => {
            setSelectedPlugin(null);
            requestAnimationFrame(() => infoButtonRef.current?.focus());
          }}
        />
      )}
      {configPlugin && (
        <PluginConfigModal
          plugin={configPlugin}
          onSave={handleSaveConfig}
          onRequestClose={() => {
            setConfigPlugin(null);
            setRestartNotice(true);
          }}
          isSaving={isSaving}
          errorMessage={actionError}
        />
      )}
    </div>
  );
};
