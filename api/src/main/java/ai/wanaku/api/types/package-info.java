/**
 * Core type definitions and data models for the Wanaku system.
 * <p>
 * This package contains the fundamental data structures used throughout Wanaku,
 * including entity types for capabilities, tools, resources, namespaces, and
 * their associated metadata. These types form the foundation of the Wanaku
 * API and are used for data exchange between components.
 * <p>
 * Key types include:
 * <ul>
 *   <li>{@link ai.wanaku.api.types.WanakuEntity} - Base interface for all entities</li>
 *   <li>{@link ai.wanaku.api.types.ToolReference} - Tool capability references</li>
 *   <li>{@link ai.wanaku.api.types.ResourceReference} - Resource capability references</li>
 *   <li>{@link ai.wanaku.api.types.ForwardReference} - Forward proxy references</li>
 *   <li>{@link ai.wanaku.api.types.Namespace} - Namespace entities for logical grouping</li>
 *   <li>{@link ai.wanaku.api.types.NameNamespacePair} - Name-namespace identifier pairs</li>
 * </ul>
 *
 * @see ai.wanaku.api.types.io
 * @see ai.wanaku.api.types.discovery
 */
package ai.wanaku.api.types;
