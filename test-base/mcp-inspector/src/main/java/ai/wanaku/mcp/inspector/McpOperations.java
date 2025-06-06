package ai.wanaku.mcp.inspector;

import io.quarkiverse.mcp.server.test.McpSseClient;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all MCP operations by coordinating between message creation,
 * REST client communication, and response handling.
 */
public class McpOperations {
    private static final Logger LOG = LoggerFactory.getLogger(McpOperations.class);

    private final McpSseClient sseClient;
    private final McpServerRestClient restClient;
    private final String sessionId;
    private final McpMessageFactory messageFactory;

    public McpOperations(McpSseClient sseClient, McpServerRestClient restClient,
                         String sessionId, McpMessageFactory messageFactory) {
        this.sseClient = sseClient;
        this.restClient = restClient;
        this.sessionId = sessionId;
        this.messageFactory = messageFactory;
    }

    /**
     * Creates a sampling message with the provided parameters.
     */
    public JsonObject createSamplingMessage(JsonObject params) {
        LOG.debug("Creating sampling message with params: {}", params);
        return sendRequest(messageFactory.createSamplingMessage(sseClient.nextRequestId(), params));
    }

    /**
     * Lists all available roots.
     */
    public JsonObject listRoots() {
        LOG.debug("Listing roots");
        return sendRequest(messageFactory.createRootsListMessage(sseClient.nextRequestId()));
    }

    /**
     * Lists all available resources.
     */
    public JsonObject listResources() {
        return listResources(null);
    }

    /**
     * Lists available resources with optional pagination.
     */
    public JsonObject listResources(String cursor) {
        LOG.debug("Listing resources with cursor: {}", cursor);
        return sendRequest(messageFactory.createResourcesListMessage(sseClient.nextRequestId(), cursor));
    }

    /**
     * Reads a specific resource by its URI.
     */
    public JsonObject readResource(String uri) {
        LOG.debug("Reading resource: {}", uri);
        return sendRequest(messageFactory.createResourceReadMessage(sseClient.nextRequestId(), uri));
    }

    /**
     * Lists all available resource templates.
     */
    public JsonObject listResourcesTemplates() {
        return listResourcesTemplates(null);
    }

    /**
     * Lists available resource templates with optional pagination.
     */
    public JsonObject listResourcesTemplates(String cursor) {
        LOG.debug("Listing resource templates with cursor: {}", cursor);
        return sendRequest(messageFactory.createResourceTemplatesListMessage(sseClient.nextRequestId(), cursor));
    }

    /**
     * Waits for a list changed notification from the server.
     */
    public JsonObject listChangedNotification() {
        LOG.debug("Waiting for list changed notification");
        return sseClient.waitForLastResponse();
    }

    /**
     * Subscribes to changes for a specific resource.
     */
    public JsonObject resourceSubscribe(String uri) {
        LOG.debug("Subscribing to resource: {}", uri);
        return sendRequest(messageFactory.createResourceSubscribeMessage(sseClient.nextRequestId(), uri));
    }

    /**
     * Lists all available prompts.
     */
    public JsonObject listPrompts() {
        return listPrompts(null);
    }

    /**
     * Lists available prompts with optional pagination.
     */
    public JsonObject listPrompts(String cursorValue) {
        LOG.debug("Listing prompts with cursor: {}", cursorValue);
        return sendRequest(messageFactory.createPromptsListMessage(sseClient.nextRequestId(), cursorValue));
    }

    /**
     * Retrieves a specific prompt with arguments.
     */
    public JsonObject getPrompt(JsonObject params) {
        LOG.debug("Getting prompt with params: {}", params);
        return sendRequest(messageFactory.createPromptGetMessage(sseClient.nextRequestId(), params));
    }

    /**
     * Lists all available tools.
     */
    public JsonObject listTools() {
        return listTools(null);
    }

    /**
     * Lists available tools with optional pagination.
     */
    public JsonObject listTools(String cursorValue) {
        LOG.debug("Listing tools with cursor: {}", cursorValue);
        return sendRequest(messageFactory.createToolsListMessage(sseClient.nextRequestId(), cursorValue));
    }

    /**
     * Invokes a tool with the specified parameters.
     */
    public JsonObject callTool(JsonObject params) {
        LOG.debug("Calling tool with params: {}", params);
        return sendRequest(messageFactory.createToolCallMessage(sseClient.nextRequestId(), params));
    }

    /**
     * Gets completion suggestions for a specific reference.
     */
    public JsonObject completions(JsonObject params) {
        LOG.debug("Getting completions with params: {}", params);
        return sendRequest(messageFactory.createCompletionMessage(sseClient.nextRequestId(), params));
    }

    /**
     * Sets the logging level for the MCP server.
     */
    public JsonObject setLogLevel(String level) {
        LOG.debug("Setting log level to: {}", level);
        return sendRequest(messageFactory.createLogLevelMessage(sseClient.nextRequestId(), level));
    }

    /**
     * Sends a request to the MCP server and waits for the response.
     *
     * @param message The message to send
     * @return The server response
     */
    private JsonObject sendRequest(JsonObject message) {
        try {
            LOG.trace("Sending request: {}", message);
            restClient.messages(sessionId, message);
            JsonObject response = sseClient.waitForLastResponse();
            LOG.trace("Received response: {}", response);
            return response;
        } catch (Exception e) {
            LOG.error("Error sending request: {}", message, e);
            throw new RuntimeException("Failed to send MCP request", e);
        }
    }
}