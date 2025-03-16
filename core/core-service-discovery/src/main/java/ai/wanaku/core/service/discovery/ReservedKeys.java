package ai.wanaku.core.service.discovery;

import java.util.Set;

import ai.wanaku.core.mcp.providers.ServiceType;

/**
 * Reserved keys
 */
class ReservedKeys {
    /**
     * This key stores the target address used by the router
     */
    public static final String WANAKU_TARGET_ADDRESS = "wanaku-target-address";

    /**
     * This key stores the target address used by the router
     */
    public static final String WANAKU_TARGET_TYPE = "wanaku-target-type";

    /**
     * This key stores the set of Wanaku resource services
     */
    public static final String WANAKU_SERVICES_RESOURCES = "wanaku-services-resources";

    /**
     * This key stores the set of Wanaku tools services
     */
    public static final String WANAKU_SERVICES_TOOLS = "wanaku-services-tools";


    public static final Set<String> ALL_KEYS = Set.of(WANAKU_TARGET_ADDRESS, WANAKU_TARGET_TYPE);

    public static String getServiceKey(ServiceType serviceType) {
        switch (serviceType) {
            case TOOL_INVOKER: return ReservedKeys.WANAKU_TARGET_ADDRESS;
            case RESOURCE_PROVIDER: return ReservedKeys.WANAKU_SERVICES_RESOURCES;
        }

        throw new IllegalArgumentException("Unknown service type: " + serviceType);
    }
}
