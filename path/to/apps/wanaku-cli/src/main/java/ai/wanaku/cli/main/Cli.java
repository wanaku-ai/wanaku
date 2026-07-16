/**
 * CLI entry point.
 */
public class Cli {
    /**
     * Main method.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // ...
        String labelFilter = getLabelFilter(args);
        List<Capability> capabilities = CapabilitiesList.doCall(capabilitiesService, labelFilter);
        // ...
    }

    /**
     * Gets the label filter from the command-line arguments.
     *
     * @param args command-line arguments
     * @return label filter or null if not provided
     */
    private static String getLabelFilter(String[] args) {
        // Implementation remains the same
        // ...
    }
}