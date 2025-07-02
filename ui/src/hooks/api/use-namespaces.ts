import { useCallback } from "react"
import { getApiV1NamespacesList, getApiV1NamespacesListResponse } from "../../api/wanaku-router-api";

export const useNamespaces = () => {

    const listNamespaces = useCallback(
        (
          options?: RequestInit
        ): Promise<getApiV1NamespacesListResponse> => {
          return getApiV1NamespacesList(options);
        },
        []
    ); 

    return {
        listNamespaces
    };
}