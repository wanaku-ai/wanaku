package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jboss.logging.Logger;
import ai.wanaku.backend.api.v1.exceptions.ServiceTemplateNotFoundException;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
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
        Map<String, String> fileContents = extractFileContents(zipBytes);

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
        Collection<String> forageGavs = resolveForageDependencies(effectiveProperties);

        // Build a new ZIP with modified properties files
        byte[] newZipBytes =
                buildCatalogZip(originalZip, index, effectiveProperties, effectiveName, serviceSystem, forageGavs);

        // Create a new DataStore for the catalog
        DataStore catalog = new DataStore();
        catalog.setName(effectiveName);
        catalog.setData(Base64.getEncoder().encodeToString(newZipBytes));

        // Deploy via ServiceCatalogBean
        return serviceCatalogBean.deploy(catalog);
    }

    private byte[] buildCatalogZip(
            byte[] templateZip,
            ServiceCatalogIndex index,
            Map<String, String> userProperties,
            String catalogName,
            String serviceSystem,
            Collection<String> forageGavs)
            throws WanakuException {
        try {
            Map<String, byte[]> entries = readZipEntries(templateZip);
            modifyIndexProperties(entries, index, catalogName, serviceSystem);
            applyUserProperties(entries, index, userProperties);
            appendForageDependencies(entries, index, forageGavs, serviceSystem);
            return assembleZip(entries);
        } catch (IOException e) {
            throw new WanakuException("Failed to build catalog ZIP: %s".formatted(e.getMessage()));
        }
    }

    private Map<String, byte[]> readZipEntries(byte[] templateZip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(templateZip))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > SafeZip.MAX_ENTRIES) {
                    throw new IOException("Template ZIP has too many entries (max %d)".formatted(SafeZip.MAX_ENTRIES));
                }
                byte[] content = SafeZip.readEntry(zis, SafeZip.MAX_ENTRY_BYTES);
                totalBytes += content.length;
                if (totalBytes > SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw new IOException("Template ZIP uncompressed size exceeds the maximum of %d bytes"
                            .formatted(SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES));
                }
                entries.put(entry.getName(), content);
                zis.closeEntry();
            }
        }
        return entries;
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

    private void appendForageDependencies(
            Map<String, byte[]> entries, ServiceCatalogIndex index, Collection<String> forageGavs, String serviceSystem)
            throws IOException {
        if (forageGavs == null || forageGavs.isEmpty()) {
            return;
        }

        for (String system : index.getServiceNames()) {
            String depsPath = index.getDependenciesFile(system);

            if (depsPath != null && entries.containsKey(depsPath)) {
                String existing = new String(entries.get(depsPath));
                Set<String> existingGavs = parseDependenciesFile(existing);
                Set<String> merged = new LinkedHashSet<>(existingGavs);

                for (String gav : forageGavs) {
                    if (!merged.contains(gav)) {
                        merged.add(gav);
                    }
                }

                StringBuilder sb = new StringBuilder();
                for (String gav : merged) {
                    sb.append(gav).append('\n');
                }
                entries.put(depsPath, sb.toString().getBytes());

                if (serviceSystem != null && !serviceSystem.isBlank()) {
                    String remappedPath = depsPath.replace(system + "/", serviceSystem + "/");
                    if (!remappedPath.equals(depsPath) && !entries.containsKey(remappedPath)) {
                        entries.put(remappedPath, entries.get(depsPath));
                    }
                }
            } else {
                String effectiveSystem = (serviceSystem != null && !serviceSystem.isBlank()) ? serviceSystem : system;
                String newDepsPath = effectiveSystem + "/" + effectiveSystem + ".dependencies.txt";

                StringBuilder sb = new StringBuilder();
                for (String gav : forageGavs) {
                    sb.append(gav).append('\n');
                }
                entries.put(newDepsPath, sb.toString().getBytes());

                byte[] indexBytes = entries.get("index.properties");
                if (indexBytes != null) {
                    Properties indexProps = new Properties();
                    indexProps.load(new StringReader(new String(indexBytes)));
                    indexProps.setProperty("catalog.dependencies." + effectiveSystem, newDepsPath);
                    StringWriter indexWriter = new StringWriter();
                    indexProps.store(indexWriter, null);
                    entries.put("index.properties", indexWriter.toString().getBytes());
                }
            }
        }
    }

    private static Set<String> parseDependenciesFile(String content) {
        Set<String> gavs = new LinkedHashSet<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                gavs.add(trimmed);
            }
        }
        return gavs;
    }

    private byte[] assembleZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(e.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static void remapSystemProperty(Properties props, String prefix, String oldSystem, String newSystem) {
        String oldKey = prefix + oldSystem;
        String value = props.getProperty(oldKey);
        if (value != null) {
            props.remove(oldKey);
            props.setProperty(prefix + newSystem, value);
        }
    }

    private Map<String, String> extractFileContents(byte[] zipBytes) throws WanakuException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > SafeZip.MAX_ENTRIES) {
                    throw new IOException("Template ZIP has too many entries (max %d)".formatted(SafeZip.MAX_ENTRIES));
                }
                if (!entry.isDirectory()) {
                    byte[] content = SafeZip.readEntry(zis, SafeZip.MAX_ENTRY_BYTES);
                    totalBytes += content.length;
                    if (totalBytes > SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw new IOException("Template ZIP uncompressed size exceeds the maximum of %d bytes"
                                .formatted(SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES));
                    }
                    result.put(entry.getName(), new String(content));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new WanakuException("Failed to extract ZIP contents: %s".formatted(e.getMessage()));
        }
        return result;
    }

    private static <V> String resolvePropertiesPath(ServiceCatalogIndex index, String system, Map<String, V> entries) {
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
