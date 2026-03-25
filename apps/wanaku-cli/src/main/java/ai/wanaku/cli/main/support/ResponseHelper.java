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

            // Try to read the response body for more details
            String responseBody = "";
            try {
                if (response.hasEntity()) {
                    responseBody = response.readEntity(String.class);
                }
            } catch (Exception e) {
                // Ignore if we can't read the body
            }

            if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                message = String.format(
                        "The server was unable to handle the request: %s%s%n",
                        response.getStatusInfo().getReasonPhrase(),
                        responseBody.isEmpty() ? "" : "\nDetails: " + responseBody);
                printer.printErrorMessage(message);
            } else {
                message = String.format(
                        "Error (status: %d, reason: %s)%s%n",
                        response.getStatus(),
                        response.getStatusInfo().getReasonPhrase(),
                        responseBody.isEmpty() ? "" : "\nDetails: " + responseBody);
                printer.printErrorMessage(message);
            }
        }
    }
}
