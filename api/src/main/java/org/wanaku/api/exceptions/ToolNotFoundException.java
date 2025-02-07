package org.wanaku.api.exceptions;

public class ToolNotFoundException extends WanakuException {

    public ToolNotFoundException() {
    }

    public ToolNotFoundException(String message) {
        super(message);
    }

    public ToolNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ToolNotFoundException(Throwable cause) {
        super(cause);
    }

    public ToolNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public static ToolNotFoundException forName(String toolName) {
        return new ToolNotFoundException(String.format("Tool %s not found", toolName));
    }
}
