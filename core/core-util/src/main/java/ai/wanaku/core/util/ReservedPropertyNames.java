package ai.wanaku.core.util;

/**
 * Reserved argument names
 */
public class ReservedPropertyNames {

    /**
     * Indicate that the argument is intended to use as a tool/service configuration
     */
    public static final String TARGET_CONFIGURATION = "configuration";

    /**
     * Indicate that the argument is intended as header for services/tools that support them
     */
    public static final String TARGET_HEADER = "header";

    /**
     * Indicate that the argument is intended as cookie for services/tools that support them
     */
    public static final String TARGET_COOKIE = "cookie";

    /**
     * Indicate that the argument is intended to be used on the service
     *
     * This is NOOP, since configurations now need to be done via references
     */
    @Deprecated(forRemoval = true)
    public static final String SCOPE_SERVICE = "service";

    /**
     * Indicate that the argument is intended to be used on the service endpoint
     *
     * This is NOOP, since configurations now need to be done via references
     */
    @Deprecated(forRemoval = true)
    public static final String SCOPE_SERVICE_ENDPOINT = "service-endpoint";
}
