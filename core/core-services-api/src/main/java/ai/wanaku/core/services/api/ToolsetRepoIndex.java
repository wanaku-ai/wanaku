package ai.wanaku.core.services.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Parses a toolset repository's {@code index.properties} file.
 * <p>
 * Expected properties:
 * <ul>
 *   <li>{@code wanaku.toolsets} - comma-separated list of toolset names</li>
 *   <li>{@code wanaku.description.<name>} - description for each toolset</li>
 *   <li>{@code wanaku.toolset.icon} - optional icon for the entire collection</li>
 *   <li>{@code wanaku.toolset.<name>.icon} - optional icon per toolset</li>
 * </ul>
 * Toolset names map to files at {@code toolsets/<name>.json} relative to the repository base URL.
 */
public class ToolsetRepoIndex {

    private static final String INDEX_FILE = "index.properties";
    private static final String PROP_TOOLSETS = "wanaku.toolsets";
    private static final String PROP_DESCRIPTION_PREFIX = "wanaku.description.";
    private static final String PROP_COLLECTION_ICON = "wanaku.toolset.icon";
    private static final String PROP_TOOLSET_ICON_SUFFIX = ".icon";
    private static final String PROP_TOOLSET_PREFIX = "wanaku.toolset.";

    private final String icon;
    private final List<ToolsetEntry> entries;

    private ToolsetRepoIndex(String icon, List<ToolsetEntry> entries) {
        this.icon = icon;
        this.entries = Collections.unmodifiableList(entries);
    }

    /**
     * Parse an index from a properties input stream.
     *
     * @param inputStream the input stream containing properties data
     * @return the parsed index
     * @throws WanakuException if required properties are missing
     */
    public static ToolsetRepoIndex fromProperties(InputStream inputStream) throws WanakuException {
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            throw new WanakuException("Failed to read index.properties: " + e.getMessage());
        }
        return parse(props);
    }

    /**
     * Fetch and parse an index from a remote repository base URL.
     *
     * @param baseUrl the base URL of the toolset repository
     * @return the parsed index
     * @throws WanakuException if fetching or parsing fails
     */
    public static ToolsetRepoIndex fromUrl(String baseUrl) throws WanakuException {
        String indexUrl = normalizeBaseUrl(baseUrl) + INDEX_FILE;
        try (InputStream is = URI.create(indexUrl).toURL().openStream()) {
            return fromProperties(is);
        } catch (IOException e) {
            throw new WanakuException("Failed to fetch index.properties from " + indexUrl + ": " + e.getMessage());
        }
    }

    private static ToolsetRepoIndex parse(Properties props) throws WanakuException {
        String toolsetsStr = props.getProperty(PROP_TOOLSETS);
        if (toolsetsStr == null || toolsetsStr.isBlank()) {
            throw new WanakuException("Required property '" + PROP_TOOLSETS + "' is missing or empty in " + INDEX_FILE);
        }

        String collectionIcon = props.getProperty(PROP_COLLECTION_ICON);

        List<ToolsetEntry> entries = new ArrayList<>();
        for (String s : toolsetsStr.split(",")) {
            String name = s.trim();
            if (name.isEmpty()) {
                continue;
            }
            String description = props.getProperty(PROP_DESCRIPTION_PREFIX + name, "");
            String toolsetIcon = props.getProperty(PROP_TOOLSET_PREFIX + name + PROP_TOOLSET_ICON_SUFFIX);
            entries.add(new ToolsetEntry(name, description, toolsetIcon));
        }

        if (entries.isEmpty()) {
            throw new WanakuException(
                    "Property '" + PROP_TOOLSETS + "' must list at least one toolset in " + INDEX_FILE);
        }

        return new ToolsetRepoIndex(collectionIcon, entries);
    }

    /**
     * Build the URL for a specific toolset JSON file within the repository.
     *
     * @param baseUrl the base URL of the toolset repository
     * @param toolsetName the toolset name
     * @return the full URL to the toolset JSON file
     */
    public static String toolsetUrl(String baseUrl, String toolsetName) {
        return normalizeBaseUrl(baseUrl) + "toolsets/" + toolsetName + ".json";
    }

    static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public String getIcon() {
        return icon;
    }

    public List<ToolsetEntry> getEntries() {
        return entries;
    }

    /**
     * A single toolset entry within a repository index.
     */
    public static class ToolsetEntry {
        private final String name;
        private final String description;
        private final String icon;

        public ToolsetEntry(String name, String description, String icon) {
            this.name = name;
            this.description = description;
            this.icon = icon;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }
}
