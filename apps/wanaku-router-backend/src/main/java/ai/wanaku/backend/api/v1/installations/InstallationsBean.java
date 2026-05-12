package ai.wanaku.backend.api.v1.installations;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import ai.wanaku.backend.api.v1.datastores.DataStoresBean;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bean for managing capability installations.
 *
 * <p>Installations are stored as {@link DataStore} entries with the label
 * {@code wanaku.type=installation}. This bean delegates persistence to
 * {@link DataStoresBean} and process lifecycle to {@link ProcessManager}.
 */
@ApplicationScoped
public class InstallationsBean {
    private static final Logger LOG = Logger.getLogger(InstallationsBean.class);

    private static final String LABEL_TYPE_KEY = "wanaku.type";
    private static final String LABEL_TYPE_VALUE = "installation";
    private static final String LABEL_INSTALLATION_TYPE_KEY = "wanaku.installation.type";
    private static final String LABEL_FILTER = LABEL_TYPE_KEY + "=" + LABEL_TYPE_VALUE;

    private static final Set<String> VALID_TYPES = Set.of("native", "cic", "code-execution");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    DataStoresBean dataStoresBean;

    @Inject
    ProcessManager processManager;

    /**
     * Lists all installation entries.
     *
     * @return list of DataStore entries with the installation label
     */
    public List<DataStore> list() {
        LOG.debug("Listing all installations");
        return dataStoresBean.list(LABEL_FILTER);
    }

    /**
     * Creates a new installation entry.
     *
     * <p>Validates the JSON data field and sets the required labels before
     * persisting via the data stores bean.
     *
     * @param dataStore the installation to create
     * @return the persisted DataStore with generated ID
     * @throws WanakuException if validation fails
     */
    public DataStore create(DataStore dataStore) {
        LOG.debugf("Creating installation: %s", dataStore.getName());
        String type = validateAndExtractType(dataStore.getData());

        dataStore.addLabel(LABEL_TYPE_KEY, LABEL_TYPE_VALUE);
        dataStore.addLabel(LABEL_INSTALLATION_TYPE_KEY, type);

        return dataStoresBean.add(dataStore);
    }

    /**
     * Updates an existing installation entry.
     *
     * <p>Validates the JSON data field and ensures installation labels are preserved.
     *
     * @param dataStore the installation to update (must include ID)
     * @throws WanakuException if validation fails or the entry does not exist
     */
    public void update(DataStore dataStore) {
        LOG.debugf("Updating installation: %s", dataStore.getId());
        String type = validateAndExtractType(dataStore.getData());

        dataStore.addLabel(LABEL_TYPE_KEY, LABEL_TYPE_VALUE);
        dataStore.addLabel(LABEL_INSTALLATION_TYPE_KEY, type);

        dataStoresBean.update(dataStore);
    }

    /**
     * Deletes an installation entry. If the process is currently running, it is stopped first.
     *
     * @param id the ID of the installation to delete
     * @throws WanakuException if the installation cannot be found or deleted
     */
    public void delete(String id) {
        LOG.debugf("Deleting installation: %s", id);

        if (processManager.isRunning(id)) {
            LOG.infof("Stopping running process before deleting installation: %s", id);
            processManager.stop(id);
        }

        dataStoresBean.removeById(id);
    }

    /**
     * Launches the capability process for an installation.
     *
     * @param id the installation ID
     * @return the process status after launch
     * @throws WanakuException if the installation is not found or cannot be launched
     */
    public ProcessStatus launch(String id) {
        LOG.infof("Launching installation: %s", id);
        DataStore dataStore = findByIdOrThrow(id);
        return processManager.launch(id, dataStore.getName(), dataStore.getData());
    }

    /**
     * Stops the capability process for an installation.
     *
     * @param id the installation ID
     * @throws WanakuException if the installation process is not running
     */
    public void stop(String id) {
        LOG.infof("Stopping installation: %s", id);
        processManager.stop(id);
    }

    /**
     * Returns the process status for an installation.
     *
     * @param id the installation ID
     * @return the current process status
     */
    public ProcessStatus getStatus(String id) {
        LOG.debugf("Getting status for installation: %s", id);
        return processManager.getStatus(id);
    }

    /**
     * Validates the JSON data field of an installation and extracts the type.
     *
     * @param data the JSON configuration string
     * @return the validated installation type
     * @throws WanakuException if validation fails
     */
    private String validateAndExtractType(String data) {
        if (data == null || data.isBlank()) {
            throw new WanakuException("Installation data must not be empty");
        }

        JsonNode config;
        try {
            config = objectMapper.readTree(data);
        } catch (Exception e) {
            throw new WanakuException("Installation data must be valid JSON: " + e.getMessage());
        }

        String type = getTextValue(config, "type");
        if (type == null || type.isBlank()) {
            throw new WanakuException("Installation data must contain a 'type' field");
        }
        if (!VALID_TYPES.contains(type)) {
            throw new WanakuException("Invalid installation type: " + type + ". Must be one of: " + VALID_TYPES);
        }

        String executablePath = getTextValue(config, "executablePath");
        if (executablePath == null || executablePath.isBlank()) {
            throw new WanakuException("Installation data must contain a non-empty 'executablePath' field");
        }

        if ("cic".equals(type)) {
            requireField(config, "serviceCatalog", "cic");
            requireField(config, "serviceCatalogSystem", "cic");
        }

        if ("code-execution".equals(type)) {
            requireField(config, "engineType", "code-execution");
            requireField(config, "language", "code-execution");
        }

        return type;
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asText();
    }

    private void requireField(JsonNode config, String fieldName, String typeName) {
        String value = getTextValue(config, fieldName);
        if (value == null || value.isBlank()) {
            throw new WanakuException("Installation type '" + typeName + "' requires the '" + fieldName + "' field");
        }
    }

    private DataStore findByIdOrThrow(String id) {
        DataStore dataStore = dataStoresBean.findById(id);
        if (dataStore == null) {
            throw new WanakuException("Installation not found with ID: " + id);
        }
        return dataStore;
    }
}
