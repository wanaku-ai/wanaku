import { useCallback } from 'react';
import { listBindings as listBindingsRequest } from '../../api/wanaku-router-api';

/**
 * Credential bindings are operator-managed and the management API only
 * exposes read operations for them. This hook intentionally offers no
 * create/update/delete methods.
 */
export const useBindings = () => {
  const listBindings = useCallback((options?: RequestInit) => listBindingsRequest(options), []);

  return { listBindings };
};
