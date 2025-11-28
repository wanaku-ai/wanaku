/**
 * JAX-RS service interfaces for the Wanaku REST API.
 * <p>
 * This package defines the REST API endpoints for managing capabilities, tools,
 * resources, namespaces, and forwards within the Wanaku system. These interfaces
 * follow the JAX-RS specification and are implemented by the Wanaku router backend.
 * <p>
 * All service endpoints are available under the {@code /api/v1} base path and include:
 * <ul>
 *   <li>{@link ai.wanaku.core.services.api.ToolsService} - Tool management endpoints ({@code /api/v1/tools})</li>
 *   <li>{@link ai.wanaku.core.services.api.ResourcesService} - Resource management endpoints ({@code /api/v1/resources})</li>
 *   <li>{@link ai.wanaku.core.services.api.NamespacesService} - Namespace management endpoints ({@code /api/v1/namespaces})</li>
 *   <li>{@link ai.wanaku.core.services.api.ForwardsService} - Forward management endpoints ({@code /api/v1/forwards})</li>
 *   <li>{@link ai.wanaku.core.services.api.CapabilitiesService} - Capability discovery and monitoring endpoints ({@code /api/v1/capabilities})</li>
 * </ul>
 *
 * @see ai.wanaku.capabilities.sdk.api.types
 */
package ai.wanaku.core.services.api;
