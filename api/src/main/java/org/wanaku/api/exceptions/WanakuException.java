package org.wanaku.api.exceptions;

public class WanakuException extends Exception {
    public WanakuException() {
    }

    public WanakuException(String message) {
        super(message);
    }

    public WanakuException(String message, Throwable cause) {
        super(message, cause);
    }

    public WanakuException(Throwable cause) {
        super(cause);
    }

    public WanakuException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
