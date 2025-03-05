package ai.wanaku.core.service.discovery;

import java.util.Collections;
import java.util.Set;

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


    public static final Set<String> ALL_KEYS = Set.of(WANAKU_TARGET_ADDRESS, WANAKU_TARGET_TYPE);
}
