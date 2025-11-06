import { useCallback } from "react";
import {
  getApiV1DataStoreList,
  postApiV1DataStoreAdd,
  deleteApiV1DataStoreRemove,
} from "../../api/wanaku-router-api";
import type { DataStore, DeleteApiV1DataStoreRemoveParams } from "../../models";

/**
 * Custom hook for DataStore API operations
 */
export const useDataStores = () => {
  const listDataStores = useCallback((options?: RequestInit) => {
    return getApiV1DataStoreList(options);
  }, []);

  const addDataStore = useCallback(
    (dataStore: DataStore, options?: RequestInit) => {
      return postApiV1DataStoreAdd(dataStore, options);
    },
    []
  );

  const deleteDataStore = useCallback(
    (params?: DeleteApiV1DataStoreRemoveParams, options?: RequestInit) => {
      return deleteApiV1DataStoreRemove(params, options);
    },
    []
  );

  return {
    listDataStores,
    addDataStore,
    deleteDataStore,
  };
};
