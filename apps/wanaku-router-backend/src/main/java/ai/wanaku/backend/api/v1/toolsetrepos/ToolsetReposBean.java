package ai.wanaku.backend.api.v1.toolsetrepos;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.core.services.api.ToolsetRepoIndex;
import ai.wanaku.core.util.StringHelper;
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
    private static final Pattern TOOLSET_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final int MAX_REDIRECTS = 5;

    static final String LABEL_TYPE_KEY = "wanaku.type";
    static final String LABEL_TYPE_VALUE = "toolset-repo";

    static final String DEFAULT_BRANCH = "main";
    static final String DEFAULT_REPO_NAME = "wanaku-toolsets";
    static final String DEFAULT_REPO_URL = "https://github.com/wanaku-ai/wanaku-toolsets";
    static final String DEFAULT_REPO_DESCRIPTION = "Official Wanaku toolset repository";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    Instance<DataStoreRepository> dataStoreRepositoryInstance;

    @Inject
    UrlAllowlistConfig urlAllowlistConfig;

    private DataStoreRepository dataStoreRepository;

    @PostConstruct
    void init() {
        dataStoreRepository = dataStoreRepositoryInstance.get();
        registerDefaultRepo();
    }

    private void registerDefaultRepo() {
        try {
            if (findByName(DEFAULT_REPO_NAME) == null) {
                LOG.infof("Registering default toolset repository: %s", DEFAULT_REPO_NAME);
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
        if (StringHelper.isBlank(name)) {
            throw new WanakuException("Repository name is required");
        }
        if (StringHelper.isBlank(url)) {
            throw new WanakuException("Repository URL is required");
        }

        validateUrl(url);

        DataStore existing = findByName(name);
        if (existing != null) {
            throw new WanakuException("Toolset repository already exists: %s".formatted(name));
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
            throw new WanakuException("Failed to serialize repository metadata: %s".formatted(e.getMessage()));
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
            throw new WanakuException("Toolset repository not found: %s".formatted(name));
        }

        if (url != null && !url.isBlank()) {
            validateUrl(url);
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
            throw new WanakuException("Failed to serialize repository metadata: %s".formatted(e.getMessage()));
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
            throw new WanakuException("Toolset repository not found: %s".formatted(name));
        }

        String url = resolveBaseUrl(ds);
        validateUrl(url);
        ToolsetRepoIndex index = fetchToolsetRepoIndex(url);

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
            throw new WanakuException("Toolset repository not found: %s".formatted(name));
        }

        String baseUrl = resolveBaseUrl(ds);
        validateUrl(baseUrl);
        validateToolsetName(toolsetName);
        String toolsetUrl = ToolsetRepoIndex.toolsetUrl(baseUrl, toolsetName);
        validateUrl(toolsetUrl);

        try {
            return fetchToolsetIndex(toolsetUrl);
        } catch (WanakuException e) {
            throw e;
        } catch (Exception e) {
            throw new WanakuException(
                    "Failed to fetch toolset '%s' from %s: %s".formatted(toolsetName, toolsetUrl, e.getMessage()));
        }
    }

    /**
     * Validates a URL to prevent SSRF attacks by blocking non-HTTP schemes,
     * private/loopback/reserved network addresses, and enforcing the configured
     * host allowlist when present.
     * <p>
     * When an allowlist is configured via {@code wanaku.toolset-repos.url-allowlist},
     * only URLs whose host matches an allowlisted pattern are accepted.
     * The allowlist supports glob patterns such as {@code *.github.com} or
     * {@code *.githubusercontent.com}.
     * </p>
     *
     * @param url the URL to validate
     * @throws WanakuException if the URL is invalid or points to a restricted address
     */
    void validateUrl(String url) throws WanakuException {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new WanakuException("Only http and https URLs are allowed");
            }
            String host = uri.getHost();
            if (host == null) {
                throw new WanakuException("URL must have a valid host");
            }

            if (isLoopbackHost(host)) {
                throw new WanakuException("URLs pointing to localhost are not allowed");
            }
            if (isPrivateOrReservedHost(host)) {
                throw new WanakuException("URLs pointing to private or reserved network ranges are not allowed");
            }
            if (urlAllowlistConfig.isAllowlistConfigured() && !urlAllowlistConfig.isHostAllowed(host)) {
                throw new WanakuException("URL host '%s' is not in the allowlist. Allowed patterns: %s"
                        .formatted(host, urlAllowlistConfig.getAllowlistPatterns()));
            }
        } catch (IllegalArgumentException e) {
            throw new WanakuException("Invalid URL: %s".formatted(e.getMessage()));
        }
    }

    static void validateToolsetName(String toolsetName) throws WanakuException {
        if (StringHelper.isBlank(toolsetName)) {
            throw new WanakuException("Toolset name is required");
        }
        if (!TOOLSET_NAME_PATTERN.matcher(toolsetName).matches()) {
            throw new WanakuException("Toolset name contains invalid characters");
        }
    }

    static boolean isLoopbackHost(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPrivateOrReservedHost(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isMulticastAddress()
                    || isCarrierGradeNat(addr)
                    || isReservedIpv6(addr);
        } catch (UnknownHostException e) {
            return isPrivateIpv4Pattern(host);
        }
    }

    static boolean isCarrierGradeNat(InetAddress addr) {
        if (!(addr instanceof java.net.Inet4Address)) {
            return false;
        }
        byte[] bytes = addr.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    static boolean isReservedIpv6(InetAddress addr) {
        if (!(addr instanceof java.net.Inet6Address)) {
            return false;
        }
        byte[] bytes = addr.getAddress();
        return (bytes[0] & 0xFF) == 0x00 && (bytes[1] & 0xFF) == 0x00;
    }

    static boolean isPrivateIpv4Pattern(String host) {
        if (host.equals("127.0.0.1") || host.equals("0.0.0.0")) {
            return true;
        }
        if (host.startsWith("10.") || host.startsWith("192.168.") || isPrivate172(host)) {
            return true;
        }
        if (host.startsWith("169.254.")) {
            return true;
        }
        if (host.startsWith("100.")) {
            try {
                int second = Integer.parseInt(host.split("\\.")[1]);
                if (second >= 64 && second <= 127) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    static boolean isPrivate172(String host) {
        if (!host.startsWith("172.")) {
            return false;
        }
        try {
            int second = Integer.parseInt(host.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
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
            if (StringHelper.isBlank(url)) {
                throw new WanakuException("Repository metadata missing URL for: %s".formatted(ds.getName()));
            }
            String branch = metadata.getOrDefault("branch", DEFAULT_BRANCH);
            return toRawContentUrl(url, branch);
        } catch (JsonProcessingException e) {
            throw new WanakuException("Failed to parse repository metadata: %s".formatted(e.getMessage()));
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

    private ToolsetRepoIndex fetchToolsetRepoIndex(String baseUrl) throws WanakuException {
        String indexUrl = baseUrl.endsWith("/") ? baseUrl + "index.properties" : baseUrl + "/index.properties";
        try (InputStream inputStream = openValidatedStream(indexUrl, 0)) {
            return ToolsetRepoIndex.fromProperties(inputStream);
        } catch (IOException e) {
            throw new WanakuException("Failed to fetch index.properties from " + indexUrl + ": " + e.getMessage());
        }
    }

    private List<ToolReference> fetchToolsetIndex(String toolsetUrl) throws Exception {
        try (InputStream inputStream = openValidatedStream(toolsetUrl, 0)) {
            return ToolsetIndexHelper.loadToolsIndex(inputStream);
        }
    }

    private InputStream openValidatedStream(String url, int redirectCount) throws IOException, WanakuException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }

        validateUrl(url);

        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.connect();

        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (StringHelper.isBlank(location)) {
                throw new IOException("Redirect response missing Location header");
            }
            String redirectedUrl = URI.create(url).resolve(location).toString();
            return openValidatedStream(redirectedUrl, redirectCount + 1);
        }

        if (status >= 400) {
            String message = connection.getResponseMessage();
            connection.disconnect();
            throw new IOException("HTTP " + status + (message != null ? " " + message : ""));
        }

        return connection.getInputStream();
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
