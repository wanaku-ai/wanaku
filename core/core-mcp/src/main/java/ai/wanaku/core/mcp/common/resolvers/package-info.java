/**
 * Resolver interfaces for the MCP capability resolution system.
 * <p>
 * This package contains the core resolver interfaces that determine how MCP requests
 * are routed to appropriate service providers. Resolvers are responsible for:
 * <ul>
 *   <li>Provisioning capabilities with their configuration and secrets</li>
 *   <li>Resolving capability references to concrete implementations</li>
 *   <li>Managing the lifecycle of registered capabilities</li>
 * </ul>
 * <p>
 * Key resolver types:
 * <ul>
 *   <li>{@link ai.wanaku.core.mcp.common.resolvers.ToolsResolver} - Resolves tool capabilities</li>
 *   <li>{@link ai.wanaku.core.mcp.common.resolvers.ResourceResolver} - Resolves resource capabilities</li>
 *   <li>{@link ai.wanaku.core.mcp.common.resolvers.ForwardResolver} - Resolves forward proxies</li>
 * </ul>
 *
 * @see ai.wanaku.core.mcp.common.resolvers.Resolver
 * @see ai.wanaku.core.mcp.common.resolvers.util
 */
package ai.wanaku.core.mcp.common.resolvers;
