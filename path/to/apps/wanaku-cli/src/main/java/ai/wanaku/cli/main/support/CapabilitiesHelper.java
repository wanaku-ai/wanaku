/**
 * Helper class for capabilities-related operations.
 */
public class CapabilitiesHelper {
    /**
     * Fetches and merges capabilities from the service, applying the label filter if provided.
     *
     * @param capabilitiesService service to fetch capabilities from
     * @param labelFilter label filter to apply (optional)
     * @return merged capabilities
     */
    public static List<Capability> fetchAndMergeCapabilities(CapabilitiesService capabilitiesService, String labelFilter) {
        List<Capability> capabilities = fetchCapabilities(capabilitiesService);
        if (labelFilter != null) {
            // Apply the label filter
            return capabilities.stream()
                    .filter(capability -> capability.getLabels().stream()
                            .anyMatch(label -> label.getName().equals(labelFilter)))
                    .collect(Collectors.toList());
        } else {
            return capabilities;
        }
    }

    /**
     * Fetches capabilities from the service.
     *
     * @param capabilitiesService service to fetch capabilities from
     * @return list of capabilities
     */
    private static List<Capability> fetchCapabilities(CapabilitiesService capabilitiesService) {
        // Implementation remains the same
        // ...
    }
}