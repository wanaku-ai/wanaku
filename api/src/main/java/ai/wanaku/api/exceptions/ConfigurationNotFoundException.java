package ai.wanaku.api.exceptions;

/**
 * This exception can be thrown if a configuration is expected in some way, but it is not found.
 */
public class ConfigurationNotFoundException extends WanakuException {

    /**
     * Constructs a new instance of this exception without a message or cause.
     */
    public ConfigurationNotFoundException() {
    }

    /**
     * Constructs a new instance of this exception with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     */
    public ConfigurationNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ConfigurationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new instance of this exception with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public ConfigurationNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message, cause,
     * enable suppression and writable stack trace.
     *
     * @param message            the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause              the cause (which is saved for later retrieval by the {@link #getCause()} method)
     * @param enableSuppression  whether suppression is enabled
     * @param writableStackTrace whether stack traces should be writtable
     */
    public ConfigurationNotFoundException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a new instance of this exception for the given tool name.
     *
     * @param toolName the name of the tool that was not found
     * @return a new instance of this exception with a message indicating that the tool was not found
     */
    public static ConfigurationNotFoundException forName(String toolName) {
        return new ConfigurationNotFoundException(String.format("Tool %s not found", toolName));
    }
}
