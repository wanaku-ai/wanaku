package ai.wanaku.api.types.providers;

/**
 * Defines types of downstream services
 */
public enum ServiceType {
    /**
     * Provides resources
     */
    RESOURCE_PROVIDER("resource-provider", 1),

    /**
     * Invokes tools
     */
    TOOL_INVOKER("tool-invoker", 2),

    /**
     * This can do both: invoke tools and provide resources
     */
    MULTI_CAPABILITY("multi-capability", 3);

    private final String value;
    private final int intValue;

    ServiceType(String value, int intValue) {
        this.value = value;
        this.intValue = intValue;
    }

    /**
     * The string value representing the service type
     * @return the string value representing the service type
     */
    public String asValue() {
        return value;
    }

    /**
     * Gets the integer value representing this service type.
     *
     * @return the integer value
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Converts an integer value to the corresponding ServiceType.
     *
     * @param value the integer value (1 for RESOURCE_PROVIDER, 2 for TOOL_INVOKER, 3 for MULTI_CAPABILITY)
     * @return the corresponding ServiceType
     * @throws IllegalArgumentException if the value does not correspond to any ServiceType
     */
    public static ServiceType fromIntValue(int value) {
        if (value == 1) {
            return RESOURCE_PROVIDER;
        }
        if (value == 2) {
            return TOOL_INVOKER;
        }

        if (value == 3) {
            return MULTI_CAPABILITY;
        }

        throw new IllegalArgumentException("Invalid service type: " + value);
    }
}
