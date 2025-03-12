package ai.wanaku.api.exceptions;

/**
 * This is the base exception class.
 *
 * The {@link WanakuException} class extends the built-in {@link RuntimeException} to provide
 * a custom exception hierarchy.
 */
public class WanakuException extends RuntimeException {

    /**
     * Constructs an instance of this exception with no detail message or cause.
     *
     * This constructor provides a basic implementation for instances where no additional context is needed.
     */
    public WanakuException() {
        super();
    }

    /**
     * Constructs an instance of this exception with the specified detail message and without a cause.
     *
     * @param message The message describing the reason for this exception being thrown.
     */
    public WanakuException(String message) {
        super(message);
    }

    /**
     * Constructs an instance of this exception with the specified detail message and underlying cause.
     *
     * @param message  The message describing the reason for this exception being thrown.
     * @param cause    The root cause that led to this exception being thrown.
     */
    public WanakuException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an instance of this exception with the specified underlying cause but without a detail message.
     *
     * @param cause The root cause that led to this exception being thrown.
     */
    public WanakuException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an instance of this exception with the specified detail message and underlying cause, along with suppression flags.
     *
     * @param message       The message describing the reason for this exception being thrown.
     * @param cause         The root cause that led to this exception being thrown.
     * @param enableSuppression Whether to suppress the reporting of this exception's cause.
     * @param writableStackTrace Whether to make the stack trace associated with this exception writable.
     */
    public WanakuException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
