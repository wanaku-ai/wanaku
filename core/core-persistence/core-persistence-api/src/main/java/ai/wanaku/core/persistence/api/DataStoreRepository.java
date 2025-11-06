package ai.wanaku.core.persistence.api;

import ai.wanaku.api.types.DataStore;
import java.util.List;

/**
 * Repository interface for DataStore entity operations.
 */
public interface DataStoreRepository extends WanakuRepository<DataStore, String> {
    /**
     * Find all data stores with the given name.
     *
     * @param name the name to search for
     * @return list of matching data stores
     */
    List<DataStore> findByName(String name);
}
