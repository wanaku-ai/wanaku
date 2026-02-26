package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

/**
 * Business logic for service catalog operations.
 * <p>
 * Catalog entries are stored as DataStore entities with label {@code wanaku.type=catalog}.
 * Each entry contains a Base64-encoded ZIP package with an {@code index.properties} manifest.
 * </p>
 */
@ApplicationScoped
public class ServiceCatalogBean {
    private static final Logger LOG = Logger.getLogger(ServiceCatalogBean.class);

    /** Label key used to identify service catalog entries in the data store. */
    public static final String LABEL_TYPE_KEY = "wanaku.type";

    /** Label value used to identify service catalog entries. */
    public static final String LABEL_TYPE_VALUE = "catalog";

    @Inject
    Instance<DataStoreRepository> dataStoreRepositoryInstance;

    private DataStoreRepository dataStoreRepository;

    @PostConstruct
    void init() {
        dataStoreRepository = dataStoreRepositoryInstance.get();
    }

    /**
     * List all service catalog entries, optionally filtered by search term.
     *
     * @param search optional search term to filter by catalog name or description
     * @return list of catalog data store entries
     */
    public List<DataStore> list(String search) {
        LOG.debug("Listing service catalogs");
        List<DataStore> all =
                dataStoreRepository.findAllFilterByLabelExpression(LABEL_TYPE_KEY + "=" + LABEL_TYPE_VALUE);

        if (search == null || search.isBlank()) {
            return all;
        }

        String lowerSearch = search.toLowerCase();
        return all.stream().filter(ds -> matchesSearch(ds, lowerSearch)).collect(Collectors.toList());
    }

    /**
     * Get a specific service catalog by its catalog index name.
     * Searches all catalog entries and matches against the name stored in the ZIP's index.properties.
     *
     * @param name the catalog name (from index.properties, not the DataStore name)
     * @return the data store entry, or null if not found
     */
    public DataStore get(String name) {
        LOG.debugf("Getting service catalog: %s", name);
        List<DataStore> catalogs = list(null);

        for (DataStore ds : catalogs) {
            try {
                ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(ds.getData());
                if (name.equals(index.getName())) {
                    return ds;
                }
            } catch (WanakuException e) {
                LOG.debugf("Failed to parse catalog index for '%s': %s", ds.getName(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * Deploy a service catalog ZIP package.
     * Validates the ZIP structure, then stores it as a DataStore entry with catalog labels.
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return the persisted data store entry
     * @throws WanakuException if validation fails
     */
    public DataStore deploy(DataStore dataStore) throws WanakuException {
        LOG.debugf("Deploying service catalog: %s", dataStore.getName());

        if (dataStore.getName() == null || dataStore.getName().isBlank()) {
            throw new WanakuException("Catalog name is required");
        }
        if (dataStore.getData() == null || dataStore.getData().isBlank()) {
            throw new WanakuException("Catalog data (Base64-encoded ZIP) is required");
        }

        // Validate ZIP structure by parsing the index
        ServiceCatalogIndex.fromBase64(dataStore.getData());

        // Set catalog label
        Map<String, String> labels = dataStore.getLabels();
        if (labels == null) {
            labels = new HashMap<>();
        } else {
            labels = new HashMap<>(labels);
        }
        labels.put(LABEL_TYPE_KEY, LABEL_TYPE_VALUE);
        dataStore.setLabels(labels);

        // Check for existing catalog with same name and remove it
        DataStore existing = get(dataStore.getName());
        if (existing != null) {
            LOG.debugf("Replacing existing catalog: %s", dataStore.getName());
            dataStoreRepository.deleteById(existing.getId());
        }

        return dataStoreRepository.persist(dataStore);
    }

    /**
     * Remove a service catalog by name.
     *
     * @param name the catalog name to remove
     * @return the number of entries removed
     */
    public int remove(String name) {
        LOG.debugf("Removing service catalog: %s", name);
        DataStore catalog = get(name);
        if (catalog == null) {
            return 0;
        }
        boolean removed = dataStoreRepository.deleteById(catalog.getId());
        return removed ? 1 : 0;
    }

    /**
     * Parse the catalog index from a data store entry.
     *
     * @param dataStore the data store entry containing the ZIP
     * @return the parsed index
     * @throws WanakuException if parsing fails
     */
    public ServiceCatalogIndex parseIndex(DataStore dataStore) throws WanakuException {
        return ServiceCatalogIndex.fromBase64(dataStore.getData());
    }

    private boolean matchesSearch(DataStore ds, String lowerSearch) {
        if (ds.getName() != null && ds.getName().toLowerCase().contains(lowerSearch)) {
            return true;
        }
        // Try to parse the index for description matching
        try {
            ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(ds.getData());
            if (index.getDescription() != null
                    && index.getDescription().toLowerCase().contains(lowerSearch)) {
                return true;
            }
        } catch (WanakuException e) {
            LOG.debugf("Failed to parse catalog index for search: %s", e.getMessage());
        }
        return false;
    }

    private boolean isCatalog(DataStore ds) {
        Map<String, String> labels = ds.getLabels();
        return labels != null && LABEL_TYPE_VALUE.equals(labels.get(LABEL_TYPE_KEY));
    }
}
