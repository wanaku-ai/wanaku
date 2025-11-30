/**
 * Persistence layer API for the Wanaku system.
 * <p>
 * This package defines repository interfaces for data access and persistence
 * operations within Wanaku. The repositories provide CRUD operations and
 * query capabilities for managing entities such as tools, resources, namespaces,
 * and forward references.
 * <p>
 * The persistence layer is designed to be implementation-agnostic, with concrete
 * implementations provided in separate modules (e.g., core-persistence-infinispan).
 * <p>
 * Key repository interfaces include:
 * <ul>
 *   <li>{@link ai.wanaku.core.persistence.api.WanakuRepository} - Base repository interface</li>
 *   <li>{@link ai.wanaku.core.persistence.api.ToolReferenceRepository} - Tool reference persistence</li>
 *   <li>{@link ai.wanaku.core.persistence.api.ResourceReferenceRepository} - Resource reference persistence</li>
 *   <li>{@link ai.wanaku.core.persistence.api.ForwardReferenceRepository} - Forward reference persistence</li>
 *   <li>{@link ai.wanaku.core.persistence.api.NamespaceRepository} - Namespace persistence</li>
 * </ul>
 *
 * @see ai.wanaku.capabilities.sdk.api.types
 */
package ai.wanaku.core.persistence.api;
