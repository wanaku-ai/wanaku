package ai.wanaku.api.types;

/**
 * Represents a reference that can be called, providing metadata and input schema information.
 *
 * A callable reference is an abstraction of an object or function that can be invoked,
 * potentially with arguments. This interface provides methods to retrieve its name, description,
 * data type, and input schema.
 *
 */
public interface CallableReference {
    /**
     * Returns the name of this callable reference.
     *
     * @return The name of this callable reference.
     */
    String getName();

    /**
     * Returns a human-readable description of this callable reference.
     *
     * @return A human-readable description of this callable reference.
     */
    String getDescription();

    /**
     * Returns the data type associated with this callable reference.
     *
     * @return The data type associated with this callable reference.
     */
    String getType();

    /**
     * Returns the input schema for this callable reference, describing its expected input structure and format.
     *
     * @return The input schema for this callable reference.
     */
    InputSchema getInputSchema();
}
