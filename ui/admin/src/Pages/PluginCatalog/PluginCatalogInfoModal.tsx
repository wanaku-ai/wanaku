import { Modal } from '@carbon/react';
import type { PluginCatalogEntry } from '../../models';

interface PluginCatalogInfoModalProps {
  plugin: PluginCatalogEntry;
  onRequestClose: () => void;
}

export const PluginCatalogInfoModal = ({ plugin, onRequestClose }: PluginCatalogInfoModalProps) => (
  <Modal
    open
    className="plugin-catalog"
    passiveModal
    modalHeading={`${plugin.name} v${plugin.version}`}
    onRequestClose={onRequestClose}
  >
    <dl tabIndex={-1} data-modal-primary-focus>
      <dt>ID</dt>
      <dd>{plugin.id}</dd>
      <dt>Description</dt>
      <dd>{plugin.description || 'Not specified'}</dd>
      <dt>Publisher</dt>
      <dd>{plugin.publisher || 'Not specified'}</dd>
      <dt>License</dt>
      <dd>{plugin.license || 'Not specified'}</dd>
      <dt>Dependencies</dt>
      <dd>
        {plugin.dependencies?.length
          ? plugin.dependencies
              .map((dependency) => `${dependency.id} v${dependency.version}`)
              .join(', ')
          : 'None'}
      </dd>
      <dt>Required services</dt>
      <dd>
        {plugin.requires?.services?.length
          ? plugin.requires.services
              .map((service) => `${service.id} v${service.version}`)
              .join(', ')
          : 'None'}
      </dd>
      <dt>Host API</dt>
      <dd>{plugin.requires?.hostApi || 'Not specified'}</dd>
      <dt>Additional metadata</dt>
      <dd>
        {Object.keys(plugin.metadata ?? {}).length > 0 ? (
          <pre>{JSON.stringify(plugin.metadata, null, 2)}</pre>
        ) : (
          'None'
        )}
      </dd>
      <dt>Download URL</dt>
      <dd>{plugin.url}</dd>
    </dl>
  </Modal>
);
