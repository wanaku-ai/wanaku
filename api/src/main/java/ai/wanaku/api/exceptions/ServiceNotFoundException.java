package ai.wanaku.api.exceptions;

/**
 * This exception is thrown when a specified service cannot be located or does not exist.
 *
 * @see WanakuException
 */
public class ServiceNotFoundException extends WanakuException {

    /**
     * Constructs an instance of the exception with no detail message or cause.
     */
    public ServiceNotFoundException() {
        super();
    }

    /**
     * Constructs an instance of the exception with a specified detail message.
     *
     * @param message the detail message for this exception
     */
    public ServiceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs an instance of the exception with a specified cause and a detail message.
     *
     * @param message the detail message for this exception
     * @param cause   the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     */
    public ServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance of the exception with a specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     */
    public ServiceNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an instance of the exception with a specified detail message, cause, suppression enabled or disabled,
     * and writable stack trace enabled or disabled.
     *
     * @param message         the detail message for this exception
     * @param cause           the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     * @param enableSuppression whether suppression is enabled or disabled
     * @param writableStackTrace  whether the stack trace should be writable
     */
    public ServiceNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Returns a new instance of this exception with a formatted string indicating that a service could not be found.
     *
     * @param serviceName the name of the missing service
     * @return a new instance of this exception for the specified service name
     */
    public static ServiceNotFoundException forName(String serviceName) {
        return new ServiceNotFoundException(String.format("Service %s not found", serviceName));
    }
}
