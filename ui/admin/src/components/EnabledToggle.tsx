import { Toggle } from '@carbon/react';
import React from 'react';

interface EnabledToggleProps {
  name: string;
  enabled: boolean;
  onSetEnabled: (name: string, enabled: boolean) => void;
}

/**
 * Enable/Disable switch for tools, resources, and prompts. These entries come
 * from forward discovery, so the next discovery would restore a deleted entry.
 */
export const EnabledToggle: React.FC<EnabledToggleProps> = ({ name, enabled, onSetEnabled }) => (
  <Toggle
    id={`enabled-${name}`}
    aria-label={`Enable ${name}`}
    size="sm"
    labelA="Disabled"
    labelB="Enabled"
    toggled={enabled}
    onToggle={(checked) => onSetEnabled(name, checked)}
  />
);
