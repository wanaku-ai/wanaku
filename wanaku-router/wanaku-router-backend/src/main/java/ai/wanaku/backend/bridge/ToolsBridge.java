package ai.wanaku.backend.bridge;

import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.ToolExecutor;

/**
 * Proxies between MCP URIs and Camel components capable of handling them.
 * <p>
 * This interface defines the contract for tool proxies, which are responsible
 * for provisioning tool configurations and providing access to tool executors.
 * The separation between proxy concerns and tool execution is achieved through
 * composition, where the proxy provides a ToolExecutor for handling invocations.
 */
public interface ToolsBridge extends Bridge {
    /**
     * Provision a configuration in the service.
     *
     * @param payload the payload to provision in the service
     * @return A provisioning reference instance
     */
    ProvisioningReference provision(ToolPayload payload);

    /**
     * Gets the tool executor for this proxy.
     * <p>
     * The executor handles the actual tool invocation logic, allowing
     * separation of proxy management from execution concerns.
     *
     * @return the tool executor
     */
    ToolExecutor getExecutor();
}
