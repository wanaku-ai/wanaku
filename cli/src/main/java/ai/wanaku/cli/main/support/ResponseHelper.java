package ai.wanaku.cli.main.support;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.jline.terminal.Terminal;

public final class ResponseHelper {
    private ResponseHelper() {}

    public static void commonResponseErrorHandler(Response response) throws IOException {
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            String message;

            if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                message = String.format(
                        "The server was unable to handle the request: %s%n",
                        response.getStatusInfo().getReasonPhrase());
                printer.printErrorMessage(message);
            } else {
                message = String.format(
                        "Unspecified error (status: %d, reason: %s)%n",
                        response.getStatus(), response.getStatusInfo().getReasonPhrase());
                printer.printErrorMessage(message);
            }
        }
    }
}
