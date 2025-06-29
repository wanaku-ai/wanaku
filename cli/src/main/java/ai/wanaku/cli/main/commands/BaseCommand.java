package ai.wanaku.cli.main.commands;

import ai.wanaku.cli.main.support.WanakuPrinter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.net.URI;
import java.util.concurrent.Callable;

public abstract class BaseCommand implements Callable<Integer> {

    public static final int  EXIT_OK = 0;
    public static final int  EXIT_ERROR = 1;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;


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
        if (host == null) {
            throw new NullPointerException("Host URL cannot be null");
        }

        try {
            return QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(host))
                    .build(clazz);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid host URL: " + host, e);
        }
    }

    @Override
    public Integer call() throws Exception {
        try (Terminal terminal = WanakuPrinter.terminalInstance() ) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            return  doCall(terminal, printer);
        }
    }

    public abstract Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception;
}
