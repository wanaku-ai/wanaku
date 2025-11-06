package ai.wanaku.core.security.auth;

/**
 * Exception thrown when OIDC token operations fail.
 */
public class OidcTokenException extends Exception {

    public OidcTokenException(String message) {
        super(message);
    }

    public OidcTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
