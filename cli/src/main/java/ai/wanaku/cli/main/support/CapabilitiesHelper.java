package ai.wanaku.cli.main.support;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Uni;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.services.api.CapabilitiesService;

/**
 * Utility class providing capabilities management functionality for the Wanaku CLI.
 *
 * <p>This class handles the fetching, merging, and processing of service capabilities
 * from various sources including management tools and resource providers. It provides
 * methods to combine activity states, format data for display, and create printable
 * representations of service capabilities.
 *
 * <p>The class follows a reactive programming model using Mutiny's {@link Uni} for
 * asynchronous operations and provides comprehensive error handling for API calls.
 *
 * @author Wanaku Team
 * @version 1.0
 * @since 1.0
 */
public final class CapabilitiesHelper {

    // Constants
    /**
     * Default timeout duration for API calls.
     */
    public static final Duration API_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Default status value used when actual status is unavailable.
     */
    public static final String DEFAULT_STATUS = "-";

    /**
     * Status value indicating an active service.
     */
    public static final String ACTIVE_STATUS = "active";

    /**
     * Status value indicating an inactive service.
     */
    public static final String INACTIVE_STATUS = "inactive";

    /**
     * Formatter for verbose timestamp display.
     * Format: "EEE, MMM dd, yyyy 'at' HH:mm:ss" (e.g., "Mon, Jan 15, 2024 at 14:30:45")
     */
    public static final DateTimeFormatter VERBOSE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy 'at' HH:mm:ss");

    /**
     * Standard column names for capability display.
     * <p>This array defines the order and names of columns when displaying
     * capabilities in table or map format. The order matches the fields
     * in {@link PrintableCapability}.
     */
    public static final String[] COLUMNS = {"service", "serviceType", "host", "port", "status", "lastSeen", "labels"};

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private CapabilitiesHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Fetches and merges capabilities from multiple sources asynchronously.
     *
     * <p>This method combines data from management tools and resource providers,
     * along with their respective activity states, to create a unified list of
     * printable capabilities.
     *
     * @param capabilitiesService the service used to fetch target information
     * @param labelFilter optional label expression to filter capabilities
     * @return a {@link Uni} emitting a list of {@link PrintableCapability} objects
     * @throws NullPointerException if targetsService is null
     * @see #combineDataIntoCapabilities(List)
     */
    public static Uni<List<PrintableCapability>> fetchAndMergeCapabilities(
            CapabilitiesService capabilitiesService, String labelFilter) {
        Objects.requireNonNull(capabilitiesService, "TargetsService cannot be null");

        return Uni.combine()
                .all()
                .unis(
                        fetchCapabilities(capabilitiesService),
                        fetchToolsActivityState(capabilitiesService),
                        fetchResourcesActivityState(capabilitiesService))
                .with(CapabilitiesHelper::combineDataIntoCapabilities);
    }

    /**
     * Fetches and merges capabilities from multiple sources asynchronously without filtering.
     *
     * @param capabilitiesService the service used to fetch target information
     * @return a {@link Uni} emitting a list of {@link PrintableCapability} objects
     * @throws NullPointerException if targetsService is null
     * @see #fetchAndMergeCapabilities(CapabilitiesService, String)
     */
    public static Uni<List<PrintableCapability>> fetchAndMergeCapabilities(CapabilitiesService capabilitiesService) {
        return fetchAndMergeCapabilities(capabilitiesService, null);
    }

    /**
     * Combines fetched data from multiple sources into a list of printable capabilities.
     *
     * <p>This method expects exactly 4 responses in the following order:
     * <ol>
     *   <li>Management tools list</li>
     *   <li>Tools activity state map</li>
     *   <li>Resource providers list</li>
     *   <li>Resources activity state map</li>
     * </ol>
     *
     * @param responses list containing exactly 4 responses from API calls
     * @return list of {@link PrintableCapability} objects
     * @throws IndexOutOfBoundsException if responses list doesn't contain exactly 4 elements
     * @throws ClassCastException if response types don't match expected types
     */
    @SuppressWarnings("unchecked")
    public static List<PrintableCapability> combineDataIntoCapabilities(List<?> responses) {
        if (responses.size() != 3) {
            throw new IndexOutOfBoundsException("Expected exactly 3 responses, got: " + responses.size());
        }

        var capabilities = (List<ServiceTarget>) responses.get(0);
        var toolsActivityState = (Map<String, List<ActivityRecord>>) responses.get(1);
        var resourcesActivityState = (Map<String, List<ActivityRecord>>) responses.get(2);

        var mergedActivityStates = mergeActivityStates(toolsActivityState, resourcesActivityState);

        return capabilities.stream()
                .map(target -> createPrintableCapability(target, mergedActivityStates))
                .toList();
    }

    /**
     * Merges two activity state maps into a single map.
     *
     * <p>When duplicate keys are found, the activity records from both maps
     * are combined into a single list for that key.
     *
     * @param toolsActivityState activity state map for management tools
     * @param resourcesActivityState activity state map for resource providers
     * @return merged activity state map with immutable value lists
     * @throws NullPointerException if either parameter is null
     */
    public static Map<String, List<ActivityRecord>> mergeActivityStates(
            Map<String, List<ActivityRecord>> toolsActivityState,
            Map<String, List<ActivityRecord>> resourcesActivityState) {

        Objects.requireNonNull(toolsActivityState, "Tools activity state map cannot be null");
        Objects.requireNonNull(resourcesActivityState, "Resources activity state map cannot be null");

        return Stream.concat(toolsActivityState.entrySet().stream(), resourcesActivityState.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        CapabilitiesHelper::getActivityRecords, // Create immutable copy
                        (existingList, newList) -> Stream.concat(existingList.stream(), newList.stream())
                                .toList()));
    }

    private static List<ActivityRecord> getActivityRecords(Map.Entry<String, List<ActivityRecord>> entry) {
        final List<ActivityRecord> value = entry.getValue();
        if (!value.isEmpty()) {
            // We need to filter orphaned records without activity state
            return value.stream().filter(Objects::nonNull).toList();
        }

        return List.of();
    }

    /**
     * Finds the activity record for a specific service target.
     *
     * <p>Searches through the activity states map to find a matching activity record
     * based on service name and target ID.
     *
     * @param serviceTarget the service target to find activity record for
     * @param activityStates map of activity states indexed by service name
     * @return the matching {@link ActivityRecord} or null if not found
     * @throws NullPointerException if either parameter is null
     */
    public static ActivityRecord findActivityRecord(
            ServiceTarget serviceTarget, Map<String, List<ActivityRecord>> activityStates) {

        Objects.requireNonNull(serviceTarget, "ServiceTarget cannot be null");
        Objects.requireNonNull(activityStates, "Activity states map cannot be null");

        return Optional.ofNullable(activityStates.get(serviceTarget.getServiceName())).orElse(List.of()).stream()
                .filter(record -> record.getId().equals(serviceTarget.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a printable capability from a service target and activity states.
     *
     * <p>This method combines service target information with its corresponding
     * activity record to create a formatted representation suitable for display.
     *
     * @param serviceTarget the service target containing basic service information
     * @param activityStates map of activity states to find matching activity record
     * @return a {@link PrintableCapability} with formatted service information
     * @throws NullPointerException if either parameter is null
     */
    public static PrintableCapability createPrintableCapability(
            ServiceTarget serviceTarget, Map<String, List<ActivityRecord>> activityStates) {

        Objects.requireNonNull(serviceTarget, "ServiceTarget cannot be null");
        Objects.requireNonNull(activityStates, "Activity states map cannot be null");

        var activityRecord = findActivityRecord(serviceTarget, activityStates);

        return new PrintableCapability(
                Optional.ofNullable(serviceTarget.getServiceName()).orElse(DEFAULT_STATUS),
                Optional.ofNullable(serviceTarget.getServiceType()).orElse(DEFAULT_STATUS),
                Optional.ofNullable(serviceTarget.getHost()).orElse(DEFAULT_STATUS),
                serviceTarget.getPort(),
                determineServiceStatus(activityRecord),
                formatLastSeenTimestamp(activityRecord),
                // TODO: Implement configuration processing when requirements are clarified
                processServiceConfigurations(Map.of()));
    }

    /**
     * Fetches the list of capabilities
     *
     * @param capabilitiesService the service to fetch management tools from
     * @return a {@link Uni} emitting a list of {@link ServiceTarget} objects
     * @throws NullPointerException if targetsService is null
     */
    public static Uni<List<ServiceTarget>> fetchCapabilities(CapabilitiesService capabilitiesService) {
        Objects.requireNonNull(capabilitiesService, "TargetsService cannot be null");
        return executeApiCall(capabilitiesService::list, List.of());
    }

    /**
     * Fetches the activity state for management tools.
     *
     * @param capabilitiesService the service to fetch activity state from
     * @return a {@link Uni} emitting a map of service names to activity records
     * @throws NullPointerException if targetsService is null
     */
    public static Uni<Map<String, List<ActivityRecord>>> fetchToolsActivityState(
            CapabilitiesService capabilitiesService) {
        Objects.requireNonNull(capabilitiesService, "TargetsService cannot be null");
        return executeApiCall(capabilitiesService::toolsState, Map.of());
    }

    /**
     * Fetches the activity state for resource providers.
     *
     * @param capabilitiesService the service to fetch activity state from
     * @return a {@link Uni} emitting a map of service names to activity records
     * @throws NullPointerException if targetsService is null
     */
    public static Uni<Map<String, List<ActivityRecord>>> fetchResourcesActivityState(
            CapabilitiesService capabilitiesService) {
        Objects.requireNonNull(capabilitiesService, "TargetsService cannot be null");
        return executeApiCall(capabilitiesService::resourcesState, Map.of());
    }

    /**
     * Executes an API call with error handling and fallback to default value.
     *
     * <p>This method provides a consistent way to handle API calls that return
     * {@link WanakuResponse} objects. If the response contains an error or if
     * an exception occurs, the method returns the provided default value.
     *
     * @param <T> the type of data returned by the API call
     * @param apiCall supplier that performs the API call
     * @param defaultValue value to return in case of error or exception
     * @return a {@link Uni} emitting either the API response data or the default value
     * @throws NullPointerException if apiCall is null
     */
    public static <T> Uni<T> executeApiCall(Supplier<WanakuResponse<T>> apiCall, T defaultValue) {
        Objects.requireNonNull(apiCall, "API call supplier cannot be null");

        var response = apiCall.get();
        return response.error() != null
                ? Uni.createFrom().item(defaultValue)
                : Uni.createFrom().item(response.data());
    }

    /**
     * Determines the service status based on activity record.
     *
     * @param activityRecord the activity record to check (may be null)
     * @return {@link #ACTIVE_STATUS}, {@link #INACTIVE_STATUS}, or {@link #DEFAULT_STATUS}
     */
    public static String determineServiceStatus(ActivityRecord activityRecord) {
        if (activityRecord == null) {
            return DEFAULT_STATUS;
        }
        return activityRecord.isActive() ? ACTIVE_STATUS : INACTIVE_STATUS;
    }

    /**
     * Formats the last seen timestamp from an activity record.
     *
     * <p>The timestamp is formatted using the system default timezone and the
     * {@link #VERBOSE_TIMESTAMP_FORMATTER} pattern.
     *
     * @param activityRecord the activity record containing the timestamp (may be null)
     * @return formatted timestamp string or empty string if record is null or has no timestamp
     */
    public static String formatLastSeenTimestamp(ActivityRecord activityRecord) {
        return Optional.ofNullable(activityRecord)
                .map(ActivityRecord::getLastSeen)
                .map(instant -> instant.atZone(ZoneId.systemDefault()))
                .map(zonedDateTime -> zonedDateTime.format(VERBOSE_TIMESTAMP_FORMATTER))
                .orElse("");
    }

    /**
     * Processes service configurations into printable format.
     *
     * <p><strong>Note:</strong> This method currently returns an empty list as
     * configuration processing requirements are not yet finalized.
     *
     * @param configurations map of configuration key-value pairs
     * @return list of {@link PrintableCapabilityConfiguration} objects (currently empty)
     */
    public static List<PrintableCapabilityConfiguration> processServiceConfigurations(
            Map<String, String> configurations) {

        if (configurations == null || configurations.isEmpty()) {
            return List.of();
        }

        return configurations.entrySet().stream()
                .map(entry -> new PrintableCapabilityConfiguration(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Prints a list of capabilities in table format.
     *
     * @param capabilities list of capabilities to print
     * @param printer the printer to use for output
     * @throws NullPointerException if either parameter is null
     */
    public static void printCapabilities(List<PrintableCapability> capabilities, WanakuPrinter printer) {
        Objects.requireNonNull(capabilities, "Capabilities list cannot be null");
        Objects.requireNonNull(printer, "Printer cannot be null");
        printer.printTable(capabilities, COLUMNS);
    }

    /**
     * Computes a summary of capabilities grouped by status.
     *
     * @param capabilities the list of capabilities to summarize
     * @return a {@link StatusSummary} with counts per status and the grouped capabilities
     * @throws NullPointerException if capabilities is null
     */
    public static StatusSummary computeStatusSummary(List<PrintableCapability> capabilities) {
        Objects.requireNonNull(capabilities, "Capabilities list cannot be null");

        int active = 0;
        int inactive = 0;
        int unknown = 0;

        for (PrintableCapability cap : capabilities) {
            switch (cap.status()) {
                case ACTIVE_STATUS -> active++;
                case INACTIVE_STATUS -> inactive++;
                default -> unknown++;
            }
        }

        return new StatusSummary(capabilities.size(), active, inactive, unknown);
    }

    /**
     * Record representing a summary of capability statuses.
     *
     * @param total total number of capabilities
     * @param active number of active capabilities
     * @param inactive number of inactive capabilities
     * @param unknown number of capabilities with unknown status
     */
    @RegisterForReflection
    public record StatusSummary(int total, int active, int inactive, int unknown) {}

    /**
     * Prints a single capability in map format.
     *
     * @param capability the capability to print
     * @param printer the printer to use for output
     * @throws NullPointerException if either parameter is null
     */
    public static void printCapability(PrintableCapability capability, WanakuPrinter printer) {
        Objects.requireNonNull(capability, "Capability cannot be null");
        Objects.requireNonNull(printer, "Printer cannot be null");
        printer.printAsMap(capability, COLUMNS);
    }

    /**
     * Record representing a printable capability with all necessary display information.
     *
     * <p>This record encapsulates service information in a format suitable for
     * display in CLI output, including service details, status, and timestamp information.
     *
     * @param service the service name
     * @param serviceType the type of service
     * @param host the host address
     * @param port the port number
     * @param status the current status of the service
     * @param lastSeen formatted timestamp of last activity
     * @param configurations list of service configurations
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
         * Compact constructor that ensures all string fields are non-null.
         *
         * <p>Null values are replaced with empty strings, and null configurations
         * list is replaced with an empty list.
         */
        public PrintableCapability {
            service = Objects.requireNonNullElse(service, "");
            serviceType = Objects.requireNonNullElse(serviceType, "");
            host = Objects.requireNonNullElse(host, "");
            status = Objects.requireNonNullElse(status, "");
            lastSeen = Objects.requireNonNullElse(lastSeen, "");
            configurations = Objects.requireNonNullElse(configurations, List.of());
        }
    }

    /**
     * Record representing a service configuration in printable format.
     *
     * @param name the configuration name/key
     * @param description the configuration description/value
     */
    @RegisterForReflection
    public record PrintableCapabilityConfiguration(String name, String description) {

        /**
         * Compact constructor that ensures all fields are non-null.
         *
         * <p>Null values are replaced with empty strings.
         */
        public PrintableCapabilityConfiguration {
            name = Objects.requireNonNullElse(name, "");
            description = Objects.requireNonNullElse(description, "");
        }
    }
}
