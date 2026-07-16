/**
 * List capabilities.
 */
public class CapabilitiesList {
    /**
     * Calls the capabilities list endpoint.
     *
     * @param capabilitiesService service to fetch capabilities from
     * @param labelFilter label filter to apply (optional)
     * @return list of capabilities
     */
    public static List<Capability> doCall(CapabilitiesService capabilitiesService, String labelFilter) {
        List<Capability> capabilities = CapabilitiesHelper.fetchAndMergeCapabilities(capabilitiesService, labelFilter);
        return capabilities;
    }
}