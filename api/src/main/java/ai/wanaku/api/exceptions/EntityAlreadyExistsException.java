package ai.wanaku.api.exceptions;

/**
 * This exception is thrown when an attempt is made to create an entity that already exists.
 *
 * This custom exception extends {@link WanakuException} and provides a
 * clear indication of when a requested entity cannot be created because it already exists.
 * It is mapped to HTTP status code 409 (Conflict) by the router.
 */
public class EntityAlreadyExistsException extends WanakuException {

    /**
     * Default constructor for the exception, providing no additional information.
     */
    public EntityAlreadyExistsException() {
        super();
    }

    /**
     * Constructor that allows specifying a custom error message for the exception.
     *
     * @param message The detailed message describing which entity already exists.
     */
    public EntityAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Constructor that provides both a custom error message and an underlying cause for the exception.
     *
     * @param message   The detailed message describing which entity already exists.
     * @param cause     The root cause of the exception, providing additional context.
     */
    public EntityAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor that specifies an underlying cause for the exception without a custom error message.
     *
     * @param cause The root cause of the exception, providing additional context.
     */
    public EntityAlreadyExistsException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor that allows specifying both a custom error message and an underlying cause for the exception,
     * along with options to enable suppression or writable stack trace.
     *
     * @param message         The detailed message describing which entity already exists.
     * @param cause           The root cause of the exception, providing additional context.
     * @param enableSuppression Whether to allow suppressing this exception during exception propagation.
     * @param writableStackTrace Whether to include a stack trace with this exception.
     */
    public EntityAlreadyExistsException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Factory method that creates a new instance of the exception for a given entity name,
     * providing a pre-formatted error message indicating that the entity already exists.
     *
     * @param entityName The name or identifier of the entity that already exists.
     * @return A new instance of the {@link EntityAlreadyExistsException} class with a custom error message.
     */
    public static EntityAlreadyExistsException forName(String entityName) {
        return new EntityAlreadyExistsException(String.format("Entity %s already exists", entityName));
    }
}
