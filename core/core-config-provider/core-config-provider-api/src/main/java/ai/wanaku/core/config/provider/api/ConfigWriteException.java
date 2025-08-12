package ai.wanaku.core.config.provider.api;

import ai.wanaku.api.exceptions.WanakuException;

/**
 * Exception thrown to indicate an error during the process of writing configuration data.
 * This exception typically wraps lower-level exceptions that occur due to I/O issues,
 * permission problems, or data formatting errors when attempting to persist
 * configuration information.
 */
public class ConfigWriteException extends WanakuException {
    public ConfigWriteException() {}

    public ConfigWriteException(String message) {
        super(message);
    }

    public ConfigWriteException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigWriteException(Throwable cause) {
        super(cause);
    }

    public ConfigWriteException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
