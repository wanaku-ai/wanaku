package ai.wanaku.core.mcp.common.resolvers;

/**
 * Base interface for resolvers in the MCP (Model Context Protocol) system.
 * <p>
 * Resolvers are responsible for consuming MCP requests and determining which
 * service provider (tool acquirer or resource acquirer) should handle them.
 * This interface serves as a marker interface for all resolver types within
 * the Wanaku MCP infrastructure.
 * <p>
 * Specialized resolver interfaces extend this base interface to provide
 * specific resolution strategies for different capability types:
 * <ul>
 *   <li>{@link ToolsResolver} - Resolves tool capability requests</li>
 *   <li>{@link ResourceResolver} - Resolves resource capability requests</li>
 *   <li>{@link ForwardResolver} - Resolves forward proxy requests</li>
 * </ul>
 *
 * @see ToolsResolver
 * @see ResourceResolver
 * @see ForwardResolver
 */
public interface Resolver {}
