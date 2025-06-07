package ai.wanaku.mcp.inspector;

import io.quarkiverse.mcp.server.test.McpSseClient;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 extension that provides Model Context Protocol (MCP) testing capabilities.
 * Handles connection setup, initialization, and cleanup for MCP server testing.
 */
public class ModelContextProtocolExtension implements AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(ModelContextProtocolExtension.class);

    // Configuration properties with default values
    private String mcpUri = System.getProperty("mcp.uri", "http://localhost:8080/");
    private String mcpContextPath = System.getProperty("mcp.context-path", "mcp/sse");

    private boolean initialized = false;

    // Core components
    private McpSseClient client;
    private McpServerRestClient mcpServerRestClient;
    private String sessionId;

    // Helper classes
    private McpConnectionManager connectionManager;
    private McpMessageFactory messageFactory;
    private McpOperations operations;

    public ModelContextProtocolExtension(int port) {
        mcpUri = String.format("http://localhost:%d/", port);
    }

    public ModelContextProtocolExtension() {
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private void initialize() {
        LOG.info("Initializing MCP extension");

        // Initialize helper components
        this.messageFactory = new McpMessageFactory();
        this.connectionManager = new McpConnectionManager(mcpUri, mcpContextPath, messageFactory);

        // Establish connection and initialize session
        McpConnectionManager.ConnectionResult connectionResult = null;
        try {
            connectionResult = connectionManager.connect();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.client = connectionResult.client();
        this.mcpServerRestClient = connectionResult.restClient();
        this.sessionId = connectionResult.sessionId();

        // Initialize operations handler
        this.operations = new McpOperations(client, mcpServerRestClient, sessionId, messageFactory);

        LOG.info("MCP extension initialized successfully with session ID: {}", sessionId);

        initialized = true;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        LOG.info("Cleaning up MCP extension for test context: {}", context.getDisplayName());

        if (client != null) {
            client.clearRequests();
        }

        LOG.info("MCP extension cleanup completed");
    }

    // Delegate methods to operations handler

    /**
     * Creates a sampling message with the provided parameters.
     *
     * @param params The sampling parameters
     * @return The server response
     */
    public JsonObject createSamplingMessage(JsonObject params) {
        ensureInitialized();
        return operations.createSamplingMessage(params);
    }

    /**
     * Lists all available roots.
     *
     * @return The server response containing available roots
     */
    public JsonObject listRoots() {
        ensureInitialized();
        return operations.listRoots();
    }

    /**
     * Lists all available resources.
     *
     * @return The server response containing available resources
     */
    public JsonObject listResources() {
        ensureInitialized();
        return operations.listResources();
    }

    /**
     * Lists available resources with pagination support.
     *
     * @param cursor The pagination cursor
     * @return The server response containing available resources
     */
    public JsonObject listResources(String cursor) {
        ensureInitialized();
        return operations.listResources(cursor);
    }

    /**
     * Reads a specific resource by its URI.
     *
     * @param uri The resource URI
     * @return The server response containing the resource content
     */
    public JsonObject readResource(String uri) {
        ensureInitialized();
        return operations.readResource(uri);
    }

    /**
     * Lists all available resource templates.
     *
     * @return The server response containing available resource templates
     */
    public JsonObject listResourcesTemplates() {
        ensureInitialized();
        return operations.listResourcesTemplates();
    }

    /**
     * Lists available resource templates with pagination support.
     *
     * @param cursor The pagination cursor
     * @return The server response containing available resource templates
     */
    public JsonObject listResourcesTemplates(String cursor) {
        ensureInitialized();
        return operations.listResourcesTemplates(cursor);
    }

    /**
     * Waits for a list changed notification from the server.
     * Servers that declared the listChanged capability should send this notification.
     *
     * @return The notification response
     */
    public JsonObject listChangedNotification() {
        ensureInitialized();
        return operations.listChangedNotification();
    }

    /**
     * Subscribes to changes for a specific resource.
     * The protocol supports optional subscriptions to resource changes.
     *
     * @param uri The resource URI to subscribe to
     * @return The server response
     */
    public JsonObject resourceSubscribe(String uri) {
        ensureInitialized();
        return operations.resourceSubscribe(uri);
    }

    /**
     * Lists all available prompts.
     *
     * @return The server response containing available prompts
     */
    public JsonObject listPrompts() {
        ensureInitialized();
        return operations.listPrompts();
    }

    /**
     * Lists available prompts with pagination support.
     *
     * @param cursorValue The pagination cursor
     * @return The server response containing available prompts
     */
    public JsonObject listPrompts(String cursorValue) {
        ensureInitialized();
        return operations.listPrompts(cursorValue);
    }

    /**
     * Retrieves a specific prompt with arguments.
     * Arguments may be auto-completed through the completion API.
     *
     * Example params:
     * <pre>
     * new JsonObject()
     *     .put("name", "code_review")
     *     .put("arguments", new JsonObject()
     *         .put("code", "def hello():\n    print('world')"));
     * </pre>
     *
     * @param params The prompt parameters including name and arguments
     * @return The server response containing the prompt
     */
    public JsonObject getPrompt(JsonObject params) {
        ensureInitialized();
        return operations.getPrompt(params);
    }

    /**
     * Lists all available tools.
     *
     * @return The server response containing available tools
     */
    public JsonObject listTools() {
        ensureInitialized();
        return operations.listTools();
    }

    /**
     * Lists available tools with pagination support.
     *
     * @param cursorValue The pagination cursor
     * @return The server response containing available tools
     */
    public JsonObject listTools(String cursorValue) {
        ensureInitialized();
        return operations.listTools(cursorValue);
    }

    /**
     * Invokes a tool with the specified parameters.
     *
     * Example params:
     * <pre>
     * new JsonObject()
     *     .put("name", "random-useless-facts")
     *     .put("arguments", new JsonObject()
     *         .put("location", "New York"));
     * </pre>
     *
     * @param params The tool parameters including name and arguments
     * @return The server response from the tool execution
     */
    public JsonObject callTool(JsonObject params) {
        ensureInitialized();
        return operations.callTool(params);
    }

    /**
     * Gets completion suggestions for a specific reference.
     *
     * Example params:
     * <pre>
     * new JsonObject()
     *     .put("ref", new JsonObject()
     *         .put("type", "ref/prompt")
     *         .put("name", "code_review"))
     *     .put("argument", new JsonObject()
     *         .put("name", "language")
     *         .put("value", "py"));
     * </pre>
     *
     * @param params The completion parameters
     * @return The server response containing completion suggestions
     */
    public JsonObject completions(JsonObject params) {
        ensureInitialized();
        return operations.completions(params);
    }

    /**
     * Sets the logging level for the MCP server.
     *
     * @param level The logging level (e.g., "debug", "info", "warn", "error")
     * @return The server response
     */
    public JsonObject setLogLevel(String level) {
        ensureInitialized();
        return operations.setLogLevel(level);
    }
}