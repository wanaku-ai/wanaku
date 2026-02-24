package ai.wanaku.core.services.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Parses and validates a service catalog index from a ZIP archive.
 * <p>
 * The ZIP archive must contain an {@code index.properties} file at the root level
 * with the following required properties:
 * <ul>
 *   <li>{@code catalog.name} - unique service name</li>
 *   <li>{@code catalog.description} - human-readable description</li>
 *   <li>{@code catalog.services} - comma-separated list of system identifiers</li>
 *   <li>{@code catalog.routes.<system>} - relative path to Camel route YAML for each system</li>
 *   <li>{@code catalog.rules.<system>} - relative path to Wanaku rules YAML for each system</li>
 * </ul>
 * Optional properties:
 * <ul>
 *   <li>{@code catalog.icon} - display icon</li>
 *   <li>{@code catalog.dependencies.<system>} - relative path to dependencies file</li>
 * </ul>
 */
public class ServiceCatalogIndex {

    private static final String INDEX_FILE = "index.properties";
    private static final String PROP_NAME = "catalog.name";
    private static final String PROP_ICON = "catalog.icon";
    private static final String PROP_DESCRIPTION = "catalog.description";
    private static final String PROP_SERVICES = "catalog.services";
    private static final String PROP_ROUTES_PREFIX = "catalog.routes.";
    private static final String PROP_RULES_PREFIX = "catalog.rules.";
    private static final String PROP_DEPENDENCIES_PREFIX = "catalog.dependencies.";

    private final String name;
    private final String icon;
    private final String description;
    private final List<String> serviceNames;
    private final Properties properties;

    private ServiceCatalogIndex(
            String name, String icon, String description, List<String> serviceNames, Properties properties) {
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.serviceNames = Collections.unmodifiableList(serviceNames);
        this.properties = properties;
    }

    /**
     * Parse a service catalog index from Base64-encoded ZIP data.
     *
     * @param base64Data the Base64-encoded ZIP archive
     * @return the parsed index
     * @throws WanakuException if the ZIP is invalid or required properties are missing
     */
    public static ServiceCatalogIndex fromBase64(String base64Data) throws WanakuException {
        byte[] zipBytes;
        try {
            zipBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new WanakuException("Invalid Base64 data: " + e.getMessage());
        }
        return fromZipBytes(zipBytes);
    }

    /**
     * Parse a service catalog index from raw ZIP bytes.
     *
     * @param zipBytes the ZIP archive bytes
     * @return the parsed index
     * @throws WanakuException if the ZIP is invalid or required properties are missing
     */
    public static ServiceCatalogIndex fromZipBytes(byte[] zipBytes) throws WanakuException {
        Properties props = null;
        Set<String> zipEntries = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Protect against ZIP path traversal
                validateZipEntryPath(entryName);

                zipEntries.add(entryName);

                if (INDEX_FILE.equals(entryName)) {
                    props = new Properties();
                    props.load(zis);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new WanakuException("Failed to read ZIP archive: " + e.getMessage());
        }

        if (props == null) {
            throw new WanakuException("ZIP archive does not contain " + INDEX_FILE);
        }

        return parseAndValidate(props, zipEntries);
    }

    /**
     * Parse a service catalog index from a properties input stream (for testing).
     *
     * @param inputStream the input stream containing properties data
     * @return the parsed index
     * @throws WanakuException if required properties are missing
     */
    public static ServiceCatalogIndex fromProperties(InputStream inputStream) throws WanakuException {
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            throw new WanakuException("Failed to read properties: " + e.getMessage());
        }
        return parseAndValidate(props, null);
    }

    private static ServiceCatalogIndex parseAndValidate(Properties props, Set<String> zipEntries)
            throws WanakuException {
        String name = requireProperty(props, PROP_NAME);
        String icon = props.getProperty(PROP_ICON);
        String description = requireProperty(props, PROP_DESCRIPTION);
        String servicesStr = requireProperty(props, PROP_SERVICES);

        List<String> serviceNames = new ArrayList<>();
        for (String s : servicesStr.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                serviceNames.add(trimmed);
            }
        }

        if (serviceNames.isEmpty()) {
            throw new WanakuException("Property '" + PROP_SERVICES + "' must list at least one system");
        }

        // Validate each system has routes and rules entries
        for (String system : serviceNames) {
            String routesPath = requireProperty(props, PROP_ROUTES_PREFIX + system);
            String rulesPath = requireProperty(props, PROP_RULES_PREFIX + system);

            validateZipEntryPath(routesPath);
            validateZipEntryPath(rulesPath);

            // If we have ZIP entries, verify referenced files exist
            if (zipEntries != null) {
                if (!zipEntries.contains(routesPath)) {
                    throw new WanakuException("Referenced routes file '" + routesPath + "' for system '" + system
                            + "' not found in ZIP archive");
                }
                if (!zipEntries.contains(rulesPath)) {
                    throw new WanakuException("Referenced rules file '" + rulesPath + "' for system '" + system
                            + "' not found in ZIP archive");
                }
            }

            String depsPath = props.getProperty(PROP_DEPENDENCIES_PREFIX + system);
            if (depsPath != null) {
                validateZipEntryPath(depsPath);
                if (zipEntries != null && !zipEntries.contains(depsPath)) {
                    throw new WanakuException("Referenced dependencies file '" + depsPath + "' for system '" + system
                            + "' not found in ZIP archive");
                }
            }
        }

        return new ServiceCatalogIndex(name, icon, description, serviceNames, props);
    }

    private static String requireProperty(Properties props, String key) throws WanakuException {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new WanakuException("Required property '" + key + "' is missing or empty in " + INDEX_FILE);
        }
        return value.trim();
    }

    /**
     * Validates a ZIP entry path to prevent path traversal attacks.
     *
     * @param path the entry path to validate
     * @throws WanakuException if the path contains traversal sequences or is absolute
     */
    static void validateZipEntryPath(String path) throws WanakuException {
        if (path == null || path.isEmpty()) {
            return;
        }
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new WanakuException("ZIP entry path must not be absolute: " + path);
        }
        if (path.contains("..")) {
            throw new WanakuException("ZIP entry path must not contain '..': " + path);
        }
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getServiceNames() {
        return serviceNames;
    }

    /**
     * Get the routes file path for a given system.
     */
    public String getRoutesFile(String system) {
        return properties.getProperty(PROP_ROUTES_PREFIX + system);
    }

    /**
     * Get the rules file path for a given system.
     */
    public String getRulesFile(String system) {
        return properties.getProperty(PROP_RULES_PREFIX + system);
    }

    /**
     * Get the dependencies file path for a given system, or null if not set.
     */
    public String getDependenciesFile(String system) {
        return properties.getProperty(PROP_DEPENDENCIES_PREFIX + system);
    }
}
