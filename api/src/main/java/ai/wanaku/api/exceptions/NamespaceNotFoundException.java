package ai.wanaku.api.exceptions;

/**
 * This exception is thrown when a specified tool cannot be located or does not exist.
 * @see WanakuException
 */
public class NamespaceNotFoundException extends WanakuException {

    /**
     * Constructs an instance of the exception with no detail message or cause.
     */
    public NamespaceNotFoundException() {
        super();
    }

    /**
     * Constructs an instance of the exception with a specified detail message.
     *
     * @param message the detail message for this exception
     */
    public NamespaceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs an instance of the exception with a specified cause and a detail message.
     *
     * @param message the detail message for this exception
     * @param cause   the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     */
    public NamespaceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance of the exception with a specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     */
    public NamespaceNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an instance of the exception with a specified detail message, cause, suppression enabled or disabled,
     * and writable stack trace enabled or disabled.
     *
     * @param message         the detail message for this exception
     * @param cause           the cause (which is saved for later retrieval by the {@link Throwable#getCause()} method)
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace  whether or not the stack trace should be writable
     */
    public NamespaceNotFoundException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Returns a new instance of this exception with a formatted string indicating that a tool could not be found.
     *
     * @param namespaceId the name of the missing tool
     * @return a new instance of this exception for the specified tool name
     */
    public static NamespaceNotFoundException forId(String namespaceId) {
        return new NamespaceNotFoundException(String.format("Namespace %s not found", namespaceId));
    }
}
