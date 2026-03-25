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
 *
 * @see ai.wanaku.backend.core.mcp.common.resolvers.Resolver
 */
package ai.wanaku.backend.core.mcp.common.resolvers;
