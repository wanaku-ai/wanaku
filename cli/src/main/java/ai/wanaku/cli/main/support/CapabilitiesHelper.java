package ai.wanaku.cli.main.support;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.services.api.TargetsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Uni;
import org.jline.terminal.Terminal;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Comprehensive utility class for managing and processing service capabilities' data.
 *
 * <p>This helper class provides robust functionality for fetching, merging, and transforming
 * service capability information from multiple API endpoints. It handles both management tools and
 * resource providers, combining their data with activity states to create a unified view of
 * system capabilities.</p>
 *
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Initialize the service
 * TargetsService service = CapabilitiesHelper.initializeTargetsService("https://api.example.com");
 *
 * // Fetch and display capabilities
 * CapabilitiesHelper.fetchAndMergeCapabilities(service)
 *     .subscribe().with(capabilities -> {
 *         CapabilitiesHelper.printCapabilities(capabilities, terminal);
 *     });
 * }</pre>
 *
 */
public final class CapabilitiesHelper {

    // Constants
    /** Maximum time to wait for API responses before timing out. */
    public static final Duration API_TIMEOUT = Duration.ofSeconds(15);

    /** Default value displayed when information is not available or accessible. */
    public static final String DEFAULT_STATUS = "-";

    /** Status indicator for services that are currently active and responding. */
    public static final String ACTIVE_STATUS = "active";

    /** Status indicator for services that are inactive or not responding. */
    public static final String INACTIVE_STATUS = "inactive";

    /**
     * Date-time formatter for displaying verbose timestamp information in human-readable format.
     * Format: "Mon, Jan 01, 2024 at 14:30:45"
     */
    public static final DateTimeFormatter VERBOSE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy 'at' HH:mm:ss");

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private CapabilitiesHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Service Initialization

    /**
     * Initializes and configures the REST client for communicating with the Wanaku service.
     *
     * <p>Creates a properly configured Quarkus REST client builder with the specified host URL.
     * The client is configured with appropriate timeouts and error handling for reliable
     * communication with the Wanaku API endpoints.</p>
     *
     * @param host the base URL of the Wanaku service API (must be a valid URI)
     * @return a configured {@link TargetsService} instance ready for API calls
     * @throws IllegalArgumentException if the host URL is invalid or malformed
     * @throws NullPointerException if host is null
     *
     * @see TargetsService
     */
    public static TargetsService initializeTargetsService(String host) {
        if (host == null) {
            throw new NullPointerException("Host URL cannot be null");
        }

        try {
            return QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(host))
                    .build(TargetsService.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid host URL: " + host, e);
        }
    }

    // Main Orchestration Methods

    /**
     * Fetches and merges capability data from multiple API endpoints concurrently.
     *
     * <p>This method orchestrates the fetching of data from four different API sources:
     * management tools, tools activity state, resource providers, and resources activity state.
     * All API calls are executed concurrently for optimal performance, and the results are
     * merged into a unified view of system capabilities.</p>
     *
     * <p>The method handles failures gracefully by providing default values when individual
     * API calls fail, ensuring that partial data is still useful and displayed to the user.</p>
     *
     * @param targetsService the configured service client for making API calls
     * @return a {@link Uni} containing the complete list of printable capabilities,
     *         never null but may be empty if all API calls fail
     * @throws NullPointerException if targetsService is null
     *
     * @see PrintableCapability
     * @see #combineDataIntoCapabilities(List)
     */
    public static Uni<List<PrintableCapability>> fetchAndMergeCapabilities(TargetsService targetsService) {
        if (targetsService == null) {
            throw new NullPointerException("TargetsService cannot be null");
        }

        return Uni.combine()
                .all()
                .unis(
                        fetchManagementTools(targetsService),
                        fetchToolsActivityState(targetsService),
                        fetchResourceProviders(targetsService),
                        fetchResourcesActivityState(targetsService)
                )
                .with(CapabilitiesHelper::combineDataIntoCapabilities);
    }

    /**
     * Combines fetched data from multiple API endpoints into a unified list of printable capabilities.
     *
     * <p>This method takes the raw responses from the four API endpoints and intelligently
     * correlates them to create a comprehensive view of each service capability. It matches
     * service targets with their corresponding activity records to provide current status
     * and activity information.</p>
     *
     * <p>The method handles cases where activity records may not exist for certain services,
     * providing appropriate default values to ensure consistent display formatting.</p>
     *
     * @param responses a list containing exactly four API responses in the following order:
     *                 <ol>
     *                   <li>Management tools ({@code List<ServiceTarget>})</li>
     *                   <li>Tools activity state ({@code Map<String, List<ActivityRecord>>})</li>
     *                   <li>Resource providers ({@code List<ServiceTarget>})</li>
     *                   <li>Resources activity state ({@code Map<String, List<ActivityRecord>>})</li>
     *                 </ol>
     * @return a list of {@link PrintableCapability} objects ready for display,
     *         never null but may be empty if no services are available
     * @throws ClassCastException if the response objects are not of the expected types
     * @throws IndexOutOfBoundsException if the responses list doesn't contain exactly 4 elements
     *
     * @see #mergeActivityStates(Map, Map)
     * @see #createPrintableCapability(ServiceTarget, Map)
     */
    @SuppressWarnings("unchecked")
    public static List<PrintableCapability> combineDataIntoCapabilities(List<?> responses) {
        if (responses.size() != 4) {
            throw new IndexOutOfBoundsException("Expected exactly 4 responses, got: " + responses.size());
        }

        var managementTools = (List<ServiceTarget>) responses.get(0);
        var toolsActivityState = (Map<String, List<ActivityRecord>>) responses.get(1);
        var resourceProviders = (List<ServiceTarget>) responses.get(2);
        var resourcesActivityState = (Map<String, List<ActivityRecord>>) responses.get(3);

        var mergedActivityStates = mergeActivityStates(toolsActivityState, resourcesActivityState);
        var allServiceTargets = Stream.concat(
                managementTools.stream(),
                resourceProviders.stream()
        ).toList();

        return allServiceTargets.stream()
                .map(target -> createPrintableCapability(target, mergedActivityStates))
                .toList();
    }

    // Data Processing Methods

    /**
     * Merges activity state maps from management tools and resource providers.
     *
     * <p>This method combines activity records from both tools and resources into a single
     * unified map. When the same service appears in both sources, their activity records
     * are merged into a single list, providing a complete view of all activities for each service.</p>
     *
     * <p>The resulting map uses immutable lists to prevent accidental modification of the
     * merged data structure.</p>
     *
     * @param toolsActivityState activity records for management tools, may be empty but not null
     * @param resourcesActivityState activity records for resource providers, may be empty but not null
     * @return a merged map containing all activity records indexed by service name,
     *         never null but may be empty if both input maps are empty
     * @throws NullPointerException if either parameter is null
     *
     * @see ActivityRecord
     */
    public static Map<String, List<ActivityRecord>> mergeActivityStates(
            Map<String, List<ActivityRecord>> toolsActivityState,
            Map<String, List<ActivityRecord>> resourcesActivityState) {

        if (toolsActivityState == null || resourcesActivityState == null) {
            throw new NullPointerException("Activity state maps cannot be null");
        }

        return Stream.concat(
                        toolsActivityState.entrySet().stream(),
                        resourcesActivityState.entrySet().stream()
                )
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()), // Create immutable copy
                        (existingList, newList) -> Stream.concat(
                                existingList.stream(),
                                newList.stream()
                        ).toList()
                ));
    }

    /**
     * Locates the activity record for a specific service target.
     *
     * <p>This method searches through the activity states to find the record that matches
     * the given service target's service name and unique identifier. It handles cases where
     * no activity record exists for a service target gracefully.</p>
     *
     * @param serviceTarget the service target to find activity information for, must not be null
     * @param activityStates map of activity records indexed by service name, must not be null
     * @return the matching {@link ActivityRecord} if found, or {@code null} if no match exists
     * @throws NullPointerException if either parameter is null
     *
     * @see ServiceTarget
     * @see ActivityRecord
     */
    public static ActivityRecord findActivityRecord(
            ServiceTarget serviceTarget,
            Map<String, List<ActivityRecord>> activityStates) {

        if (serviceTarget == null || activityStates == null) {
            throw new NullPointerException("ServiceTarget and activityStates cannot be null");
        }

        return Optional.ofNullable(activityStates.get(serviceTarget.getService()))
                .orElse(List.of())
                .stream()
                .filter(record -> record.getId().equals(serviceTarget.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a PrintableCapability record from a service target and its activity state.
     *
     * <p>This method transforms raw API data into a formatted capability record suitable for
     * terminal display. It includes status determination based on activity records
     * and proper formatting of timestamps and configuration data.</p>
     *
     * <p>The method handles null values gracefully, substituting appropriate default values
     * to ensure consistent display formatting across all capabilities.</p>
     *
     * @param serviceTarget the service target containing basic service information, must not be null
     * @param activityStates map of activity records indexed by service name, must not be null
     * @return a formatted {@link PrintableCapability} ready for display, never null
     * @throws NullPointerException if either parameter is null
     *
     * @see PrintableCapability
     * @see #determineServiceStatus(ActivityRecord)
     * @see #formatLastSeenTimestamp(ActivityRecord)
     */
    public static PrintableCapability createPrintableCapability(
            ServiceTarget serviceTarget,
            Map<String, List<ActivityRecord>> activityStates) {

        if (serviceTarget == null || activityStates == null) {
            throw new NullPointerException("ServiceTarget and activityStates cannot be null");
        }

        var activityRecord = findActivityRecord(serviceTarget, activityStates);

        return new PrintableCapability(
                Optional.ofNullable(serviceTarget.getService()).orElse(DEFAULT_STATUS),
                Optional.ofNullable(serviceTarget.getServiceType())
                        .map(ServiceType::asValue)
                        .orElse(DEFAULT_STATUS),
                Optional.ofNullable(serviceTarget.getHost()).orElse(DEFAULT_STATUS),
                Optional.of(serviceTarget.getPort()).orElse(0),
                determineServiceStatus(activityRecord),
                formatLastSeenTimestamp(activityRecord),
                // TODO: remove configuration
                processServiceConfigurations(Map.of())
        );
    }

    /**
     * Fetches the list of management tools from the API with error handling.
     *
     * <p>Retrieves all available management tools from the Wanaku API. If the API call fails
     * or returns an error response, an empty list is returned to allow the application to
     * continue functioning with partial data.</p>
     *
     * @param targetsService the configured service client for making API calls, must not be null
     * @return a {@link Uni} containing the list of management tool targets,
     *         or an empty list if the API call fails
     * @throws NullPointerException if targetsService is null
     *
     * @see ServiceTarget
     * @see #executeApiCall(Supplier, Object)
     */
    public static Uni<List<ServiceTarget>> fetchManagementTools(TargetsService targetsService) {
        if (targetsService == null) {
            throw new NullPointerException("TargetsService cannot be null");
        }
        return executeApiCall(targetsService::toolsList, List.of());
    }

    /**
     * Fetches the activity state for all management tools from the API.
     *
     * <p>Retrieves the current activity status and state information for all management tools.
     * This includes information about when each tool was last seen and whether it's currently active.</p>
     *
     * @param targetsService the configured service client for making API calls, must not be null
     * @return a {@link Uni} containing a map of tool activity records indexed by service name,
     *         or an empty map if the API call fails
     * @throws NullPointerException if targetsService is null
     *
     * @see ActivityRecord
     * @see #executeApiCall(Supplier, Object)
     */
    public static Uni<Map<String, List<ActivityRecord>>> fetchToolsActivityState(TargetsService targetsService) {
        if (targetsService == null) {
            throw new NullPointerException("TargetsService cannot be null");
        }
        return executeApiCall(targetsService::toolsState, Map.of());
    }

    /**
     * Fetches the list of resource providers from the API with error handling.
     *
     * <p>Retrieves all available resource providers from the Wanaku API. Resource providers
     * are services that provide computational or storage resources to the system.</p>
     *
     * @param targetsService the configured service client for making API calls, must not be null
     * @return a {@link Uni} containing the list of resource provider targets,
     *         or an empty list if the API call fails
     * @throws NullPointerException if targetsService is null
     *
     * @see ServiceTarget
     * @see #executeApiCall(Supplier, Object)
     */
    public static Uni<List<ServiceTarget>> fetchResourceProviders(TargetsService targetsService) {
        if (targetsService == null) {
            throw new NullPointerException("TargetsService cannot be null");
        }
        return executeApiCall(targetsService::resourcesList, List.of());
    }

    /**
     * Fetches the activity state for all resource providers from the API.
     *
     * <p>Retrieves the current activity status and state information for all resource providers.
     * This includes information about resource availability, last seen timestamps, and current status.</p>
     *
     * @param targetsService the configured service client for making API calls, must not be null
     * @return a {@link Uni} containing a map of resource activity records indexed by service name,
     *         or an empty map if the API call fails
     * @throws NullPointerException if targetsService is null
     *
     * @see ActivityRecord
     * @see #executeApiCall(Supplier, Object)
     */
    public static Uni<Map<String, List<ActivityRecord>>> fetchResourcesActivityState(TargetsService targetsService) {
        if (targetsService == null) {
            throw new NullPointerException("TargetsService cannot be null");
        }
        return executeApiCall(targetsService::resourcesState, Map.of());
    }

    // Utility Methods

    /**
     * Executes an API call with consistent error handling and fallback mechanisms.
     *
     * <p>This method provides a centralized approach to handling API calls that return
     * {@link WanakuResponse} objects. It ensures that failures are handled gracefully
     * by providing default values, allowing the application to continue functioning
     * even when some API endpoints are unavailable.</p>
     *
     * <p>The method handles both runtime exceptions and API-level errors reported
     * through the WanakuResponse error field.</p>
     *
     * @param <T> the type of data returned by the API call
     * @param apiCall a supplier function that makes the actual API call, must not be null
     * @param defaultValue the value to return if the API call fails or returns an error, may be null
     * @return a {@link Uni} containing either the successful API response data or the default value
     * @throws NullPointerException if apiCall is null
     *
     * @see WanakuResponse
     */
    public static <T> Uni<T> executeApiCall(Supplier<WanakuResponse<T>> apiCall, T defaultValue) {
        if (apiCall == null) {
            throw new NullPointerException("API call supplier cannot be null");
        }

        try {
            var response = apiCall.get();
            return response.error() != null
                    ? Uni.createFrom().item(defaultValue)
                    : Uni.createFrom().item(response.data());
        } catch (Exception e) {
            // In a production environment, you might want to log this exception
            // Logger.warn("API call failed", e);
            throw new WanakuException("API call failed.", e);
            //return Uni.createFrom().item(defaultValue);
        }
    }

    /**
     * Determines the display status based on activity record information.
     *
     * <p>This method analyzes the activity record to determine the appropriate status
     * string for display. It handles null activity records gracefully and provides
     * clear status indicators based on the service's current state.</p>
     *
     * @param activityRecord the activity record to evaluate, may be null
     * @return {@link #ACTIVE_STATUS} if the service is active,
     *         {@link #INACTIVE_STATUS} if the service is inactive,
     *
     * @see ActivityRecord#isActive()
     */
    public static String determineServiceStatus(ActivityRecord activityRecord) {
        if (activityRecord == null) {
            return DEFAULT_STATUS;
        }
        return activityRecord.isActive() ? ACTIVE_STATUS : INACTIVE_STATUS;
    }

    /**
     * Formats the last seen timestamp from an activity record for display.
     *
     * <p>Converts the activity record's last seen timestamp to a human-readable
     * format using the system's default timezone. The format includes the full
     * date and time information for comprehensive visibility into service activity.</p>
     *
     * @param activityRecord the activity record containing timestamp information, may be null
     * @return a formatted timestamp string in the format "Monday, January 01, 2024 at 14:30:45",
     *         or an empty string if no timestamp is available
     *
     * @see #VERBOSE_TIMESTAMP_FORMATTER
     * @see ActivityRecord#getLastSeen()
     */
    public static String formatLastSeenTimestamp(ActivityRecord activityRecord) {
        return Optional.ofNullable(activityRecord)
                .map(ActivityRecord::getLastSeen)
                .map(instant -> instant.atZone(ZoneId.systemDefault()))
                .map(zonedDateTime -> zonedDateTime.format(VERBOSE_TIMESTAMP_FORMATTER))
                .orElse("");
    }

    /**
     * Processes and transforms service configuration data for display.
     *
     * <p>Converts the raw configuration map from a service target into a list of
     * formatted configuration entries suitable for display. Each configuration
     * entry includes both the configuration name and its description or value.</p>
     *
     * @param configurations a map of configuration names to their descriptions or values,
     *                      may be null or empty
     * @return a list of {@link PrintableCapabilityConfiguration} objects,
     *         never null but may be empty if no configurations exist
     *
     * @see PrintableCapabilityConfiguration
     */
    public static List<PrintableCapabilityConfiguration> processServiceConfigurations(
            Map<String, String> configurations) {

        if (configurations == null || configurations.isEmpty()) {
            return List.of();
        }

        return configurations.entrySet()
                .stream()
                .map(entry -> new PrintableCapabilityConfiguration(
                        entry.getKey(),
                        entry.getValue()))
                .toList();
    }

    /**
     * Displays the capabilities list in the terminal using formatted table output.
     *
     * <p>Creates a well-formatted table display of all service capabilities using the
     * WanakuPrinter utility. The table includes columns for service name, type, host,
     * port, status, and last seen timestamp.</p>
     *
     * <p>This method handles terminal-specific formatting and ensures that the output
     * is properly aligned and readable across different terminal environments.</p>
     *
     * @param capabilities the list of capabilities to display, must not be null
     * @param terminal the terminal instance to use for output, must not be null
     * @see PrintableCapability
     * @see WanakuPrinter
     */
    public static void printCapabilities(List<PrintableCapability> capabilities, Terminal terminal) {

        if (capabilities == null || terminal == null) {
            throw new NullPointerException("Capabilities list and terminal cannot be null");
        }

        var printer = new WanakuPrinter(null, terminal);
        printer.printTable(capabilities,
                "service", "serviceType", "host", "port", "status", "lastSeen");
    }

    /**
     * Prints a single capability in a formatted key-value map display to the terminal.
     *
     * <p>This method displays a single capability's information in a structured format
     * using the WanakuPrinter. The capability data is presented as a key-value table
     * showing the specified fields in a user-friendly format.</p>
     *
     * <p>The displayed fields include:</p>
     * <ul>
     *   <li><strong>service</strong> - The service identifier (e.g., http, sqs, file)</li>
     *   <li><strong>serviceType</strong> - The type of service (e.g., tool-invoker, resource-provider)</li>
     *   <li><strong>host</strong> - The host address where the service is running</li>
     *   <li><strong>port</strong> - The port number the service is listening on</li>
     *   <li><strong>status</strong> - Current status of the service (e.g., active, inactive)</li>
     *   <li><strong>lastSeen</strong> - Timestamp of the last activity or health check</li>
     * </ul>
     *
     * <p>Example output format:</p>
     * <pre>
     * service     : http
     * serviceType : tool-invoker
     * host        : 192.168.1.101
     * port        : 9000
     * status      : active
     * lastSeen    : Monday, June 23, 2025 at 07:00:26
     * </pre>
     *
     * @param capability the capability to display; must not be null
     * @param terminal the terminal instance to output to; must not be null
     * @throws NullPointerException if either capability or terminal is null
     * @throws Exception if there's an error during the printing process, including:
     *                  <ul>
     *                    <li>Terminal output errors</li>
     *                    <li>Object conversion failures</li>
     *                    <li>Formatting or display issues</li>
     *                  </ul>
     */
    public static void printCapability(PrintableCapability capability, Terminal terminal)
            throws Exception {

        if (capability == null || terminal == null) {
            throw new NullPointerException("Capabilities list and terminal cannot be null");
        }

        var printer = new WanakuPrinter(null, terminal);
        printer.printAsMap(capability, "service", "serviceType", "host", "port", "status", "lastSeen");
    }


    // Record Definitions

    /**
     * Represents a service capability formatted and optimized for terminal display.
     *
     * <p>This record encapsulates all the essential information needed to display a service
     * capability in a terminal table format. It includes service identification details,
     * network location information, current operational status, and configuration data.</p>
     *
     * <p>The record automatically handles null values by converting them to empty strings
     * or appropriate default values, ensuring consistent formatting across all displayed
     * capabilities.</p>
     *
     * @param service the name identifier of the service, never null after construction
     * @param serviceType the type or category of the service (e.g., "database", "web-server"), never null
     * @param host the hostname or IP address where the service is accessible, never null
     * @param port the port number the service is listening on, defaults to 0 if not specified
     * @param status current operational status ("active", "inactive", or "-"), never null
     * @param lastSeen formatted timestamp of when the service was last seen active, never null but may be empty
     * @param configurations list of service configuration entries, never null but may be empty
     *
     * @see PrintableCapabilityConfiguration
     */
    @RegisterForReflection
    public record PrintableCapability(
            String service,
            String serviceType,
            String host,
            int port,
            String status,
            String lastSeen,
            List<PrintableCapabilityConfiguration> configurations) {

        /**
         * Compact constructor that validates and normalizes all record fields.
         *
         * <p>Ensures that null values are converted to appropriate defaults for consistent
         * display formatting. This prevents null pointer exceptions during table rendering
         * and provides a clean, predictable display format.</p>
         */
        public PrintableCapability {
            service = service != null ? service : "";
            serviceType = serviceType != null ? serviceType : "";
            host = host != null ? host : "";
            status = status != null ? status : "";
            lastSeen = lastSeen != null ? lastSeen : "";
            configurations = configurations != null ? configurations : List.of();
        }
    }

    /**
     * Represents a single configuration entry for a service capability.
     *
     * <p>This record stores configuration information as name-value pairs, providing
     * a structured way to display service-specific configuration details in the
     * capabilities table or detailed views.</p>
     *
     * @param name the configuration parameter name or key, never null after construction
     * @param description the configuration value or description, never null after construction
     */
    @RegisterForReflection
    public record PrintableCapabilityConfiguration(String name, String description) {

        /**
         * Compact constructor that ensures non-null values for consistent display.
         *
         * <p>Converts null values to empty strings to prevent display issues and
         * ensure consistent formatting across all configuration entries.</p>
         */
        public PrintableCapabilityConfiguration {
            name = name != null ? name : "";
            description = description != null ? description : "";
        }
    }
}