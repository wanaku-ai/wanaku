import { Toggle } from '@carbon/react';
import React from 'react';

interface EnabledToggleProps {
  name: string;
  namespace?: string | null;
  enabled: boolean;
  onSetEnabled: (name: string, namespace: string, enabled: boolean) => void;
}

/**
 * Enable/Disable switch for tools, resources, and prompts. These entries come
 * from forward discovery, so the next discovery would restore a deleted entry.
 */
export const EnabledToggle: React.FC<EnabledToggleProps> = ({
  name,
  namespace,
  enabled,
  onSetEnabled,
}) => {
  // The registry keys entries by namespace and name.
  const entryNamespace = namespace ?? 'default';
  return (
    <Toggle
      id={`enabled-${entryNamespace}-${name}`}
      aria-label={`Enable ${name}`}
      size="sm"
      labelA="Disabled"
      labelB="Enabled"
      toggled={enabled}
      onToggle={(checked) => onSetEnabled(name, entryNamespace, checked)}
    />
  );
};
