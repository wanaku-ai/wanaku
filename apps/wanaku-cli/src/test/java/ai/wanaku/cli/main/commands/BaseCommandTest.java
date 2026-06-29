package ai.wanaku.cli.main.commands;

import javax.net.ssl.SSLContext;

import java.net.http.HttpClient;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BaseCommandTest {

    /** Minimal concrete subclass used to exercise {@link BaseCommand} behaviour. */
    private static class TestCommand extends BaseCommand {
        @Override
        public Integer doCall(Terminal terminal, WanakuPrinter printer) {
            return EXIT_OK;
        }

        HttpClient buildHttpClient(boolean insecureFlag) {
            this.insecure = insecureFlag;
            return createHttpClient();
        }
    }

    // ---- --insecure flag tests ----

    @Test
    void createHttpClientWithInsecureFlag() throws Exception {
        TestCommand cmd = new TestCommand();

        HttpClient client = cmd.buildHttpClient(true);

        // A custom SSLContext must have been set — it must differ from the JVM default.
        assertNotSame(
                SSLContext.getDefault(), client.sslContext(), "Expected a custom SSLContext when --insecure is set");

        // InsecureSSLHelper.createInsecureSSLParameters() sets endpointIdentificationAlgorithm to null,
        // which disables hostname verification in java.net.http.HttpClient.
        assertNull(
                client.sslParameters().getEndpointIdentificationAlgorithm(),
                "Expected hostname verification to be disabled (endpointIdentificationAlgorithm == null)");
    }

    @Test
    void createHttpClientWithoutInsecureFlag() throws Exception {
        TestCommand cmd = new TestCommand();

        HttpClient client = cmd.buildHttpClient(false);

        // When --insecure is not set the builder must not override the JVM default SSLContext.
        assertSame(
                SSLContext.getDefault(),
                client.sslContext(),
                "Expected the JVM default SSLContext when --insecure is not set");
    }
}
