package ai.wanaku.api.exceptions;

public class InvalidResponseTypeException extends WanakuException {

    public InvalidResponseTypeException() {
    }

    public InvalidResponseTypeException(String message) {
        super(message);
    }

    public InvalidResponseTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidResponseTypeException(Throwable cause) {
        super(cause);
    }

    public InvalidResponseTypeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static InvalidResponseTypeException forName(String toolName) {
        return new InvalidResponseTypeException(String.format("Tool %s not found", toolName));
    }
}
