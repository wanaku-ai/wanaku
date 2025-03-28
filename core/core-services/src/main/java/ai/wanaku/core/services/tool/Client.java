package ai.wanaku.core.services.tool;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.exchange.ToolInvokeRequest;

/**
 * A client is responsible for exchanging data with a service
 */
public interface Client {
    /**
     * Exchange data with a service
     * @param request The tool invocation request as received by the MCP router
     * @return Whatever response was obtained by calling the server (it must be convertible to a String)
     * @throws WanakuException if the operation cannot be executed
     */
    Object exchange(ToolInvokeRequest request) throws WanakuException;
}
