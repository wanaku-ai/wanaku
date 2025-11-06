package ai.wanaku.cli.main.commands;

import ai.wanaku.cli.main.support.AuthenticationInterceptor;
import ai.wanaku.cli.main.support.WanakuPrinter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import java.util.concurrent.Callable;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

public abstract class BaseCommand implements Callable<Integer> {

    public static final int EXIT_OK = 0;
    public static final int EXIT_ERROR = 1;

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Display the help and sub-commands")
    private boolean helpRequested = false;

    @CommandLine.Option(
            names = {"--token"},
            description = "Override authentication token for this command")
    protected String authTokenOverride;

    @CommandLine.Option(
            names = {"--no-auth"},
            description = "Disable authentication for this command")
    protected boolean noAuth = false;

    /**
     * Initializes and configures the REST client for communicating with the Wanaku service.
     *
     * <p>Creates a properly configured Quarkus REST client builder with the specified host URL.
     * The client is configured with appropriate timeouts and error handling for reliable
     * communication with the Wanaku API endpoints.</p>
     *
     * @param <T> the type of the service interface
     * @param clazz the Class object representing the service interface
     * @param host the base URL of the Wanaku service API (must be a valid URI)
     * @return a configured Service instance ready for API calls
     * @throws IllegalArgumentException if the host URL is invalid or malformed
     * @throws NullPointerException if host is null
     */
    protected static <T> T initService(Class<T> clazz, String host) {
        return initService(clazz, host, null, false);
    }

    protected <T> T initAuthenticatedService(Class<T> clazz, String host) {
        return initService(clazz, host, this.authTokenOverride, this.noAuth);
    }

    protected static <T> T initService(Class<T> clazz, String host, String tokenOverride, boolean noAuth) {
        if (host == null) {
            throw new NullPointerException("Host URL cannot be null");
        }

        try {
            QuarkusRestClientBuilder builder =
                    QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(host));

            if (!noAuth) {
                AuthenticationInterceptor authInterceptor = tokenOverride != null
                        ? new AuthenticationInterceptor() {
                            @Override
                            public void filter(jakarta.ws.rs.client.ClientRequestContext requestContext)
                                    throws java.io.IOException {
                                requestContext
                                        .getHeaders()
                                        .add(jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION, "Bearer " + tokenOverride);
                            }
                        }
                        : new AuthenticationInterceptor();
                builder.register(authInterceptor);
            }

            return builder.build(clazz);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid host URL: " + host, e);
        }
    }

    @Override
    public Integer call() throws Exception {
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            return doCall(terminal, printer);
        }
    }

    public abstract Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception;
}
