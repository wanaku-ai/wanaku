package ai.wanaku.core.mcp.providers;

/**
 * Defines types of downstream services
 */
public enum ServiceType {
    /**
     * Provides resources
     */
    RESOURCE_PROVIDER("resource-provider"),

    /**
     * Invokes tools
     */
    TOOL_INVOKER("tool-invoker");

    private String value;

    ServiceType(String value) {
        this.value = value;
    }

    /**
     * The string value representing the service type
     * @return
     */
    public String asValue() {
        return value;
    }

    public static ServiceType fromValue(String value) {
        switch (value) {
            case "resource-provider": return RESOURCE_PROVIDER;
            case "tool-invoker": return TOOL_INVOKER;
            default: throw new IllegalArgumentException("Value " + value + " is not a valid service type");
        }
    }
}
