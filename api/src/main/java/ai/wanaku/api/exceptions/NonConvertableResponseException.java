package ai.wanaku.api.exceptions;

public class NonConvertableResponseException extends WanakuException {

    public NonConvertableResponseException() {
    }

    public NonConvertableResponseException(String message) {
        super(message);
    }

    public NonConvertableResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonConvertableResponseException(Throwable cause) {
        super(cause);
    }

    public NonConvertableResponseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static NonConvertableResponseException forName(String toolName) {
        return new NonConvertableResponseException(String.format("Tool %s not found", toolName));
    }
}
