package ai.wanaku.cli.main.support;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;

public final class ResponseHelper {
    private ResponseHelper() {}

    public static int handleNotFound(
            WebApplicationException ex, String entityType, String identifier, WanakuPrinter printer)
            throws IOException {
        Response response = ex.getResponse();
        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            printer.printWarningMessage(String.format(
                    "%s '%s' not found: %s",
                    entityType, identifier, response.getStatusInfo().getReasonPhrase()));
            return BaseCommand.EXIT_ERROR;
        }
        commonResponseErrorHandler(response);
        return BaseCommand.EXIT_ERROR;
    }

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
            } catch (Exception ignored) {
                // Ignore if we can't read the body
            }

            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                message = String.format(
                        "The requested resource was not found: %s%s",
                        response.getStatusInfo().getReasonPhrase(),
                        responseBody.isEmpty() ? "" : "\nDetails: " + responseBody);
                printer.printErrorMessage(message);
            } else if (response.getStatus() == Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
                message = String.format(
                        "The server was unable to handle the request: %s%s",
                        response.getStatusInfo().getReasonPhrase(),
                        responseBody.isEmpty() ? "" : "\nDetails: " + responseBody);
                printer.printErrorMessage(message);
            } else {
                message = String.format(
                        "Error (status: %d, reason: %s)%s",
                        response.getStatus(),
                        response.getStatusInfo().getReasonPhrase(),
                        responseBody.isEmpty() ? "" : "\nDetails: " + responseBody);
                printer.printErrorMessage(message);
            }
        }
    }
}
