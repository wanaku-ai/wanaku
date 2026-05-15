package ai.wanaku.backend.api.v1.toolsetrepos;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
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

        validateUrlFailFast(url);

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
            validateUrlFailFast(url);
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
        validateUrlFailFast(url);
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
        validateUrlFailFast(baseUrl);
        validateToolsetName(toolsetName);
        String toolsetUrl = ToolsetRepoIndex.toolsetUrl(baseUrl, toolsetName);
        validateUrlFailFast(toolsetUrl);

        try {
            return fetchToolsetIndex(toolsetUrl);
        } catch (WanakuException e) {
            // Re-throw WanakuException without wrapping to preserve the original error message
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
     * DNS is resolved and the resulting IP is checked against private/reserved
     * ranges. The connection is made using the original hostname so that HTTPS
     * TLS handshake (SNI and certificate verification) works correctly.
     * <p>
     * <strong>Note:</strong> DNS-rebinding TOCTOU attacks are not fully prevented
     * by this approach — a malicious DNS server could return a public IP during
     * validation and a private IP when the JVM resolver is queried again at
     * connection time. For environments requiring full TOCTOU protection, a
     * custom {@code SSLSocketFactory} with hostname-based SNI and IP-pinned
     * connections would be needed.
     * <p>
     * When an allowlist is configured via {@code wanaku.toolset-repos.url-allowlist},
     * only URLs whose host matches an allowlisted pattern are accepted. The
     * allowlist supports glob patterns such as {@code *.github.com} or
     * {@code *.githubusercontent.com}.
     *
     * @param url the URL to validate
     * @return a {@link ResolvedUrl} containing the parsed URI and resolved address
     * @throws WanakuException if the URL is invalid or points to a restricted address
     */
    ResolvedUrl validateUrl(String url) throws WanakuException {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new WanakuException("Only http and https URLs are allowed");
            }
            String host = uri.getHost();
            if (host == null) {
                throw new WanakuException("URL must have a valid host");
            }

            if (host.equalsIgnoreCase("localhost")) {
                throw new WanakuException("URLs pointing to localhost are not allowed");
            }

            InetAddress resolvedAddr;
            try {
                resolvedAddr = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new WanakuException("Unable to resolve host '%s': %s".formatted(host, e.getMessage()));
            }

            if (resolvedAddr.isLoopbackAddress() || resolvedAddr.isAnyLocalAddress()) {
                throw new WanakuException("URLs pointing to localhost are not allowed");
            }
            if (isPrivateOrReservedAddress(resolvedAddr)) {
                throw new WanakuException("URLs pointing to private or reserved network ranges are not allowed");
            }
            if (urlAllowlistConfig.isAllowlistConfigured() && !urlAllowlistConfig.isHostAllowed(host)) {
                throw new WanakuException("URL host '%s' is not in the allowlist. Allowed patterns: %s"
                        .formatted(host, urlAllowlistConfig.getAllowlistPatterns()));
            }

            return new ResolvedUrl(uri, host, resolvedAddr);
        } catch (IllegalArgumentException e) {
            throw new WanakuException("Invalid URL: %s".formatted(e.getMessage()));
        }
    }

    /**
     * Fail-fast URL validation that discards the resolved address.
     * <p>
     * Use this when the actual connection will be made via
     * {@link #openValidatedStream}, which performs its own validation.
     * This method provides early feedback for invalid or restricted URLs
     * before any I/O is attempted.
     *
     * @param url the URL to validate
     * @throws WanakuException if the URL is invalid or points to a restricted address
     */
    void validateUrlFailFast(String url) throws WanakuException {
        validateUrl(url);
    }

    static void validateToolsetName(String toolsetName) throws WanakuException {
        if (StringHelper.isBlank(toolsetName)) {
            throw new WanakuException("Toolset name is required");
        }
        if (!TOOLSET_NAME_PATTERN.matcher(toolsetName).matches()) {
            throw new WanakuException("Toolset name contains invalid characters");
        }
    }

    static boolean isPrivateOrReservedAddress(InetAddress addr) {
        return addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isMulticastAddress()
                || isCarrierGradeNat(addr)
                || isReservedIpv6(addr);
    }

    static boolean isCarrierGradeNat(InetAddress addr) {
        if (!(addr instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = addr.getAddress();
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    /**
     * Checks whether an IPv6 address is reserved.
     * <p>
     * Note: IPv4-mapped IPv6 addresses (e.g., {@code ::ffff:127.0.0.1}) are
     * typically resolved by the JVM as {@link Inet4Address} rather than
     * {@link Inet6Address}, so they are caught by the standard IPv4 checks
     * (loopback, site-local, link-local, CGNAT). This method handles the
     * case where the JVM returns an {@link Inet6Address} for such addresses.
     * </p>
     */
    static boolean isReservedIpv6(InetAddress addr) {
        if (!(addr instanceof Inet6Address)) {
            return false;
        }
        byte[] bytes = addr.getAddress();

        if (isIpv4MappedIpv6(bytes)) {
            byte[] v4Bytes = new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]};
            try {
                InetAddress v4Addr = InetAddress.getByAddress(v4Bytes);
                return v4Addr.isLoopbackAddress()
                        || v4Addr.isSiteLocalAddress()
                        || v4Addr.isLinkLocalAddress()
                        || isCarrierGradeNat(v4Addr);
            } catch (UnknownHostException e) {
                return true;
            }
        }

        int firstByte = bytes[0] & 0xFF;
        if ((firstByte & 0xFE) == 0xFC) {
            return true;
        }

        return (firstByte == 0x00) && ((bytes[1] & 0xFF) == 0x00);
    }

    static boolean isIpv4MappedIpv6(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0x00) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    record ResolvedUrl(URI uri, String host, InetAddress address) {}

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
        connection.setRequestProperty("User-Agent", "Wanaku-Router/1.0");
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

        return new DisconnectOnCloseInputStream(connection.getInputStream(), connection);
    }

    private static class DisconnectOnCloseInputStream extends FilterInputStream {
        private final HttpURLConnection connection;
        private boolean closed = false;

        DisconnectOnCloseInputStream(InputStream in, HttpURLConnection connection) {
            super(in);
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    super.close();
                } finally {
                    connection.disconnect();
                }
            }
        }
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
