package ai.wanaku.core.persistence.api;

import ai.wanaku.capabilities.sdk.api.types.DataStore;
import java.util.List;

/**
 * Repository interface for DataStore entity operations.
 * <p>
 * This interface extends {@link LabelAwareInfinispanRepository} to provide persistence operations
 * for data store entries, which represent arbitrary binary or text data that can be stored,
 * retrieved, and filtered by labels.
 */
public interface DataStoreRepository extends LabelAwareInfinispanRepository<DataStore, String> {
    /**
     * Find all data stores with the given name.
     * <p>
     * Multiple data stores may share the same name but will have unique IDs.
     *
     * @param name the name to search for
     * @return list of matching data stores, or an empty list if none found
     */
    List<DataStore> findByName(String name);
}
