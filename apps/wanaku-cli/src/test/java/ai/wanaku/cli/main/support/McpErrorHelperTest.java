package ai.wanaku.cli.main.support;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpErrorHelperTest {

    @Test
    @DisplayName("Should produce friendly message for ConnectException")
    void shouldHandleConnectException() {
        Exception ex = new ConnectException("Connection refused");
        String message = McpErrorHelper.friendlyMessage(ex, "http://localhost:59999/mcp");

        assertTrue(message.contains("Connection refused"));
        assertTrue(message.contains("http://localhost:59999/mcp"));
        assertTrue(message.contains("MCP server is not running"));
        assertFalse(message.contains("java.net.ConnectException"));
    }

    @Test
    @DisplayName("Should produce friendly message for wrapped ConnectException")
    void shouldHandleWrappedConnectException() {
        ConnectException cause = new ConnectException("Connection refused");
        Exception ex = new ExecutionException("java.net.ConnectException: Connection refused", cause);
        String message = McpErrorHelper.friendlyMessage(ex, "http://localhost:59999/mcp");

        assertTrue(message.contains("Connection refused"));
        assertTrue(message.contains("http://localhost:59999/mcp"));
        assertTrue(message.contains("Possible causes"));
        assertFalse(message.contains("ExecutionException"));
    }

    @Test
    @DisplayName("Should produce friendly message for IllegalArgumentException with URI issue")
    void shouldHandleInvalidUri() {
        Exception ex = new IllegalArgumentException("URI with undefined scheme");
        String message = McpErrorHelper.friendlyMessage(ex, "not-a-valid-uri");

        assertTrue(message.contains("Invalid URI"));
        assertTrue(message.contains("not-a-valid-uri"));
        assertTrue(message.contains("not a valid MCP server URI"));
        assertFalse(message.contains("IllegalArgumentException"));
    }

    @Test
    @DisplayName("Should produce friendly message for UnknownHostException")
    void shouldHandleUnknownHost() {
        Exception ex = new UnknownHostException("nonexistent.host.example");
        String message = McpErrorHelper.friendlyMessage(ex, "http://nonexistent.host.example:8080/mcp");

        assertTrue(message.contains("Unable to resolve host"));
        assertTrue(message.contains("nonexistent.host.example"));
        assertTrue(message.contains("DNS resolution failure"));
    }

    @Test
    @DisplayName("Should produce friendly message for wrapped UnknownHostException")
    void shouldHandleWrappedUnknownHost() {
        UnknownHostException cause = new UnknownHostException("badhost.example");
        Exception ex = new ExecutionException("lookup failed", cause);
        String message = McpErrorHelper.friendlyMessage(ex, "http://badhost.example:8080/mcp");

        assertTrue(message.contains("Unable to resolve host"));
        assertTrue(message.contains("badhost.example"));
    }

    @Test
    @DisplayName("Should produce friendly message for SocketTimeoutException")
    void shouldHandleTimeout() {
        Exception ex = new SocketTimeoutException("Read timed out");
        String message = McpErrorHelper.friendlyMessage(ex, "http://localhost:8080/mcp");

        assertTrue(message.contains("Request timed out"));
        assertTrue(message.contains("http://localhost:8080/mcp"));
        assertTrue(message.contains("MCP server is not responding"));
    }

    @Test
    @DisplayName("Should pass through plain message for unrecognized exceptions")
    void shouldPassThroughGenericException() {
        Exception ex = new RuntimeException("something went wrong");
        String message = McpErrorHelper.friendlyMessage(ex, "http://localhost:8080/mcp");

        assertTrue(message.contains("something went wrong"));
    }

    @Test
    @DisplayName("Should handle exception with null message")
    void shouldHandleNullMessage() {
        Exception ex = new RuntimeException((String) null);
        String message = McpErrorHelper.friendlyMessage(ex, "http://localhost:8080/mcp");

        assertTrue(message.contains("unexpected error"));
    }

    @Test
    @DisplayName("Should handle ConnectException with null URI")
    void shouldHandleConnectExceptionWithNullUri() {
        Exception ex = new ConnectException("Connection refused");
        String message = McpErrorHelper.friendlyMessage(ex, null);

        assertTrue(message.contains("Connection refused"));
        assertTrue(message.contains("MCP server is not running"));
    }

    @Test
    @DisplayName("Should handle deeply wrapped ConnectException")
    void shouldHandleDeeplyWrappedConnectException() {
        ConnectException root = new ConnectException("Connection refused");
        Exception mid = new ExecutionException("inner", root);
        Exception outer = new RuntimeException("outer", mid);
        String message = McpErrorHelper.friendlyMessage(outer, "http://localhost:59999/mcp");

        assertTrue(message.contains("Connection refused"));
        assertTrue(message.contains("Possible causes"));
        assertFalse(message.contains("ExecutionException"));
        assertFalse(message.contains("RuntimeException"));
    }

    @Test
    @DisplayName("Should produce friendly message for ToolArgumentsException with invalid tool name")
    void shouldHandleToolArgumentsExceptionForInvalidTool() {
        ToolArgumentsException ex = new ToolArgumentsException("Invalid tool name: nonexistent", -32602);
        String message = McpErrorHelper.friendlyToolCallMessage(ex, "nonexistent");

        assertTrue(message.contains("Invalid tool name"));
        assertTrue(message.contains("Tool name is not registered"));
    }

    @Test
    @DisplayName("Should produce friendly message for ToolExecutionException")
    void shouldHandleToolExecutionException() {
        ToolExecutionException ex = new ToolExecutionException("tool failed");
        String message = McpErrorHelper.friendlyToolCallMessage(ex, "my-tool");

        assertTrue(message.contains("tool failed"));
    }

    @Test
    @DisplayName("Should produce friendly message for ToolExecutionException with null message")
    void shouldHandleToolExecutionExceptionWithNullMessage() {
        ToolExecutionException ex = new ToolExecutionException((String) null);
        String message = McpErrorHelper.friendlyToolCallMessage(ex, "my-tool");

        assertTrue(message.contains("Tool call failed"));
        assertTrue(message.contains("my-tool"));
    }
}
