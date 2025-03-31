package ai.wanaku.routers.proxies;

import java.util.Map;

/**
 * Proxies between MCP URIs and services capable of handling them
 */
public interface Proxy {
    /**
     * The name of the proxy
     * @return the name of the proxy
     */
    String name();

    /**
     * Retrieve configurations from the service
     * @param target the target service to retrieve configurations from
     * @return A map of configurations and their descriptions
     */
    Map<String, String> getServiceConfigurations(String target);
}
