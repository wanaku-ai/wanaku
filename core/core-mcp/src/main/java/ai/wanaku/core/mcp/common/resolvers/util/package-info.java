/**
 * Utility resolver implementations for testing and default behavior.
 * <p>
 * This package contains no-operation (noop) implementations of the core resolver
 * interfaces. These implementations are useful for:
 * <ul>
 *   <li>Unit testing scenarios where actual resolution is not needed</li>
 *   <li>Providing default/fallback behavior when no real resolver is configured</li>
 *   <li>Serving as examples or base classes for custom resolver implementations</li>
 * </ul>
 * <p>
 * All noop resolvers provide empty or minimal implementations that never throw
 * exceptions and return empty results.
 *
 * @see ai.wanaku.core.mcp.common.resolvers
 */
package ai.wanaku.core.mcp.common.resolvers.util;
