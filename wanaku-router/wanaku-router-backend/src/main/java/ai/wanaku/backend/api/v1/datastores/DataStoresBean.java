package ai.wanaku.backend.api.v1.datastores;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.DataStore;
import ai.wanaku.backend.common.AbstractBean;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import ai.wanaku.core.persistence.api.WanakuRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Bean for managing DataStore entities.
 */
@ApplicationScoped
public class DataStoresBean extends AbstractBean<DataStore> {
    private static final Logger LOG = Logger.getLogger(DataStoresBean.class);

    @Inject
    Instance<DataStoreRepository> dataStoreRepositoryInstance;

    private DataStoreRepository dataStoreRepository;

    @PostConstruct
    void init() {
        dataStoreRepository = dataStoreRepositoryInstance.get();
    }

    /**
     * Add a new data store entry.
     *
     * @param dataStore the data store to add
     * @return the persisted data store with generated ID
     */
    public DataStore add(DataStore dataStore) {
        LOG.debugf("Adding data store: %s", dataStore);
        return dataStoreRepository.persist(dataStore);
    }

    /**
     * Update an existing data store entry.
     *
     * @param dataStore the data store to update
     * @throws WanakuException if the data store doesn't exist
     */
    public void update(DataStore dataStore) throws WanakuException {
        LOG.debugf("Updating data store: %s", dataStore);
        if (dataStore.getId() == null) {
            throw new WanakuException("Cannot update data store without ID");
        }
        DataStore existing = dataStoreRepository.findById(dataStore.getId());
        if (existing == null) {
            throw new WanakuException("Data store not found with ID: " + dataStore.getId());
        }
        dataStoreRepository.update(dataStore.getId(), dataStore);
    }

    /**
     * List all data stores.
     *
     * @return list of all data stores
     */
    public List<DataStore> list() {
        LOG.debug("Listing all data stores");
        return dataStoreRepository.listAll();
    }

    /**
     * Find a data store by ID.
     *
     * @param id the ID to search for
     * @return the data store or null if not found
     */
    public DataStore findById(String id) {
        LOG.debugf("Finding data store by ID: %s", id);
        return dataStoreRepository.findById(id);
    }

    /**
     * Find data stores by name.
     *
     * @param name the name to search for
     * @return list of matching data stores
     */
    public List<DataStore> findByName(String name) {
        LOG.debugf("Finding data stores by name: %s", name);
        return dataStoreRepository.findByName(name);
    }

    /**
     * Remove a data store by ID.
     *
     * @param id the ID of the data store to remove
     * @return the number of entries removed
     */
    public int removeById(String id) {
        LOG.debugf("Removing data store by ID: %s", id);
        boolean removed = dataStoreRepository.deleteById(id);
        return removed ? 1 : 0;
    }

    /**
     * Remove data stores by name.
     *
     * @param name the name of the data stores to remove
     * @return the number of entries removed
     */
    public int remove(String name) {
        LOG.debugf("Removing data stores by name: %s", name);
        return removeByName(name);
    }

    @Override
    protected WanakuRepository<DataStore, String> getRepository() {
        return dataStoreRepository;
    }
}
