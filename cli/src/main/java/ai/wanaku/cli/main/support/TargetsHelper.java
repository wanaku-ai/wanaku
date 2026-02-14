package ai.wanaku.cli.main.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.formatLastSeenTimestamp;

public class TargetsHelper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TargetsHelper() {}

    /**
     * Converts a map of service states to a list of printable target representations.
     * Each target is represented as a map containing display-friendly key-value pairs
     * suitable for reporting or user interface purposes.
     *
     * @param states a map where keys are service names and values are lists of
     *               {@link ActivityRecord} objects representing the activities
     *               for each service. Must not be null.
     * @return a list of maps, where each map represents a single target with the
     *         following keys:
     *         <ul>
     *           <li>"id" - the activity record ID</li>
     *           <li>"service" - the service name</li>
     *           <li>"active" - string representation of the activity status ("true"/"false")</li>
     *           <li>"last_seen" - formatted timestamp of when the activity was last seen</li>
     *         </ul>
     *         Returns an empty list if the input map is empty or contains no activities.
     * @throws NullPointerException if {@code states} is null or contains null values
     * @throws RuntimeException if {@code formatLastSeenTimestamp} fails for any activity record
     *
     * @see ActivityRecord
     * @since 1.0
     */
    public static List<Map<String, String>> getPrintableTargets(Map<String, List<ActivityRecord>> states) {

        if (states == null) {
            throw new IllegalArgumentException("States map cannot be null");
        }

        return states.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> entry.getValue().stream()
                        .filter(Objects::nonNull)
                        .map(activityRecord -> createTargetMap(entry.getKey(), activityRecord)))
                .toList();
    }

    /**
     * Creates a printable target map from a service name and activity record.
     * This is a helper method to improve readability and maintainability.
     *
     * @param serviceName the name of the service
     * @param activityRecord the activity record to convert
     * @return a map containing the printable representation of the target
     */
    public static Map<String, String> createTargetMap(String serviceName, ActivityRecord activityRecord) {
        Map<String, String> targetMap = new HashMap<>();
        targetMap.put("id", activityRecord.getId());
        targetMap.put("service", serviceName);
        targetMap.put("active", String.valueOf(activityRecord.isActive()));
        targetMap.put("last_seen", formatLastSeenTimestamp(activityRecord)); // Note: underscore for consistency

        return targetMap;
    }
}
