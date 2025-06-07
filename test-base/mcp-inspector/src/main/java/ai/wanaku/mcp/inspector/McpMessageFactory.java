package ai.wanaku.mcp.inspector;

import io.vertx.core.json.JsonObject;

/**
 * Factory class for creating standardized MCP (Model Context Protocol) messages.
 * Handles the creation of JSON-RPC 2.0 compliant messages with proper structure.
 */
public class McpMessageFactory {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String CLIENT_NAME = "junit-test-client";
    private static final String CLIENT_VERSION = "0.1";

    /**
     * Creates a basic MCP message with method and optional ID.
     *
     * @param method The MCP method name
     * @param id The request ID (can be null for notifications)
     * @return A JsonObject representing the MCP message
     */
    public JsonObject createMessage(String method, Integer id) {
        JsonObject message = new JsonObject()
                .put("jsonrpc", JSONRPC_VERSION)
                .put("method", method);

        if (id != null) {
            message.put("id", id);
        }

        return message;
    }

    /**
     * Creates an initialization message for establishing MCP connection.
     *
     * @param id The request ID
     * @return A JsonObject representing the initialization message
     */
    public JsonObject createInitializeMessage(Integer id) {
        return createMessage("initialize", id)
                .put("params", createInitializeParams())
                .put("capabilities", createClientCapabilities());
    }

    /**
     * Creates an initialized notification message.
     *
     * @param id The request ID
     * @return A JsonObject representing the initialized notification
     */
    public JsonObject createInitializedNotification(Integer id) {
        return createMessage("notifications/initialized", id);
    }

    /**
     * Creates a sampling message creation request.
     *
     * @param id The request ID
     * @param params The sampling parameters
     * @return A JsonObject representing the sampling message
     */
    public JsonObject createSamplingMessage(Integer id, JsonObject params) {
        return createMessage("sampling/createMessage", id)
                .put("params", params);
    }

    /**
     * Creates a roots list request.
     *
     * @param id The request ID
     * @return A JsonObject representing the roots list request
     */
    public JsonObject createRootsListMessage(Integer id) {
        return createMessage("roots/list", id);
    }

    /**
     * Creates a resources list request with optional pagination.
     *
     * @param id The request ID
     * @param cursor Optional pagination cursor
     * @return A JsonObject representing the resources list request
     */
    public JsonObject createResourcesListMessage(Integer id, String cursor) {
        JsonObject message = createMessage("resources/list", id);
        if (cursor != null) {
            message.put("params", new JsonObject().put("cursor", cursor));
        }
        return message;
    }

    /**
     * Creates a resource read request.
     *
     * @param id The request ID
     * @param uri The resource URI
     * @return A JsonObject representing the resource read request
     */
    public JsonObject createResourceReadMessage(Integer id, String uri) {
        return createMessage("resources/read", id)
                .put("params", new JsonObject().put("uri", uri));
    }

    /**
     * Creates a resource templates list request with optional pagination.
     *
     * @param id The request ID
     * @param cursor Optional pagination cursor
     * @return A JsonObject representing the resource templates list request
     */
    public JsonObject createResourceTemplatesListMessage(Integer id, String cursor) {
        JsonObject message = createMessage("resources/templates/list", id);
        if (cursor != null) {
            message.put("params", new JsonObject().put("cursor", cursor));
        }
        return message;
    }

    /**
     * Creates a resource subscription request.
     *
     * @param id The request ID
     * @param uri The resource URI to subscribe to
     * @return A JsonObject representing the resource subscription request
     */
    public JsonObject createResourceSubscribeMessage(Integer id, String uri) {
        return createMessage("resources/subscribe", id)
                .put("params", new JsonObject().put("uri", uri));
    }

    /**
     * Creates a prompts list request with optional pagination.
     *
     * @param id The request ID
     * @param cursor Optional pagination cursor
     * @return A JsonObject representing the prompts list request
     */
    public JsonObject createPromptsListMessage(Integer id, String cursor) {
        JsonObject message = createMessage("prompts/list", id);
        if (cursor != null) {
            message.put("params", new JsonObject().put("cursor", cursor));
        }
        return message;
    }

    /**
     * Creates a prompt get request.
     *
     * @param id The request ID
     * @param params The prompt parameters (name and arguments)
     * @return A JsonObject representing the prompt get request
     */
    public JsonObject createPromptGetMessage(Integer id, JsonObject params) {
        return createMessage("prompts/get", id)
                .put("params", params);
    }

    /**
     * Creates a tools list request with optional pagination.
     *
     * @param id The request ID
     * @param cursor Optional pagination cursor
     * @return A JsonObject representing the tools list request
     */
    public JsonObject createToolsListMessage(Integer id, String cursor) {
        JsonObject message = createMessage("tools/list", id);
        if (cursor != null) {
            message.put("params", new JsonObject().put("cursor", cursor));
        }
        return message;
    }

    /**
     * Creates a tool call request.
     *
     * @param id The request ID
     * @param params The tool parameters (name and arguments)
     * @return A JsonObject representing the tool call request
     */
    public JsonObject createToolCallMessage(Integer id, JsonObject params) {
        return createMessage("tools/call", id)
                .put("params", params);
    }

    /**
     * Creates a completion request.
     *
     * @param id The request ID
     * @param params The completion parameters
     * @return A JsonObject representing the completion request
     */
    public JsonObject createCompletionMessage(Integer id, JsonObject params) {
        return createMessage("completion/complete", id)
                .put("params", params);
    }

    /**
     * Creates a log level setting request.
     *
     * @param id The request ID
     * @param level The log level
     * @return A JsonObject representing the log level request
     */
    public JsonObject createLogLevelMessage(Integer id, String level) {
        return createMessage("logging/setLevel", id)
                .put("params", new JsonObject().put("level", level));
    }

    /**
     * Creates initialization parameters for the MCP client.
     *
     * @return JsonObject containing client info and protocol version
     */
    private JsonObject createInitializeParams() {
        return new JsonObject()
                .put("clientInfo", new JsonObject()
                        .put("name", CLIENT_NAME)
                        .put("version", CLIENT_VERSION))
                .put("protocolVersion", PROTOCOL_VERSION);
    }

    /**
     * Creates client capabilities object.
     *
     * @return JsonObject containing client capabilities
     */
    private JsonObject createClientCapabilities() {
        return new JsonObject()
                .put("sampling", new JsonObject())
                .put("roots", new JsonObject()
                        .put("listChanged", true));
    }
}