package ai.wanaku.api.exceptions;

public class ResourceNotFoundException extends WanakuException {

    public ResourceNotFoundException() {
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceNotFoundException(Throwable cause) {
        super(cause);
    }

    public ResourceNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static ResourceNotFoundException forName(String resourceName) {
        return new ResourceNotFoundException(String.format("Resource %s not found", resourceName));
    }
}
