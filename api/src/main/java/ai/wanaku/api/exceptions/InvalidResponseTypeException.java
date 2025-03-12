package ai.wanaku.api.exceptions;

/**
 * This exception can be thrown if a response of certain type is expected, but it came differently.
 *
 * Typically used to indicate that an API or service returned a response in an unexpected format,
 * such as an Integer object instead of String, or vice versa.
 */
public class InvalidResponseTypeException extends WanakuException {

    /**
     * Constructs a new instance of this exception without a message or cause.
     */
    public InvalidResponseTypeException() {
    }

    /**
     * Constructs a new instance of this exception with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     */
    public InvalidResponseTypeException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public InvalidResponseTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new instance of this exception with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public InvalidResponseTypeException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message, cause,
     * enable suppression and writable stack trace.
     *
     * @param message        the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause          the cause (which is saved for later retrieval by the {@link #getCause()} method)
     * @param enableSuppression whether suppression is enabled
     * @param writableStackTrace whether stack traces should be writtable
     */
    public InvalidResponseTypeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a new instance of this exception for the given tool name.
     *
     * Note that this method is incorrectly named - it should probably be renamed to something like
     * {@code forResponseType} or similar. This will throw an exception with a message indicating
     * that the response type was not found.
     *
     * @param toolName the name of the tool (or response type) that was expected but not found
     * @return a new instance of this exception with a message indicating that the response type was not found
     */
    public static InvalidResponseTypeException forName(String toolName) {
        return new InvalidResponseTypeException(String.format("Tool %s not found", toolName));
    }
}
