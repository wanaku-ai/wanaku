package ai.wanaku.cli.main.support.security;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

/**
 * Utility for creating insecure SSL contexts that skip certificate verification.
 * <p>
 * WARNING: Only use for development/testing with self-signed certificates.
 * This completely disables SSL certificate validation and hostname verification,
 * making connections vulnerable to man-in-the-middle attacks.
 * </p>
 */
public class InsecureSSLHelper {

    private InsecureSSLHelper() {}

    private static final X509ExtendedTrustManager[] TRUST_ALL = new X509ExtendedTrustManager[] {
        new X509ExtendedTrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) {}
        }
    };

    private static final HostnameVerifier TRUST_ALL_HOSTS = (hostname, session) -> true;

    /**
     * Creates an insecure SSL context that trusts all certificates.
     *
     * @return an SSLContext configured to trust all certificates
     * @throws GeneralSecurityException if SSL context creation fails
     */
    public static SSLContext createInsecureSSLContext() throws GeneralSecurityException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, TRUST_ALL, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Returns a hostname verifier that accepts all hostnames.
     *
     * @return a HostnameVerifier that always returns true
     */
    public static HostnameVerifier getInsecureHostnameVerifier() {
        return TRUST_ALL_HOSTS;
    }

    /**
     * Creates an insecure SSL socket factory that trusts all certificates.
     *
     * @return an SSLSocketFactory configured to trust all certificates
     * @throws GeneralSecurityException if SSL socket factory creation fails
     */
    public static SSLSocketFactory getInsecureSSLSocketFactory() throws GeneralSecurityException {
        return createInsecureSSLContext().getSocketFactory();
    }

    /**
     * Creates SSL parameters that disable endpoint identification (hostname verification).
     * This is required for java.net.http.HttpClient to skip hostname verification.
     *
     * @return SSLParameters with endpoint identification disabled
     */
    public static SSLParameters createInsecureSSLParameters() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm(null); // Disable hostname verification
        return sslParameters;
    }
}
