package ai.wanaku.api.exceptions;

public class ConfigurationNotFoundException extends WanakuException {

    public ConfigurationNotFoundException() {
    }

    public ConfigurationNotFoundException(String message) {
        super(message);
    }

    public ConfigurationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationNotFoundException(Throwable cause) {
        super(cause);
    }

    public ConfigurationNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static ConfigurationNotFoundException forName(String toolName) {
        return new ConfigurationNotFoundException(String.format("Tool %s not found", toolName));
    }
}
