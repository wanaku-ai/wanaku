package ai.wanaku.api.exceptions;

/**
 * This exception can be thrown if the resource is not found.
 *
 * This custom exception extends {@link WanakuException} and provides a
 * clear indication of when a requested resource cannot be located or accessed.
 */
public class ResourceNotFoundException extends WanakuException {

    /**
     * Default constructor for the exception, providing no additional information.
     */
    public ResourceNotFoundException() {}

    /**
     * Constructor that allows specifying a custom error message for the exception.
     *
     * @param message The detailed message describing why the resource was not found.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructor that provides both a custom error message and an underlying cause for the exception.
     *
     * @param message   The detailed message describing why the resource was not found.
     * @param cause     The root cause of the exception, providing additional context.
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor that specifies an underlying cause for the exception without a custom error message.
     *
     * @param cause The root cause of the exception, providing additional context.
     */
    public ResourceNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor that allows specifying both a custom error message and an underlying cause for the exception,
     * along with options to enable suppression or writable stack trace.
     *
     * @param message         The detailed message describing why the resource was not found.
     * @param cause           The root cause of the exception, providing additional context.
     * @param enableSuppression Whether to allow suppressing this exception during exception propagation.
     * @param writableStackTrace Whether to include a stack trace with this exception.
     */
    public ResourceNotFoundException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Factory method that creates a new instance of the exception for a given resource name,
     * providing a pre-formatted error message indicating that the resource was not found.
     *
     * @param resourceName The name or identifier of the resource that was not found.
     * @return A new instance of the {@link ResourceNotFoundException} class with a custom error message.
     */
    public static ResourceNotFoundException forName(String resourceName) {
        return new ResourceNotFoundException(String.format("Resource %s not found", resourceName));
    }
}
