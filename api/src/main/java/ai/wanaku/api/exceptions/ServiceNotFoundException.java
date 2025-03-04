package ai.wanaku.api.exceptions;

public class ServiceNotFoundException extends WanakuException {

    public ServiceNotFoundException() {
    }

    public ServiceNotFoundException(String message) {
        super(message);
    }

    public ServiceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServiceNotFoundException(Throwable cause) {
        super(cause);
    }

    public ServiceNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static ServiceNotFoundException forName(String toolName) {
        return new ServiceNotFoundException(String.format("Tool %s not found", toolName));
    }
}
