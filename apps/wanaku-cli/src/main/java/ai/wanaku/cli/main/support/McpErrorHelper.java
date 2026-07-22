package ai.wanaku.cli.main.support;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;

/**
 * Translates MCP client exceptions into user-friendly error messages.
 *
 * <p>MCP client operations often throw wrapped exceptions (e.g.,
 * {@code ExecutionException} wrapping {@code ConnectException}) whose default
 * messages expose raw Java class names. This helper unwraps the cause chain
 * and produces concise, actionable messages consistent with the rest of
 * the Wanaku CLI.</p>
 */
public final class McpErrorHelper {

    private McpErrorHelper() {}

    /**
     * Produces a user-friendly error message for a tool-call failure.
     *
     * <p>Handles MCP-specific exceptions that carry structured error information
     * (error codes, tool names) and produces actionable messages.</p>
     *
     * @param ex the tool execution exception (either {@code ToolExecutionException}
     *           or {@code ToolArgumentsException})
     * @param toolName the name of the tool that was being called (may be null)
     * @return a user-friendly error message
     */
    public static String friendlyToolCallMessage(ToolExecutionException ex, String toolName) {
        return buildToolCallMessage(ex.getMessage(), ex.errorCode(), toolName);
    }

    /**
     * Produces a user-friendly error message for a tool-arguments failure.
     *
     * @param ex the tool arguments exception
     * @param toolName the name of the tool that was being called (may be null)
     * @return a user-friendly error message
     */
    public static String friendlyToolCallMessage(ToolArgumentsException ex, String toolName) {
        return buildToolCallMessage(ex.getMessage(), ex.errorCode(), toolName);
    }

    private static String buildToolCallMessage(String message, Integer errorCode, String toolName) {
        StringBuilder sb = new StringBuilder();

        if (message != null && !message.isBlank()) {
            sb.append(message);
        } else if (toolName != null) {
            sb.append("Tool call failed: ").append(toolName);
        } else {
            sb.append("Tool call failed");
        }

        if (errorCode != null && errorCode == -32602) {
            sb.append("\n\nPossible causes:\n");
            sb.append("  - Tool name is not registered on the MCP server\n");
            sb.append("  - Invalid tool arguments");
        }

        return sb.toString();
    }

    /**
     * Produces a user-friendly error message for the given exception and
     * MCP server URI.
     *
     * @param ex the exception thrown during MCP client operations
     * @param uri the MCP server URI the command was targeting (may be null)
     * @return a user-friendly error message
     */
    public static String friendlyMessage(Exception ex, String uri) {
        Throwable root = rootCause(ex);

        if (root instanceof ConnectException) {
            return connectionRefusedMessage(uri);
        }

        if (root instanceof UnknownHostException) {
            return unknownHostMessage(root);
        }

        if (root instanceof SocketTimeoutException) {
            return timeoutMessage(uri);
        }

        if (root instanceof IllegalArgumentException) {
            String msg = root.getMessage();
            if (msg != null && (msg.contains("URI") || msg.contains("scheme") || msg.contains("url"))) {
                return invalidUriMessage(uri);
            }
        }

        // For any other exception, return the root cause message without class name prefixes
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getMessage();
        }
        if (message == null || message.isBlank()) {
            return "An unexpected error occurred. Run with --verbose for more details";
        }
        return message;
    }

    private static String connectionRefusedMessage(String uri) {
        StringBuilder sb = new StringBuilder();
        sb.append("Connection refused");
        if (uri != null) {
            sb.append(": could not connect to ").append(uri);
        }
        sb.append("\n\nPossible causes:\n");
        sb.append("  - MCP server is not running\n");
        sb.append("  - Incorrect --uri value\n");
        sb.append("  - Network connectivity issues");
        return sb.toString();
    }

    private static String unknownHostMessage(Throwable root) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unable to resolve host: ").append(root.getMessage());
        sb.append("\n\nPossible causes:\n");
        sb.append("  - Invalid hostname in --uri option\n");
        sb.append("  - DNS resolution failure\n");
        sb.append("  - Network connectivity issues");
        return sb.toString();
    }

    private static String timeoutMessage(String uri) {
        StringBuilder sb = new StringBuilder();
        sb.append("Request timed out");
        if (uri != null) {
            sb.append(" connecting to ").append(uri);
        }
        sb.append("\n\nPossible causes:\n");
        sb.append("  - MCP server is not responding\n");
        sb.append("  - Network latency issues\n");
        sb.append("  - Firewall blocking the connection");
        return sb.toString();
    }

    private static String invalidUriMessage(String uri) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid URI");
        if (uri != null) {
            sb.append(": '").append(uri).append("' is not a valid MCP server URI");
        }
        sb.append("\n\nExpected format: http://<host>:<port>/path or https://<host>:<port>/path");
        return sb.toString();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
