package ai.wanaku.backend.proxies;

import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.mcp.common.Tool;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ToolsProxy extends Proxy, Tool {
    /**
     * Provision a configuration in the service
     *
     * @param payload the payload to provision in the service
     * @return A provisioning reference instance
     */
    ProvisioningReference provision(ToolPayload payload);
}
