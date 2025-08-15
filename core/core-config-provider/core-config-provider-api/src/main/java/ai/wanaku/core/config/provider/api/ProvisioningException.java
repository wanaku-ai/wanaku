package ai.wanaku.core.config.provider.api;

import ai.wanaku.api.exceptions.WanakuException;

/**
 * Exception thrown to indicate an error during the provisioning process.
 * This exception is typically used when an operation to set up or supply
 * a new configuration, secret, or other resource fails. It can wrap
 * lower-level exceptions such as I/O errors, network issues, or
 * problems with external systems involved in the provisioning.
 */
public class ProvisioningException extends WanakuException {
    public ProvisioningException() {}

    public ProvisioningException(String message) {
        super(message);
    }

    public ProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProvisioningException(Throwable cause) {
        super(cause);
    }

    public ProvisioningException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
