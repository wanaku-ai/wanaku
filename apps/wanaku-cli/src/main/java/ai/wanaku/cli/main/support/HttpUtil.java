package ai.wanaku.cli.main.support;

import java.net.http.HttpClient;
import java.time.Duration;
import ai.wanaku.cli.main.support.security.InsecureSSLHelper;

/**
 * Factory methods for {@link java.net.http.HttpClient}.
 * <p>
 * Centralises the optional insecure-SSL wiring so that callers never need to
 * reference {@link InsecureSSLHelper} directly.
 * </p>
 * <p>
 * WARNING: The insecure variants completely disable SSL certificate validation
 * and hostname verification. Use only for development/testing with self-signed
 * certificates.
 * </p>
 */
public final class HttpUtil {

    private HttpUtil() {}

    /**
     * Returns a plain, secure {@link HttpClient}.
     *
     * @return a new HttpClient with default (secure) settings
     */
    public static HttpClient newHttpClient() {
        return HttpClient.newHttpClient();
    }

    /**
     * Returns an {@link HttpClient}, optionally configured to skip SSL certificate
     * and hostname verification.
     *
     * @param insecure when {@code true}, disables SSL verification (development only)
     * @return a configured HttpClient
     */
    public static HttpClient newHttpClient(boolean insecure) {
        if (!insecure) {
            return HttpClient.newHttpClient();
        }
        return applyInsecureSsl(HttpClient.newBuilder()).build();
    }

    /**
     * Returns an {@link HttpClient}, optionally configured to skip SSL certificate
     * and hostname verification, with a TCP-level connect timeout.
     *
     * @param insecure       when {@code true}, disables SSL verification (development only)
     * @param connectTimeout TCP connect timeout to apply to the client
     * @return a configured HttpClient
     */
    public static HttpClient newHttpClient(boolean insecure, Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (insecure) {
            builder = applyInsecureSsl(builder);
        }
        return builder.build();
    }

    private static HttpClient.Builder applyInsecureSsl(HttpClient.Builder builder) {
        try {
            return builder.sslContext(InsecureSSLHelper.createInsecureSSLContext())
                    .sslParameters(InsecureSSLHelper.createInsecureSSLParameters());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}
