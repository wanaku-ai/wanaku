package ai.wanaku.api.exceptions;

/**
 * This exception can be thrown if a response is not convertible to the required type.
 *
 * Typically used when attempting to deserialize or convert a response from a service or API,
 * but encountering issues due to mismatched types, formats, or other errors.
 */
public class NonConvertableResponseException extends WanakuException {

    /**
     * Constructs a new instance of this exception without a message or cause.
     */
    public NonConvertableResponseException() {
    }

    /**
     * Constructs a new instance of this exception with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     */
    public NonConvertableResponseException(String message) {
        super(message);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public NonConvertableResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new instance of this exception with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public NonConvertableResponseException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new instance of this exception with the specified detail message, cause,
     * enable suppression and writable stack trace.
     *
     * @param message        the detail message (which is saved for later retrieval by the {@link #getMessage()} method)
     * @param cause          the cause (which is saved for later retrieval by the {@link #getCause()} method)
     * @param enableSuppression whether or not suppression is enabled
     * @param writableStackTrace whether or not stack traces should be writtable
     */
    public NonConvertableResponseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a new instance of this exception for the given tool name.
     *
     * Note that this method is incorrectly named - it should probably be renamed to something like
     * {@code forResponseType} or similar. This will throw an exception with a message indicating
     * that the response was not convertible to the required type.
     *
     * @param toolName the name of the tool (or response) that could not be converted to the required type
     * @return a new instance of this exception with a message indicating that the response was not convertible
     */
    public static NonConvertableResponseException forName(String toolName) {
        return new NonConvertableResponseException(String.format("Tool %s not found", toolName));
    }
}
