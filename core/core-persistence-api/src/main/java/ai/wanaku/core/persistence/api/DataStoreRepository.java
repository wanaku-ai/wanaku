package ai.wanaku.core.persistence.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

public interface DataStoreRepository extends LabelAwareRepository<DataStore, String> {

    List<DataStore> findByName(String name);
}
