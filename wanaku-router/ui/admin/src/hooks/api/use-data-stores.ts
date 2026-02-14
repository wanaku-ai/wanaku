import {useCallback} from "react";
import {deleteApiV1DataStoreRemove, getApiV1DataStoreList, postApiV1DataStoreAdd,} from "../../api/wanaku-router-api";
import type {DataStore, DeleteApiV1DataStoreRemoveParams, GetApiV1DataStoreListParams,} from "../../models";

/**
 * Custom hook for DataStore API operations
 */
export const useDataStores = () => {
  const listDataStores = useCallback(
    (params?: GetApiV1DataStoreListParams, options?: RequestInit) => {
      return getApiV1DataStoreList(params, options);
    },
    []
  );

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
