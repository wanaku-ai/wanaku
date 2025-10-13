package ai.wanaku.api.types;

/**
 * Represents a reference to a callable entity that can be invoked with arguments.
 * <p>
 * A callable reference is an abstraction for any object or function that can be invoked,
 * potentially with arguments. This interface provides metadata about the callable entity
 * including its name, description, type, input schema, and namespace.
 * </p>
 * <p>
 * This interface is implemented by various tool and capability reference types to provide
 * a common contract for invocable entities in the Wanaku system.
 * </p>
 *
 * @see ToolReference
 * @see RemoteToolReference
 */
public interface CallableReference {
    /**
     * Gets the name of this callable reference.
     *
     * @return the name of this callable reference
     */
    String getName();

    /**
     * Gets the human-readable description of this callable reference.
     * <p>
     * The description provides information about what this callable does,
     * its purpose, and how it should be used.
     * </p>
     *
     * @return a human-readable description of this callable reference
     */
    String getDescription();

    /**
     * Gets the type associated with this callable reference.
     * <p>
     * The type typically indicates the category or implementation mechanism
     * of the callable (e.g., "http", "function", "tool").
     * </p>
     *
     * @return the type associated with this callable reference
     */
    String getType();

    /**
     * Gets the input schema for this callable reference.
     * <p>
     * The input schema describes the expected structure, format, and validation
     * rules for arguments that can be passed to this callable.
     * </p>
     *
     * @return the input schema for this callable reference
     */
    InputSchema getInputSchema();

    /**
     * Gets the namespace in which this callable reference is registered.
     * <p>
     * The namespace provides logical grouping and isolation for callable
     * references within the Wanaku system.
     * </p>
     *
     * @return the namespace identifier
     */
    String getNamespace();
}
