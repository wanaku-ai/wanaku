package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import ai.wanaku.backend.api.v1.exceptions.ServiceTemplateNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import ai.wanaku.core.services.api.SafeZip;
import ai.wanaku.core.services.api.ServiceCatalogIndex;
import ai.wanaku.core.util.StringHelper;

/**
 * Business logic for service template operations.
 * <p>
 * Template entries are stored as DataStore entities with label {@code wanaku.type=template}.
 * Each entry contains a Base64-encoded ZIP package with an {@code index.properties} manifest
 * and optional {@code service.properties} files in system directories.
 * </p>
 */
@ApplicationScoped
public class ServiceTemplateBean {
    private static final Logger LOG = Logger.getLogger(ServiceTemplateBean.class);

    /** Label key used to identify service template entries in the data store. */
    public static final String LABEL_TYPE_KEY = "wanaku.type";

    /** Label value used to identify service template entries. */
    public static final String LABEL_TYPE_VALUE = "template";

    /**
     * Allowed characters for the optional service name / system identifiers that callers may supply
     * when instantiating a template. These values are used to build catalog entry names, so they are
     * restricted to a conservative identifier charset.
     */
    private static final Pattern SYSTEM_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    @Inject
    Instance<DataStoreRepository> dataStoreRepositoryInstance;

    @Inject
    ServiceCatalogBean serviceCatalogBean;

    @Inject
    ForageDependencyResolver forageDependencyResolver;

    private DataStoreRepository dataStoreRepository;

    @PostConstruct
    void init() {
        dataStoreRepository = dataStoreRepositoryInstance.get();
    }

    /**
     * List all service template entries, optionally filtered by search term.
     *
     * @param search optional search term to filter by template name or description
     * @return list of template data store entries
     */
    public List<DataStore> list(String search) {
        LOG.debug("Listing service templates");
        List<DataStore> all =
                dataStoreRepository.findAllFilterByLabelExpression(LABEL_TYPE_KEY + "=" + LABEL_TYPE_VALUE);

        if (StringHelper.isBlank(search)) {
            return all;
        }

        String lowerSearch = search.toLowerCase();
        return all.stream().filter(ds -> matchesSearch(ds, lowerSearch)).collect(Collectors.toList());
    }

    /**
     * Get a specific service template by its catalog index name.
     * Searches all template entries and matches against the name stored in the ZIP's index.properties.
     *
     * @param name the template name (from index.properties, not the DataStore name)
     * @return the data store entry, or null if not found
     */
    public DataStore get(String name) {
        LOG.debugf("Getting service template: %s", name);
        List<DataStore> templates = list(null);

        for (DataStore ds : templates) {
            try {
                ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(ds.getData());
                if (name.equals(index.getName())) {
                    return ds;
                }
            } catch (WanakuException e) {
                LOG.debugf("Failed to parse template index for '%s': %s", ds.getName(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * Deploy a service template ZIP package.
     * Validates the ZIP structure, then stores it as a DataStore entry with template labels.
     *
     * @param dataStore the data store entry containing the Base64-encoded ZIP
     * @return the persisted data store entry
     * @throws WanakuException if validation fails
     */
    public DataStore deploy(DataStore dataStore) throws WanakuException {
        LOG.debugf("Deploying service template: %s", dataStore.getName());

        if (StringHelper.isBlank(dataStore.getName())) {
            throw new WanakuException("Template name is required");
        }
        if (StringHelper.isBlank(dataStore.getData())) {
            throw new WanakuException("Template data (Base64-encoded ZIP) is required");
        }

        // Validate ZIP structure by parsing the index
        ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(dataStore.getData());

        // Set template label
        Map<String, String> labels = dataStore.getLabels();
        if (labels == null) {
            labels = new HashMap<>();
        } else {
            labels = new HashMap<>(labels);
        }
        labels.put(LABEL_TYPE_KEY, LABEL_TYPE_VALUE);
        dataStore.setLabels(labels);

        // Check for existing template with same name and remove it
        DataStore existing = get(index.getName());
        if (existing != null) {
            LOG.debugf("Replacing existing template: %s", dataStore.getName());
            dataStoreRepository.deleteById(existing.getId());
        }

        return dataStoreRepository.persist(dataStore);
    }

    /**
     * Remove a service template by name.
     *
     * @param name the template name to remove
     * @return the number of entries removed
     */
    public int remove(String name) {
        LOG.debugf("Removing service template: %s", name);
        DataStore template = get(name);
        if (template == null) {
            return 0;
        }
        boolean removed = dataStoreRepository.deleteById(template.getId());
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

    /**
     * Get all properties from a service template.
     * Returns a map of system name to property key-value pairs.
     *
     * @param name the template name
     * @return map of system → property key → value
     * @throws WanakuException if template not found or parsing fails
     */
    public Map<String, Map<String, String>> getProperties(String name) throws WanakuException {
        LOG.debugf("Getting properties for template: %s", name);

        DataStore template = get(name);
        if (template == null) {
            throw new ServiceTemplateNotFoundException("Service template not found: %s".formatted(name));
        }

        ServiceCatalogIndex index = parseIndex(template);
        Map<String, Map<String, String>> result = new HashMap<>();

        byte[] zipBytes = SafeZip.decodeArchive(template.getData());
        Map<String, String> fileContents = CatalogZipReader.readEntriesAsText(zipBytes);

        for (String system : index.getServiceNames()) {
            String propertiesPath = resolvePropertiesPath(index, system, fileContents);
            if (propertiesPath != null) {
                String content = fileContents.get(propertiesPath);
                if (content != null) {
                    Properties props = new Properties();
                    try {
                        props.load(new StringReader(content));
                        Map<String, String> systemProps = new HashMap<>();
                        for (String key : props.stringPropertyNames()) {
                            systemProps.put(key, props.getProperty(key));
                        }
                        result.put(system, systemProps);
                    } catch (IOException e) {
                        LOG.warnf("Failed to parse properties file for system '%s': %s", system, e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Instantiate a service template by filling in property values.
     * Creates a new service catalog from the template.
     *
     * @param templateName the template name
     * @param userProperties flat map of property key → value (no system namespacing)
     * @return the newly created service catalog DataStore
     * @throws WanakuException if instantiation fails
     */
    public DataStore instantiate(String templateName, Map<String, String> userProperties) throws WanakuException {
        return instantiate(templateName, userProperties, null, null);
    }

    /**
     * Instantiate a service template by filling in property values, with optional overrides
     * for the service name and system identifier.
     *
     * @param templateName the template name
     * @param userProperties flat map of property key → value (no system namespacing)
     * @param serviceName optional override for the catalog name (null to use template default)
     * @param serviceSystem optional override for the system identifier (null to use template default)
     * @return the newly created service catalog DataStore
     * @throws WanakuException if instantiation fails
     */
    public DataStore instantiate(
            String templateName, Map<String, String> userProperties, String serviceName, String serviceSystem)
            throws WanakuException {
        validateOptionalIdentifier(serviceName, "serviceName");
        validateOptionalIdentifier(serviceSystem, "serviceSystem");
        LOG.debugf(
                "Instantiating template '%s' with %d properties",
                templateName, userProperties != null ? userProperties.size() : 0);

        DataStore template = get(templateName);
        if (template == null) {
            throw new ServiceTemplateNotFoundException("Service template not found: %s".formatted(templateName));
        }

        ServiceCatalogIndex index = parseIndex(template);
        byte[] originalZip = SafeZip.decodeArchive(template.getData());

        String effectiveName = (serviceName != null && !serviceName.isBlank()) ? serviceName : index.getName();

        Map<String, String> effectiveProperties = userProperties != null ? userProperties : Map.of();

        Map<String, byte[]> entries = CatalogZipReader.readEntries(originalZip);
        Map<String, String> textEntries = CatalogZipReader.toTextView(entries);

        Map<String, String> mergedProperties = TemplatePropertyMerger.merge(textEntries, index, effectiveProperties);
        Collection<String> forageGavs = resolveForageDependencies(mergedProperties);

        try {
            modifyIndexProperties(entries, index, effectiveName, serviceSystem);
            applyUserProperties(entries, index, effectiveProperties);
            ForageDependencyAppender.append(entries, index, forageGavs, serviceSystem);

            DataStore catalog = new DataStore();
            catalog.setName(effectiveName);
            catalog.setData(Base64.getEncoder().encodeToString(CatalogZipWriter.assemble(entries)));
            return serviceCatalogBean.deploy(catalog);
        } catch (IOException e) {
            throw new WanakuException("Failed to build catalog ZIP: %s".formatted(e.getMessage()));
        }
    }

    private static void validateOptionalIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.contains("..") || !SYSTEM_IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new WanakuException(
                    "Invalid %s '%s': only letters, digits, '.', '_' and '-' are allowed (and no '..')"
                            .formatted(fieldName, value));
        }
    }

    private void modifyIndexProperties(
            Map<String, byte[]> entries, ServiceCatalogIndex index, String catalogName, String serviceSystem)
            throws IOException, WanakuException {
        byte[] indexBytes = entries.get("index.properties");
        if (indexBytes == null) {
            throw new WanakuException("index.properties not found in template ZIP");
        }

        Properties indexProps = new Properties();
        indexProps.load(new StringReader(new String(indexBytes)));

        for (String system : index.getServiceNames()) {
            indexProps.remove("catalog.properties." + system);
        }

        if (catalogName != null && !catalogName.isBlank()) {
            indexProps.setProperty("catalog.name", catalogName);
        }

        if (serviceSystem != null && !serviceSystem.isBlank()) {
            for (String oldSystem : index.getServiceNames()) {
                remapSystemProperty(indexProps, "catalog.routes.", oldSystem, serviceSystem);
                remapSystemProperty(indexProps, "catalog.rules.", oldSystem, serviceSystem);
                remapSystemProperty(indexProps, "catalog.dependencies.", oldSystem, serviceSystem);
            }
            indexProps.setProperty("catalog.services", serviceSystem);
        }

        StringWriter indexWriter = new StringWriter();
        indexProps.store(indexWriter, null);
        entries.put("index.properties", indexWriter.toString().getBytes());
    }

    private void applyUserProperties(
            Map<String, byte[]> entries, ServiceCatalogIndex index, Map<String, String> userProperties)
            throws IOException {
        for (String system : index.getServiceNames()) {
            String propertiesPath = resolvePropertiesPath(index, system, entries);
            if (propertiesPath != null) {
                byte[] propsBytes = entries.get(propertiesPath);
                Properties props = new Properties();
                props.load(new StringReader(new String(propsBytes)));

                for (Map.Entry<String, String> userEntry : userProperties.entrySet()) {
                    if (props.containsKey(userEntry.getKey())) {
                        props.setProperty(userEntry.getKey(), userEntry.getValue());
                    }
                }

                StringWriter propsWriter = new StringWriter();
                props.store(propsWriter, null);
                entries.put(propertiesPath, propsWriter.toString().getBytes());
            }
        }
    }

    private Collection<String> resolveForageDependencies(Map<String, String> userProperties) {
        Set<String> gavs = new LinkedHashSet<>();
        userProperties.forEach((key, value) -> {
            if (key.startsWith("forage.") && key.endsWith(".kind") && value != null && !value.isBlank()) {
                LOG.debugf("Resolving Forage dependencies for bean kind '%s' (property '%s')", value, key);
                Collection<String> beanGavs = forageDependencyResolver.resolveGavs(value);
                gavs.addAll(beanGavs);
            }
        });
        return gavs;
    }

    private static void remapSystemProperty(Properties props, String prefix, String oldSystem, String newSystem) {
        String oldKey = prefix + oldSystem;
        String value = props.getProperty(oldKey);
        if (value != null) {
            props.remove(oldKey);
            props.setProperty(prefix + newSystem, value);
        }
    }

    static <V> String resolvePropertiesPath(ServiceCatalogIndex index, String system, Map<String, V> entries) {
        String declared = index.getPropertiesFile(system);
        if (declared != null && entries.containsKey(declared)) {
            return declared;
        }

        String conventional = system + "/service.properties";
        if (entries.containsKey(conventional)) {
            return conventional;
        }

        return null;
    }

    private boolean matchesSearch(DataStore ds, String lowerSearch) {
        if (ds.getName() != null && ds.getName().toLowerCase().contains(lowerSearch)) {
            return true;
        }
        try {
            ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(ds.getData());
            if (index.getDescription() != null
                    && index.getDescription().toLowerCase().contains(lowerSearch)) {
                return true;
            }
        } catch (WanakuException e) {
            LOG.debugf("Failed to parse template index for search: %s", e.getMessage());
        }
        return false;
    }
}
