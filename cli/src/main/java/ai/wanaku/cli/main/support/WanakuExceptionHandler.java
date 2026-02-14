package ai.wanaku.cli.main.support;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Centralized exception handler for the Wanaku CLI.
 *
 * <p>This handler provides user-friendly error messages for common exceptions that occur
 * during command execution, replacing verbose stack traces with actionable error information.
 * It implements Picocli's {@link IExecutionExceptionHandler} interface to intercept exceptions
 * before they reach the user.</p>
 *
 * <p>Supported exception types:</p>
 * <ul>
 *   <li>{@link ConnectException} - Connection refused or service unreachable</li>
 *   <li>{@link UnknownHostException} - DNS resolution failures</li>
 *   <li>{@link SocketTimeoutException} - Request timeouts</li>
 *   <li>{@link WebApplicationException} - HTTP error responses</li>
 *   <li>{@link ProcessingException} - REST client processing errors</li>
 * </ul>
 *
 * <p>The handler can operate in two modes:</p>
 * <ul>
 *   <li><strong>Normal mode</strong>: Displays concise, user-friendly error messages</li>
 *   <li><strong>Verbose mode</strong>: Shows full stack traces for debugging</li>
 * </ul>
 */
public class WanakuExceptionHandler implements IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult)
            throws Exception {
        try (Terminal terminal = WanakuPrinter.terminalInstance()) {
            WanakuPrinter printer = new WanakuPrinter(null, terminal);

            // Check if verbose mode is enabled from the parse result
            boolean verbose = isVerbose(parseResult);

            // If verbose mode is enabled, show full stack trace
            if (verbose) {
                printer.printErrorMessage("Error: " + ex.getMessage());
                ex.printStackTrace(terminal.writer());
                terminal.writer().flush();
                return BaseCommand.EXIT_ERROR;
            }

            // Handle specific exception types with user-friendly messages
            if (ex instanceof ProcessingException && ex.getCause() instanceof ConnectException) {
                handleConnectException((ConnectException) ex.getCause(), printer, parseResult);
            } else if (ex instanceof ConnectException) {
                handleConnectException((ConnectException) ex, printer, parseResult);
            } else if (ex instanceof ProcessingException && ex.getCause() instanceof UnknownHostException) {
                handleUnknownHostException((UnknownHostException) ex.getCause(), printer);
            } else if (ex instanceof UnknownHostException) {
                handleUnknownHostException((UnknownHostException) ex, printer);
            } else if (ex instanceof ProcessingException && ex.getCause() instanceof SocketTimeoutException) {
                handleSocketTimeoutException((SocketTimeoutException) ex.getCause(), printer, parseResult);
            } else if (ex instanceof SocketTimeoutException) {
                handleSocketTimeoutException((SocketTimeoutException) ex, printer, parseResult);
            } else if (ex instanceof WebApplicationException) {
                handleWebApplicationException((WebApplicationException) ex, printer);
            } else if (ex instanceof ProcessingException) {
                handleProcessingException((ProcessingException) ex, printer);
            } else {
                // Generic error handling
                handleGenericException(ex, printer);
            }

            terminal.flush();
            return BaseCommand.EXIT_ERROR;
        }
    }

    private void handleConnectException(ConnectException ex, WanakuPrinter printer, ParseResult parseResult) {
        String host = extractHostOption(parseResult);
        String message = "Unable to connect to Wanaku service" + (host != null ? " at " + host : "")
                + "\n\nPossible causes:\n"
                + "  - Service is not running\n"
                + "  - Incorrect --host URL\n"
                + "  - Network connectivity issues\n";
        printer.printErrorMessage(message);
    }

    private void handleUnknownHostException(UnknownHostException ex, WanakuPrinter printer) {
        String message = "Unable to resolve host: " + ex.getMessage()
                + "\n\nPossible causes:\n"
                + "  - Invalid hostname in --host option\n"
                + "  - DNS resolution failure\n"
                + "  - Network connectivity issues";
        printer.printErrorMessage(message);
    }

    private void handleSocketTimeoutException(
            SocketTimeoutException ex, WanakuPrinter printer, ParseResult parseResult) {
        String host = extractHostOption(parseResult);
        String message = "Request timed out" + (host != null ? " connecting to " + host : "")
                + "\n\nPossible causes:\n"
                + "  - Service is not responding\n"
                + "  - Network latency issues\n"
                + "  - Firewall blocking the connection";
        printer.printErrorMessage(message);
    }

    private void handleWebApplicationException(WebApplicationException ex, WanakuPrinter printer) throws IOException {
        // Delegate to existing handler
        ResponseHelper.commonResponseErrorHandler(ex.getResponse());
    }

    private void handleProcessingException(ProcessingException ex, WanakuPrinter printer) {
        String message = "Failed to process request: " + ex.getMessage();
        if (ex.getCause() != null) {
            message += "\n\nCause: " + ex.getCause().getMessage();
        }
        printer.printErrorMessage(message);
    }

    private void handleGenericException(Exception ex, WanakuPrinter printer) {
        String message = "An error occurred: " + ex.getMessage() + "\n\nRun with --verbose flag for more details";
        printer.printErrorMessage(message);
    }

    /**
     * Checks if verbose mode is enabled from the parse result.
     *
     * @param parseResult the command line parse result
     * @return true if --verbose flag is present, false otherwise
     */
    private boolean isVerbose(ParseResult parseResult) {
        try {
            if (parseResult.hasMatchedOption("--verbose")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore and return false if unable to check verbose flag
        }
        return false;
    }

    /**
     * Extracts the --host option value from the parse result if available.
     *
     * @param parseResult the command line parse result
     * @return the host value, or null if not found
     */
    private String extractHostOption(ParseResult parseResult) {
        try {
            if (parseResult.hasMatchedOption("--host")) {
                return parseResult.matchedOption("--host").getValue();
            }
        } catch (Exception e) {
            // Ignore and return null if unable to extract host
        }
        return null;
    }
}
