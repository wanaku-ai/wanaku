/**
 * Proxy infrastructure for routing MCP requests to backend services.
 * <p>
 * This package contains proxy implementations that mediate between MCP URIs
 * and the backend services capable of handling them. Proxies support various
 * transport mechanisms and protocols for communicating with MCP servers.
 * <p>
 * Common proxy types include:
 * <ul>
 *   <li>HTTP/REST proxies for web-based MCP servers</li>
 *   <li>SSE (Server-Sent Events) proxies for streaming MCP servers</li>
 *   <li>STDIO proxies for process-based MCP servers</li>
 * </ul>
 *
 * @see ai.wanaku.backend.proxies.Proxy
 */
package ai.wanaku.backend.proxies;
