import { useCallback } from 'react';
import { listBindings as listBindingsRequest } from '../../api/wanaku-router-api';

/** Lists credential bindings for the admin UI. */
export const useBindings = () => {
  const listBindings = useCallback((options?: RequestInit) => listBindingsRequest(options), []);

  return { listBindings };
};
