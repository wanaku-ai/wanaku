package ai.wanaku.backend.api.v1.toolsetrepos;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.services.api.ToolsetRepoIndex;
import ai.wanaku.core.util.ToolsetIndexHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Business logic for toolset repository management.
 * <p>
 * Repositories are stored as DataStore entities with label {@code wanaku.type=toolset-repo}.
 * The {@code data} field holds a JSON object with repository metadata (url, description, icon).
 * </p>
 */
@ApplicationScoped
public class ToolsetReposBean {
    private static final Logger LOG = Logger.getLogger(ToolsetReposBean.class);

    static final String LABEL_TYPE_KEY = "wanaku.type";
    static final String LABEL_TYPE_VALUE = "toolset-repo";

    static final String DEFAULT_BRANCH = "main";
    static final String DEFAULT_REPO_NAME = "wanaku-toolsets";
    static final String DEFAULT_REPO_URL = "https://github.com/wanaku-ai/wanaku-toolsets";
    static final String DEFAULT_REPO_DESCRIPTION = "Official Wanaku toolset repository";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    Instance<DataStoreRepository> dataStoreRepositoryInstance;

    private DataStoreRepository dataStoreRepository;

    @PostConstruct
    void init() {
        dataStoreRepository = dataStoreRepositoryInstance.get();
        registerDefaultRepo();
    }

    private void registerDefaultRepo() {
        try {
            if (findByName(DEFAULT_REPO_NAME) == null) {
                LOG.info("Registering default toolset repository: " + DEFAULT_REPO_NAME);
                add(DEFAULT_REPO_NAME, DEFAULT_REPO_URL, DEFAULT_REPO_DESCRIPTION, null, DEFAULT_BRANCH);
            }
        } catch (Exception e) {
            LOG.warnf("Failed to register default toolset repository: %s", e.getMessage());
        }
    }

    /**
     * List all registered toolset repositories.
     *
     * @return list of repository summaries
     */
    public List<Map<String, String>> list() {
        LOG.debug("Listing toolset repositories");
        List<DataStore> all =
                dataStoreRepository.findAllFilterByLabelExpression(LABEL_TYPE_KEY + "=" + LABEL_TYPE_VALUE);

        return all.stream().map(this::toSummary).collect(Collectors.toList());
    }

    /**
     * Add a new toolset repository.
     *
     * @param name the repository name
     * @param url the repository base URL
     * @param description optional description
     * @param icon optional icon
     * @return summary of the created repository
     * @throws WanakuException if registration fails
     */
    public Map<String, String> add(String name, String url, String description, String icon, String branch)
            throws WanakuException {
        if (name == null || name.isBlank()) {
            throw new WanakuException("Repository name is required");
        }
        if (url == null || url.isBlank()) {
            throw new WanakuException("Repository URL is required");
        }

        DataStore existing = findByName(name);
        if (existing != null) {
            throw new WanakuException("Toolset repository already exists: " + name);
        }

        String effectiveBranch = (branch != null && !branch.isBlank()) ? branch : DEFAULT_BRANCH;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("url", url);
        metadata.put("branch", effectiveBranch);
        if (description != null && !description.isBlank()) {
            metadata.put("description", description);
        }
        if (icon != null && !icon.isBlank()) {
            metadata.put("icon", icon);
        }

        DataStore ds = new DataStore();
        ds.setName(name);
        try {
            ds.setData(MAPPER.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            throw new WanakuException("Failed to serialize repository metadata: " + e.getMessage());
        }

        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL_TYPE_KEY, LABEL_TYPE_VALUE);
        ds.setLabels(labels);

        DataStore persisted = dataStoreRepository.persist(ds);
        return toSummary(persisted);
    }

    /**
     * Update an existing toolset repository.
     *
     * @param name the repository name to update
     * @param url new URL (or null to keep existing)
     * @param description new description (or null to keep existing)
     * @param icon new icon (or null to keep existing)
     * @param branch new branch (or null to keep existing)
     * @return summary of the updated repository
     * @throws WanakuException if the repository is not found
     */
    public Map<String, String> update(String name, String url, String description, String icon, String branch)
            throws WanakuException {
        DataStore ds = findByName(name);
        if (ds == null) {
            throw new WanakuException("Toolset repository not found: " + name);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> metadata = MAPPER.readValue(ds.getData(), Map.class);
            if (url != null && !url.isBlank()) {
                metadata.put("url", url);
            }
            if (branch != null && !branch.isBlank()) {
                metadata.put("branch", branch);
            }
            if (description != null) {
                metadata.put("description", description);
            }
            if (icon != null) {
                metadata.put("icon", icon);
            }
            ds.setData(MAPPER.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            throw new WanakuException("Failed to serialize repository metadata: " + e.getMessage());
        }

        dataStoreRepository.update(ds.getId(), ds);
        return toSummary(ds);
    }

    /**
     * Remove a toolset repository by name.
     *
     * @param name the repository name
     * @return the number of entries removed
     */
    public int remove(String name) {
        LOG.debugf("Removing toolset repository: %s", name);
        DataStore ds = findByName(name);
        if (ds == null) {
            return 0;
        }
        boolean removed = dataStoreRepository.deleteById(ds.getId());
        return removed ? 1 : 0;
    }

    /**
     * Browse a toolset repository's catalog by fetching its index.properties.
     *
     * @param name the repository name
     * @return parsed catalog with collection icon and toolset entries
     * @throws WanakuException if the repository is not found or fetching fails
     */
    public Map<String, Object> browse(String name) throws WanakuException {
        DataStore ds = findByName(name);
        if (ds == null) {
            throw new WanakuException("Toolset repository not found: " + name);
        }

        String url = resolveBaseUrl(ds);
        ToolsetRepoIndex index = ToolsetRepoIndex.fromUrl(url);

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("url", url);
        result.put("icon", index.getIcon());

        List<Map<String, String>> toolsets = new ArrayList<>();
        for (ToolsetRepoIndex.ToolsetEntry entry : index.getEntries()) {
            Map<String, String> toolset = new HashMap<>();
            toolset.put("name", entry.getName());
            toolset.put("description", entry.getDescription());
            toolset.put("icon", entry.getIcon());
            toolsets.add(toolset);
        }
        result.put("toolsets", toolsets);

        return result;
    }

    /**
     * Fetch a specific toolset's tools from a repository.
     *
     * @param name the repository name
     * @param toolsetName the toolset name
     * @return list of tool references from the toolset
     * @throws WanakuException if fetching fails
     */
    public List<ToolReference> fetchToolset(String name, String toolsetName) throws WanakuException {
        DataStore ds = findByName(name);
        if (ds == null) {
            throw new WanakuException("Toolset repository not found: " + name);
        }

        String baseUrl = resolveBaseUrl(ds);
        String toolsetUrl = ToolsetRepoIndex.toolsetUrl(baseUrl, toolsetName);

        try {
            return ToolsetIndexHelper.loadToolsIndex(URI.create(toolsetUrl).toURL());
        } catch (Exception e) {
            throw new WanakuException(
                    "Failed to fetch toolset '" + toolsetName + "' from " + toolsetUrl + ": " + e.getMessage());
        }
    }

    private DataStore findByName(String name) {
        List<DataStore> repos =
                dataStoreRepository.findAllFilterByLabelExpression(LABEL_TYPE_KEY + "=" + LABEL_TYPE_VALUE);
        return repos.stream()
                .filter(ds -> name.equals(ds.getName()))
                .findFirst()
                .orElse(null);
    }

    private String resolveBaseUrl(DataStore ds) throws WanakuException {
        try {
            Map<String, String> metadata = MAPPER.readValue(ds.getData(), Map.class);
            String url = metadata.get("url");
            if (url == null || url.isBlank()) {
                throw new WanakuException("Repository metadata missing URL for: " + ds.getName());
            }
            String branch = metadata.getOrDefault("branch", DEFAULT_BRANCH);
            return toRawContentUrl(url, branch);
        } catch (JsonProcessingException e) {
            throw new WanakuException("Failed to parse repository metadata: " + e.getMessage());
        }
    }

    static String toRawContentUrl(String url, String branch) {
        if (url.contains("github.com") && !url.contains("raw.githubusercontent.com")) {
            String cleaned = url.replaceFirst("https?://github\\.com/", "");
            cleaned = cleaned.replaceAll("/+$", "");
            return "https://raw.githubusercontent.com/" + cleaned + "/" + branch;
        }
        return url;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toSummary(DataStore ds) {
        Map<String, String> summary = new HashMap<>();
        summary.put("name", ds.getName());
        try {
            Map<String, String> metadata = MAPPER.readValue(ds.getData(), Map.class);
            summary.putAll(metadata);
        } catch (JsonProcessingException e) {
            LOG.warnf("Failed to parse metadata for repo '%s': %s", ds.getName(), e.getMessage());
        }
        return summary;
    }
}
