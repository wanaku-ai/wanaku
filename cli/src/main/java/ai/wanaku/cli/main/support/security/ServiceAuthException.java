package ai.wanaku.cli.main.support.security;

import ai.wanaku.api.exceptions.WanakuException;

/**
 * Exception thrown when service authentication fails in the Wanaku capabilities SDK.
 * <p>
 * This exception is used to indicate authentication-related failures when attempting
 * to authenticate with services, including invalid credentials, expired tokens,
 * insufficient permissions, or other authentication-related issues.
 * </p>
 */
public class ServiceAuthException extends WanakuException {
    /**
     * Constructs a new ServiceAuthException with no detail message.
     */
    public ServiceAuthException() {
        super();
    }

    /**
     * Constructs a new ServiceAuthException with the specified detail message.
     *
     * @param message the detail message explaining the reason for the authentication failure
     */
    public ServiceAuthException(String message) {
        super(message);
    }

    /**
     * Constructs a new ServiceAuthException with the specified detail message and cause.
     *
     * @param message the detail message explaining the reason for the authentication failure
     * @param cause the underlying cause of this exception
     */
    public ServiceAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ServiceAuthException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public ServiceAuthException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new ServiceAuthException with the specified detail message, cause,
     * suppression enabled or disabled, and writable stack trace enabled or disabled.
     *
     * @param message the detail message explaining the reason for the authentication failure
     * @param cause the underlying cause of this exception
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     */
    public ServiceAuthException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
